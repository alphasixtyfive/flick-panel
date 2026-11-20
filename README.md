# Flick Panel

Flick Panel is a native Android wall dashboard for Home Assistant. Favourites contains the
tiles pinned on this tablet. Rooms uses Home Assistant areas to show lighting and thermostat
controls in a single scrollable page. The **Rooms** tab in setup chooses which areas
this tablet displays and their order. Cameras shows pinned previews and opens one
full-screen live feed at a time.
In setup, use **Cameras** to choose gallery feeds and star one for Home. Use **Add app** to
pin a launcher shortcut to an app already installed on the tablet.

## First run

1. In Home Assistant, create a long-lived access token from the Security page of a dedicated
   non-admin account.
2. Open Flick Panel, enter the Home Assistant URL and token, and choose a setup PIN.
3. Add favourites by searching for entities. For a numeric sensor choose **Value** or
   **Small graph**. For a dimmable light choose **Slider** or **Switch**. **Done** saves the layout.
4. Hold **Home** and use the PIN keypad to return to setup. Hold and drag a tile by its
   handle, or tap the handle to move it one place. The tile’s options menu lets you edit
   or remove it. Leaving the app or five minutes in setup closes the editor.

The token is encrypted with Android Keystore. Use HTTPS when available. The app also accepts
explicit private IPv4 addresses such as a local Home Assistant server at
`http://192.168.1.5:8123` for households with LAN-only HTTP.

## Panel options

- **Colour scheme** offers Graphite, Charcoal amber, Porcelain, and Android colours.
- Add a **Weather** entity to put current conditions and temperature next to the clock.
- Add a **Music player** tile for playback controls. **Browse music** opens the selected
  player's Home Assistant media library when the player supports browsing and playback.
  Music Assistant playlists and radio stations get their own shelves, with cover art
  and station logos when available.
- **Home message** takes a sensor or text helper whose state is the rendered message. Use a
  text source controlled by the existing Home Assistant logic.
- **Wake on approach** takes an existing binary occupancy sensor. Select a tested radar zone
  near the tablet. The sensor opens the dashboard from the app's idle clock when the display
  is on. Android and any existing device-management schedule handle physical wake and sleep.
- **Wall display** keeps the foreground panel awake and dims the idle clock, including
  while a managed charger has paused charging.
- For the kitchen boost, pin the existing boost button and link its timer in the tile's
  settings. Home Assistant owns the boost and its countdown.

## Data flow

`ha` owns the authenticated connection and entity capabilities. `config` stores the
versioned tablet layout and local setup. `PanelUi.kt` resolves configured areas into room
sections. `ui/Tiles.kt` renders the shared dashboard tiles. Home Assistant owns device
state and actions. The panel loads configured areas after connecting and subscribes to
their controls alongside pinned entities. `PanelImages.kt` bounds image memory and decodes
scaled copies off the UI thread. Camera previews refresh while visible. Music artwork and
recent folders are reused when reopening the browser.

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
