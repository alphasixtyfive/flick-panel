# Flick Panel user manual

## Install and connect

1. Download the APK for your tablet from [GitHub Releases](https://github.com/alphasixtyfive/flick-panel/releases/latest). Use the universal APK if you are unsure which processor the tablet uses. Android may ask you to allow installation from your browser or file manager.
2. In Home Assistant, open the Security page of the account you want to use and create a long-lived access token. A dedicated non-admin account works for everyday dashboard use.
3. Open Flick Panel, enter your Home Assistant URL and token, then set a PIN for setup.
4. Add a few tiles and tap **Save changes**. Close settings when you’re ready to use the dashboard. To reopen setup later, hold **Home** in Flick Panel and enter the PIN.

Use HTTPS for remote connections. A private IPv4 address such as `http://192.168.1.5:8123` works on a LAN. Flick Panel stores the token using Android Keystore. To upgrade in place, install an APK signed with the same certificate. If you are moving from a development-signed Pilot APK to a signed release, export the layout before switching builds.

## Favourites and rooms

Choose **Add tile** in setup and search by entity name, ID, or room. Choose a tile format and optionally give it a shorter label. **Standard** and **Wide** set its size in the grid. Choose **Accent** on supported tiles to personalise the icon or graph line. **Theme** follows the panel's colours. To reorder a tile, drag its handle or tap the handle to move it one place. Use the tile menu to resize or remove it. **Undo removal** is available before saving.

Under **Rooms**, choose which Home Assistant areas to show and set their order. Open a room to hide, show, resize, rename, recolour, or reorder controls. A custom name applies to that room. Search hidden controls by name, entity ID, or type. Use **Switch** or **Dimmer** for dimmable lights. **Use automatic layout** restores that room's defaults. **Heating controls** determines whether assigned thermostats appear in rooms.

Wide displays show the room list beside the tiles. On a small display, tap the room name to choose another room.
On very short screens, Favourites uses one scrollable board so the alert and navigation leave space for the tiles.

To pin an installed app, choose **Add tile** and tap **Apps** in the header. Open **Panel → Android Home app** in setup to make Flick Panel the launcher on a dedicated tablet. The Android Home gesture brings you back after using another app.

### Example kitchen layout

See the [demo dashboard and room controls](screenshots/) for a starting point.

| Where | What to add | How to use it |
| --- | --- | --- |
| Favourites | A Slider tile for pendant lights, a Cover tile for the garden gate, and a Small graph tile for home power | Set brightness by dragging across the tile. Tap the gate to open or close it. The graph shows the last six hours when the sensor has recorded history. |
| Favourites | A Tasks tile for groceries, a Calendar tile for upcoming events, and a Music player tile for the kitchen speaker | Tap a tile to open the full list, calendar or player. |
| Kitchen room | Worktop lights, a window blind, a thermostat, and a dinner scene | Hold **Home** to enter setup. Open **Rooms → Kitchen** to choose which controls appear and in what order. |
| Home alerts | A binary sensor for high pollen | In **Panel → Home Assistant → Home alerts**, select the sensor and choose **On** as the state to show. |
| Idle display | A photo folder and a text sensor for the next appointment | Choose them under **Panel → Display**. Tap the clock to preview the result. |

## Choose a tile

| Tile | Use it for |
| --- | --- |
| Switch or Slider | Tap to switch a light on or off. Swipe a Slider tile to set brightness. The adjustments button opens colour and white-tone controls when supported. |
| Fan | Tap to turn a fan on or off and swipe to set its speed when the device supports those controls. |
| Cover | Operate blinds, shades, garage doors, or gates. A gate uses one tap and shows its current state. |
| Thermostat | See the current and target temperatures and adjust the target. |
| Value, Percentage or Small graph | Read a sensor, show a fill bar for a 0–100% sensor, or see recent numeric history. |
| Button or Countdown | Run a scene, script, or button action. Link a timer to a Button or show it on its own as a Countdown. Enable confirmation for actions that deserve a second tap. |
| Selector or Number control | Choose one of a Home Assistant select entity's options or adjust a number using its configured range and step. |
| Music player | See whether the player is playing, buffering, or paused. Open artwork, playback controls, position, volume, and the media library when supported. |
| Tasks or Calendar | Check off and add items, restore completed items, or see the next seven days of events. |
| Person location | Show a person's Home Assistant picture and zone. Open the built-in map when Home Assistant supplies a position. |
| Image or Content reader | Show an image entity such as a vacuum map, or open text such as a recipe from a sensor state or attribute. |
| Weather or Camera | Put current weather near the clock, or open a camera preview for live video. |

The map works offline. It includes a worldwide overview and detailed road, park, and water data for north and central London and Varna. [Map data credits](../app/src/main/assets/offline_map/ATTRIBUTION.md)

## Cameras and talkback

Pin a camera to Favourites for a quick preview, or open it from the Cameras tab. On compatible cameras, **Talk** turns on the microphone and **End call** returns to listening. The [camera intercom guide](intercom.md) shows both states, covers native WebRTC and go2rtc setup, and includes an example for adding a porch light and gate to the viewer.

Flick Panel can show the last saved preview as the camera loads or when it goes offline. The tablet keeps previews in its private cache for up to three days, scoped to the current Home Assistant connection. Use **Clear cache** in Android's Flick Panel app settings to remove them. The viewer shows **Live** once video starts.

## Appearance, alerts, and photos

- **Appearance** has Graphite, Charcoal amber, Porcelain, and Android colours, plus **Higher contrast** and **Reduce motion**.
- Under **Panel → Home Assistant**, choose a text source for **Home message** or select the sensor states that should appear as **Home alerts**. Tap an alert on Home to read the details.
- Set **Panel → Display → Idle photos** to a Home Assistant media folder or an album exposed by your Home Assistant server. Choose **Use this folder** and save, then tap the dashboard clock to preview it.
- Under **Panel → Display → Screensaver message**, choose a Home Assistant text sensor or input text. The message appears beneath the date on the photo clock. The dashboard has a separate **Home message** setting.
- **Dashboard brightness** can follow Android or use a fixed level. **Screensaver brightness** sets the backlight for the clock and photos. **Reset** returns to 55% for photos or 13% for the clock alone. **Wall display** keeps Flick Panel awake while it is visible.
- **Screensaver timeout** defaults to five minutes of dashboard inactivity. Set it to **Never automatically** to leave the timing to a Home Assistant automation or a tap on the clock.

Enter a message or template under **Flick Panel → Configure** in the [Home Assistant integration](https://github.com/alphasixtyfive/flick-panel-home-assistant#screensaver-text). Then choose that tablet's **Screensaver message** sensor in the app. An existing [Template sensor](https://www.home-assistant.io/integrations/template/#creating-a-template-helper-from-the-user-interface) works too.

For an appointment, return a short line such as `17:30 · Football practice` and leave the result empty when there is nothing to show. Use a shared calendar or a time-only message for a tablet in a common area. The [Calendar integration](https://www.home-assistant.io/integrations/calendar/) exposes the next event and provides a separate action to list events.

## Backup and restore

Open **Panel → Backup & restore** in setup. Export the last saved layout, or choose **Save and export** to include edits from this setup session. To restore, select a backup and review its summary before replacing the layout. Use the same Home Assistant instance so entity IDs match. The layout includes tiles, rooms, appearance, and the photo folder reference. Keep the connection token and setup PIN available separately.

Setup locks after five minutes of inactivity. Enter the PIN again to continue applied edits while Flick Panel remains running.

## Home Assistant actions and tablet sensors

Under **Panel → Home Assistant → Tablet link**, name the tablet and tap **Link tablet**. It appears under Home Assistant's Mobile App integration. Add the [optional Flick Panel integration](https://github.com/alphasixtyfive/flick-panel-home-assistant) through **Settings → Devices & services** after installing it on the server.

The **Display** switch and Flick Panel actions can blank or wake the dashboard, show the clock, set brightness, switch tabs, open or close a viewer, and launch a saved Android app. Actions reach Flick Panel while it is in the foreground. Android manages physical display power and the lock screen. The brightness action temporarily overrides the saved dashboard level. The screensaver limit still applies, and the **Display mode** sensor reports the resulting level.

A linked tablet can report battery level and temperature, charging and power source, Battery Saver, display mode, Wi-Fi signal, and ambient light. The available readings depend on the hardware. Flick Panel sends no location updates. You can disable the Mobile App location entity in Home Assistant.

Existing automations can use the tablet's Mobile App notify action. Replace the action name with the one Home Assistant created for your tablet.

```yaml
action: notify.mobile_app_kitchen_panel
data:
  message: command_flick_panel
  data:
    command: open_camera
    camera: camera.front_gate
```

| Command | Data |
| --- | --- |
| `wake`, `sleep`, `show_clock`, `close_viewer` | |
| `set_brightness` | `level` from 1 to 100, or `auto` |
| `keep_awake` | `enabled` as `true`, `false`, or `auto` |
| `show_page` | `page` as `favourites`, `rooms`, or `cameras` |
| `open_camera` | `camera` as a saved camera entity ID |
| `open_tile` | `entity_id` as a saved camera, player, list, calendar, map, or reader |
| `launch_app` | `package_name` as a saved Android shortcut |
