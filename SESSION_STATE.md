# Session State

This file is the persistent handoff point for humans and AI contributors.

## Status

**Phase:** Project foundation / architecture
**Implementation:** Not yet feature-complete
**Last updated:** 2026-08-16

## Completed

- Public GitHub repository created.
- Initial README established.
- Comprehensive AI/developer handoff protocol added.
- Decision made to target an Arduino-IDE-like Android experience rather than a generic serial-terminal app.
- Decision made to keep the architecture modular so the toolchain can later be reused by OpenDeviceToolkit.
- RP2040 multifunction hardware support identified as a major optional feature.
- Public-project security boundary established: no automatic vulnerability/exploit/access-gaining features.

## Not yet implemented

- Android project skeleton.
- Main IDE/editor screen.
- Project/sketch storage.
- Arduino CLI/toolchain integration.
- Board package manager.
- Library manager.
- USB device discovery.
- Serial monitor.
- Compile pipeline.
- Upload pipeline.
- Human-friendly compiler diagnostics.
- Board capability database.
- RP2040 logic-analyzer firmware.
- RP2040 programming/debug firmware.
- Automated tests.
- Android CI/build pipeline.
- Release APK packaging.

## Immediate next task

Create the Android application skeleton and establish the clean module boundaries before implementing individual features.

## Next contributor should

1. Read `HANDOFF.md`, `ARCHITECTURE.md`, `ROADMAP.md`, and `SECURITY.md`.
2. Inspect the repository tree and recent commits.
3. Create the Android project structure.
4. Make the smallest possible buildable app.
5. Add CI that can prove the Android project builds.
6. Update this file with the exact build result.

## Testing rule

A feature is not considered complete until there is a reproducible test or a clearly documented hardware-dependent test procedure.

## Known unknowns

- Exact Android-native strategy for running Arduino CLI and its native toolchains.
- Storage requirements for multiple board cores/toolchains.
- USB host behavior across Android manufacturers.
- Best implementation strategy for third-party board indexes.
- RP2040 USB transport architecture and firmware partitioning.
- Which target programming protocols should be included in the first RP2040 hardware release.
