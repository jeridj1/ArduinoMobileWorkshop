package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Owns the arduino-cli lifecycle and the board / library indexes the UI binds to.
 *
 * The arduino-cli binary is only present when the APK was built via CI (bundled
 * as a jniLib). When it is absent (local debug builds, or before the first run),
 * every index operation fails soft: parsing yields nothing and the built-in
 * default board list is retained so the UI is never empty and the build never
 * depends on a working runtime CLI. Indexes are cached to disk so a cold start
 * can show the last-known state without re-downloading.
 */
class ToolchainManager(private val context: Context) {
    companion object { private const val TAG = "AMW_Toolchain" }

    private val cli = ArduinoCliManager(context)
    private val toolchainDir = File(context.filesDir, "arduino-toolchain")
    private val cacheDir = File(toolchainDir, "cache")
    private val boardCacheFile = File(cacheDir, "boards.json")
    private val libraryCacheFile = File(cacheDir, "libraries.json")

    @Volatile private var isInitialized = false
    private val boardLock = Any()
    private val libraryLock = Any()
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
        toolchainDir.mkdirs(); cacheDir.mkdirs()
        synchronized(boardLock) { if (!loadBoardCache()) initializeDefaultBoards() }
        synchronized(libraryLock) { loadLibraryCache() }
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

    // ---------------- Board index ----------------

    /** Downloads the package index, then enumerates every known board from it. */
    fun refreshBoardIndex(callback: ((Boolean, String) -> Unit)? = null) {
        Thread {
            val update = cli.run("core", "update-index", timeoutSeconds = 120)
            val list = cli.run("board", "listall", "--format", "json", timeoutSeconds = 120)
            val parsed = parseBoardListall(list.output)
            synchronized(boardLock) {
                if (parsed.isNotEmpty()) {
                    availableBoards.clear()
                    availableBoards += parsed
                    saveBoardCache()
                }
                if (availableBoards.isEmpty()) initializeDefaultBoards()
            }
            val ok = update.success && list.success && parsed.isNotEmpty()
            callback?.invoke(ok, update.output + System.lineSeparator() + list.output)
        }.start()
    }

