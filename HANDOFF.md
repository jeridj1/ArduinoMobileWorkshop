# Arduino Mobile Workshop Handoff

## Purpose
Arduino Mobile Workshop (AMW) is an Android-first development environment intended to feel familiar to Arduino IDE users while becoming a serious electronics development workbench on a phone or tablet.

The flagship target is a Samsung Galaxy S23 Ultra. The intended experience is: connect a microcontroller over USB OTG, edit firmware, compile it, manage board cores/libraries, upload with real protocol confirmation, inspect serial data, and use RP2040 hardware-tool modes without needing a desktop computer.

## User's current flagship specification
The requested product direction is deliberately ambitious. Do not cut these features merely to make the first build easier:

- Sketch editor: syntax highlighting, autocomplete, line numbers, error gutters, undo/redo, find/replace, S Pen-friendly hit targets.
- Cloud compilation using real `arduino-cli` on the backend, returning the correct `.hex`, `.uf2`, or `.bin` artifact plus structured diagnostics translated into plain English.
- Real USB upload with explicit stages, recovery/retry, and verification whenever the protocol supports it.
- Serial monitor and serial plotter with live numeric graphs, multiple series, pause/export.
- Real Arduino Boards Manager and Library Manager using current package/library indexes.
- RP2040 UF2 and PICOBOOT flashing, including multi-programmer operation.
- Logic analyzer with waveform zoom/pan, dual cursors, delta-time/frequency/duty measurements, triggers, and UART/I2C/SPI decoding where the hardware supports it.
- AI assistant for explaining compiler/upload errors and generating/fixing sketches.
- Example/project template gallery.
- S Pen, tablet and Samsung DeX-friendly layouts.
- High-quality dark/light/system themes and polished loading, empty, permission, disconnected, error, and recovery states.

## Architecture decision from the latest design work
The replacement architecture being designed is:

- Expo/React Native mobile application as the primary UI.
- A native Android/Kotlin Expo module for USB Host functionality. Expo Go cannot provide the real USB-host path required for production hardware access.
- One `UsbTransport` interface shared by the UI, with `native` and deterministic `mock` implementations.
- Hono + oRPC backend.
- Drizzle/Turso persistence.
- Cloud `arduino-cli` compilation rather than depending on on-device execution.

Planned backend areas include compile, diagnostics, boards, libraries, sketches/versioning, AI assistance, and examples. Planned mobile areas include Sketches, Editor, Devices, Bench, and Settings.

The native USB module is expected to cover Android USB enumeration/permission plus CDC-ACM, CH340, CP210x and FTDI serial transport, and verified upload backends for supported AVR, ESP and RP2040 protocols. Long-running flashing/capture operations need appropriate Android foreground-service behavior and notification handling.

## Critical reliability rule
Never convert an uncertain hardware state into a confident success. Upload screens must show real staged states such as preparing/resetting/handshake/erasing/writing/verifying/done/failed. A final `done` state means the protocol actually established completion. If a protocol cannot verify read-back, say that explicitly.

## Current GitHub state
Repository: `jeridj1/ArduinoMobileWorkshop`
Default branch: `main`

Important: code shown in another AI/Runable session is NOT automatically present in GitHub. Only commits actually pushed to GitHub are authoritative for cross-account continuation. The latest GitHub history contains the earlier Kotlin implementation and subsequent fixes, but the newer Expo/React Native migration described above must not be assumed to have been pushed unless it appears in Git history.

Recent relevant GitHub commits include:
- `b81ac249d5c975d3534de43e0cbb8ac53c8fcb59` - fix for the `ProgrammerMode` enum string-literal CI breakage.
- `04a8c156d7a9a247ee4a28a8c3bf20728f1ca18d` - historical session-state finalization note.
- `6f8e5e8287651bc4fa2adc039549d6b8331f4371` - RP2040 configuration/pin-map screen and helper-firmware pipeline.
- `5012071498e75cbd2500349718ee0014101de103` - Arduino CLI permission-denied fix and interactive board/library managers.
- `f7c4b0e4f5dedb08364a2b97dc8a44e97ac6b440` - RP2040 PICOBOOT compile fixes.

Historical CI issue #17 reported `app:compileDebugKotlin` failure from malformed `MultiProgrammerActivity.kt` around commit `08231a7`. The source on current main has since been repaired, but CI must always be checked rather than inferred from old documentation.

## Important existing handoff material
Before changing the project, read:

1. `README.md`
2. `HANDOFF.md`
3. `SESSION_STATE.md`
4. `ROADMAP.md`
5. `ARCHITECTURE.md`
6. `SECURITY.md`
7. recent commits and current CI workflow files

Do not assume a roadmap item is implemented merely because documentation says it is.

## Known historical risks
The older Kotlin implementation had placeholder RP2040 helper UF2 assets. Those cannot be treated as real programmer firmware. The older PICOBOOT implementation also requires real-device validation. Any new implementation must keep these limitations explicit until hardware testing proves otherwise.

## Cross-account continuation
GitHub is the handoff boundary. A coworker can continue from another Runable account if the working tree is pushed here. Credits/usage allowance do not need to transfer between accounts for the code to transfer.

Before the current Runable session reaches its usage limit, the safest handoff sequence is:

1. Commit all coherent work.
2. Push it to `jeridj1/ArduinoMobileWorkshop`.
3. Update `SESSION_STATE.md` with the exact latest commit SHA, build/test result, known failures, and next task.
4. Leave this `HANDOFF.md` intact.
5. Have the coworker open/clone the repository and use the continuation prompt below.

### Suggested continuation prompt
> Continue `jeridj1/ArduinoMobileWorkshop` from the latest GitHub commit. Read `HANDOFF.md`, `SESSION_STATE.md`, `README.md`, `ROADMAP.md`, `ARCHITECTURE.md`, `SECURITY.md`, recent commits, and CI workflows before changing anything. The target is a production-quality flagship Android app for a Samsung Galaxy S23 Ultra. The requested feature set includes the sketch editor, cloud real `arduino-cli` compilation with human-readable diagnostics, native Android USB Host transport, verified firmware upload/recovery, serial monitor/plotter, real Boards and Library Managers, RP2040 UF2/PICOBOOT and multi-programmer support, logic analyzer tools with protocol decoding, AI assistance, examples/templates, and S Pen/tablet/DeX layouts. Establish the actual current build/CI state first. Fix blockers in dependency order. Do not fake hardware success or hide failures with mocks. Use mocks only where they are explicitly the simulator fallback. Commit each coherent milestone and update `SESSION_STATE.md` before stopping.

## Change discipline

- Prefer small coherent commits.
- Do not silently remove requested functionality.
- Do not add dependencies without documenting why.
- Do not claim a build is verified unless the build/test result actually exists.
- Keep documentation synchronized with implementation.
- Keep experimental hardware protocols clearly marked until tested on physical hardware.
- Preserve the public repository's security boundary: programming, diagnostics, firmware analysis, and recovery of authorized hardware are in scope; unauthorized access automation is not.

## Relationship to OpenDeviceToolkit
The user's broader OpenDeviceToolkit project contains related device-diagnostics/workbench concepts. Do not copy private code into this public repository merely because it exists there. Shared components should be deliberately separated and suitable for public release.
