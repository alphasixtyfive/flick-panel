package app.flickpanel.config

import app.flickpanel.ha.PhotoFolder
import app.flickpanel.ha.isPhotoSourceId
import app.flickpanel.ha.validReaderAttribute
import org.json.JSONArray
import org.json.JSONObject

private val areaPattern = Regex("[A-Za-z0-9_-]{1,128}")
internal fun validAreaId(areaId: String): Boolean = areaPattern.matches(areaId)

internal object PanelConfigCodec {
    fun encode(config: PanelConfig): String = JSONObject()
        .put("version", 1)
        .put("tiles", JSONArray(config.favourites.map { tile ->
            JSONObject().put("entity", tile.entity).put("kind", tile.kind.name).put("label", tile.label)
                .put("status", tile.statusEntity).put("home", tile.showOnHome)
                .put("stream", tile.cameraStream)
                .apply {
                    if (tile.wideOnTablet) put("wide", true)
                    if (tile.kind.supportsIcon) tile.icon?.let { put("icon", it.name) }
                    if (tile.confirmAction) put("confirm", true)
                    tile.readerAttribute?.let { put("readerAttribute", it) }
                    if (tile.kind.supportsAccent) tile.accent?.let { put(tile.kind.accentKey, it.name) }
                    if (tile.kind == TileKind.VACUUM) {
                        put("vacuumMap", tile.vacuumMap).put("vacuumBattery", tile.vacuumBattery)
                    }
                    if (tile.kind == TileKind.CAMERA) {
                        tile.intercomStream?.let { put("intercom", it) }
                        if (tile.cameraActions.isNotEmpty()) put("cameraActions", JSONArray(tile.cameraActions.map {
                            JSONObject().put("entity", it.entity).put("label", it.label).apply {
                                if (it.icon != CameraActionIcon.AUTOMATIC) put("icon", it.icon.name)
                            }
                        }))
                    }
                }
        }))
        .put("theme", config.theme.name)
        .put("message", config.messageEntity)
        .put("screensaverMessage", config.screensaverMessageEntity ?: JSONObject.NULL)
        .put("homeBadges", config.homeBadges.takeIf { it.isNotEmpty() }?.let { badges ->
            JSONArray(badges.map { badge ->
                JSONObject().put("entity", badge.entity).put("label", badge.label)
                    .put("states", JSONArray(badge.activeStates))
            })
        })
        .put("idle", config.idleSeconds)
        .put("wall", config.wallMode)
        .put("dashboardBrightness", config.dashboardBrightness)
        .put("screensaverBrightness", config.screensaverBrightness)
        .put("highContrast", config.highContrast)
        .put("reduceMotion", config.reduceMotion)
        .put("rooms", config.roomIds?.let(::JSONArray))
        .put("roomHeating", config.showRoomHeating)
        .put("roomTiles", config.roomTiles.takeIf { it.isNotEmpty() }?.let { rooms ->
            JSONObject(rooms.mapValues { (_, controls) ->
                JSONArray(controls.map { control ->
                    JSONObject().put("entity", control.entity).put("format", control.lightFormat?.name).apply {
                        if (control.wideOnTablet) put("wide", true)
                        if (control.hidden) put("hidden", true)
                        control.label?.let { put("label", it) }
                        control.accent?.let { put("accent", it.name) }
                        control.icon?.let { put("icon", it.name) }
                        if (control.followsArea) put("followsArea", true)
                    }
                })
            })
        })
        .put("idleAlbum", config.idleAlbum?.let { JSONObject().put("id", it.id).put("title", it.title) })
        .toString()