    private fun parseBoardListall(json: String): List<Board> {
        val out = mutableListOf<Board>()
        try {
            val arr = JSONArray(json.trim())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val fqbn = optStr(o, "fqbn", "FQBN", "id") ?: continue
                val name = optStr(o, "name", "Name", "board_name") ?: fqbn
                val parts = fqbn.split(":")
                val pkg = if (parts.size >= 2) parts[0] + ":" + parts[1] else fqbn
                val arch = if (parts.size >= 2) parts[1] else ""
                out += Board(
                    id = fqbn,
                    name = name,
                    platform = optStr(o, "platform", "Platform") ?: arch,
                    packageName = pkg,
                    version = optStr(o, "version", "Version") ?: "",
                    architecture = arch,
                    uploadTool = optStr(o, "upload_tool", "uploadTool") ?: "",
                    programmer = optStr(o, "programmer", "Programmer") ?: ""
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseBoardListall failed: " + (e.message ?: ""))
        }
        return out
    }

    private fun loadBoardCache(): Boolean {
        try {
            if (!boardCacheFile.exists()) return false
            val arr = JSONArray(boardCacheFile.readText())
            if (arr.length() == 0) return false
            availableBoards.clear()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                availableBoards += Board(
                    o.optString("id"), o.optString("name"), o.optString("platform"),
                    o.optString("packageName"), o.optString("version"),
                    o.optString("architecture"), o.optString("uploadTool"),
                    o.optString("programmer")
                )
            }
            return availableBoards.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "loadBoardCache failed: " + (e.message ?: ""))
            return false
        }
    }

    private fun saveBoardCache() {
        try {
            val arr = JSONArray()
            for (b in availableBoards) {
                arr.put(JSONObject().apply {
                    put("id", b.id); put("name", b.name); put("platform", b.platform)
                    put("packageName", b.packageName); put("version", b.version)
                    put("architecture", b.architecture); put("uploadTool", b.uploadTool)
                    put("programmer", b.programmer)
                })
            }
            boardCacheFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "saveBoardCache failed: " + (e.message ?: ""))
        }
    }

    // ---------------- Library index ----------------

    private fun refreshInstalledLibraries() {
        val result = cli.run("lib", "list", "--format", "json", timeoutSeconds = 60)
        val parsed = parseLibraryList(result.output)
        synchronized(libraryLock) {
            installedLibraries.clear()
            if (parsed.isNotEmpty()) {
                installedLibraries += parsed
                saveLibraryCache()
            }
        }
    }

    fun refreshLibraryIndex(callback: ((Boolean, String) -> Unit)? = null) {
        Thread {
            val update = cli.run("lib", "update-index", timeoutSeconds = 120)
            refreshInstalledLibraries()
            callback?.invoke(update.success, update.output)
        }.start()
    }

    private fun parseLibraryList(json: String): List<Library> {
        val out = mutableListOf<Library>()
        try {
            val arr = JSONArray(json.trim())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val lib = o.optJSONObject("library") ?: o
                val name = optStr(o, "real_name", "name") ?: optStr(lib, "name") ?: continue
                val sentence = optStr(lib, "sentence") ?: ""
                val paragraph = optStr(lib, "paragraph") ?: ""
                out += Library(
                    id = name,
                    name = name,
                    version = optStr(lib, "version") ?: optStr(o, "version") ?: "",
                    author = optStr(lib, "author") ?: optStr(lib, "maintainer") ?: "",
                    description = if (paragraph.isNotEmpty()) sentence + " " + paragraph else sentence,
                    packageName = name
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseLibraryList failed: " + (e.message ?: ""))
        }
        return out
    }

    private fun loadLibraryCache(): Boolean {
        try {
            if (!libraryCacheFile.exists()) return false
            val arr = JSONArray(libraryCacheFile.readText())
            installedLibraries.clear()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                installedLibraries += Library(
                    o.optString("id"), o.optString("name"), o.optString("version"),
                    o.optString("author"), o.optString("description"), o.optString("packageName")
                )
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "loadLibraryCache failed: " + (e.message ?: ""))
            return false
        }
    }

    private fun saveLibraryCache() {
        try {
            val arr = JSONArray()
            for (l in installedLibraries) {
                arr.put(JSONObject().apply {
                    put("id", l.id); put("name", l.name); put("version", l.version)
                    put("author", l.author); put("description", l.description); put("packageName", l.packageName)
                })
            }
            libraryCacheFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "saveLibraryCache failed: " + (e.message ?: ""))
        }
    }

    /** Searches the remote library index for [query]; calls back off the UI thread. */
    fun searchLibraries(query: String, callback: (List<Library>) -> Unit) {
        Thread {
            val q = if (query.isBlank()) "a" else query
            val result = cli.run("lib", "search", q, "--format", "json", timeoutSeconds = 120)
            callback(parseLibrarySearch(result.output))
        }.start()
    }

    private fun parseLibrarySearch(json: String): List<Library> {
        val out = mutableListOf<Library>()
        try {
            val arr = JSONArray(json.trim())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = optStr(o, "name") ?: continue
                val release = latestRelease(o.optJSONObject("releases"))
                val sentence = release?.let { optStr(it, "sentence") } ?: ""
                val paragraph = release?.let { optStr(it, "paragraph") } ?: ""
                out += Library(
                    id = name,
                    name = optStr(o, "real_name") ?: name,
                    version = release?.let { optStr(it, "version") } ?: "",
                    author = release?.let { optStr(it, "author") } ?: "",
                    description = if (paragraph.isNotEmpty()) sentence + " " + paragraph else sentence,
                    packageName = name
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseLibrarySearch failed: " + (e.message ?: ""))
        }
        return out
    }

    private fun latestRelease(releases: JSONObject?): JSONObject? {
        if (releases == null) return null
        var best: JSONObject? = null
        val keys = releases.keys()
        while (keys.hasNext()) {
            val r = releases.optJSONObject(keys.next()) ?: continue
            if (best == null || (optStr(r, "version") ?: "") > (optStr(best, "version") ?: "")) best = r
        }
        return best
    }

    private fun optStr(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = o.optString(k, "")
            if (v.isNotEmpty()) return v
        }
        return null
    }

    // ---------------- Public API (preserved surface) ----------------

    fun getAvailableBoards(): List<Board> = synchronized(boardLock) { availableBoards.toList() }
    fun getInstalledBoards(): List<Board> = getAvailableBoards()
    fun getBoardConfig(boardId: String): Board? = synchronized(boardLock) { availableBoards.find { it.id == boardId } }
    fun getBoardConfigByName(boardName: String): Board? = synchronized(boardLock) { availableBoards.find { it.name == boardName } }
    fun addBoardConfig(config: Board): Boolean = synchronized(boardLock) {
        if (availableBoards.any { it.id == config.id }) false
        else { availableBoards += config; saveBoardCache(); true }
    }
    fun removeBoardConfig(boardId: String): Boolean = synchronized(boardLock) {
        val removed = availableBoards.removeIf { it.id == boardId }
        if (removed) saveBoardCache()
        removed
    }
    fun getInstalledLibraries(): List<Library> = synchronized(libraryLock) { installedLibraries.toList() }
    fun getLibrary(libraryId: String): Library? = synchronized(libraryLock) { installedLibraries.find { it.id == libraryId } }

    fun installLibrary(libraryName: String, callback: (Boolean, String) -> Unit) {
        Thread {
            val result = cli.run("lib", "install", libraryName, timeoutSeconds = 300)
            if (result.success) refreshInstalledLibraries()
            callback(result.success, result.output)
        }.start()
    }

    fun installBoardPackage(packageName: String, callback: (Boolean, String) -> Unit) {
        Thread {
            val result = cli.run("core", "install", packageName, timeoutSeconds = 600)
            if (result.success) refreshBoardIndex(null)
            callback(result.success, result.output)
        }.start()
    }

    fun updateIndexes(callback: (Boolean, String) -> Unit) {
        Thread {
            val core = cli.run("core", "update-index", timeoutSeconds = 300)
            val lib = cli.run("lib", "update-index", timeoutSeconds = 300)
            refreshBoardIndex(null)
            refreshInstalledLibraries()
            callback(core.success && lib.success, core.output + System.lineSeparator() + lib.output)
        }.start()
    }

    fun hasCompilerForPlatform(platform: String): Boolean = cli.ensureInstalled() &&
        platform.lowercase() in setOf("rp2040", "avr", "esp32", "esp8266")

    fun compileSketch(sketchDir: File, boardId: String): Boolean = compileSketchDetailed(sketchDir, boardId).success

    fun compileSketchDetailed(sketchDir: File, boardId: String): ArduinoCliManager.Result {
        if (!isInitialized) return ArduinoCliManager.Result(-1, "Toolchain is not initialized")
        val board = getBoardConfig(boardId) ?: return ArduinoCliManager.Result(-1, "Unknown board: " + boardId)
        return cli.run("compile", "--fqbn", board.id, sketchDir.absolutePath, timeoutSeconds = 600)
    }

    fun uploadToDevice(hexFile: File, boardId: String, serialPort: String? = null): Boolean {
        val board = getBoardConfig(boardId) ?: return false
        val args = mutableListOf("upload", "--fqbn", board.id)
        if (!serialPort.isNullOrBlank()) args += listOf("--port", serialPort)
        val sketchPath = hexFile.parentFile?.absolutePath ?: return false
        args += sketchPath
        return cli.run(*args.toTypedArray(), timeoutSeconds = 300).success
    }

    fun getToolchainDir(): File = toolchainDir
    fun isInitialized(): Boolean = isInitialized
    fun getCliManager(): ArduinoCliManager = cli
}
