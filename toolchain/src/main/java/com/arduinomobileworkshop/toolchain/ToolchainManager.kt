package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

class ToolchainManager(private val context: Context) {
    companion object {
        private const val TAG = "AMW_Toolchain"
        private const val TOOLCHAIN_DIR = "arduino-toolchain"
        private const val CLI_NAME = "arduino-cli"
        private const val ESP32_INDEX = "https://espressif.github.io/arduino-esp32/package_esp32_index.json"
        private const val ESP8266_INDEX = "https://arduino.esp8266.com/stable/package_esp8266com_index.json"
        private const val RP2040_INDEX = "https://github.com/earlephilhower/arduino-pico/releases/download/global/package_rp2040_index.json"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var toolchainDir: File? = null
    private var isInitialized = false
    private val availableBoards: MutableList<Board> = mutableListOf()
    private val installedLibraries: MutableList<Library> = mutableListOf()

    fun initialize() {
        if (isInitialized) return
        try {
            toolchainDir = File(context.filesDir, TOOLCHAIN_DIR).also { it.mkdirs() }
            initializeDefaultBoards()
            initializeDefaultLibraries()
            isInitialized = true
            Log.d(TAG, "Toolchain initialized; Arduino CLI available=${isArduinoCliAvailable()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize toolchain", e)
            isInitialized = false
        }
    }

    private fun initializeDefaultBoards() {
        availableBoards.clear()
        availableBoards += Board("arduino_uno", "Arduino Uno", "avr", "arduino:avr", "1.8.6", "avr", "avrdude", "arduino", "arduino:avr:uno")
        availableBoards += Board("arduino_nano", "Arduino Nano", "avr", "arduino:avr", "1.8.6", "avr", "avrdude", "arduino", "arduino:avr:nano")
        availableBoards += Board("arduino_mega", "Arduino Mega 2560", "avr", "arduino:avr", "1.8.6", "avr", "avrdude", "arduino", "arduino:avr:mega")
        availableBoards += Board("esp32_dev", "ESP32 Dev Module", "esp32", "esp32:esp32", "latest", "xtensa", "esptool", "esp32", "esp32:esp32:esp32")
        availableBoards += Board("raspberry_pi_pico", "Raspberry Pi Pico", "rp2040", "rp2040:rp2040", "latest", "arm", "picotool", "uf2", "rp2040:rp2040:rpipico")
        availableBoards += Board("adafruit_feather_rp2040", "Adafruit Feather RP2040", "rp2040", "rp2040:rp2040", "latest", "arm", "picotool", "uf2", "rp2040:rp2040:adafruit_feather")
        availableBoards += Board("sparkfun_pro_micro_rp2040", "SparkFun Pro Micro RP2040", "rp2040", "rp2040:rp2040", "latest", "arm", "picotool", "uf2", "rp2040:rp2040:promicro")
    }

    private fun initializeDefaultLibraries() {
        installedLibraries.clear()
    }

    fun getAvailableBoards(): List<Board> = availableBoards.toList()
    fun getInstalledBoards(): List<Board> = availableBoards.filter { isCoreInstalled(it) }
    fun getBoardConfig(boardId: String): Board? = availableBoards.find { it.id == boardId }
    fun getBoardConfigByName(boardName: String): Board? = availableBoards.find { it.name == boardName }

    fun addBoardConfig(config: Board): Boolean {
        if (availableBoards.any { it.id == config.id }) return false
        availableBoards.add(config)
        return true
    }

    fun removeBoardConfig(boardId: String): Boolean = availableBoards.removeIf { it.id == boardId }

    fun getInstalledLibraries(): List<Library> = installedLibraries.toList()
    fun getLibrary(libraryId: String): Library? = installedLibraries.find { it.id == libraryId }

    fun isArduinoCliAvailable(): Boolean = locateArduinoCli()?.canExecute() == true

    fun installArduinoCli(callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val dir = toolchainDir ?: throw IllegalStateException("Toolchain is not initialized")
                val target = File(dir, CLI_NAME)
                val url = when {
                    Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "https://downloads.arduino.cc/arduino-cli/arduino-cli_latest_Linux_ARM64.tar.gz"
                    Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "https://downloads.arduino.cc/arduino-cli/arduino-cli_latest_Linux_ARMv7.tar.gz"
                    Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "https://downloads.arduino.cc/arduino-cli/arduino-cli_latest_Linux_64bit.tar.gz"
                    Build.SUPPORTED_ABIS.any { it == "x86" } -> "https://downloads.arduino.cc/arduino-cli/arduino-cli_latest_Linux_32bit.tar.gz"
                    else -> throw IllegalStateException("Unsupported Android CPU: ${Build.SUPPORTED_ABIS.joinToString()}")
                }
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
                connection.inputStream.use { extractCli(it, target) }
                if (!target.setExecutable(true, true) && !target.canExecute()) throw IllegalStateException("Downloaded Arduino CLI but could not make it executable")
                val version = runCli(listOf("version")).stdout.trim()
                if (version.isEmpty()) throw IllegalStateException("Arduino CLI did not start")
                configureCli()
                callback(true, version)
            } catch (e: Exception) {
                Log.e(TAG, "Arduino CLI installation failed", e)
                callback(false, e.message ?: "Arduino CLI installation failed")
            }
        }
    }

    fun installLibrary(library: String, callback: (Boolean, String) -> Unit) {
        executor.execute { val result = runCli(listOf("lib", "install", library)); callback(result.exitCode == 0, result.message()) }
    }

    fun installBoardPackage(packageName: String, callback: (Boolean, String) -> Unit) {
        executor.execute { val result = runCli(listOf("core", "install", packageName)); callback(result.exitCode == 0, result.message()) }
    }

    fun updateIndexes(callback: (Boolean, String) -> Unit) {
        executor.execute { val result = runCli(listOf("core", "update-index")); callback(result.exitCode == 0, result.message()) }
    }

    fun compileSketch(sketchDir: File, boardId: String): Boolean {
        if (!isInitialized || !sketchDir.isDirectory) return false
        val board = getBoardConfig(boardId) ?: return false
        if (!isArduinoCliAvailable() || board.fqbn.isBlank()) return false
        val outputDir = File(sketchDir, ".build").also { it.mkdirs() }
        val result = runCli(listOf("compile", "--fqbn", board.fqbn, "--build-path", outputDir.absolutePath, sketchDir.absolutePath))
        Log.i(TAG, "Compile ${sketchDir.name}: ${result.message()}")
        return result.exitCode == 0
    }

    fun uploadToDevice(hexFile: File, boardId: String, serialPort: String? = null): Boolean {
        if (!isInitialized || !hexFile.exists()) return false
        val board = getBoardConfig(boardId) ?: return false
        if (!isArduinoCliAvailable() || board.fqbn.isBlank()) return false
        val command = mutableListOf("upload", "-b", board.fqbn)
        if (!serialPort.isNullOrBlank()) command += listOf("-p", serialPort)
        command += listOf("--input-file", hexFile.absolutePath)
        val result = runCli(command)
        Log.i(TAG, "Upload ${hexFile.name}: ${result.message()}")
        return result.exitCode == 0
    }

    fun getToolchainDir(): File? = toolchainDir
    fun isInitialized(): Boolean = isInitialized

    private fun isCoreInstalled(board: Board): Boolean {
        if (!isArduinoCliAvailable()) return false
        return runCli(listOf("core", "list")).stdout.lines().any { it.trimStart().startsWith(board.packageName) }
    }

    private fun configureCli() {
        runCli(listOf("config", "init", "--overwrite"))
        runCli(listOf("config", "set", "board_manager.additional_urls", "$ESP32_INDEX,$ESP8266_INDEX,$RP2040_INDEX"))
        runCli(listOf("core", "update-index"))
    }

    private fun locateArduinoCli(): File? {
        val bundled = toolchainDir?.let { File(it, CLI_NAME) }
        if (bundled?.canExecute() == true) return bundled
        val path = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        return path.asSequence().map { File(it, CLI_NAME) }.firstOrNull { it.canExecute() }
    }

    private data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        fun message(): String = (stdout + if (stderr.isNotBlank()) "\n$stderr" else "").trim()
    }

    private fun runCli(args: List<String>): CommandResult {
        val executable = locateArduinoCli() ?: return CommandResult(-1, "", "Arduino CLI is not installed")
        return try {
            val process = ProcessBuilder(listOf(executable.absolutePath) + args)
                .directory(toolchainDir ?: context.filesDir)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            CommandResult(process.waitFor(), stdout, stderr)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Failed to execute Arduino CLI")
        }
    }

    private fun extractCli(input: InputStream, target: File) {
        GZIPInputStream(input).use { gzip ->
            val header = ByteArray(512)
            while (true) {
                readFully(gzip, header)
                if (header.all { it.toInt() == 0 }) return
                val name = header.copyOfRange(0, 100).toString(Charsets.UTF_8).trim('\u0000', ' ')
                val sizeText = header.copyOfRange(124, 136).toString(Charsets.US_ASCII).trim('\u0000', ' ')
                val size = sizeText.toLongOrNull(8) ?: 0L
                if (name.endsWith("/")) skipFully(gzip, size)
                else if (name.substringAfterLast('/') == CLI_NAME) {
                    FileOutputStream(target).use { output -> copyExactly(gzip, output, size) }
                    skipFully(gzip, (512 - (size % 512)) % 512)
                    return
                } else {
                    skipFully(gzip, size)
                    skipFully(gzip, (512 - (size % 512)) % 512)
                }
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw IllegalStateException("Unexpected end of Arduino CLI archive")
            offset += read
        }
    }

    private fun copyExactly(input: InputStream, output: FileOutputStream, size: Long) {
        val buffer = ByteArray(16 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IllegalStateException("Unexpected end of Arduino CLI archive")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) throw IllegalStateException("Unexpected end of Arduino CLI archive")
                remaining--
            } else remaining -= skipped
        }
    }
}
