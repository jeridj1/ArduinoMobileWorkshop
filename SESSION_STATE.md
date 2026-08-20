# Session State

This file is the persistent handoff point for humans and AI contributors.

## Status

**Phase:** Project foundation / architecture
**Implementation:** Android project skeleton complete, build fixed
**Last updated:** 2026-08-20

## Completed

- Public GitHub repository created.
- Initial README established.
- Comprehensive AI/developer handoff protocol added.
- Decision made to target an Arduino-IDE-like Android experience rather than a generic serial-terminal app.
- Decision made to keep the architecture modular so the toolchain can later be reused by OpenDeviceToolkit.
- RP2040 multifunction hardware support identified as a major optional feature.
- Public-project security boundary established: no automatic vulnerability/exploit/access-gaining features.
- **Android project skeleton created:**  
  - 8 Activities: MainActivity, BoardsManagerActivity, LibraryManagerActivity, FilePickerActivity, SerialMonitorActivity, LogicAnalyzerActivity, MultiProgrammerActivity, SettingsActivity  
  - 4 Library modules: toolchain, workspace, usb, rp2040  
  - Layouts for all activities  
  - CI workflow configured
- **Build issues fixed:**  
  - Fixed usb module dependency: changed from felHR85:UsbSerial to mik3y:usb-serial-for-android:3.11.0  
  - Restored app module dependencies: uncommented toolchain, workspace, usb, rp2040 project dependencies  
  - Removed insecure JitPack protocol setting from settings.gradle.kts  
  - Cleaned up debug files (BUILD_TRIGGER*, test_access.txt)
  - Updated compileSdk and targetSdk to 35 for usb-serial-for-android:3.11.0 compatibility
  - Fixed Material 3 theme attributes (replaced colorBackground with android:windowBackground)
  - Fixed Kotlin version to 1.9.20

## Not yet implemented

- Main IDE/editor screen.
- Project/sketch storage.
- Arduino CLI/toolchain integration (currently mocked).
- Board package manager.
- Library manager.
- USB device discovery (currently mocked in MultiProgrammerActivity).
- Serial monitor (UI exists but backend not fully implemented).
- Compile pipeline (mocked in ToolchainManager).
- Upload pipeline (mocked in ToolchainManager).
- Human-friendly compiler diagnostics.
- Board capability database.
- RP2040 logic-analyzer firmware.
- RP2040 programming/debug firmware.
- Automated tests.
- Android CI/build pipeline (workflow exists but needs validation).
- Release APK packaging.

## Immediate next task

Verify the build compiles successfully with the fixed dependencies, then implement the actual toolchain backend (compile, upload, library installation).

## Next contributor should

1. Read `HANDOFF.md`, `ARCHITECTURE.md`, `ROADMAP.md`, and `SECURITY.md`.
2. Run `./gradlew build` to verify the project compiles.
3. Implement actual Arduino CLI/toolchain integration in the toolchain module.
4. Replace mock implementations with real functionality.
5. Update this file with the exact build result.

## Testing rule

A feature is not considered complete until there is a reproducible test or a clearly documented hardware-dependent test procedure.

## Known unknowns

- Exact Android-native strategy for running Arduino CLI and its native toolchains.
- Storage requirements for multiple board cores/toolchains.
- USB host behavior across Android manufacturers.
- Best implementation strategy for third-party board indexes.
- RP2040 USB transport architecture and firmware partitioning.
- Which target programming protocols should be included in the first RP2040 hardware release.
