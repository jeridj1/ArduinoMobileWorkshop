package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.R
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
            project = workspace.createProject("Sketch_" + System.currentTimeMillis())
            loadProject()
        }
        binding.compileButton.setOnClickListener { compileProject() }
        binding.uploadButton.setOnClickListener { uploadProject() }

        setupLineNumbers()
    }

    /**
     * Keeps the monospace line-number gutter in sync with the editor: rebuilt
     * on every text change and re-scrolled vertically whenever the editor
     * scrolls, so the gutter always tracks the visible lines.
     */
    private fun setupLineNumbers() {
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()
                syncGutterScroll()
            }
        })
        binding.editor.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.lineNumbers.scrollY = scrollY
        }
    }

    private fun updateLineNumbers() {
        val lineCount = binding.editor.layout?.lineCount ?: binding.editor.lineCount
        if (lineCount < 1) {
            binding.lineNumbers.text = "1"
            return
        }
        val sb = StringBuilder()
        for (i in 1..lineCount) {
            sb.append(i)
            sb.append('\n')
        }
        binding.lineNumbers.text = sb.toString()
    }

    private fun syncGutterScroll() {
        binding.lineNumbers.scrollY = binding.editor.scrollY
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_example -> { showExampleChooser(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showExampleChooser() {
        val examples = arrayOf("Blink", "SerialTest")
        AlertDialog.Builder(this)
            .setTitle("New from example")
            .setItems(examples) { _, which ->
                val name = examples[which]
                loadExample(name)
            }
            .show()
    }

    private fun loadExample(name: String) {
        try {
            val content = assets.open("examples/" + name + ".ino").bufferedReader().use { it.readText() }
            project = workspace.createProject(name + "_" + System.currentTimeMillis())
            val current = project ?: return
            File(current.mainFile).writeText(content)
            loadProject()
            binding.output.text = "Loaded example: " + name
            Toast.makeText(this, "Loaded " + name, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            binding.output.text = "Failed to load example " + name + ": " + (e.message ?: "")
            Toast.makeText(this, "Example not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProject() {
        val current = project ?: return
        binding.projectName.text = current.name
        binding.editor.setText(
            File(current.mainFile).takeIf { it.exists() }?.readText()
                ?: "void setup() {\n}\n\nvoid loop() {\n}\n"
        )
        binding.editor.requestFocus()
        binding.editor.post { updateLineNumbers(); syncGutterScroll() }
    }

    private fun saveProject() {
        val current = project ?: return
        File(current.mainFile).writeText(binding.editor.text.toString())
        binding.output.text = "Saved " + current.mainFile
    }

    private fun compileProject() {
        saveProject()
        val current = project ?: return
        val board = toolchain.getAvailableBoards().getOrNull(binding.boardSpinner.selectedItemPosition) ?: return
        binding.output.text = "Compiling " + current.name + " for " + board.name + "..."
        Thread {
            val result = toolchain.compileSketchDetailed(File(current.path), board.id)
            runOnUiThread {
                binding.output.text = result.output.ifBlank { if (result.success) "Build succeeded." else "Build failed (exit " + result.exitCode + ")." }
                Toast.makeText(this, if (result.success) "Compile succeeded" else "Compile failed", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun uploadProject() {
        saveProject()
        val current = project ?: return
        val board = toolchain.getAvailableBoards().getOrNull(binding.boardSpinner.selectedItemPosition) ?: return
        binding.output.text = "Uploading " + current.name + "..."
        Thread {
            val result = toolchain.getCliManager().run("upload", "--fqbn", board.id, current.path, timeoutSeconds = 300)
            runOnUiThread { binding.output.text = result.output.ifBlank { if (result.success) "Upload succeeded." else "Upload failed." } }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
