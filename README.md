# Flick Panel

Flick Panel is a native Android wall dashboard for Home Assistant. Favourites contains the
tiles pinned on this tablet. Rooms uses Home Assistant areas to show lighting and thermostat
controls for the selected room. The **Rooms** section in setup chooses which areas
this tablet displays, their order, and whether heating controls appear. Select the
edit icon beside a room to hide or reorder its controls, choose Standard or Wide,
and switch between Switch and Dimmer on dimmable lights. Use **Use automatic layout**
to return to the Home Assistant area defaults. Save changes to apply the layout.
Cameras
shows pinned previews and opens one full-screen feed at a time.
In setup, use **Cameras** to choose gallery feeds, edit their settings and star the
ones you want on Home. On wider tablets, the camera pair sits beside a two-by-two
people grid. Swipe the people grid or use its arrows for more profiles. The agenda,
app shortcuts and graphs sit below.
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

On tablets, setup shows the section menu beside the current editor. **Save changes** saves the
layout across all sections. On smaller displays, setup uses tabs.

The token is encrypted with Android Keystore. Use HTTPS when available. The app also accepts
explicit private IPv4 addresses such as a local Home Assistant server at
`http://192.168.1.5:8123` for households with LAN-only HTTP.

## Panel options

- **Appearance** previews Graphite, Charcoal amber, Porcelain, and Android colours.
  Choose **Higher contrast** or **Reduce motion** there.
- Add a **Weather** entity to put current conditions and temperature next to the clock.
- Add a **Music player** tile for playback controls. **Browse music** opens the selected
  player's Home Assistant media library when the player supports browsing and playback.
  On players that support volume control, swipe across the tile to set volume.
  Music Assistant playlists and radio stations get their own shelves, with cover art
  and station logos when available.
- **Home message** takes a sensor or text helper whose state is the rendered message. Use a
  text source controlled by the existing Home Assistant logic.
- **Wake on approach** takes an existing binary occupancy sensor. Select a tested radar zone
  near the tablet. The sensor opens the dashboard from the app's idle clock when the display
  is on. Android and any existing device-management schedule handle physical wake and sleep.
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
- **Tasks** shows how many tasks are open and previews the first one. Open the tile
  to check tasks off, add a task when the list supports it, or expand completed items.
- **Calendar** previews the next event and opens a seven-day agenda grouped by date.
  It includes all-day and ongoing events. **Wide** lets either tile preview two items.
- **Cover** shows the state and position reported by Home Assistant. It offers the
  Open, Close, and Stop actions advertised by the device. A gate uses the whole
  tile as a toggle and shows its current state.
- **Person location** shows the person's Home Assistant picture and zone, with initials
  when the picture is unavailable. Tap it to open the in-app map when Home Assistant
  reports a position. The bundled basemap works without network tile requests.
- **Content reader** opens a sensor or text helper in a full-screen reader. Select
  the entity state or a text attribute as the source. A longer recipe or note can
  live in a text attribute, with line breaks kept in the reader.
- A camera can link an existing Home Assistant **gate** cover. The live viewer offers
  **Open gate** when that cover is closed. The cover's Home Assistant automation
  controls when and how the gate moves.

## Control the panel from Home Assistant

In PIN-protected setup, open **Panel → Home Assistant → Remote control**, name the
tablet, and choose **Link tablet**. Home Assistant's built-in Mobile App
integration adds a notification action for the tablet. Use that device's
`notify.mobile_app_…` action in scripts or automations.

```yaml
action: notify.mobile_app_kitchen_panel
data:
  message: command_flick_panel
  data:
    command: open_camera
    camera: camera.front_gate
```

Replace the action with the one Home Assistant created for your tablet.
To blank Panel or bring it back, use `command: sleep` or `command: wake` and omit
`camera`. Only cameras saved under **Cameras** can be opened remotely.
Blanking dims the display to Android's minimum and keeps Panel connected in the
foreground. A tap restores it locally. Android's lock screen and display power
are managed separately by the device.

## Data flow

`ha` owns the authenticated connection and entity capabilities. `config` stores the
versioned tablet layout and local setup. `PanelUi.kt` resolves configured areas into room
sections. `ui/Tiles.kt` arranges the dashboard grid, and `ui/PanelTile.kt` dispatches to
individual tile implementations. Home Assistant owns device
state and actions. The panel loads configured areas after connecting and subscribes to
their controls alongside pinned entities. Timestamp-only notifications reuse the current
entity snapshot. Favourites and Rooms compose rows as they approach the viewport. Camera
previews and graph refreshes follow those rows. `PanelImages.kt` bounds
image memory and decodes scaled copies off the UI thread, with smaller caches on low-memory
devices. Concurrent consumers share a request and the last consumer to leave cancels it.
Music artwork, idle photos, and
recent music folders are reused on the same Home Assistant connection. Agenda cards
share a rate-limited refresh with their full-list views and retain the last results
in memory if Home Assistant disconnects.

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
