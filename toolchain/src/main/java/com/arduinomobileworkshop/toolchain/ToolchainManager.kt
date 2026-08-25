package com.arduinomobileworkshop.toolchain

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Owns the arduino-cli lifecycle and the board / library indexes the UI binds to.
 *
 * Indexes are fetched in two ways, in priority order:
 *  1. Over the network via OkHttp (downloads.arduino.cc package_index.json /
 *     library_index.json), parsed with org.json. Real Arduino board packages
 *     are downloaded into the sandboxed application storage directory.
 *  2. Through the bundled arduino-cli binary (core/lib update-index + list
 *     --format json) when it is present (CI-built jniLib).
 *
 * When neither path yields data (no network / no CLI), parsing fails soft: the
 * built-in default board list is retained so the UI is never empty and the
 * build never depends on a working runtime CLI. Indexes are cached to disk so a
 * cold start can show the last-known state without re-downloading.
 */
class ToolchainManager(private val context: Context) {
    companion object {
        private const val TAG = "AMW_Toolchain"
        private const val PACKAGE_INDEX_URL = "https://downloads.arduino.cc/packages/package_index.json"
        private const val LIBRARY_INDEX_URL = "https://downloads.arduino.cc/libraries/library_index.json"
    }

    private val cli = ArduinoCliManager(context)
    private val toolchainDir = File(context.filesDir, "arduino-toolchain")
    private val cacheDir = File(toolchainDir, "cache")
    private val boardCacheFile = File(cacheDir, "boards.json")
    private val libraryCacheFile = File(cacheDir, "libraries.json")
    private val profilesDir = File(toolchainDir, "profiles")
    private val downloadsDir = File(toolchainDir, "downloads")

    /** Single shared HTTP client (OkHttp recommends one instance per process). */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

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
        toolchainDir.mkdirs(); cacheDir.mkdirs(); profilesDir.mkdirs(); downloadsDir.mkdirs()
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

    // ---------------- Network fetch engine ----------------

