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
            if (!workspaceDir?.exists()!!) {
                workspaceDir?.mkdirs()
            }
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
        if (workspaceDir == null || !workspaceDir?.exists()!!) return
        workspaceDir?.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val inoFile = File(file, file.name + ".ino")
                if (inoFile.exists()) {
                    val project = SketchProject(
                        id = file.name + "_" + file.path.hashCode(),
                        name = file.name,
                        path = file.absolutePath,
                        createdAt = file.lastModified(),
                        modifiedAt = inoFile.lastModified(),
                        mainFile = inoFile.absolutePath
                    )
                    projects.add(project)
                }
            }
        }
    }
    
    fun getWorkspaceDir(): File? = workspaceDir
    
    fun createProject(name: String): SketchProject? {
        if (!isInitialized) return null
        return try {
            val sanitizedName = name.replace("[^a-zA-Z0-9_]+".toRegex(), "_")
            val projectDir = File(workspaceDir, sanitizedName)
            if (projectDir.exists()) {
                return projects.find { it.name == sanitizedName }
            }
            projectDir.mkdirs()
            val inoFile = File(projectDir, sanitizedName + ".ino")
            inoFile.writeText("void setup(){}
void loop(){}")
            val project = SketchProject(
                id = sanitizedName + "_" + projectDir.path.hashCode(),
                name = sanitizedName,
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
        return try {
            val project = projects.find { it.id == projectId }
            project?.let {
                File(it.path).deleteRecursively()
                projects.remove(project)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    fun deleteSketch(name: String): Boolean = projects.find { it.name == name }?.let { deleteProject(it.id) } ?: false
    fun isInitialized(): Boolean = isInitialized
}