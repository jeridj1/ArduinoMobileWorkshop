# Session State

This file is the persistent handoff point for humans and AI contributors.

## Status

**Phase:** Core backend implementation (USB serial, workspace, toolchain wrapper)
**Implementation:** USB serial backend, sketch file I/O + parser, arduino-cli wrapper implemented; static cross-reference verified
**Last updated:** 2026-08-25

## Completed this session

### 1. Low-level USB & driver system (/usb + app USB receiver)

- `usb/build.gradle.kts`: pinned `com.github.mik3y:usb-serial-for-android:3.8.0`
  (JitPack, declared in settings.gradle.kts). Standard drivers for CH340, CP2102,
  FTDI and CDC/ACM (official Arduino).
- `UsbSerialManager.kt`: rewritten to drive a single open `UsbSerialPort` with a
  background `SerialInputOutputManager` thread (Executors.newSingleThreadExecutor).
  Opens via `UsbDevice` + `openDevice()`, sets 8N1 parameters (DATABITS_8,
  STOPBITS_1, PARITY_NONE), exposes a `Listener` stream (onNewData/onRunError),
  writes byte payloads, changes baud by stop/restart of the IO manager, controls
  DTR/RTS, and tears the connection down gracefully on hardware detach / run errors
  (errors posted to the main thread).
- `SerialPortManager.kt`: listener-facing adapter backed by `UsbSerialManager`;
  preserves the addListener / startReceiving / write / close API the Serial Monitor UI
  uses, so the UI is unchanged.
- `UsbManager.kt`: `openSerialPort()` now routes through `UsbSerialManager` (single
  owner of the port); discovery/permission/attach-detach facade preserved. Fixed the
  null-typed `workspaceDir`-style issues are in the workspace module (see below).
- `app/.../usb/UsbDeviceReceiver.kt`: verifies the attached device's vendor ID
  against `SUPPORTED_VENDOR_IDS` (Arduino 0x2341/0x2A03, CP210x 0x10C4, CH340
  0x1A86, RP2040 0x2E8A, FTDI 0x0403), hands attach/detach context to the central
  `UsbManager` execution loop, and proactively requests USB permission. Uses the
  Tiramisu-safe `getParcelableExtra` overload.

### 2. App integration & intent filters (/app)

- `AndroidManifest.xml`: `android.hardware.usb.host` now `required="true"`; the
  `UsbDeviceReceiver` is `exported="true"` with USB_DEVICE_ATTACHED/DETACHED
  intent-filters linked to `@xml/device_filter`. Also fixed a pre-existing service
  mapping bug: rp2040 services are now declared with fully-qualified names
  (`com.arduinomobileworkshop.rp2040.services.*`) instead of the app-namespaced
  relative names that did not resolve to the real classes.
- `app/src/main/res/xml/device_filter.xml` (new): Arduino(9025), CP210x(4292),
  CH340(6790), RP2040(11914).
- `SerialMonitorActivity.kt`: already binds `activity_serial_monitor.xml` to the
  `UsbSerialManager` callback stream via `openSerialPort()` + `SerialPortManager`
  listener (real-time append to the scroll log + string-to-byte transmitter). No
  change required; verified IDs match the layout.

### 3. Workspace management (/workspace)

- `WorkspaceManager.kt`: fixed a real compile error (`File(workspaceDir, ...)`
  where `workspaceDir` was `File?`). Primary sketchbook is the app sandbox
  (`getExternalFilesDir/ArduinoSketchbook`), writable with no runtime permissions.
  Added scoped-storage file-level read/create/overwrite/delete for `.ino`/`.h`/
  `.cpp`/`.c`, header-file creation with include guards, file import, best-effort
  export to public Documents, and a parser hook. All existing public methods
  (createProject/listProjects/deleteProject/etc.) preserved for the UI.
- `SketchParser.kt` (new): splits a raw `.ino` string into includes / globals /
  setup() / loop() / other functions to feed IDE text buffers (round-trips via raw).

