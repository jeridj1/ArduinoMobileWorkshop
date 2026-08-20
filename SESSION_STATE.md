# Session State

This file is the persistent handoff point for humans and AI contributors.

## Status

**Phase:** Project foundation / architecture  
**Implementation:** Android project skeleton complete, build reconciliation in progress  
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
  - Fixed Material Components version from non-existent 2.0.0 to 1.14.0
  - Updated Android Gradle Plugin from 8.3.0 to 8.6.0 to support compileSdk 35
  - Updated Gradle wrapper from 8.6 to 8.7 to support AGP 8.6.0
  - Updated all modules to compileSdk 35 and targetSdk 35

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
- Android CI/build pipeline (workflow exists, needs validation on vibe/build-repair branch).
- Release APK packaging.

## Immediate next task

Verify the build compiles successfully on the `vibe/build-repair` branch, which reconciles `main` and `agent/full-build-repair`.

## Next contributor should

1. Read `HANDOFF.md`, `ARCHITECTURE.md`, `ROADMAP.md`, and `SECURITY.md`.
2. Check out the `vibe/build-repair` branch which contains:
   - All 35 fixes from `agent/full-build-repair`
   - All legitimate changes from `main`
   - Fixed Material Components version (1.14.0 instead of non-existent 2.0.0)
   - Updated AGP to 8.6.0 and Gradle to 8.7
   - Updated compileSdk/targetSdk to 35 for all modules
3. Wait for CI to complete on `vibe/build-repair` branch (triggered by push)
4. If build succeeds, merge `vibe/build-repair` into `main`
5. Update this file with the exact build result
6. Continue implementing actual toolchain functionality

## Testing rule

A feature is not considered complete until there is a reproducible test or a clearly documented hardware-dependent test procedure.

## Known unknowns

- Exact Android-native strategy for running Arduino CLI and its native toolchains.
- Storage requirements for multiple board cores/toolchains.
- USB host behavior across Android manufacturers.
- Best implementation strategy for third-party board indexes.
- RP2040 USB transport architecture and firmware partitioning.
- Which target programming protocols should be included in the first RP2040 hardware release.

## Current Branch Status

- Branch: `vibe/build-repair`
- Base: `agent/full-build-repair` (35 commits of fixes)
- Merged changes from `main`:
  - 62bbb37: Update compileSdk and targetSdk to 35
  - 5464b40: Add build verification guide
  - 5ebf109: Fix build issues and clean up repository
- Additional fixes:
  - Material Components: 1.11.0 → 1.14.0 (2.0.0 doesn't exist)
  - usb-serial-for-android: 3.10.0 → 3.11.0
  - AGP: 8.3.0 → 8.6.0
  - Gradle: 8.6 → 8.7
  - Kotlin: kept at 1.9.20 (mik3y library is Java-based)
