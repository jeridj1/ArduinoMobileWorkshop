# Roadmap

This roadmap is intentionally staged. A polished tool is more useful than a giant half-working feature pile.

## Phase 0 - Foundation

- [x] Public repository.
- [x] Project goals.
- [x] AI handoff protocol.
- [x] Persistent session state.
- [x] Architecture document.
- [x] Security boundary.
- [x] Android project skeleton (8 activities, 4 modules, layouts).
- [x] First buildable APK (verified; CI green).
- [x] Android CI (workflow validated; debug + release APK + unit tests).
- [x] Automated JVM unit tests (workspace, rp2040, toolchain modules).

## Phase 1 - Familiar Arduino IDE workflow

- [ ] Main editor screen.
- [ ] New/Open/Save/Save As.
- [ ] Sketch/project folders.
- [ ] Syntax highlighting.
- [ ] Undo/redo.
- [ ] Search/replace.
- [ ] Compile button.
- [ ] Upload button.
- [ ] Board selector.
- [ ] Port selector.
- [ ] Build output panel.
- [ ] Human-readable errors.
- [ ] Serial Monitor (UI exists, backend mocked).

## Phase 2 - Arduino toolchain

- [ ] Arduino CLI integration proof of concept (currently mocked).
- [ ] AVR core support.
- [ ] ESP32 core support.
- [ ] ESP8266 core support.
- [ ] RP2040 Arduino core support.
- [ ] Third-party board-index management.
- [ ] Board package installation/removal/version selection.
- [ ] Library Manager (UI exists, backend mocked).
- [ ] Library dependency handling.
- [ ] Cached/offline packages where practical.

## Phase 3 - Android hardware integration

- [ ] USB Host device detection (currently mocked in MultiProgrammerActivity).
- [ ] USB permission handling.
- [ ] Serial-port abstraction.
- [ ] Automatic port identification.
- [ ] Board auto-detection where protocol permits.
- [ ] Upload progress.
- [ ] Upload cancellation/recovery.
- [ ] Disconnect/reconnect handling.

## Phase 4 - Mobile quality

- [ ] Phone layout.
- [ ] Tablet layout.
- [ ] Stylus-friendly editor behavior.
- [ ] Dark/light themes.
- [ ] Large-text/accessibility support.
- [ ] Project backup/export.
- [ ] Import existing Arduino sketches.
- [ ] Crash-safe project recovery.

## Phase 5 - RP2040 electronics bench accessory

- [ ] RP2040 bridge firmware.
- [ ] Android bridge protocol.
- [ ] Logic analyzer capture (UI exists, backend mocked).
- [ ] Waveform viewer.
- [ ] Trigger configuration.
- [ ] Frequency/period/pulse measurements.
- [ ] UART analyzer.
- [ ] I2C analyzer.
- [ ] SPI analyzer.
- [ ] GPIO tools.

## Phase 6 - Programming/debugging accessory

- [ ] CMSIS-DAP/SWD backend where supported.
- [ ] Supported-target database.
- [ ] Firmware image management.
- [ ] Read/verify/write workflows for supported targets.
- [ ] Explicit target voltage and wiring warnings.
- [ ] Additional programming protocols as independently validated backends.

## Phase 7 - OpenDeviceToolkit integration

- [ ] Identify reusable public modules.
- [ ] Establish stable shared interfaces.
- [ ] Integrate RP2040 bridge support where appropriate.
- [ ] Avoid duplicating USB, serial, firmware, and protocol code.

## Release criteria

A release must have:

- Reproducible build.
- Installation instructions.
- Supported-board list.
- Known limitations.
- Hardware test results.
- No claims of universal compatibility without evidence.