    fun decode(json: String): PanelConfig {
        val obj = JSONObject(json)
        require(obj.optInt("version") == 1) { "This layout version requires a newer Flick Panel" }
        val list = obj.optJSONArray("tiles") ?: JSONArray()
        val tiles = (0 until list.length()).mapNotNull { index ->
            val tile = list.optJSONObject(index) ?: return@mapNotNull null
            val kind = TileKind.entries.firstOrNull { it.name == tile.optString("kind") } ?: return@mapNotNull null
            val entity = tile.optString("entity")
                .takeIf { if (kind == TileKind.APP) validAppTarget(it) else validEntity(it) } ?: return@mapNotNull null
            if (kind == TileKind.TASKS && !entity.startsWith("todo.") ||
                kind == TileKind.CALENDAR && !entity.startsWith("calendar.") ||
                kind == TileKind.COVER && !entity.startsWith("cover.") ||
                kind == TileKind.FAN && !entity.startsWith("fan.") ||
                kind == TileKind.PERCENTAGE && !entity.startsWith("sensor.") ||
                kind == TileKind.PERSON && !entity.startsWith("person.") ||
                kind == TileKind.READER && entity.substringBefore('.') !in setOf("sensor", "input_text")) return@mapNotNull null
            val vacuumMap = if (kind == TileKind.VACUUM) {
                if (!entity.startsWith("vacuum.")) return@mapNotNull null
                tile.text("vacuumMap")?.takeIf(::isVacuumMap) ?: return@mapNotNull null
            } else null
            val readerAttribute = if (kind == TileKind.READER && !tile.isNull("readerAttribute")) {
                (tile.opt("readerAttribute") as? String)?.takeIf(::validReaderAttribute) ?: return@mapNotNull null
            } else null
            TileSpec(entity, kind, tile.text("label")?.take(72), tile.text("status")?.takeIf {
                kind == TileKind.ACTION && validEntity(it) && it.startsWith("timer.")
            }, showOnHome = kind != TileKind.CAMERA || tile.optBoolean("home", true),
                wideOnTablet = kind.resizable && tile.optBoolean("wide"),
                confirmAction = kind == TileKind.ACTION && tile.optBoolean("confirm"),
                readerAttribute = readerAttribute,
                cameraStream = tile.text("stream")?.takeIf {
                    kind == TileKind.CAMERA && it != entity && validEntity(it) && it.startsWith("camera.")
                },
                accent = if (kind.supportsAccent) tile.accent(kind.accentKey) else null,
                intercomStream = tile.text("intercom")?.takeIf { kind == TileKind.CAMERA && validIntercomStream(it) },
                cameraActions = if (kind == TileKind.CAMERA) decodeCameraActions(tile) else emptyList(),
                vacuumMap = vacuumMap,
                vacuumBattery = if (vacuumMap != null) tile.text("vacuumBattery")?.takeIf(::isVacuumBattery) else null,
                icon = if (kind.supportsIcon) tile.tileIcon() else null,
            )
        }.distinctBy { if (it.kind == TileKind.VACUUM) TileKind.VACUUM.name else it.entity }
        val homeMessage = obj.text("message")?.takeIf(::validEntity)
        return PanelConfig(
            favourites = tiles,
            theme = PanelTheme.entries.firstOrNull { it.name == obj.optString("theme") } ?: PanelTheme.SLATE,
            messageEntity = homeMessage,
            screensaverMessageEntity = if (obj.has("screensaverMessage")) {
                obj.text("screensaverMessage")?.takeIf(::validEntity)
            } else homeMessage,
            homeBadges = decodeBadges(obj.optJSONArray("homeBadges")),
            idleSeconds = obj.optInt("idle", defaultScreensaverTimeoutSeconds)
                .let { if (it == 0) 0 else it.coerceIn(screensaverTimeoutRange) },
            wallMode = obj.optBoolean("wall", true),
            dashboardBrightness = obj.optInt("dashboardBrightness").takeIf { it in 1..100 },
            screensaverBrightness = obj.optInt("screensaverBrightness").takeIf { it in 1..100 },
            highContrast = obj.optBoolean("highContrast"),
            reduceMotion = obj.optBoolean("reduceMotion"),
            showRoomHeating = obj.optBoolean("roomHeating", true),
            roomTiles = decodeRoomTiles(obj.optJSONObject("roomTiles")),
            roomIds = obj.optJSONArray("rooms")?.let { areas ->
                (0 until areas.length()).mapNotNull { areas.optString(it).takeIf(::validAreaId) }.distinct()
            },
            idleAlbum = obj.optJSONObject("idleAlbum")?.let { album ->
                val id = album.text("id")?.takeIf(::isPhotoSourceId) ?: return@let null
                PhotoFolder(id, album.text("title")?.take(140) ?: "Photo folder")
            }
        )
    }

