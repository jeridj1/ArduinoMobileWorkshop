# Session State

This file is the persistent handoff point for humans and AI contributors.

## Status

**Build:** GREEN ✅ — CI run 32804567880 (commit 996de69) `./gradlew clean
assembleDebug` succeeded and produced `app/build/outputs/apk/debug/app-debug.apk`.
The Go cross-compiled arduino-cli (libarduino_cli.so) is built in the same run.
**Phase:** Core backend implementation complete + full CI green-build path
**Implementation:** USB serial backend, sketch file I/O + parser, arduino-cli
wrapper implemented; all module + app compile errors resolved; CI verified green.
**Last updated:** 2026-08-25

## Completed this session

### 1. Low-level USB & driver system (/usb + app USB receiver)

- `usb/build.gradle.kts`: pinned `com.github.mik3y:usb-serial-for-android:3.8.0`
  (JitPack, declared in settings.gradle.kts). Standard drivers for CH340, CP2102,
  FTDI and CDC/ACM (official Arduino).
- `UsbSerialManager.kt`: drives a single open `UsbSerialPort` with a background
  `SerialInputOutputManager` thread (Executors.newSingleThreadExecutor). Opens via
  `UsbDevice` + `openDevice()`, sets 8N1 (DATABITS_8, STOPBITS_1, PARITY_NONE),
  exposes a `Listener` stream (onNewData/onRunError), writes byte payloads,
  changes baud by stop/restart of the IO manager, controls DTR/RTS, and tears down
  gracefully on detach/run errors (errors posted to the main thread).
- `SerialPortManager.kt`: listener-facing adapter backed by `UsbSerialManager`;
  preserves the addListener / startReceiving / write / close API the Serial Monitor
  UI uses, so the UI is unchanged.
- `UsbManager.kt`: `openSerialPort()` routes through `UsbSerialManager` (single
  owner of the port); discovery/permission/attach-detach facade preserved.
- `app/.../usb/UsbDeviceReceiver.kt`: verifies attached device vendor ID against
  `SUPPORTED_VENDOR_IDS` (Arduino 0x2341/0x2A03, CP210x 0x10C4, CH340 0x1A86,
  RP2040 0x2E8A, FTDI 0x0403), hands context to the central `UsbManager` loop, and
  proactively requests USB permission. Tiramisu-safe `getParcelableExtra`.

### 2. App integration & intent filters (/app)

- `AndroidManifest.xml`: `android.hardware.usb.host` required="true";
  `UsbDeviceReceiver` exported="true" with USB_DEVICE_ATTACHED/DETACHED
  intent-filters linked to `@xml/device_filter`. Fixed a pre-existing service
  mapping bug: rp2040 services now use FQNs
  (`com.arduinomobileworkshop.rp2040.services.*`).
- `app/src/main/res/xml/device_filter.xml` (new): Arduino(9025), CP2040(4292),
  CH340(6790), RP2040(11914).
- `SerialMonitorActivity.kt`: binds `activity_serial_monitor.xml` to the
  `UsbSerialManager` callback stream via `openSerialPort()` +
  `SerialPortManager` listener (real-time append + string-to-byte transmitter).

### 3. Workspace management (/workspace)

- `WorkspaceManager.kt`: fixed a real compile error
  (`File(workspaceDir, ...)` where `workspaceDir` was `File?`). Primary
  sketchbook is the app sandbox (`getExternalFilesDir/ArduinoSketchbook`),
  writable with no runtime permissions. Added scoped-storage file-level
  read/create/overwrite/delete for `.ino`/`.h`/`.cpp`/`.c`, header-file
  creation with include guards, file import, best-effort export to public
  Documents, and a parser hook. All existing public methods preserved for the UI.
- `SketchParser.kt` (new): splits a raw `.ino` string into includes / globals /
  setup() / loop() / other functions to feed IDE text buffers.

### 4. Compiler toolchain integration (/toolchain)

- `ArduinoCliManager.kt`: extracts a bundled cross-compiled (arm64-v8a)
  arduino-cli binary from APK assets into the app-private files dir
  (`/data/data/<pkg>/files/arduino-cli/`), with a jniLib
  (`libarduino_cli.so` in nativeLibraryDir) fallback. Runs `arduino-cli
  compile` via `ProcessBuilder` with `redirectErrorStream(true)` so compiler
  stderr is redirected into the returned output the UI console renders. Sets
  ARDUINO_DIRECTORIES_* env vars under the app files dir.
- CI builds the Go arduino-cli for `GOOS=android GOARCH=arm64` and places it at
  `app/src/main/jniLibs/arm64-v8a/libarduino_cli.so` -> matches the jniLib
  fallback. `ToolchainManager` delegates compile/upload to `ArduinoCliManager`.

### 5. CI green-build path (this continuation)

The build was red on first CI runs; root causes were found and fixed in order
(each verified by the autonomous failure-as-issue diagnostics in the workflow):

1. **Go version** (`5633fb9`): go.mod requires >=1.26.1; pinned Go 1.26.1.
2. **AGP too old** (`f407e9b`): `material:1.14.0` transitively pulls
   `androidx.core:1.16.0` which requires AGP 8.6.0+. Bumped AGP 8.5.2 -> 8.6.0
   (Gradle 8.7 wrapper already satisfies it).
3. **Corrupted workflow YAML** (`3ff18fa`): a prior edit round-tripped the
   workflow through a fetcher that wraps long lines, splitting
   `gradle/actions/setup-gradle@v4` across two lines -> 0 jobs. Rewrote the
   workflow cleanly from scratch (no round-trip).
