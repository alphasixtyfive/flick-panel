# Flick Panel

Flick Panel is a native Android wall dashboard for Home Assistant. Favourites contains the
tiles pinned on this tablet. Rooms uses Home Assistant area assignments to show lighting
and thermostats. Cameras shows pinned camera snapshots and opens one live HLS viewer at a time.

## First run

1. In Home Assistant, create a long-lived access token from the Security page of a dedicated
   non-admin account.
2. Open Flick Panel, enter the Home Assistant URL and token, and choose a setup PIN.
3. Add favourites by searching for entities. For a numeric sensor choose **Value** or
   **Small graph**. For a dimmable light choose **Slider** or **Switch**. **Done** saves the layout.
4. Hold **Home** and enter the setup PIN to return to setup. The grip reorders tiles with a
   hold-and-drag gesture or opens a Move menu with a tap. Leaving the app or five minutes
   in setup closes the editor.

The token is encrypted with Android Keystore. Use HTTPS when available. The app also accepts
explicit private IPv4 addresses such as a local Home Assistant server at
`http://192.168.1.5:8123` for households with LAN-only HTTP.

## Panel options

- **Colour scheme** offers Slate blue, Charcoal amber, Porcelain, and Android colours.
- **Home message** takes a sensor or text helper whose state is the rendered message. Use a
  text source controlled by the existing Home Assistant logic.
- **Wake on approach** takes an existing binary occupancy sensor. Select a tested radar zone
  near the tablet. The sensor opens the dashboard from the app's idle clock when the display
  is on. Android and any existing device-management schedule handle physical wake and sleep.
- **Wall display** keeps Flick Panel visible while charging and dims the idle clock.
- For the kitchen boost, pin the existing boost button and link its timer in the tile's
  settings. Home Assistant owns the boost and its countdown.

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
