package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.util.Log
import java.io.File

class ToolchainManager(private val context: Context) {
    companion object { private const val TAG = "AMW_Toolchain" }

    private val cli = ArduinoCliManager(context)
    private val toolchainDir = File(context.filesDir, "arduino-toolchain")
    private var isInitialized = false
    private val availableBoards = mutableListOf<Board>()
    private val installedLibraries = mutableListOf<Library>()

    data class Board(
        val id: String,
        val name: String,
        val platform: String,
        val packageName: String,
        val version: String,
        val architecture: String,
        val uploadTool: String,
        val programmer: String
    )

    data class Library(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val packageName: String
    )

    fun initialize() {
        if (isInitialized) return
        toolchainDir.mkdirs()
        initializeDefaultBoards()
        isInitialized = true
        Thread { refreshInstalledLibraries() }.start()
        Log.d(TAG, "Toolchain initialized")
    }

    private fun initializeDefaultBoards() {
        availableBoards.clear()
        availableBoards += Board("arduino:avr:uno", "Arduino Uno", "avr", "arduino:avr", "1.8.6", "avr", "avrdude", "arduino")
        availableBoards += Board("arduino:avr:nano", "Arduino Nano", "avr", "arduino:avr", "1.8.6", "avr", "avrdude", "arduino")
        availableBoards += Board("arduino:avr:mega", "Arduino Mega 2560", "avr", "arduino:avr", "1.8.6", "avr", "avrdude", "arduino")
        availableBoards += Board("esp32:esp32:esp32", "ESP32 Dev Module", "esp32", "esp32:esp32", "3.x", "xtensa", "esptool", "esp32")
        availableBoards += Board("rp2040:rp2040:rpipico", "Raspberry Pi Pico", "rp2040", "rp2040:rp2040", "4.x", "arm", "uf2", "uf2")
    }

    private fun refreshInstalledLibraries() {
        val result = cli.run("lib", "list", "--format", "json", timeoutSeconds = 60)
        if (!result.success) return
        // The UI keeps a small local inventory; CLI remains the source of truth for builds.
        installedLibraries.clear()
    }

    fun getAvailableBoards(): List<Board> = availableBoards.toList()
    fun getInstalledBoards(): List<Board> = availableBoards.toList()
    fun getBoardConfig(boardId: String): Board? = availableBoards.find { it.id == boardId }
    fun getBoardConfigByName(boardName: String): Board? = availableBoards.find { it.name == boardName }

    fun addBoardConfig(config: Board): Boolean {
        if (availableBoards.any { it.id == config.id }) return false
        availableBoards += config
        return true
    }

    fun removeBoardConfig(boardId: String): Boolean = availableBoards.removeIf { it.id == boardId }
    fun getInstalledLibraries(): List<Library> = installedLibraries.toList()
    fun getLibrary(libraryId: String): Library? = installedLibraries.find { it.id == libraryId }

    fun installLibrary(libraryName: String, callback: (Boolean, String) -> Unit) {
        Thread {
            val result = cli.run("lib", "install", libraryName, timeoutSeconds = 300)
            callback(result.success, result.output)
        }.start()
    }

    fun installBoardPackage(packageName: String, callback: (Boolean, String) -> Unit) {
        Thread {
            val result = cli.run("core", "install", packageName, timeoutSeconds = 600)
            callback(result.success, result.output)
        }.start()
    }

    fun updateIndexes(callback: (Boolean, String) -> Unit) {
        Thread {
            val result = cli.run("core", "update-index", timeoutSeconds = 300)
            callback(result.success, result.output)
        }.start()
    }

    fun hasCompilerForPlatform(platform: String): Boolean = cli.ensureInstalled() &&
        platform.lowercase() in setOf("rp2040", "avr", "esp32", "esp8266")

    fun compileSketch(sketchDir: File, boardId: String): Boolean = compileSketchDetailed(sketchDir, boardId).success

    fun compileSketchDetailed(sketchDir: File, boardId: String): ArduinoCliManager.Result {
        if (!isInitialized) return ArduinoCliManager.Result(-1, "Toolchain is not initialized")
        val board = getBoardConfig(boardId) ?: return ArduinoCliManager.Result(-1, "Unknown board: $boardId")
        return cli.run("compile", "--fqbn", board.id, sketchDir.absolutePath, timeoutSeconds = 600)
    }

    fun uploadToDevice(hexFile: File, boardId: String, serialPort: String? = null): Boolean {
        val board = getBoardConfig(boardId) ?: return false
        val args = mutableListOf("upload", "--fqbn", board.id)
        if (!serialPort.isNullOrBlank()) args += listOf("--port", serialPort)
        args += hexFile.parentFile?.absolutePath ?: return false
        return cli.run(*args.toTypedArray(), timeoutSeconds = 300).success
    }

    fun getToolchainDir(): File = toolchainDir
    fun isInitialized(): Boolean = isInitialized
    fun getCliManager(): ArduinoCliManager = cli
}
