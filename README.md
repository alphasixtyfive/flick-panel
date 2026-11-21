# Flick Panel

Flick Panel is a native Android wall dashboard for Home Assistant. Favourites fits the
pinned tiles into the available space. Page arrows appear if the tiles need another
screen. Rooms uses Home Assistant areas to show lighting and thermostat controls.
Before you choose rooms, the panel suggests areas of pinned lights and thermostats.
The **Rooms** section in setup chooses individual areas, their order, and whether
heating controls appear. Select the
edit icon beside a room to hide or reorder its controls, choose Standard or Wide,
and switch between Switch and Dimmer on dimmable lights. Use **Use automatic layout**
to return to the Home Assistant area defaults. Save changes to apply the layout.
Cameras
shows pinned previews and opens one full-screen feed at a time.
In setup, use **Cameras** to choose gallery feeds, edit their settings and star the
ones you want on Home. On wider tablets, the camera pair sits beside the people
grid, with the agenda and app shortcuts nearby.
Use **Add tile → Android apps** to pin a launcher shortcut to an app already installed on the tablet.
On a dedicated tablet, **Setup → Panel → Android Home app** opens Android's default
launcher settings. Choosing Flick Panel there makes the Android Home gesture return to it
from other apps.

## First run

1. In Home Assistant, create a long-lived access token from the Security page of a dedicated
   non-admin account.
2. Open Flick Panel, enter the Home Assistant URL and token, and choose a setup PIN.
3. Open **Add tile** and search by entity name, ID, or room. For a numeric sensor choose **Value** or
   **Small graph**. For a dimmable light choose **Slider** or **Switch**. Swipe across a
   brightness tile to set the level, or tap it to turn the light on or off. **Save changes** saves
   the layout.
   **Image** displays a Home Assistant image entity like a vacuum map. Size previews in
   **Edit tile** show how **Standard** and **Wide** occupy the grid. The Favourites tile
   menu can also change the size directly. The grid uses fewer columns in narrower
   windows and at larger text sizes. A Home Assistant template
   sensor can supply a value or status for an existing tile without running templates on
   the tablet.
4. Hold **Home** and use the PIN keypad to return to setup. Tap a tile to edit it. Hold and
   drag its handle, or tap the handle to move it one place. The options menu removes a
   tile, and **Undo removal** restores it before saving. Leaving the app or five minutes
   in setup closes the editor.

On tablets, setup shows the section menu beside the current editor. On smaller
displays, setup uses a bottom navigation bar. **Save changes** saves the layout
across all sections.

The token is encrypted with Android Keystore. Use HTTPS when available. The app also accepts
explicit private IPv4 addresses such as a local Home Assistant server at
`http://192.168.1.5:8123` for households with LAN-only HTTP.

## Panel options

- **Appearance** previews Graphite, Charcoal amber, Porcelain, and Android colours.
  Choose **Higher contrast** or **Reduce motion** there.
- Add a **Weather** entity to put current conditions and temperature next to the clock.
- **Home message** takes a sensor or text helper whose state is the rendered message. Use a
  text source controlled by the existing Home Assistant logic.
- Under **Panel → Home Assistant → Wake on approach**, select an existing binary
  occupancy sensor for the area in front of the tablet. A new detection takes the idle
  clock or blank display straight to Favourites. **Wall display** keeps Panel available
  while idle. This feature wakes Flick Panel's display while the app is in the foreground.
  For a discrete hand-wave sensor, trigger the `wake` command below from its Home Assistant automation.
- **Wall display** keeps the foreground panel awake and dims the idle clock, including
  while a managed charger has paused charging. Set the idle timeout to **Never
  automatically** to open the clock only from the dashboard clock.
- **Panel → Display → Idle photos** selects a Home Assistant media folder or Synology
  Photos album. Copy photos into a folder under `/media` on Home Assistant OS, then
  open the folder under **My media**, choose **Use this folder**, and save. Tap the
  dashboard clock to preview the photos with the clock and any configured message.
- For the kitchen boost, pin the existing boost button and link its timer in the tile's
  settings. The tile shows the remaining time when the boost runs. A standalone timer
  can use the **Countdown** format. Home Assistant owns both the boost and the timer.
- **Selector** displays the choices from a Home Assistant select entity. **Number control**
  uses the minimum, maximum and increment from a number entity. For buttons, scenes and
  scripts, **Confirm before running** adds a confirmation step.
- **Indicator** displays values such as temperature and humidity on Favourites.
  Hold **Home**, enter the setup PIN, then open an item under **Favourites → Indicators**
  to edit it. To add a reading, use **Add tile**, choose the Home Assistant sensor and
  select **Indicator**. TalkBack reads the tile name and value.
- **Tasks** previews open items on Favourites. Open the tile for the checklist.
  Tap a row to check it off, use **Add item**, or expand **Completed** to restore an item.
  The app offers the actions supported by the list in Home Assistant.
- **Calendar** previews up to two events on Favourites and opens a seven-day agenda
  grouped by date. The preview shows today's date when the agenda is empty.
- **Music player** shows the current track on the tile. Tap it for artwork,
  playback, a track position bar and direct volume controls.
  **Browse music** opens the selected player's library. Its playback bar stays within
  reach while you browse. Controls follow the player's Home Assistant capabilities.
  Music Assistant playlists and radio stations appear with cover art and station logos when available.
- **Cover** shows the state and position reported by Home Assistant. It offers the
  Open, Close, and Stop actions advertised by the device. A gate uses the whole
  tile as a toggle and shows its current state.
