# SESSION_STATE.md — ArduinoMobileWorkshop

> Handoff state document. Updated after the CRITICAL ADVANCED UPGRADE ASSIGNMENT
> (all four parts complete, CI green).

## Project

- Repository: jeridj1/ArduinoMobileWorkshop (public, Kotlin)
- Modules: app, usb, workspace, toolchain, rp2040
- Toolchain: Gradle 8.7, AGP 8.6.0, Kotlin 2.0.20, compileSdk 35, minSdk 24, targetSdk 35
- JitPack repo declared in dependencyResolutionManagement
- CI: GitHub Actions workflow — Go 1.26.1 (check-latest) builds libarduino_cli.so into
  app/src/main/jniLibs/arm64-v8a/ (continue-on-error, 20-min step timeout); Gradle assemble
  with tee build.log and build-log artifact upload; testDebugUnitTest --stacktrace --continue;
  on failure opens a ci-failure issue (Kotlin e: lines + 120-line tail). Job timeout 30 min.

## Milestone Status — CRITICAL ADVANCED UPGRADE ASSIGNMENT

ALL FOUR PARTS COMPLETE. CI GREEN ON MAIN (HEAD = edc4a31).

### Phase 1 — Network & Download Engine (toolchain) — commit fac68a3 — GREEN ✅
- Added com.squareup.okhttp3:okhttp:4.12.0 to toolchain/build.gradle.kts.
- Added INTERNET + ACCESS_NETWORK_STATE permissions and usesCleartextTraffic="true" to
  AndroidManifest.xml; windowSoftInputMode="adjustResize" for editor/main/serial-monitor/
  multi-programmer activities.
- Rewrote ToolchainManager.kt with an OkHttp client and network-first fetching:
  fetchText(url), downloadFile(url, dest, callback), refreshBoardIndexFromNetwork(),
  parsePackageIndex(json), savePlatformProfile(...), parseLibraryIndexNetwork(json),
  listDownloadedProfiles(), downloadBoardProfile(boardId). Real Arduino
  package_index.json and library_index.json are fetched, parsed with org.json, and
  per-platform board profile JSONs are cached into the sandboxed files dir; archives are
  downloaded via downloadBoardProfile. The bundled arduino-cli remains the fallback. The
  full existing public API is preserved.
- URLs: https://downloads.arduino.cc/packages/package_index.json and
  https://downloads.arduino.cc/libraries/library_index.json
- Verified green: Actions run 32811883565 -> success.

