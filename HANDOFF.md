# Arduino Mobile Workshop Handoff

## Purpose

Arduino Mobile Workshop (AMW) is a public, Android-first development environment intended to feel familiar to users of the desktop Arduino IDE while being substantially better suited to phones and tablets.

The primary goal is simple: connect a microcontroller to an Android device, edit or paste firmware, compile it locally on the phone, install missing board cores/libraries when needed, and upload the result without requiring a Windows/Mac/Linux computer.

## Current repository state

This repository is intentionally at the beginning of implementation. The initial README establishes the project direction. Do not assume features are implemented merely because they appear in the roadmap.

## Product direction

The interface should deliberately resemble the classic Arduino IDE wherever that makes navigation easier:

- Familiar sketch/editor layout.
- New, Open, Save, Save As, Verify/Compile, Upload, Serial Monitor.
- Board selection and port selection in obvious locations.
- Boards Manager and Library Manager concepts.
- Human-readable compiler and upload errors.
- A simple advanced/settings area for users who need deeper control.
- Touch-friendly controls without making the interface feel like a toy.
- Stylus support through normal Android text-selection, cursor, scrolling, and precise touch targets.
- Responsive layouts for phones, tablets, and future devices. Do not hard-code the Galaxy S23 Ultra dimensions.

## Core technical direction

Use a layered architecture so the Android UI is not permanently tied to the compiler implementation.

Suggested layers:

1. Android UI
2. Project/workspace manager
3. Toolchain orchestration
4. Board/core/package manager
5. Library manager
6. Compiler/linker adapter
7. Upload/programmer transport
8. USB/serial abstraction
9. Device capability database
10. Optional hardware-tool plugins

Arduino CLI should be evaluated as the foundation for Arduino-compatible board/core/package management, compilation, and uploading rather than reimplementing the Arduino build system from scratch.

## AI continuation protocol

Every AI/developer session MUST begin by reading:

1. `README.md`
2. `HANDOFF.md`
3. `SESSION_STATE.md`
4. `ROADMAP.md`
5. `ARCHITECTURE.md`
6. `SECURITY.md`
7. The relevant source files and recent commits.

Before ending a session, the contributor MUST update `SESSION_STATE.md` with:

- Current date/time.
- What was actually completed.
- Files created or changed.
- What was tested.
- What failed.
- Known bugs.
- Exact next task.
- Any architectural decisions made.
- Any assumptions that still need verification.
- The safest first action for the next contributor.

Never claim a feature works because documentation says it should work. Inspect code and test results.

If an AI runs out of context, reaches a usage limit, or must stop unexpectedly, the last completed atomic task should be recorded in `SESSION_STATE.md` before stopping whenever possible. The next AI should continue from that file rather than asking the user to reconstruct the entire project history.

## Change discipline

- Prefer small, understandable commits.
- Do not rewrite working code merely for cosmetic reasons.
- Do not add dependencies without documenting why they are needed.
- Do not silently remove functionality.
- Keep public documentation synchronized with implementation.
- Mark experimental functionality clearly.
- Never report an untested build as verified.

## Relationship to OpenDeviceToolkit

The user's existing private repository `jeridj1/OpenDeviceToolkit` contains broader device-diagnostic/workbench concepts, including an earlier handoff that identifies an RP2040 multifunction hardware bridge as a future direction. AMW may eventually share reusable libraries or interfaces with that project.

Do NOT copy private repository content into this public repository merely because it exists there. Reuse only independently implementable concepts or code that is explicitly suitable for public release.

The long-term relationship should look like:

AMW = public mobile development/firmware workbench.
ODT = broader device diagnostics and engineering workbench.
Shared components = only deliberately separated, documented, reusable modules.

## RP2040 expansion

A major optional feature is an RP2040-based hardware accessory that can turn the phone into a practical electronics bench instrument.

Potential modes include:

- Logic analyzer.
- UART/serial bridge.
- I2C monitor/tool.
- SPI monitor/tool.
- GPIO analyzer.
- SWD/CMSIS-DAP programming/debug interface where supported.
- Firmware programming interfaces for supported targets.
- Frequency/period measurement.
- Pulse/PWM measurement and generation.

"Universal programmer" must be treated as a long-term goal, not a promise that every chip family can be programmed with one circuit. Each target family needs an explicit electrical protocol, voltage level, reset/programming sequence, and software backend.

## Public-project security boundary

This public edition is an engineering, programming, diagnostic, and recovery tool. It must NOT include automatic vulnerability discovery, exploit selection, credential attacks, unauthorized-access automation, or code intended to gain privileged access to arbitrary devices.

Device identification, protocol inspection, firmware analysis, debugging, programming, and recovery of devices the user owns or is authorized to service are within the intended scope.

See `SECURITY.md` for the detailed boundary.
