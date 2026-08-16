# Architecture

## Design principle

The app should feel like an Arduino IDE first and an Android application second. Android-specific behavior should improve mobility without forcing the user to learn an entirely different workflow.

## Proposed layers

### 1. UI

Responsibilities:

- Sketch editor.
- File/project navigation.
- Compile/upload controls.
- Board and port selectors.
- Serial Monitor.
- Boards Manager.
- Library Manager.
- Settings.
- Build/upload progress.
- Diagnostics.

The UI must support touch and stylus equally. Avoid tiny controls and avoid relying on hover behavior.

### 2. Workspace

Handles:

- Sketch folders.
- `.ino`, headers, source files, assets, and configuration.
- Recent projects.
- Import/export.
- Local project history where practical.

### 3. Toolchain service

A platform-independent interface should expose operations such as:

- list boards
- install core
- install library
- compile sketch
- upload firmware
- identify connected board
- list serial ports

The first implementation should evaluate Arduino CLI rather than creating a second Arduino build system.

### 4. Board/package manager

Responsibilities:

- Board index URLs.
- Core installation.
- Core version selection.
- Installed package inventory.
- Dependency handling.
- Updates.
- Offline/cache behavior.

### 5. Compiler adapter

The UI must not directly know how GCC, avr-g++, xtensa tools, or other compilers work. Adapters should translate a generic build request into toolchain-specific commands and return structured diagnostics.

### 6. Upload adapter

Likewise, upload methods should be modular. Examples include:

- avrdude-style AVR uploads.
- esptool-style ESP uploads.
- UF2 mass-storage workflows.
- Arduino CLI upload backends.
- CMSIS-DAP/SWD in the RP2040 hardware layer.

### 7. USB transport

Use Android USB Host APIs. The transport layer should expose device attach/detach, descriptors, interfaces, endpoints, permissions, and serial communication without forcing the rest of the app to understand Android USB internals.

### 8. Hardware-tool plugin layer

Optional hardware accessories should plug into the same application without contaminating the basic Arduino workflow.

Examples:

- RP2040 Logic Analyzer.
- RP2040 Programmer/Debugger.
- Serial bridge.
- I2C/SPI analyzer.
- PWM/frequency meter.

## RP2040 logic analyzer concept

The RP2040 is a particularly useful companion because its PIO blocks and DMA can capture digital signals with predictable timing.

Initial target:

- 8 digital channels where practical.
- Configurable sample rate.
- Trigger on edge/pattern.
- Buffered capture using DMA.
- USB transfer to Android.
- Mobile waveform viewer.
- Zoom/pan.
- Cursor measurements.
- Frequency and period measurement.
- Pulse width and duty-cycle measurement.
- Export captures to a documented format.

The first version should prioritize reliable capture and useful visualization over chasing extreme sample rates.

## RP2040 programmer/debugger concept

The accessory can expose USB interfaces for supported protocols, potentially including CMSIS-DAP/SWD. Additional target protocols should be added as separate backends rather than pretending one interface can program everything.

Target backends can eventually include:

- ARM SWD/CMSIS-DAP.
- UART bootloaders.
- SPI flash programming.
- I2C EEPROM/programming workflows where electrically appropriate.
- UF2-compatible targets.

Voltage translation and target power must be explicit hardware concerns. The app must never assume that a target is 3.3 V tolerant.

## Shared protocol between Android and RP2040

Use a documented framed USB protocol with:

- protocol version
- command ID
- payload length
- sequence number
- status/error code
- payload
- integrity check where appropriate

This permits firmware updates and future accessory versions without rewriting the entire Android app.

## Future OpenDeviceToolkit integration

Keep the Android application core and hardware-access APIs modular enough that OpenDeviceToolkit can eventually consume them. The two projects should not be forcibly merged.

Possible future shared modules:

- USB device inventory.
- Serial transport.
- Firmware image handling.
- Board/target identification.
- RP2040 bridge protocol.
- Programming backends.
- Logic-capture data format.

## Reliability requirements

The application should clearly distinguish:

- detected
- selected
- supported
- connected
- compiling
- uploaded
- verified
- unknown
- failed

Never turn an unknown hardware condition into a confident guess.
