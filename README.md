# Arduino Mobile Workshop

An Android-first development environment for compiling, managing, and uploading firmware to Arduino and other microcontroller boards directly from a phone.

## Project goals

- Compile and upload Arduino sketches from Android.
- Detect boards and USB serial devices automatically.
- Install and manage board cores and libraries.
- Support common Arduino, ESP8266, ESP32, and other boards.
- Provide a clean mobile interface instead of forcing users through desktop-oriented tooling.
- Give useful, human-readable error messages when something goes wrong.
- Keep the architecture modular so the compiler/upload engine can later be reused by other projects.

## Status

Early project setup. Architecture and implementation are being developed incrementally.

## Planned architecture

The project will separate the Android user interface from the firmware toolchain layer. The toolchain layer will handle board packages, libraries, compilation, USB serial communication, and uploading. This separation is intentional so the same capabilities can eventually be integrated into other device-management software.

## License

License to be selected before the first public release.