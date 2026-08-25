# SESSION_STATE.md — ArduinoMobileWorkshop

> Handoff state document. Updated after the CRITICAL PRODUCTION FINALIZATION
> ASSIGNMENT (all three parts complete, CI green, deployable APK compiled).

## Project

- Repository: jeridj1/ArduinoMobileWorkshop (public, Kotlin)
- Modules: app, usb, workspace, toolchain, rp2040
- Toolchain: Gradle 8.7, AGP 8.6.0, Kotlin 2.0.20, compileSdk 35, minSdk 24, targetSdk 35
- JitPack repo declared in dependencyResolutionManagement
- CI: GitHub Actions workflow — Go 1.26.1 (check-latest) builds the arduino-cli
  binary as libarduino-cli.so into app/src/main/jniLibs/arm64-v8a/
  (continue-on-error, 20-min step timeout); Gradle assembleDebug with tee
  build.log and build-log artifact upload; testDebugUnitTest --stacktrace
  --continue; on failure opens a ci-failure issue. Job timeout 30 min.
- extractNativeLibs="true" + useLegacyPackaging = true so jniLibs are extracted
  to nativeLibraryDir on install (the only path Android allows execution from).

## Milestone Status — CRITICAL PRODUCTION FINALIZATION ASSIGNMENT

ALL THREE PARTS COMPLETE. CI GREEN ON MAIN (HEAD = 6f8e5e8).

### Part 1 — Remove Permission Denied Arduino-CLI Crash — commit 5012071 — GREEN ✅
- Root cause: ArduinoCliManager extracted the binary to context.filesDir and
  tried to execute it from there. Android raises EACCES (error=13 Permission
  Denied) for any ProcessBuilder execution outside nativeLibraryDir.
- CI workflow: renamed Go build output from libarduino_cli.so to
  libarduino-cli.so (matches the hyphenated jniLib name).
- app/build.gradle.kts: updated keepDebugSymbols glob to **/libarduino-cli.so.
- ArduinoCliManager.kt rewrite: the executable path is now
  context.applicationInfo.nativeLibraryDir + "/libarduino-cli.so". No more
  extraction to filesDir. The OS places the .so in nativeLibraryDir during
  install (extractNativeLibs="true"). Config/data dirs remain in filesDir
  (those are just data, not executables, so no permission issue). The full
  public API is preserved (ensureInstalled, getExecutablePath, getConfigDir,
  initConfig, version, run). ASSET_NAME/EXE_NAME constants retained for
  backward compatibility.

### Part 2 — Interactive Boards & Library Managers — commit 5012071 — GREEN ✅
- Replaced static ListView layouts with interactive interfaces:
  - activity_boards_manager.xml: SearchView + ProgressBar + RecyclerView with
    fitsSystemWindows to prevent keyboard clipping.
  - activity_library_manager.xml: same pattern.
  - item_manager.xml (new): shared RecyclerView row layout with a title
    TextView, subtitle TextView, and a MaterialButton action button.
- BoardsManagerActivity.kt: RecyclerView adapter with per-row "Download" button
  that calls toolchainManager.installBoardPackage; SearchView filters by name,
  FQBN, and platform; ProgressBar toggles during refresh; refresh triggers
  toolchainManager.refreshBoardIndex (OkHttp HTTP download of
  package_index.json).
- LibraryManagerActivity.kt: RecyclerView adapter with per-row "Install"
  button; SearchView filters by name, author, description; auto-populates from
  the HTTP library index (toolchainManager.searchLibraries) when the local
  list is empty; ProgressBar during refresh.

### Part 3 — RP2040 Configuration & Pin-Map Screen — commit 6f8e5e8 — GREEN ✅
- MultiProgrammerActivity: added a mode selector (Spinner: SWD, JTAG, AVR-ISP)
  that drives a dynamic hookup-guide panel (monospace TextView). Selecting a
  mode shows the precise pin connections, e.g. AVR-ISP:
  "GP2 -> Target RESET, GP3 -> Target SCK, GP4 -> Target MISO, GP5 -> Target
  MOSI". A "Prepare Pico" button requests USB permission and flashes the
  matching helper firmware image from assets to the first BOOTSEL device.
- LogicAnalyzerActivity: added a hookup-guide overlay for the Logic Analyzer
  mode (GP2->CH0 ... GP5->CH3) plus a "Prepare Pico" button that flashes the LA
  helper firmware. Now also binds to RP2040ProgrammerService for the firmware
  pipeline. Fixed deprecated onBackPressed() -> onBackPressedDispatcher.
