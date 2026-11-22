# Build and release Flick Panel

Use JDK 17 or later and Android SDK 35. The app supports Android 9 and later.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Run `gradlew.bat` on Windows. `:app:assemblePilot` builds an optimized, development-signed APK for local testing. The outputs are under `app/build/outputs/apk`.

## Where the code lives

- `ha` handles Home Assistant WebSocket messages, entity capabilities, and service calls.
- `config` owns the layout JSON, backup format, and local credentials.
- `RoomLayout.kt` resolves selected Home Assistant areas into room controls. `DeviceCommandQueue.kt` keeps a control pending until Home Assistant reports the change or the request ends.
- `ui` renders dashboard and setup screens. Tile formats live in their own files. Lights and fans share the same level control.
- `camera` handles live WebRTC for the native Home Assistant and go2rtc transports. The viewer can use an HLS feed from Media3. `CameraPreviewFiles.kt` stores small camera previews in the app cache.

Camera previews restore from a bounded disk cache and refresh only while visible. Concurrent requests for the same preview share work. The last observer leaving cancels an unfinished request. Live players release their resources when the viewer closes or the screen leaves the foreground. Run `adb logcat -s FlickPanelCamera:I` for first-frame and Talk timings.

## Prepare release assets

1. Run the tests, lint, and optimized APK build. Check setup, local Home Assistant controls, the camera gallery, and a live viewer on a tablet.
2. Build the universal release APK. For smaller downloads, build again with `-PpanelAbi=arm64-v8a` or `-PpanelAbi=armeabi-v7a`. The same option accepts `x86_64` and `x86`. Copy each build to a distinct filename before running the next one because Gradle uses the same output path.
3. Sign release APKs with the certificate used for the existing public release. Use `apksigner verify --print-certs` and compare with the published APK before offering an in-place update. Generate a SHA-256 checksum for each signed binary.
4. Use the [release notes](releases/0.1.85.md) for the release page. Attach the signed APKs and checksums.

Suggested GitHub description

> A native Android wall dashboard for Home Assistant with room controls, live camera intercom, music, lists, and a photo clock.
