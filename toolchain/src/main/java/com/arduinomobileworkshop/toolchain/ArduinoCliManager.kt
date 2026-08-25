package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Wrapper around a cross-compiled (arm64-v8a) arduino-cli binary bundled with
 * the app.
 *
 * Desktop arduino-cli executables cannot run on Android, so a native
 * arm64-v8a build is shipped inside the APK and extracted at runtime into the
 * application's private files directory
 * (/data/data/com.arduinomobileworkshop.app/files/arduino-cli/). Compiler
 * commands (e.g. 'arduino-cli compile') are launched with [ProcessBuilder];
 * standard error is merged into the output stream so compiler diagnostics are
 * redirected straight back to the user's graphical console.
 *
 * Resolution order for the binary:
 *  1. APK asset '$ASSET_NAME'  -> extracted into filesDir.
 *  2. jniLib '$JNI_LIB_NAME'    -> copied from nativeLibraryDir (fallback).
 */
class ArduinoCliManager(private val context: Context) {

    data class Result(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
    }

    companion object {
        private const val TAG = "AMW_ArduinoCli"
        const val ASSET_NAME = "arduino-cli-arm64"
        const val EXE_NAME = "arduino-cli"
        const val JNI_LIB_NAME = "libarduino_cli.so"
    }

    private val installDir: File = File(context.filesDir, "arduino-cli")
    private val configDir: File = File(context.filesDir, "arduino-cli-config")
    private val dataDir: File = File(configDir, "data")
    private val userDir: File = File(configDir, "user")

    private val executable: File get() = File(installDir, EXE_NAME)

    @Synchronized
    fun ensureInstalled(): Boolean {
        if (executable.exists() && executable.canExecute()) return true
        return extract()
    }

    private fun extract(): Boolean {
        installDir.mkdirs()

        // 1) Bundled cross-compiled asset.
        val fromAsset = try {
            context.assets.open(ASSET_NAME).use { input ->
                FileOutputStream(executable).use { output -> input.copyTo(output) }
            }
            executable.setExecutable(true, false)
            true
        } catch (e: IOException) {
            false
        }
        if (fromAsset && executable.canExecute()) return true

        // 2) Fallback: jniLib packaged as a .so.
        val jniLib = File(context.applicationInfo.nativeLibraryDir, JNI_LIB_NAME)
        if (jniLib.exists() && jniLib.canExecute()) {
            try {
                jniLib.copyTo(executable, overwrite = true)
                executable.setExecutable(true, false)
                return executable.canExecute()
            } catch (e: IOException) {
                Log.w(TAG, "Fallback copy failed: " + (e.message ?: ""))
            }
        }
        return false
    }

    fun getExecutablePath(): String = executable.absolutePath
    fun getInstallDir(): File = installDir
    fun getConfigDir(): File = configDir

    /** Writes an arduino-cli config so data/user dirs stay in app-private storage. */
    @Synchronized
    fun initConfig(): Result {
        dataDir.mkdirs(); userDir.mkdirs()
        return run("config", "init", "--dest-dir", configDir.absolutePath, timeoutSeconds = 30)
    }

    fun version(): Result = run("version", timeoutSeconds = 30)

    /**
     * Runs arduino-cli with [args]. Standard error is redirected into the
     * returned [Result.output] so the UI console shows compiler diagnostics.
     */
    fun run(vararg args: String, timeoutSeconds: Long = 120): Result {
        if (!ensureInstalled()) {
            return Result(
                -1,
                "Arduino CLI is not bundled with this APK build. Expected asset '"
                    + ASSET_NAME + "' or jniLib '" + JNI_LIB_NAME + "'."
            )
        }
        configDir.mkdirs(); dataDir.mkdirs(); userDir.mkdirs()
        return try {
            val command = ArrayList<String>(args.size + 1).apply {
                add(executable.absolutePath)
                addAll(args)
            }
            val process = ProcessBuilder(command)
                .directory(configDir)
                .redirectErrorStream(true)   // merge stderr into stdout for the console
                .apply {
                    environment()["ARDUINO_DATA_DIR"] = dataDir.absolutePath
                    environment()["ARDUINO_USER_DIR"] = userDir.absolutePath
                    environment()["ARDUINO_DIRECTORIES_DATA"] = dataDir.absolutePath
                    environment()["ARDUINO_DIRECTORIES_USER"] = userDir.absolutePath
                    environment()["ARDUINO_DIRECTORIES_DOWNLOADS"] =
                        File(configDir, "downloads").absolutePath
                    environment()["HOME"] = configDir.absolutePath
                }
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(
                    -1,
                    output + System.lineSeparator() +
                        "[arduino-cli timed out after " + timeoutSeconds + "s]"
                )
            }
            Result(process.exitValue(), output)
        } catch (e: Exception) {
            Result(-1, "Failed to run Arduino CLI: " + (e.message ?: ""))
        }
    }
}