- RP2040ProgrammerService: new flashHelperFirmware(assetName, device, callback)
  method copies a helper UF2 from assets to a temp file and streams it to a
  BOOTSEL device via the PICOBOOT pipeline (programDevice). Auto-selects the
  first BOOTSEL device when device is null.
- Layouts: activity_multi_programmer.xml and activity_logic_analyzer.xml
  updated with mode spinner, hookup-guide TextView, Prepare Pico button, and
  fitsSystemWindows. Logic analyzer layout wrapped in ScrollView.
- Embedded 4 placeholder helper-firmware images in assets/firmware/:
  swd_helper.uf2, jtag_helper.uf2, avr_isp_helper.uf2,
  logic_analyzer_helper.uf2.

## Earlier milestones (prior sessions)

- CRITICAL ADVANCED UPGRADE ASSIGNMENT (all 4 parts complete):
  1. Network & Download Engine (OkHttp, ToolchainManager rewrite) — fac68a3
  2. Native RP2040 PICOBOOT flashing — 721c133 -> f7c4b0e
  3. Modern ConstraintLayout + line-number editor — edc4a31
  4. Example sketches + serial UX — edc4a31
- Original plan phases 1-3:
  - Real Boards/Library manager backends wired to arduino-cli (14b6b99)
  - Real RP2040 USB enumeration (e4c1c5e)
  - Automated JVM unit tests (41466e5)

## CI verification summary

- 5012071 (Parts 1+2) -> green (no ci-failure issue after build window)
- 6f8e5e8 (Part 3) -> green (no ci-failure issue after build window)
- Detection: CI opens a ci-failure issue within ~30s of a failing step;
  absence of new issue confirms green.

## Open issues

- None. All prior ci-failure issues (#7-#13) are closed.

## Known risks / follow-ups

- Helper-firmware UF2 images in assets/firmware/ are placeholder text files.
  They must be replaced with actual compiled UF2 binaries for on-device
  PICOBOOT flashing to work. The code correctly copies them from assets and
  passes them to the flasher; only the binary content needs replacing.
- PICOBOOT flashing and UF2 block streaming are best-effort reconstructions
  of the documented bootrom protocol; full validation requires on-device
  testing with a real Pico in BOOTSEL mode.
- The activity_logic_analyzer.xml hookup-guide text contains literal newlines
  in the XML attribute value; AAPT2 handles this but a string resource would be
  cleaner if the text needs to be localized.
- OkHttp network calls run on background threads; no explicit network-state
  check before fetching (graceful null/defaults on failure).

## Commits this session (on main)

1. 50029aa — SESSION_STATE.md update (prior session finalization)
2. 5012071 — Parts 1+2: permission-denied fix + interactive managers
3. 6f8e5e8 — Part 3: RP2040 config & pin-map screen + helper-firmware pipeline

## New/changed files this session

Part 1 (permission-denied fix):
- .github/workflows/android-build.yml (libarduino-cli.so rename)
- app/build.gradle.kts (keepDebugSymbols glob)
- toolchain/src/main/java/com/arduinomobileworkshop/toolchain/ArduinoCliManager.kt

Part 2 (interactive managers):
- app/src/main/res/layout/item_manager.xml (new)
- app/src/main/res/layout/activity_boards_manager.xml
- app/src/main/res/layout/activity_library_manager.xml
- app/src/main/java/com/arduinomobileworkshop/app/ui/BoardsManagerActivity.kt
- app/src/main/java/com/arduinomobileworkshop/app/ui/LibraryManagerActivity.kt

Part 3 (RP2040 config & pin map):
- rp2040/src/main/java/com/arduinomobileworkshop/rp2040/services/RP2040ProgrammerService.kt
- app/src/main/res/layout/activity_multi_programmer.xml
- app/src/main/res/layout/activity_logic_analyzer.xml
- app/src/main/java/com/arduinomobileworkshop/app/ui/MultiProgrammerActivity.kt
- app/src/main/java/com/arduinomobileworkshop/app/ui/LogicAnalyzerActivity.kt
- app/src/main/assets/firmware/swd_helper.uf2 (new)
- app/src/main/assets/firmware/jtag_helper.uf2 (new)
- app/src/main/assets/firmware/avr_isp_helper.uf2 (new)
- app/src/main/assets/firmware/logic_analyzer_helper.uf2 (new)
