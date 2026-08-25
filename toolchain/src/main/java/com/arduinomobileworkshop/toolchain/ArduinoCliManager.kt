package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wrapper around a cross-compiled (arm64-v8a) arduino-cli binary bundled with
 * the app as a jniLib.
 *
 * Android only permits programmatic execution of binaries from the app's
 * nativeLibraryDir (the directory where .so files are extracted on install).
 * The CLI is therefore packed as "libarduino-cli.so" in
 * app/src/main/jniLibs/arm64-v8a/ and launched directly from
 * context.applicationInfo.nativeLibraryDir.  Attempting to execute from
 * filesDir or cacheDir raises EACCES (error=13 Permission Denied).
 *
 * Standard error is merged into the output stream so compiler diagnostics are
 * redirected straight back to the user's graphical console.
 */
class ArduinoCliManager(private val context: Context) {

    data class Result(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
    }

    companion object {
        private const val TAG = "AMW_ArduinoCli"

        /** The jniLib name — must match the file in jniLibs/arm64-v8a/. */
        const val JNI_LIB_NAME = "libarduino-cli.so"

        // Retained for backward compatibility with any external callers.
        const val ASSET_NAME = "arduino-cli-arm64"
        const val EXE_NAME = "arduino-cli"
    }

    private val configDir: File = File(context.filesDir, "arduino-cli-config")
    private val dataDir: File = File(configDir, "data")
    private val userDir: File = File(configDir, "user")

    /**
     * The only path Android allows programmatic execution from.  The binary
     * is packed as a jniLib and extracted here by the package installer when
     * extractNativeLibs="true" (useLegacyPackaging = true).
     */
    private val executable: File
        get() = File(context.applicationInfo.nativeLibraryDir, JNI_LIB_NAME)

    /**
     * Returns true when the native binary is present and executable.
     * No extraction is needed — the OS already placed it in nativeLibraryDir.
     */
    @Synchronized
    fun ensureInstalled(): Boolean = executable.exists() && executable.canExecute()

    fun getExecutablePath(): String = executable.absolutePath
    fun getInstallDir(): File = executable.parentFile ?: File(context.applicationInfo.nativeLibraryDir)
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
                "Arduino CLI native binary not found at " + executable.absolutePath +
                    ". Expected jniLib '" + JNI_LIB_NAME + "' in the APK (arm64-v8a)."
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
                .redirectErrorStream(true)
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
