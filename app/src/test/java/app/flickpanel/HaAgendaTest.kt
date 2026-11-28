package app.flickpanel

import app.flickpanel.ha.AgendaMoment
import app.flickpanel.ha.HaClient
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.HaProblem
import app.flickpanel.ha.Link
import app.flickpanel.ha.decodeAgendaEvents
import app.flickpanel.ha.decodeAgendaTasks
import app.flickpanel.ha.parseServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.LinkedBlockingQueue

class HaAgendaTest {
    @Test fun calendarKeepsAllDayDatesAndRespectsTimedOffsets() {
        val london = ZoneId.of("Europe/London")
        val rows = JSONArray("""[
            {"summary":"Morning","start":"2026-03-29T09:00:00+01:00","end":"2026-03-29T10:00:00+01:00"},
            {"summary":"Holiday","start":"2026-03-29","end":"2026-03-30"},
            {"summary":"Reminder","start":"2026-03-29T10:00:00+01:00","end":"2026-03-29T10:00:00+01:00"},
            {"summary":"Single date","start":"2026-03-30","end":"2026-03-30"},
            {"summary":"Bad range","start":"2026-03-29","end":"2026-03-28"},
            {"summary":"Bad type","start":"2026-03-29","end":"2026-03-30T00:00:00+01:00"}
        ]""")
        val events = decodeAgendaEvents(rows, london)
        assertEquals(listOf("Holiday", "Morning", "Reminder", "Single date"), events.map { it.summary })
        assertEquals(AgendaMoment.AllDay(LocalDate.parse("2026-03-29")), events.first().start)
        assertEquals(Instant.parse("2026-03-29T23:00:00Z"), events.first().end.atStart(london))
        assertEquals(AgendaMoment.Timed(Instant.parse("2026-03-29T08:00:00Z")), events[1].start)
        assertEquals(events[2].start, events[2].end)
        assertEquals(AgendaMoment.AllDay(LocalDate.parse("2026-03-31")), events.last().end)
    }

    @Test fun taskRowsKeepDuplicateNamesAndRejectMalformedItems() {
        val rows = JSONArray("""[
            {"uid":"done","summary":"Shopping","status":"completed"},
            {"uid":"open","summary":"Shopping","status":"needs_action","due":"2026-11-23"},
            {"uid":"work","summary":"Pay bill","status":"needs_action","due":"2026-11-25T20:30:00-05:00"},
            {"uid":"invalid","summary":"Invalid status","status":"unknown"},
            {"summary":"No identifier","status":"needs_action"}
        ]""")
        val tasks = decodeAgendaTasks(rows)
        assertEquals(listOf("open", "work", "done"), tasks.map { it.uid })
        assertEquals(AgendaMoment.AllDay(LocalDate.parse("2026-11-23")), tasks.first().due)
        assertEquals(AgendaMoment.Timed(Instant.parse("2026-11-26T01:30:00Z")), tasks[1].due)
        assertTrue(tasks.last().completed)
    }

    @Test fun agendaUsesTargetedServicesAndChecksTaskCapabilities() = runBlocking {
        val requests = LinkedBlockingQueue<JSONObject>()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(AgendaFixture(requests)))
            server.start()
            val address = parseServer(server.url("/").newBuilder().host("127.0.0.1").build().toString())
            val client = HaClient(HaCredentials(address, "agenda-fixture-token"), this)
            try {
                client.select(setOf("todo.household", "todo.read_only", "calendar.family"))
                client.start()
                withTimeout(6_000) { client.connectionState.first { it.link == Link.ONLINE } }
                val list = client.tasks("todo.household")
                val start = Instant.parse("2026-11-21T07:00:00Z")
                val end = Instant.parse("2026-11-27T07:00:00Z")
                val events = client.calendar("calendar.family", start, end)
                assertEquals("uid-1", list.single().uid)
                assertEquals("Appointment", events.single().summary)
                client.setTaskCompleted("todo.household", list.single().uid, true)
                client.addTask("todo.household", "  Buy milk  ")
                assertTrue(runCatching { client.addTask("todo.read_only", "No permission") }.exceptionOrNull() is HaProblem)
                assertTrue(runCatching { client.addTask("todo.household", "   ") }.exceptionOrNull() is HaProblem)
                val sent = requests.toList()
                assertEquals(listOf("get_items", "get_events", "update_item", "add_item"), sent.map { it.getString("service") })
                assertTrue(sent[0].getBoolean("return_response"))
                assertEquals(listOf("needs_action", "completed"), sent[0].getJSONObject("service_data").getJSONArray("status")
                    .let { listOf(it.getString(0), it.getString(1)) })
                assertTrue(sent[1].getBoolean("return_response"))
                assertEquals("calendar.family", sent[1].getJSONObject("target").getString("entity_id"))
                assertEquals(start.toString(), sent[1].getJSONObject("service_data").getString("start_date_time"))
                assertEquals(end.toString(), sent[1].getJSONObject("service_data").getString("end_date_time"))
                assertFalse(sent[2].has("return_response"))
                assertEquals("uid-1", sent[2].getJSONObject("service_data").getString("item"))
                assertEquals("completed", sent[2].getJSONObject("service_data").getString("status"))
                assertEquals("Buy milk", sent[3].getJSONObject("service_data").getString("item"))
            } finally {
                client.stop()
            }
        }
    }
}

private class AgendaFixture(private val requests: LinkedBlockingQueue<JSONObject>) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send("""{"type":"auth_required"}""")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val message = JSONObject(text)
        val id = message.optInt("id")
        fun result(payload: String = "null") =
            webSocket.send("""{"id":$id,"type":"result","success":true,"result":$payload}""")
        when (message.optString("type")) {
            "auth" -> webSocket.send("""{"type":"auth_ok"}""")
            "subscribe_entities" -> {
                result()
                webSocket.send("""{"id":$id,"type":"event","event":{"a":{
                    "todo.household":{"s":"1","a":{"supported_features":5}},
                    "todo.read_only":{"s":"0","a":{"supported_features":0}},
                    "calendar.family":{"s":"off"}
                }}}""")
            }
            "call_service" -> {
                requests.add(message)
                when (message.getString("service")) {
                    "get_items" -> result("""{"response":{"todo.household":{"items":[
                        {"uid":"uid-1","summary":"First task","status":"needs_action"}
                    ]}}}""")
                    "get_events" -> result("""{"response":{"calendar.family":{"events":[
                        {"summary":"Appointment","start":"2026-11-22T12:00:00Z","end":"2026-11-22T12:30:00Z"}
                    ]}}}""")
                    else -> result()
                }
            }
        }
    }
}
