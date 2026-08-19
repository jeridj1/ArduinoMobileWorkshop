# ArduinoMobileWorkshop autonomous repair brief

## Mission
Take ownership of this repository as a real Android application project. Repair it systematically until it produces a working, installable APK and the core functionality is stable. Do not stop after fixing the first compiler error.

## Project intent
ArduinoMobileWorkshop is an Android mobile Arduino development/workshop application. It is intended to work with Arduino/RP2040-class hardware over USB, manage boards, libraries, sketches/projects, provide serial-monitor and development/programming functionality, and package as a usable Android application.

## Working rules
- Work from a dedicated repair branch or PR. Never destroy `main` merely to make the build pass.
- Inspect the repository architecture before adding compatibility hacks.
- Prefer clean top-level model types and clear module boundaries over nested-model/import workarounds.
- Preserve existing intended functionality where it is sound, but replace structurally broken code when necessary.
- Do not merely silence compiler errors with casts, dummy implementations, empty methods, or broad exception swallowing.
- Android UI code must not perform blocking USB/device I/O on the main thread.
- USB resources, executors, receivers, and connections must be closed/cancelled with lifecycle-safe cleanup.
- Avoid duplicate managers/classes representing the same responsibility. Consolidate or clearly separate them.
- Keep Gradle/module configuration consistent and remove stale dependencies/configuration only when confirmed unused.
- Run the strongest available build/test checks after meaningful groups of changes.
- Continue iterating through failures without requiring a human to say “continue” after each one.
- When the build succeeds, verify that the APK artifact is actually generated and identify its exact path.
- After compilation succeeds, inspect for obvious runtime/lifecycle/threading/resource problems before declaring completion.

## Current known history
The repair work has already exposed and repaired substantial lower-level issues. USB, workspace, toolchain, and RP2040 modules have reached successful compilation in prior CI runs. The remaining failures have been concentrated in the Android app module.

Known app-layer trouble has included:
- incorrect imports/references for `Board`, `Library`, and `SketchProject`;
- duplicate/stale `SerialMonitorActivity` implementations;
- references to a nonexistent `SerialPortManager`;
- missing Android `R`/`ArrayAdapter` imports;
- Material component/dependency mismatches;
- stale `selectedDevices` references;
- serial reads occurring on the Android main thread.

A serial-monitor repair has already moved blocking reads onto a dedicated worker executor with cancellation/lifecycle cleanup. Do not reintroduce main-thread USB I/O.

## Definition of done
Do not report the project as “fixed” merely because one CI job passes. Completion requires:
1. clean Android build;
2. successful APK generation;
3. APK artifact identifiable and downloadable from CI;
4. no unresolved compiler errors or missing dependencies;
5. no obvious lifecycle/threading/resource-management defects in the repaired paths;
6. core USB/workspace/toolchain/serial-monitor paths remain internally consistent;
7. changes are isolated in the repair branch/PR and summarized clearly.

If a requirement cannot be verified in the available environment, say exactly what could not be verified rather than claiming it works.
