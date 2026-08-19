package com.arduinomobileworkshop.workspace

import android.content.Context
import android.util.Log
import java.io.File

class WorkspaceManager(private val context: Context) {
    companion object {
        private const val TAG = "AMW_Workspace"
        private const val WORKSPACE_DIR = "ArduinoSketchbook"
    }

    private var workspaceDir: File? = null
    private var isInitialized = false
    private val projects: MutableList<SketchProject> = mutableListOf()

    data class SketchProject(
        val id: String,
        val name: String,
        val path: String,
        val createdAt: Long,
        val modifiedAt: Long,
        val mainFile: String
    )

    fun initialize() {
        if (isInitialized) return
        try {
            workspaceDir = File(context.getExternalFilesDir(null), WORKSPACE_DIR)
            if (!workspaceDir!!.exists()) workspaceDir!!.mkdirs()
            loadProjects()
            isInitialized = true
            Log.d(TAG, "Workspace initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize workspace", e)
            isInitialized = false
        }
    }

    private fun loadProjects() {
        projects.clear()
        val dir = workspaceDir ?: return
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val inoFile = File(file, file.name + ".ino")
                if (inoFile.exists()) {
                    projects.add(SketchProject(file.name + "_" + file.path.hashCode(), file.name, file.absolutePath, file.lastModified(), inoFile.lastModified(), inoFile.absolutePath))
                }
            }
        }
    }

    fun getWorkspaceDir(): File? = workspaceDir

    fun createProject(name: String): SketchProject? {
        if (!isInitialized) return null
        return try {
            val sanitizedName = name.replace("[^a-zA-Z0-9_]+".toRegex(), "_")
            val root = workspaceDir ?: return null
            val projectDir = File(root, sanitizedName)
            if (projectDir.exists()) return projects.find { it.name == sanitizedName }
            if (!projectDir.mkdirs() && !projectDir.exists()) return null
            val inoFile = File(projectDir, "$sanitizedName.ino")
            inoFile.writeText("void setup(){}\nvoid loop(){}")
            val now = System.currentTimeMillis()
            val project = SketchProject(sanitizedName + "_" + projectDir.path.hashCode(), sanitizedName, projectDir.absolutePath, now, now, inoFile.absolutePath)
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
        return try {
            val project = projects.find { it.id == projectId } ?: return false
            val deleted = File(project.path).deleteRecursively()
            if (deleted) projects.remove(project)
            deleted
        } catch (e: Exception) {
            false
        }
    }

    fun deleteSketch(name: String): Boolean = projects.find { it.name == name }?.let { deleteProject(it.id) } ?: false
    fun isInitialized(): Boolean = isInitialized
}