    private fun decodeCameraActions(tile: JSONObject): List<CameraAction> {
        val rows = tile.optJSONArray("cameraActions")
        if (rows == null) {
            val gate = tile.text("gate")?.takeIf { validEntity(it) && it.startsWith("cover.") }
            return if (gate == null) emptyList() else listOf(CameraAction(gate))
        }
        return (0 until minOf(rows.length(), cameraActionLimit)).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val entity = row.text("entity")?.takeIf(::validCameraAction) ?: return@mapNotNull null
            val icon = row.text("icon")?.let { name -> CameraActionIcon.entries.firstOrNull { it.name == name } }
            CameraAction(entity, row.text("label")?.trim()?.take(cameraActionLabelLimit), icon ?: CameraActionIcon.AUTOMATIC)
        }.distinctBy(CameraAction::entity)
    }

    private fun decodeRoomTiles(rooms: JSONObject?): Map<String, List<RoomTilePreference>> = buildMap {
        if (rooms == null) return@buildMap
        for (id in rooms.keys()) {
            if (!validAreaId(id)) continue
            val controls = rooms.optJSONArray(id) ?: continue
            val preferences = (0 until controls.length()).mapNotNull { index ->
                val control = controls.optJSONObject(index) ?: return@mapNotNull null
                val entity = control.optString("entity").takeIf(::validEntity) ?: return@mapNotNull null
                val light = entity.startsWith("light.")
                if (roomTileKind(entity) == null) return@mapNotNull null
                val lightFormat = if (light) when (control.optString("format")) {
                    TileKind.TOGGLE.name -> TileKind.TOGGLE
                    TileKind.SLIDER.name -> TileKind.SLIDER
                    else -> null
                } else null
                RoomTilePreference(
                    entity, lightFormat,
                    wideOnTablet = control.optBoolean("wide"),
                    hidden = control.optBoolean("hidden"),
                    label = (control.opt("label") as? String)?.trim()?.take(72)?.takeIf(String::isNotBlank),
                    accent = control.accent("accent"),
                    followsArea = control.optBoolean("followsArea"),
                    icon = control.tileIcon(),
                )
            }.distinctBy(RoomTilePreference::entity)
            if (preferences.isNotEmpty()) put(id, preferences)
        }
    }

    private fun JSONObject.tileIcon(): TileIcon? =
        text("icon")?.let { name -> TileIcon.entries.firstOrNull { it.name == name } }

    private fun decodeBadges(rows: JSONArray?): List<HomeBadge> {
        if (rows == null) return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val badge = rows.optJSONObject(index) ?: return@mapNotNull null
            val entity = badge.optString("entity").takeIf(::validBadgeSource) ?: return@mapNotNull null
            val values = badge.optJSONArray("states") ?: return@mapNotNull null
            val states = badgeStates((0 until values.length()).mapNotNull { values.opt(it) as? String })
            if (states.isEmpty()) return@mapNotNull null
            HomeBadge(entity, badge.text("label")?.trim()?.take(48), states)
        }.distinctBy(HomeBadge::entity)
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.accent(key: String): TileAccent? {
        val name = text(key)
        return TileAccent.entries.firstOrNull { it.name == name }
    }

    // Retain graph accents when layouts are opened by earlier versions.
    private val TileKind.accentKey get() = if (this == TileKind.TREND) "graphColour" else "accent"
}