    /** Blocking HTTP GET; returns the response body or null on any failure. */
    private fun fetchText(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP " + response.code + " for " + url)
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchText failed for " + url + ": " + (e.message ?: ""))
            null
        }
    }

    /** Blocking download of [url] into [dest]; returns true on success. */
    private fun downloadFile(url: String, dest: File, callback: ((Int) -> Unit)? = null): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                FileOutputStream(dest).use { out ->
                    val input = body.byteStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                    out.flush()
                }
                callback?.invoke(100)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "downloadFile failed for " + url + ": " + (e.message ?: ""))
            false
        }
    }

    /**
     * Fetches the real Arduino package index over the network, parses it into
     * [availableBoards] and writes a per-platform board-profile JSON into the
     * sandboxed profiles dir. Returns true on success.
     */
    private fun refreshBoardIndexFromNetwork(): Boolean {
        val json = fetchText(PACKAGE_INDEX_URL) ?: return false
        try { File(cacheDir, "package_index.json").writeText(json) } catch (_: Exception) {}
        return parsePackageIndex(json)
    }

    private fun parsePackageIndex(json: String): Boolean {
        var added = 0
        try {
            val root = JSONObject(json)
            val packages = root.optJSONArray("packages") ?: return false
            synchronized(boardLock) {
                availableBoards.clear()
                for (p in 0 until packages.length()) {
                    val pkg = packages.optJSONObject(p) ?: continue
                    val pkgName = pkg.optString("name")
                    if (pkgName.isEmpty()) continue
                    val platforms = pkg.optJSONArray("platforms") ?: continue
                    for (pi in 0 until platforms.length()) {
                        val plat = platforms.optJSONObject(pi) ?: continue
                        val arch = plat.optString("architecture")
                        val version = plat.optString("version")
                        val platName = optStr(plat, "name") ?: arch
                        val url = plat.optString("url")
                        val fqbnPkg = pkgName + ":" + arch
                        savePlatformProfile(pkgName, arch, version, platName, url, plat.optJSONArray("boards"))
                        val boards = plat.optJSONArray("boards")
                        if (boards != null) {
                            for (b in 0 until boards.length()) {
                                val boardObj = boards.optJSONObject(b) ?: continue
                                val boardName = optStr(boardObj, "name") ?: continue
                                val boardId = (pkgName + ":" + arch + ":" + sanitize(boardName))
                                availableBoards += Board(
                                    id = boardId,
                                    name = boardName,
                                    platform = arch,
                                    packageName = fqbnPkg,
                                    version = version,
                                    architecture = arch,
                                    uploadTool = optStr(boardObj, "upload_tool") ?: "",
                                    programmer = optStr(boardObj, "programmer") ?: ""
                                )
                                added++
                            }
                        }
                    }
                }
                if (availableBoards.isNotEmpty()) saveBoardCache()
                if (availableBoards.isEmpty()) initializeDefaultBoards()
            }
        } catch (e: Exception) {
            Log.w(TAG, "parsePackageIndex failed: " + (e.message ?: ""))
            return false
        }
        Log.d(TAG, "Network board index parsed: " + added + " boards")
        return added > 0
    }

    private fun savePlatformProfile(pkg: String, arch: String, version: String, name: String, url: String, boards: JSONArray?) {
        try {
            val profile = JSONObject().apply {
                put("package", pkg)
                put("architecture", arch)
                put("version", version)
                put("name", name)
                put("url", url)
                val arr = JSONArray()
                if (boards != null) for (i in 0 until boards.length()) {
                    val b = boards.optJSONObject(i) ?: continue
                    arr.put(b.optString("name"))
                }
                put("boards", arr)
            }
            File(profilesDir, pkg + "_" + arch + "_" + version + ".json").writeText(profile.toString())
        } catch (e: Exception) {
            Log.w(TAG, "savePlatformProfile failed: " + (e.message ?: ""))
        }
    }

    /** Lists downloaded board profile JSONs in the sandboxed profiles dir. */
    fun listDownloadedProfiles(): List<File> = profilesDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList() ?: emptyList()

    /**
     * Downloads the target board package archive referenced by the platform
     * profile for [boardId] directly into the sandboxed downloads directory.
     * Returns the downloaded file, or null on failure.
     */
    fun downloadBoardProfile(boardId: String): File? {
        val board = getBoardConfig(boardId) ?: return null
        val profile = listDownloadedProfiles().firstOrNull { f ->
            f.name.startsWith(board.packageName.replace(":", "_") + "_")
        } ?: return null
        return try {
            val url = JSONObject(profile.readText()).optString("url")
            if (url.isEmpty()) return null
            val dest = File(downloadsDir, url.substringAfterLast("/"))
            if (downloadFile(url, dest)) dest else null
        } catch (e: Exception) {
            Log.w(TAG, "downloadBoardProfile failed: " + (e.message ?: ""))
            null
        }
    }

    private fun sanitize(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')

    // ---------------- Board index ----------------

    /** Downloads the package index, then enumerates every known board from it. */
    fun refreshBoardIndex(callback: ((Boolean, String) -> Unit)? = null) {
        Thread {
            val netOk = refreshBoardIndexFromNetwork()
            var msg = if (netOk) "Network index fetched" else "Network fetch failed"
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
            val ok = (netOk || (update.success && list.success && parsed.isNotEmpty()))
            callback?.invoke(ok, msg + System.lineSeparator() + update.output + System.lineSeparator() + list.output)
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
            val netJson = fetchText(LIBRARY_INDEX_URL)
            if (netJson != null) {
                try { File(cacheDir, "library_index.json").writeText(netJson) } catch (_: Exception) {}
                val parsed = parseLibraryIndexNetwork(netJson)
                synchronized(libraryLock) {
                    if (parsed.isNotEmpty()) {
                        installedLibraries.clear()
                        installedLibraries += parsed
                        saveLibraryCache()
                    }
                }
            }
            val update = cli.run("lib", "update-index", timeoutSeconds = 120)
            refreshInstalledLibraries()
            callback?.invoke(update.success || netJson != null, update.output)
        }.start()
    }

    private fun parseLibraryIndexNetwork(json: String): List<Library> {
        val out = mutableListOf<Library>()
        try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("libraries") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = optStr(o, "name") ?: continue
                out += Library(
                    id = name,
                    name = name,
                    version = optStr(o, "version") ?: "",
                    author = optStr(o, "author") ?: optStr(o, "maintainer") ?: "",
                    description = optStr(o, "sentence") ?: "",
                    packageName = name
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseLibraryIndexNetwork failed: " + (e.message ?: ""))
        }
        return out
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
            val parsed = parseLibrarySearch(result.output)
            if (parsed.isEmpty()) {
                val netJson = fetchText(LIBRARY_INDEX_URL)
                if (netJson != null) callback(parseLibraryIndexNetwork(netJson)) else callback(emptyList())
            } else {
                callback(parsed)
            }
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
            val netOk = refreshBoardIndexFromNetwork()
            val core = cli.run("core", "update-index", timeoutSeconds = 300)
            val lib = cli.run("lib", "update-index", timeoutSeconds = 300)
            refreshBoardIndex(null)
            refreshInstalledLibraries()
            callback(core.success && lib.success || netOk, core.output + System.lineSeparator() + lib.output)
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