- **Person location** shows the person's Home Assistant picture and zone, with initials
  when the picture is unavailable. Tap it to open the in-app map when Home Assistant
  reports a position. The full-screen map works offline, with OpenStreetMap road,
  park and water detail for north and central London and Varna. Natural Earth
  supplies the worldwide overview. See the [map data credits](app/src/main/assets/offline_map/ATTRIBUTION.md).
- In PIN-protected setup, open **Panel → Home Assistant → Home alerts**. Pick an
  existing sensor and select the states that should show an alert on Home. For example,
  use **On** for a maintenance binary sensor or **High** and **Very high** for a pollen sensor.
  Alerts appear beside the tabs when there is room and below them on compact screens.
  Tap an alert to read the current warning, including any issues listed by the sensor.
- **Content reader** opens a sensor or text helper in a full-screen reader. Select
  the entity state or a text attribute as the source. A longer recipe or note can
  live in a text attribute, with line breaks kept in the reader.
- A camera can link an existing Home Assistant **gate** cover. The live viewer offers
  **Open gate** when that cover is closed. The cover's Home Assistant automation
  controls when and how the gate moves.
- **Live stream source** in a camera tile's setup can use a second Home Assistant camera
  entity for live playback. Use it for a lower-resolution feed on tablets. The tile
  keeps its original preview, label and gate control.

## Control the panel from Home Assistant

In PIN-protected setup, open **Panel → Home Assistant → Tablet link**, name the
tablet, and choose **Link tablet**. Home Assistant's built-in
[Mobile App integration](https://www.home-assistant.io/integrations/mobile_app/)
creates the device and its `notify.mobile_app_…` action. Check the action name in
Home Assistant before using it in a script or automation.

```yaml
action: notify.mobile_app_kitchen_panel
data:
  message: command_flick_panel
  data:
    command: open_camera
    camera: camera.front_gate
```

| Command | Additional data | Result |
| --- | --- | --- |
| `wake` | | Return from the clock or blank screen to Favourites |
| `sleep` | | Blank the panel and reduce brightness to Android's minimum |
| `show_clock` | | Show the idle clock and configured photos |
| `set_brightness` | `level` as an integer from 1–100, or `auto` | Set or clear the brightness override |
| `keep_awake` | `enabled` as `true`, `false`, or `auto` | Override or restore the saved Wall display setting |
| `show_page` | `page` as `favourites`, `rooms`, or `cameras` | Open a dashboard tab |
| `open_camera` | `camera` as a saved camera entity ID | Open its live feed |
| `open_tile` | `entity_id` as a saved tile entity ID | Open a camera, music player, task list, calendar, map or reader |
| `close_viewer` | | Close the current full-screen view |

Display overrides apply until cleared, the tablet is unlinked, or the app restarts.
Commands work while Flick Panel is in the foreground. A local tap also restores
the blank display. Android manages hardware power and the lock screen separately.
The tablet accepts commands outside PIN-protected setup. Use the underlying Home
Assistant action to operate devices such as lights and gates.

A linked tablet publishes five diagnostic entities under its Mobile App device:
battery level, battery charging, charger connected, battery temperature, and
display mode. The display sensor also reports the selected tab, brightness
override and effective Wall display setting. Readings update on change while
the tablet is linked and active. Home Assistant can disable individual entities.

## Data flow

`ha` owns the Home Assistant protocol. `HaConnection.kt` manages the WebSocket lifecycle
and waits for the first entity snapshot before reporting the connection as online.
`config` owns the layout codec and local storage. `RoomLayout.kt` resolves configured
areas into room sections, and `PanelUi.kt` holds the dashboard and its active viewer.
`DeviceCommandQueue.kt` tracks one pending command per entity and confirms changes
against the state Home Assistant sends back. Home Assistant owns device state and
actions. The panel loads areas when configured or when Rooms is opened.
Switching tabs reuses the room subscription. Timestamp-only updates reuse the current
entity snapshot. Favourites and Rooms compose rows as they approach the viewport. Camera
previews and graph refreshes follow those rows. `PanelImages.kt` bounds
image memory and decodes scaled copies off the UI thread, with smaller caches on low-memory
devices. Concurrent consumers share a request and the last consumer to leave cancels it.
Music artwork, idle photos, and
recent music folders are reused on the same Home Assistant connection. Agenda cards
share a rate-limited refresh with their full-list views and retain the last results
in memory if Home Assistant disconnects. A checked item moves immediately and is
restored if Home Assistant rejects the change.

Live video uses Home Assistant HLS and one Media3 player, as in Flick TV. Android releases the player
when the viewer closes or the app goes to the background.
Camera previews and idle photos pause in the background. For timings, `adb logcat -s FlickPanelCamera:I`
reports `hls_url_ms` and `first_frame_ms` from the camera tap. The first-frame timer
stops at Media3's `onRenderedFirstFrame` callback.

## Build

Use JDK 17 or newer and Android SDK 35. The application supports Android 9 or newer.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assemblePilot
```

The Pilot APK is optimized and development-signed. The output is
`app/build/outputs/apk/pilot/app-pilot.apk`. Use a stable private signing certificate for a
deployment intended to receive future in-place updates.

The JVM suite exercises the layout codec, entity capability rules, compact updates, and a
local Home Assistant protocol fixture. Real cameras, screen wake, and tablet layout should
also be checked on the intended device.
