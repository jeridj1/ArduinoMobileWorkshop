package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivityFilePickerBinding
import com.arduinomobileworkshop.workspace.SketchProject
import com.arduinomobileworkshop.workspace.WorkspaceManager

class FilePickerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_FILE_PATH = "file_path"
        const val MODE_OPEN = 0
        const val MODE_SAVE = 1
    }
    
    private lateinit var binding: ActivityFilePickerBinding
    private val workspaceManager: WorkspaceManager
        get() = ArduinoMobileWorkshopApp.instance.workspaceManager
    private lateinit var projectAdapter: ArrayAdapter<SketchProject>
    private var mode = MODE_OPEN
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_OPEN)
        binding = ActivityFilePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initializeUi()
        loadProjects()
    }
    
    private fun initializeUi() {
        projectAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        binding.fileListView.adapter = projectAdapter
        binding.fileListView.setOnItemClickListener { _, _, position, _ ->
            projectAdapter.getItem(position)?.let { onProjectSelected(it) }
        }
        if (mode == MODE_OPEN) supportActionBar?.title = "Open Sketch"
        else supportActionBar?.title = "Select Location"
        binding.newSketchButton.setOnClickListener {
            val name = "Sketch_${System.currentTimeMillis()}"
            workspaceManager.createProject(name)?.let { onProjectSelected(it) }
        }
    }
    
    private fun loadProjects() {
        val projects = workspaceManager.listProjects()
        runOnUiThread {
            projectAdapter.clear()
            projectAdapter.addAll(projects)
            projectAdapter.notifyDataSetChanged()
            binding.emptyMessage.visibility = if (projects.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    
    private fun onProjectSelected(project: SketchProject) {
        val intent = android.content.Intent()
        intent.putExtra(EXTRA_FILE_PATH, project.path)
        setResult(RESULT_OK, intent)
        finish()
    }
    
    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    override fun onResume() { super.onResume(); loadProjects() }
}
