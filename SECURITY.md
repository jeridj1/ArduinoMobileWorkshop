# Security and Scope

Arduino Mobile Workshop is intended for programming, diagnostics, debugging, firmware development, and electronics experimentation on hardware the user owns or is authorized to service.

## Included

- Board identification.
- USB/serial discovery.
- Firmware compilation.
- Firmware upload/programming through supported interfaces.
- Debugging interfaces such as supported CMSIS-DAP/SWD workflows.
- Logic analysis and protocol observation.
- Firmware image inspection and management.
- Recovery workflows for supported devices.
- Device documentation and capability reporting.

## Excluded from the public project

The public application must not automatically:

- Search arbitrary connected devices for exploitable vulnerabilities.
- Select or execute exploits to obtain access.
- Attempt credential guessing or credential theft.
- Bypass authentication or authorization.
- Circumvent access controls.
- Install persistence for unauthorized access.
- Treat a successful exploit as a normal device-management operation.

A device being physically connected is not sufficient authorization for invasive security actions.

## Important distinction

A programmer/debugger and a vulnerability-exploitation tool are different things. Programming a supported target, reading a flash chip, debugging firmware, or analyzing a signal can be legitimate engineering work. Automatically finding a way around a device's security boundary is outside this public project's intended scope.

## Safety

Hardware operations must include explicit voltage, pinout, target-power, and irreversible-operation warnings when relevant. The app should prefer a refusal to guess over a potentially destructive guess.