### 4. Compiler toolchain integration (/toolchain)

- `ArduinoCliManager.kt`: extracts a bundled cross-compiled (arm64-v8a)
  arduino-cli binary from APK assets into the app-private files dir
  (`/data/data/<pkg>/files/arduino-cli/`), with a jniLib
  (`libarduino_cli.so` in nativeLibraryDir) fallback. Runs `arduino-cli compile`
  etc. via `ProcessBuilder` with `redirectErrorStream(true)` so compiler stderr
  is redirected into the returned output that the UI console renders. Sets
  ARDUINO_DIRECTORIES_* env vars under the app files dir.
- CI (`.github/workflows/android-build.yml`) already builds the Go arduino-cli for
  `GOOS=android GOARCH=arm64` and places it at
  `app/src/main/jniLibs/arm64-v8a/libarduino_cli.so` -> matches the jniLib
  fallback. `ToolchainManager` (unchanged) already delegates compile/upload to
  `ArduinoCliManager`.

### 5. Verification (static)

- Cross-checked all activity <-> layout/menus ID references (Editor, Main, Serial
  Monitor, menu_main) and string/theme references: all resolve.
- Verified pushed file contents (dollar-escaping, templates, no dropped
  backslashes) by fetching the exact commit blobs.
- Added ProGuard keep rules for `com.hoho.android.usbserial.**` (hygiene; release
  minify is currently false).

## Files changed

- usb/build.gradle.kts
- usb/src/main/java/com/arduinomobileworkshop/usb/UsbSerialManager.kt
- usb/src/main/java/com/arduinomobileworkshop/usb/SerialPortManager.kt
- usb/src/main/java/com/arduinomobileworkshop/usb/UsbManager.kt
- app/src/main/AndroidManifest.xml
- app/src/main/res/xml/device_filter.xml (new)
- app/src/main/java/com/arduinomobileworkshop/app/usb/UsbDeviceReceiver.kt
- workspace/src/main/java/com/arduinomobileworkshop/workspace/WorkspaceManager.kt
- workspace/src/main/java/com/arduinomobileworkshop/workspace/SketchParser.kt (new)
- toolchain/src/main/java/com/arduinomobileworkshop/toolchain/ArduinoCliManager.kt
- app/proguard-rules.pro

## What was tested

- Static cross-reference verification only. The Gradle build was NOT executed in
  this environment (no Android SDK / network for dependency resolution here).
- The GitHub Actions CI (`android-build.yml`) runs `./gradlew clean assembleDebug`
  on push to main; that is the authoritative build verification for these commits.

## Known limitations / next steps

- Build verification pending CI run on the pushed commits. If CI fails, the most
  likely cause is the JitPack fetch for usb-serial-for-android:3.8.0 (confirmed the
  3.8.0 tag exists on mik3y/usb-serial-for-android).
- `exportToDocuments` to the public Documents folder is best-effort under scoped
  storage (may need a SAF grant on API 30+); the sandbox path is the reliable one.
- The arduino-cli binary is only present when built via CI (jniLib). On a local
  `assembleDebug` without the jniLib/asset, compile/upload will report that the CLI
  is not bundled (graceful), and everything else still builds.
- Not yet implemented: real board/core package installation flows, library manager
  backend wiring beyond `lib install`, syntax highlighting, RP2040 firmware,
  automated tests.

## Next contributor should

1. Read this file, `HANDOFF.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `SECURITY.md`.
2. Check the CI run for the latest commits on main.
3. If green, proceed to wire Boards/Library manager backends to `ArduinoCliManager`
   and add unit tests for `SketchParser` / `UsbSerialManager` state transitions.
4. If red, fix the reported compile error (do not mark build as verified).

## Testing rule

A feature is not considered complete until there is a reproducible test or a clearly
documented hardware-dependent test procedure. Never report an untested build as
verified.
