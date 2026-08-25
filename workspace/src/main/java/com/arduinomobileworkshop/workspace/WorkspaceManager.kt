package com.arduinomobileworkshop.workspace

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Manages Arduino sketch storage using Android scoped storage conventions.
 *
 * The primary sketchbook lives in the application's local sandbox
 * (getExternalFilesDir/ArduinoSketchbook), which is writable without any
 * runtime permissions. A best-effort public Documents folder is also exposed
 * for import/export so users can share sketches outside the sandbox.
 *
 * Projects are folders named after the sketch; the main file is <name>.ino.
 * Additional .ino tabs and .h/.cpp/.c files live alongside it. File-level
 * read / create / overwrite / delete helpers operate on those text files and
 * a small parser ([SketchParser]) feeds raw strings into the IDE text buffers.
 */
class WorkspaceManager(private val context: Context) {

    companion object {
        private const val TAG = "AMW_Workspace"
        const val WORKSPACE_DIR = "ArduinoSketchbook"
        const val PUBLIC_DOCS_DIR = "ArduinoMobileWorkshop"
        val SOURCE_EXTENSIONS = setOf("ino", "h", "hpp", "cpp", "c")

        private val DEFAULT_SKETCH_TEMPLATE = """void setup() {
  // put your setup code here, to run once:
}

void loop() {
  // put your main code here, to run repeatedly:
}
"""

        private val DEFAULT_HEADER_TEMPLATE = """#ifndef %s
#define %s


#endif // %s
"""
    }

    data class SketchProject(
        val id: String,
        val name: String,
        val path: String,
        val createdAt: Long,
        val modifiedAt: Long,
        val mainFile: String
    )

