package app.flickpanel

import app.flickpanel.ha.Entity
import app.flickpanel.ha.ReadingContent
import app.flickpanel.ha.readContent
import app.flickpanel.ha.readerAttributes
import app.flickpanel.ha.supportsReader
import app.flickpanel.ha.validReaderAttribute
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaReadingTest {
    @Test fun stateAndSelectedStringAttributeCanBeRead() {
        val recipe = Entity("sensor.recipe", "Tomato soup", JSONObject()
            .put("instructions", "Ingredients\r\nTomatoes\r\n\rSimmer for 25 minutes\u0000")
            .put("servings", 4)
            .put("ingredients", JSONArray(listOf("tomatoes", "oil")))
            .put("notes", "  "))

        assertEquals(ReadingContent("Tomato soup", false), readContent(recipe, null))
        assertEquals(
            ReadingContent("Ingredients\nTomatoes\n\nSimmer for 25 minutes", false),
            readContent(recipe, "instructions"),
        )
        assertEquals("Ingredients Tomatoes Simmer for 25 minutes", readContent(recipe, "instructions")?.preview)
        assertEquals(listOf("instructions"), recipe.readerAttributes())
        for (attribute in listOf("servings", "ingredients", "notes", "missing")) {
            assertNull(readContent(recipe, attribute))
        }
    }

    @Test fun unavailableAndPasswordInputsNeverShowText() {
        val attributes = JSONObject().put("message", "Old text")
        assertNull(readContent(Entity("sensor.notice", "unavailable", attributes), "message"))
        assertNull(readContent(Entity("switch.notice", "on", attributes), null))
        assertNull(readContent(Entity("sensor.notice", "unknown"), null))

        val password = Entity("input_text.pin", "0208", attributes.put("mode", "password"))
        assertFalse(password.supportsReader)
        assertTrue(password.readerAttributes().isEmpty())
        assertNull(readContent(password, null))
        assertNull(readContent(password, "message"))
        assertTrue(Entity("input_text.notice", "Take the bins out").supportsReader)
    }

    @Test fun metadataAndCredentialAttributesCannotBeSelectedOrRead() {
        val hidden = listOf("friendly_name", "entity_picture", "accessToken", "API_KEY", "login-password", "private key")
        val attributes = JSONObject().put("Préparation", "Recipe instructions")
        hidden.forEach { attributes.put(it, "Hidden text") }
        val entity = Entity("sensor.recipe", "Soup", attributes)

        assertEquals(listOf("Préparation"), entity.readerAttributes())
        assertTrue(validReaderAttribute("Préparation"))
        for (attribute in hidden + listOf(" ", "embedded\nnewline", "x".repeat(129))) {
            assertFalse(validReaderAttribute(attribute))
            assertNull(readContent(entity, attribute))
        }
    }

    @Test fun lengthLimitsNeverSplitSurrogatePairs() {
        val prefix = "a".repeat(23_999)
        val recipe = Entity("sensor.recipe", "Soup", JSONObject().put("instructions", prefix + "🍲"))
        assertEquals(ReadingContent(prefix, true), readContent(recipe, "instructions"))

        val previewPrefix = "a".repeat(279)
        val reading = ReadingContent(previewPrefix + "🍲 to serve", false)
        assertEquals(previewPrefix + "…", reading.preview)
    }
}