4. **CI diagnostics upgrade** (`dc8a1a2`): replaced the github-script report
   step with a `gh issue create` shell step that greps kotlinc `e: ...` lines
   (and other error/FAILED lines) out of build.log into the failure issue, plus a
   120-line tail. This made every subsequent compile error immediately actionable.
5. **Kotlin platform-declaration clash** (`ad1048e`): `UsbManager.kt` declared
   `val androidUsbManager`/`val usbSerialManager` (which auto-generate
   getAndroidUsbManager()/getUsbSerialManager() JVM getters) AND explicit
   `fun getAndroidUsbManager()`/`fun getUsbSerialManager()` -> same JVM
   signature. Removed the two redundant functions (callers use the idiomatic
   property access).
6. **Material3 colorBackground attr** (`ad1048e`): `Theme.Material3` exposes
   the background via the framework `android:colorBackground` attr, not the
   unqualified `colorBackground` (a Theme.MaterialComponents name). Changed the
   three `<item name="colorBackground">` entries (styles.xml + themes.xml
   Light/Dark) to `android:colorBackground`. `colorOnBackground` is a valid
   Material3 attr and is unchanged.
7. **Invalid framework drawables** (`a1b4357`): `menu_main.xml` referenced
   `@android:drawable/ic_menu_open` and `@android:drawable/ic_menu_upload`,
   which are not public Android framework drawables. Replaced action_open ->
   `@drawable/ic_file` (local vector, verified present) and action_upload ->
   `@android:drawable/ic_menu_send` (valid). All other framework menu icons and
   local drawables verified present.
8. **Missing R import** (`996de69`): `LogicAnalyzerActivity.kt` and
   `MultiProgrammerActivity.kt` used `R.layout/R.id/R.menu` without importing
   the app's R class (they live in the `.ui` subpackage; R is in
   `com.arduinomobileworkshop.app`). Added `import com.arduinomobileworkshop.app.R`.
9. **Wrong MaterialSwitch FQN** (`996de69`): the layouts used
   `com.google.android.material.switch.MaterialSwitch` (that package has
   SwitchMaterial, not MaterialSwitch) -> viewBinding typed fields with an
   inaccessible class -> "Cannot access class 'MaterialSwitch'". Correct FQN is
   `com.google.android.material.materialswitch.MaterialSwitch`. Fixed in
   `activity_serial_monitor.xml` and `activity_settings.xml`; this also
   resolves the downstream `isChecked`/`setOnCheckedChangeListener` errors.

After fix #9, run 32804567880 (996de69) -> conclusion: success. Stale ci-failure
issues #7-#12 were closed (state_reason not_planned).

> Tooling note: the `web_search.open_url` fetcher wraps long lines (inserts
> newlines mid-token) and the unauthenticated api.github.com endpoint is rate
> limited (60/h). For source edits, file content was reconstructed by removing
> only the mid-token wraps (never round-tripped through the fetcher); run status
> is readable via the authenticated github_app connector (issues for failures,
> runs API once the rate window resets).

## Files changed

Prior continuation (already on main):
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
- build.gradle.kts (AGP 8.6.0)
- .github/workflows/android-build.yml (Go 1.26.1, decoupled Go build, diagnostics)

This continuation (CI green-build fixes):
- usb/src/main/java/com/arduinomobileworkshop/usb/UsbManager.kt (removed clashing getters)
- app/src/main/res/values/styles.xml (android:colorBackground)
- app/src/main/res/values/themes.xml (android:colorBackground)
- app/src/main/res/menu/menu_main.xml (valid drawables)
- app/src/main/java/com/arduinomobileworkshop/app/ui/LogicAnalyzerActivity.kt (R import)
- app/src/main/java/com/arduinomobileworkshop/app/ui/MultiProgrammerActivity.kt (R import)
- app/src/main/res/layout/activity_serial_monitor.xml (MaterialSwitch FQN)
- app/src/main/res/layout/activity_settings.xml (MaterialSwitch FQN)
- SESSION_STATE.md (this file)

## What was tested

- CI run 32804567880 (commit 996de69): `./gradlew clean assembleDebug` =>
  conclusion: success; APK artifact `ArduinoMobileWorkshop-debug` produced.
- Static cross-reference verification of all activity <-> layout/menu/string refs.
- Verified pushed file contents by fetching exact commit blobs (no round-trip).

## Known limitations / next steps

- `exportToDocuments` to the public Documents folder is best-effort under scoped
  storage (may need a SAF grant on API 30+); the sandbox path is the reliable one.
- The arduino-cli binary is only present when built via CI (jniLib). On a local
  `assembleDebug` without the jniLib/asset, compile/upload reports that the CLI
  is not bundled (graceful), and everything else still builds.
- Hardware/runtime behavior (actual USB serial I/O, real board compile/upload,
  RP2040 UF2 programming) is not exercised by CI; needs on-device testing.
- Not yet implemented: real board/core package installation flows, library
  manager backend wiring beyond `lib install`, real RP2040 USB enumeration
  (Multi-Programmer device list is still mocked), syntax highlighting, automated
  tests.

## Next contributor should

1. Read this file, `HANDOFF.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `SECURITY.md`.
2. The build on main is green; do not re-mark untested behavior as verified.
3. Proceed to wire Boards/Library manager backends to `ArduinoCliManager` and add
   unit tests for `SketchParser` / `UsbSerialManager` state transitions.
4. Replace the mocked RP2040 device list in `MultiProgrammerActivity` with real
   USB enumeration via `UsbManager`/RP2040 vendor ID.

## Testing rule

A feature is not considered complete until there is a reproducible test or a
clearly documented hardware-dependent test procedure. Never report an untested
build as verified.