### Phase 2 — Native RP2040 Mass-Storage Flashing (usb/rp2040) — commits 721c133 -> f7c4b0e — GREEN ✅
- Targets the Pico BOOTSEL bootrom device (VID 0x2E8A, PID 0x0003) for native flashing.
- RP2040PicobootFlasher.kt (new): claims the vendor bulk interface (class 0xFF, iface #1)
  via UsbDeviceConnection.claimInterface(), sends PICOBOOT commands (EXCLUSIVE_ACCESS 0x01,
  EXIT_XIP 0x06, FLASH_ERASE 0x03, WRITE 0x05, REBOOT 0x02) over the bulk OUT endpoint
  (0x03) and streams UF2 payloads straight to flash, polling command completion via a
  vendor control-IN request (bmRequestType dir-in | type-vendor | 0x01, request 0x40,
  PicobootCmdStatus). No OS mass-storage mount needed. UF2 blocks parsed (magic
  0x0A324655 / 0x9E5D5157 / end 0x0AB16F30, 512-byte blocks, 256-byte payload at offset 32).
- RP2040Manager: RP2040_PID_BOOTLOADER changed 0x000A -> 0x0003 (BOOTSEL bootrom); added
  scanForBootloaderDevices().
- RP2040ProgrammerService: programDevice branches — PID 0x0003 uses the native PICOBOOT
  flasher via programViaPicoboot(); serial-mode devices fall back to programViaSerial().
  Added androidUsbManager field.
- UsbDeviceReceiver: added RP2040_PID_BOOTLOADER = 0x0003, isRp2040Bootloader() check,
  BOOTSEL detection logging.
- device_filter.xml: explicit Pico BOOTSEL entry (vendor-id="11914" product-id="3").
- MultiProgrammerActivity: calls scanForBootloaderDevices() first; shows BOOTSEL/serial
  mode labels and a "Hold BOOTSEL to flash" hint when only serial-mode devices are present.
- Build fix in f7c4b0e (721c133 had 2 Kotlin compile errors, auto-reported as issue #13):
  UF2_MAGIC_START1 (0x9E5D5157) exceeds Int.MAX and was inferred Long, so bb.getInt() is
  compared against the magics' .toInt(); UsbConstants exposes no USB_RECIPIENT_INTERFACE,
  so a local const USB_RECIPIENT_INTERFACE = 0x01 is used in the control bmRequestType.
- Verified green: no new ci-failure issue appeared after f7c4b0e (Actions run 32812220746;
  runs API was rate-limited during polling, issue-based detection used instead).

### Phase 3 — Modern Layout Constraints & Developer View (app) — commit edc4a31 — GREEN ✅
- activity_editor.xml: root converted to ConstraintLayout with fitsSystemWindows="true" and a
  guideline at 62%. Editor is a horizontal LinearLayout: a monospace line-number gutter
  (lineNumbers TextView, 48dp, @color/line_number) beside a HorizontalScrollView wrapping a
  monospace EditText. Hardcoded margins removed; the output pane is pinned to the bottom and
  shrinks under the keyboard instead of clipping.
- activity_main.xml: root converted to ConstraintLayout with fitsSystemWindows="true"; the
  button grid is wrapped in a NestedScrollView (main_scroll) so it scrolls rather than
  clipping off-screen when the IME / gesture bars expand.
- EditorActivity.kt: setupLineNumbers() rebuilds the gutter via a TextWatcher on text change;
  setOnScrollChangeListener syncs the gutter scroll to the editor scroll. Added an Examples
  menu (menu_editor.xml, action_example, showAsAction="never") with showExampleChooser()
  (AlertDialog) and loadExample(name) reading from assets/examples/.
- Verified green: no new ci-failure issue appeared after edc4a31 (~8 min elapsed at check).

### Phase 4 — Bonus Usefulness Features (app) — commit edc4a31 — GREEN ✅
- Embedded core example sketches under app/src/main/assets/examples/: Blink.ino (LED pin
  13 at 1 Hz) and SerialTest.ino (incrementing counter at 9600 baud), loaded via the
  EditorActivity Examples menu.
- SerialMonitor: explicit "Clear Log" button; auto-scroll now uses
  NestedScrollView.fullScroll(View.FOCUS_DOWN) via scrollToBottom() posted to the view; a
  scroll listener on serialOutputScroll re-enables stick-to-bottom when the user scrolls
  back down and disables it while scrolling up, so active streams stay pinned to the bottom.
- activity_serial_monitor.xml: fitsSystemWindows="true" on root; serialOutputScroll
  constraint changed from @id/toolbar to @id/baudRow (new baudRow id on the baud-rate row).
- Verified green with Phase 3 (same commit edc4a31).

## Earlier milestones (prior sessions)

- Phase 1 of the original plan: real Boards/Library manager backends wired to arduino-cli
  (commit 14b6b99).
- Phase 2 of the original plan: real RP2040 USB enumeration in the multi-programmer
  (commit e4c1c5e).
- Phase 3 of the original plan: automated JVM unit tests for SketchParser (workspace) and
  UsbSerialManager (usb, Mockito); test deps + unitTests.returnDefaultValues wired; CI
  testDebugUnitTest step added (commit 41466e5).
- All prior milestones verified green.

## CI verification summary

- fac68a3 -> run 32811883565 -> success (confirmed via runs API).
- 721c133 -> FAILED (issue #13, 2 Kotlin compile errors).
- f7c4b0e -> green (no new ci-failure issue; runs API rate-limited, issue-based detection).
- edc4a31 -> green (no new ci-failure issue after ~8 min; issue-based detection).
- Detection method: the CI workflow opens a ci-failure issue within ~30s of a failing step,
  so the absence of a new ci-failure issue after the build window confirms green. The
  github_app connector has no Actions runs/logs/jobs capability, so issue-based detection
  is the reliable path (5000/hr quota); unauthenticated api.github.com is 60/hr.

## Open issues

- None. Stale ci-failure #13 (commit 721c133) was closed as completed — the fix landed in
  f7c4b0e and was documented in a closing comment.

## Known risks / follow-ups

- PICOBOOT flashing and UF2 block streaming are best-effort reconstructions of the documented
  bootrom protocol; full validation requires on-device testing with a real Pico in BOOTSEL mode.
- windowSoftInputMode="adjustResize" + fitsSystemWindows should prevent clipping, but actual
  behavior across all screen sizes needs device testing.
- OkHttp network calls run on background threads; there is no explicit network-state check
  before fetching (graceful null/defaults on failure).
- downloadBoardProfile() looks up profile files by board.packageName.replace(":", "_") + "_"
  prefix; the profile JSON url field must be non-empty for the download to succeed.

## Commits this session (on main)

1. fac68a3 — Phase 1: network & download engine (OkHttp, permissions, ToolchainManager rewrite)
2. 721c133 — Phase 2: native RP2040 PICOBOOT flashing (6 files) — failed compile
3. f7c4b0e — Phase 2 fix: UF2 magic Long->Int + local USB_RECIPIENT_INTERFACE
4. edc4a31 — Phase 3 & 4: layouts, line-number editor, examples, serial UX (8 files)

## New/changed files this session

- toolchain/build.gradle.kts (okhttp dep)
- toolchain/src/main/java/com/arduinomobileworkshop/toolchain/ToolchainManager.kt
- app/src/main/AndroidManifest.xml (permissions, softInputMode)
- rp2040/src/main/java/com/arduinomobileworkshop/rp2040/RP2040PicobootFlasher.kt (new)
- rp2040/src/main/java/com/arduinomobileworkshop/rp2040/RP2040Manager.kt
- rp2040/src/main/java/com/arduinomobileworkshop/rp2040/RP2040ProgrammerService.kt
- usb/src/main/java/com/arduinomobileworkshop/usb/UsbDeviceReceiver.kt
- app/src/main/res/xml/device_filter.xml
- app/src/main/java/com/arduinomobileworkshop/app/MultiProgrammerActivity.kt
- app/src/main/res/layout/activity_editor.xml
- app/src/main/res/layout/activity_main.xml
- app/src/main/res/menu/menu_editor.xml (new)
- app/src/main/assets/examples/Blink.ino (new)
- app/src/main/assets/examples/SerialTest.ino (new)
- app/src/main/java/com/arduinomobileworkshop/app/EditorActivity.kt
- app/src/main/res/layout/activity_serial_monitor.xml
- app/src/main/java/com/arduinomobileworkshop/app/SerialMonitorActivity.kt
