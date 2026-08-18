name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Build with Gradle (use installed Gradle, not repo wrapper)
      uses: gradle/gradle-build-action@v3
      with:
        wrapper-enabled: false
        gradle-version: '8.6'
        arguments: assembleDebug --stacktrace --no-daemon

    - name: Upload APK
      uses: actions/upload-artifact@v4
      if: success()
      with:
        name: ArduinoMobileWorkshop
        path: app/build/outputs/apk/debug/app-debug.apk
