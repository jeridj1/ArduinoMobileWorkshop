package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivityEditorBinding
import com.arduinomobileworkshop.toolchain.ToolchainManager
import com.arduinomobileworkshop.workspace.WorkspaceManager
import java.io.File

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val workspace: WorkspaceManager get() = ArduinoMobileWorkshopApp.instance.workspaceManager
    private val toolchain: ToolchainManager get() = ArduinoMobileWorkshopApp.instance.toolchainManager
    private var project: WorkspaceManager.SketchProject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val boards = toolchain.getAvailableBoards()
        binding.boardSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, boards).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val requestedPath = intent.getStringExtra(FilePickerActivity.EXTRA_FILE_PATH)
        project = requestedPath?.let { path -> workspace.listProjects().firstOrNull { it.path == path } }
            ?: workspace.listProjects().firstOrNull()

        if (project == null) project = workspace.createProject("MySketch")
        loadProject()

        binding.saveButton.setOnClickListener { saveProject() }
        binding.newButton.setOnClickListener {
            project = workspace.createProject("Sketch_${System.currentTimeMillis()}")
            loadProject()
        }
        binding.compileButton.setOnClickListener { compileProject() }
        binding.uploadButton.setOnClickListener { uploadProject() }
    }

    private fun loadProject() {
        val current = project ?: return
        binding.projectName.text = current.name
        binding.editor.setText(File(current.mainFile).takeIf { it.exists() }?.readText() ?: "void setup() {\n}\n\nvoid loop() {\n}\n")
    }

    private fun saveProject() {
        val current = project ?: return
        File(current.mainFile).writeText(binding.editor.text.toString())
        binding.output.text = "Saved ${current.mainFile}"
    }

    private fun compileProject() {
        saveProject()
        val current = project ?: return
        val board = toolchain.getAvailableBoards().getOrNull(binding.boardSpinner.selectedItemPosition) ?: return
        binding.output.text = "Compiling ${current.name} for ${board.name}..."
        Thread {
            val result = toolchain.compileSketchDetailed(File(current.path), board.id)
            runOnUiThread {
                binding.output.text = result.output.ifBlank { if (result.success) "Build succeeded." else "Build failed (exit ${result.exitCode})." }
                Toast.makeText(this, if (result.success) "Compile succeeded" else "Compile failed", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun uploadProject() {
        saveProject()
        val current = project ?: return
        val board = toolchain.getAvailableBoards().getOrNull(binding.boardSpinner.selectedItemPosition) ?: return
        val devices = ArduinoMobileWorkshopApp.instance.usbManager.getSerialDevices()
        val port = devices.firstOrNull()?.let { ArduinoMobileWorkshopApp.instance.usbManager.androidUsbManager.getDeviceList() }
        binding.output.text = "Uploading ${current.name}..."
        Thread {
            val result = toolchain.getCliManager().run("upload", "--fqbn", board.id, current.path, timeoutSeconds = 300)
            runOnUiThread { binding.output.text = result.output.ifBlank { if (result.success) "Upload succeeded." else "Upload failed." } }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
