package com.arduinomobileworkshop.toolchain

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

class ArduinoCliManager(private val context: Context) {
    data class Result(val exitCode: Int, val output: String) { val success: Boolean get() = exitCode == 0 }
    private val executable: File get() = File(context.applicationInfo.nativeLibraryDir, "libarduino_cli.so")
    private val configDir = File(context.filesDir, "arduino-cli")

    @Synchronized
    fun ensureInstalled(): Boolean = executable.exists() && executable.canExecute()

    fun run(vararg args: String, timeoutSeconds: Long = 120): Result {
        if (!ensureInstalled()) return Result(-1, "Android Arduino CLI is not present in this APK build.")
        return try {
            configDir.mkdirs()
            val command = ArrayList<String>(args.size + 1)
            command.add(executable.absolutePath)
            command.addAll(args)
            val process = ProcessBuilder(command)
                .directory(configDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["ARDUINO_DATA_DIR"] = File(configDir, "data").absolutePath
                    environment()["ARDUINO_USER_DIR"] = File(configDir, "user").absolutePath
                    environment()["ARDUINO_DIRECTORIES_DATA"] = File(configDir, "data").absolutePath
                }
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return Result(-1, "$output\nArduino CLI timed out.")
            }
            Result(process.exitValue(), output)
        } catch (e: Exception) { Result(-1, e.message ?: "Unable to run Arduino CLI") }
    }
}
