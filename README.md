# Arduino Mobile Workshop

A full-featured Android IDE for compiling, managing, and uploading firmware to Arduino and other microcontroller boards, with special support for RP2040 devices including Logic Analyzer and Multi-Programmer modes.

## Features

### Core Features
- Arduino IDE-like interface - Familiar look and feel for Arduino users
- Serial Monitor - Real-time communication with connected devices
- Boards Manager - Install and manage board definitions
- Library Manager - Install and manage Arduino libraries
- File Management - Open, save, and manage sketches

### RP2040-Specific Features
- Multi-Programmer Mode - Program multiple RP2040 devices simultaneously
- Built-in Logic Analyzer - Capture and analyze digital signals using RP2040 PIO
- UF2 Bootloader Support - Direct firmware uploads via UF2 protocol
- Direct Serial Programming - Alternative programming method

## Architecture

The app is organized into modules:
- app - Main application module with UI activities
- toolchain - Compilation and build system
- workspace - Sketch and project management
- usb - USB communication layer
- rp2040 - RP2040-specific functionality

## Getting Started

### Prerequisites
- Android device with API 24+ (Android 7.0+)
- USB OTG support for device communication
- RP2040 device (Raspberry Pi Pico or compatible)

### Installation
1. Clone this repository
2. Open in Android Studio
3. Build and run on your Android device
4. Connect your Arduino/RP2040 device via USB OTG

## Usage

### Basic Workflow
1. Open the app
2. Select your board from Boards Manager
3. Create or open a sketch
4. Click Verify to compile
5. Click Upload to program your device

### Using Logic Analyzer
1. Connect your RP2040 device
2. Navigate to Logic Analyzer from the menu
3. Select sample rate (100kHz - 2MHz)
4. Select number of channels (4, 8, or 16)
5. Click Start Capture
6. Analyze the captured signals

### Using Multi-Programmer
1. Connect multiple RP2040 devices
2. Navigate to Multi-Programmer from the menu
3. Click Scan to detect connected devices
4. Select devices to program
5. Add your UF2 file
6. Click Program to flash all selected devices

## Project Structure

ArduinoMobileWorkshop/
- app/ - Main app module
  - src/main/java - UI Activities
  - src/main/res - Resources
- toolchain/ - Compilation module
- workspace/ - Project management module
- usb/ - USB communication module
- rp2040/ - RP2040-specific module
- .github/workflows/ - CI/CD workflows

## Technologies Used

- Kotlin - Primary programming language
- AndroidX - Modern Android components
- Coroutines - Asynchronous operations
- USB Serial - Device communication
- Navigation Component - App navigation
- Material Design - UI components

## RP2040 Implementation Details

### Logic Analyzer
Uses RP2040 PIO for high-speed sampling. Supports sample rates up to 2 MHz. Can capture on 4, 8, or 16 channels simultaneously. Data is streamed to the app for visualization and analysis.

### Multi-Programmer
Can program multiple RP2040 devices in sequence. Supports both UF2 bootloader and direct serial modes. Progress tracking for each device. Error handling and retry logic.

## Build

The project uses GitHub Actions for continuous integration. Builds are automatically triggered on pushes to the main branch.

### Local Build
./gradlew assembleDebug

### Release Build
./gradlew assembleRelease

## Contributing

Contributions are welcome! Please open issues or submit pull requests.

## License

This project is licensed under the MIT License.