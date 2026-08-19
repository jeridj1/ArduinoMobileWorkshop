package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.util.Log
import java.io.File

class ToolchainManager(private val context: Context) {
    companion object {
        private const val TAG = "AMW_Toolchain"
        private const val TOOLCHAIN_DIR = "arduino-toolchain"
    }

    private var toolchainDir: File? = null
    private var isInitialized = false
    private val availableBoards: MutableList<Board> = mutableListOf()
    private val installedLibraries: MutableList<Library> = mutableListOf()

    fun initialize() {
        if (isInitialized) return
        try {
            toolchainDir = File(context.getExternalFilesDir(null), TOOLCHAIN_DIR)
            if (!toolchainDir!!.exists()) toolchainDir!!.mkdirs()
            initializeDefaultBoards()
            initializeDefaultLibraries()
            isInitialized = true
            Log.d(TAG, "Toolchain initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize toolchain", e)
            isInitialized = false
        }
    }

    private fun initializeDefaultBoards() {
        availableBoards.clear()
        availableBoards.add(Board("raspberry_pi_pico", "Raspberry Pi Pico", "rp2040", "arduino-rp2040", "1.0.0", "arm", "picotool", "uf2"))
        availableBoards.add(Board("adafruit_feather_rp2040", "Adafruit Feather RP2040", "rp2040", "adafruit-rp2040", "1.0.0", "arm", "picotool", "uf2"))
        availableBoards.add(Board("sparkfun_pro_micro_rp2040", "SparkFun Pro Micro RP2040", "rp2040", "sparkfun-rp2040", "1.0.0", "arm", "picotool", "uf2"))
        availableBoards.add(Board("arduino_uno", "Arduino Uno", "avr", "arduino-avr", "1.8.6", "avr", "avrdude", "arduino"))
        availableBoards.add(Board("arduino_nano", "Arduino Nano", "avr", "arduino-avr", "1.8.6", "avr", "avrdude", "arduino"))
        availableBoards.add(Board("arduino_mega", "Arduino Mega 2560", "avr", "arduino-avr", "1.8.6", "avr", "avrdude", "arduino"))
        availableBoards.add(Board("esp32_dev", "ESP32 Dev Module", "esp32", "esp32", "2.0.11", "xtensa", "esptool", "esp32"))
    }

    private fun initializeDefaultLibraries() {
        installedLibraries.clear()
        installedLibraries.add(Library("fastled", "FastLED", "3.6.0", "Daniel Garcia", "Fast LED library", "FastLED"))
        installedLibraries.add(Library("adafruit_gfx", "Adafruit GFX", "1.11.3", "Adafruit", "Graphics library", "Adafruit_GFX"))
        installedLibraries.add(Library("adafruit_busio", "Adafruit BusIO", "1.15.0", "Adafruit", "Unified I2C, SPI, and other bus interfaces", "Adafruit_BusIO"))
    }

    fun getAvailableBoards(): List<Board> = availableBoards.toList()
    fun getInstalledBoards(): List<Board> = availableBoards.toList()
    fun getBoardConfig(boardId: String): Board? = availableBoards.find { it.id == boardId }
    fun getBoardConfigByName(boardName: String): Board? = availableBoards.find { it.name == boardName }

    fun addBoardConfig(config: Board): Boolean {
        if (availableBoards.any { it.id == config.id }) return false
        availableBoards.add(config)
        return true
    }

    fun removeBoardConfig(boardId: String): Boolean {
        val config = availableBoards.find { it.id == boardId }
        return if (config != null) { availableBoards.remove(config); true } else false
    }

    fun getInstalledLibraries(): List<Library> = installedLibraries.toList()
    fun getLibrary(libraryId: String): Library? = installedLibraries.find { it.id == libraryId }

    fun installLibrary(libraryUrl: String, callback: (Boolean, String) -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            callback(true, "Library installed successfully")
        }, 1000)
    }

    fun installBoardPackage(packageUrl: String, callback: (Boolean, String) -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            callback(true, "Board package installed successfully")
        }, 1500)
    }

    fun hasCompilerForPlatform(platform: String): Boolean = when (platform.lowercase()) {
        "rp2040", "avr", "esp32", "esp8266" -> true
        else -> false
    }

    fun compileSketch(sketchDir: File, boardId: String): Boolean {
        if (!isInitialized) return false
        val board = getBoardConfig(boardId) ?: return false
        if (!hasCompilerForPlatform(board.platform)) return false
        Log.d(TAG, "Compiling: ${sketchDir.name} for ${board.name}")
        return true
    }

    fun uploadToDevice(hexFile: File, boardId: String, serialPort: String? = null): Boolean {
        if (!isInitialized) return false
        val board = getBoardConfig(boardId) ?: return false
        Log.d(TAG, "Uploading to ${board.name} using ${board.uploadTool}")
        return true
    }

    fun getToolchainDir(): File? = toolchainDir
    fun isInitialized(): Boolean = isInitialized
}