    private val workspaceDir: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, WORKSPACE_DIR)
    private val publicDocsDir: File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), PUBLIC_DOCS_DIR)

    private var isInitialized = false
    private val projects: MutableList<SketchProject> = mutableListOf()

    fun initialize() {
        if (isInitialized) return
        try {
            if (!workspaceDir.exists()) workspaceDir.mkdirs()
            loadProjects()
            isInitialized = true
            Log.d(TAG, "Workspace initialized at ${workspaceDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize workspace", e)
            isInitialized = false
        }
    }

    private fun loadProjects() {
        projects.clear()
        if (!workspaceDir.exists()) return
        workspaceDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                loadProjectAt(dir)?.let { projects.add(it) }
            }
        }
    }

    private fun loadProjectAt(dir: File): SketchProject? {
        val inoFile = File(dir, dir.name + ".ino")
        if (!inoFile.exists()) return null
        return SketchProject(
            id = "${dir.name}_${dir.path.hashCode()}",
            name = dir.name,
            path = dir.absolutePath,
            createdAt = dir.lastModified(),
            modifiedAt = inoFile.lastModified(),
            mainFile = inoFile.absolutePath
        )
    }

    fun getWorkspaceDir(): File = workspaceDir
    fun getPublicDocumentsDir(): File = publicDocsDir

    fun createProject(name: String): SketchProject? {
        if (!isInitialized) return null
        return try {
            val sanitized = name.replace("[^a-zA-Z0-9_]+".toRegex(), "_").trim('_').ifEmpty { "Sketch" }
            val projectDir = File(workspaceDir, sanitized)
            if (projectDir.exists()) {
                return projects.find { it.name == sanitized } ?: loadProjectAt(projectDir)
            }
            projectDir.mkdirs()
            val inoFile = File(projectDir, "$sanitized.ino")
            inoFile.writeText(DEFAULT_SKETCH_TEMPLATE)
            val project = SketchProject(
                id = "${sanitized}_${projectDir.path.hashCode()}",
                name = sanitized,
                path = projectDir.absolutePath,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                mainFile = inoFile.absolutePath
            )
            projects.add(project)
            project
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create project", e)
            null
        }
    }

    fun createSketch(name: String): File? = createProject(name)?.let { File(it.path) }

    fun listProjects(): List<SketchProject> {
        if (!isInitialized) return emptyList()
        loadProjects()
        return projects.toList()
    }

    fun listSketches(): List<File> = listProjects().map { File(it.path) }

    fun getProject(projectId: String): SketchProject? = projects.find { it.id == projectId }
    fun getProjectByName(name: String): SketchProject? = projects.find { it.name == name }

    fun deleteProject(projectId: String): Boolean {
        if (!isInitialized) return false
        val project = projects.find { it.id == projectId } ?: return false
        return try {
            File(project.path).deleteRecursively()
            projects.remove(project)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete project", e)
            false
        }
    }

    fun deleteSketch(name: String): Boolean =
        projects.find { it.name == name }?.let { deleteProject(it.id) } ?: false

    // ---- File-level .ino / .h text operations ----

    /** All source files in the project, main .ino first, then the rest by name. */
    fun listProjectFiles(project: SketchProject): List<File> {
        val dir = File(project.path)
        if (!dir.isDirectory) return emptyList()
        val mainName = File(project.mainFile).name
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in SOURCE_EXTENSIONS }
            ?: return emptyList()
        return files.sortedWith(compareBy({ it.name != mainName }, { it.name }))
    }

    fun readFile(project: SketchProject, fileName: String): String? =
        readFile(File(project.path, fileName))

    fun readFile(file: File): String? = try {
        if (!file.exists()) null else file.readText()
    } catch (e: IOException) {
        Log.e(TAG, "readFile failed: ${file.name}", e); null
    }

    /** Raw text of the main .ino buffer (for the editor). */
    fun loadMainFile(project: SketchProject): String? = readFile(File(project.mainFile))

    /** Creates a new source/header file. Returns null if it already exists. */
    fun createFile(project: SketchProject, fileName: String, content: String = ""): File? {
        val dir = File(project.path)
        if (!dir.isDirectory) return null
        val file = File(dir, fileName)
        if (file.exists()) return null
        return try {
            file.writeText(content)
            file
        } catch (e: IOException) {
            Log.e(TAG, "createFile failed", e); null
        }
    }

    /** Creates a header (.h) file; extension is added if missing. */
    fun createHeaderFile(project: SketchProject, headerName: String, content: String = ""): File? {
        val name = if (headerName.endsWith(".h", ignoreCase = true)) headerName else "$headerName.h"
        val guard = headerName.replace("[^A-Za-z0-9_]".toRegex(), "_").uppercase() + "_H"
        val body = if (content.isBlank()) DEFAULT_HEADER_TEMPLATE.format(guard, guard, guard) else content
        return createFile(project, name, body)
    }

    /** Overwrites (or creates) a file in the project. */
    fun writeFile(project: SketchProject, fileName: String, content: String): Boolean =
        writeFile(File(project.path, fileName), content)

    fun writeFile(file: File, content: String): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeText(content)
        true
    } catch (e: IOException) {
        Log.e(TAG, "writeFile failed: ${file.name}", e); false
    }

    fun overwriteMainFile(project: SketchProject, content: String): Boolean =
        writeFile(File(project.mainFile), content)

    fun deleteFile(project: SketchProject, fileName: String): Boolean =
        try { File(project.path, fileName).delete() } catch (e: Exception) { false }

    /** Imports an external file into the project (overwrites if a same-named file exists). */
    fun importFile(project: SketchProject, source: File, newName: String? = null): File? {
        if (!source.isFile) return null
        return try {
            val target = File(project.path, newName ?: source.name)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            target
        } catch (e: IOException) {
            Log.e(TAG, "importFile failed", e); null
        }
    }

    /**
     * Best-effort copy of a project file into the public Documents folder.
     * Returns the exported path, or null if scoped storage blocks the write
     * (a SAF grant would be needed on API 30+ for arbitrary public folders).
     */
    fun exportToDocuments(project: SketchProject, fileName: String): String? {
        val src = File(project.path, fileName)
        if (!src.isFile) return null
        return try {
            if (!publicDocsDir.exists()) publicDocsDir.mkdirs()
            val dst = File(publicDocsDir, fileName)
            src.copyTo(dst, overwrite = true)
            dst.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "exportToDocuments failed (scoped storage?): " + (e.message ?: ""))
            null
        }
    }

    // ---- Text parser feeding raw strings into IDE buffers ----

    fun parseSource(content: String): SketchSource = SketchParser.parse(content)

    fun parseProject(project: SketchProject): SketchSource? =
        loadMainFile(project)?.let { SketchParser.parse(it) }

    fun isInitialized(): Boolean = isInitialized
}
