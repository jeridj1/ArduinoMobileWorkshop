package com.arduinomobileworkshop.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.R
import java.io.File

class MainActivity : AppCompatActivity() {
    private var currentSketchDir: File? = null
    private val workspaceManager get() = ArduinoMobileWorkshopApp.instance.workspaceManager
    private val toolchainManager get() = ArduinoMobileWorkshopApp.instance.toolchainManager
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(FilePickerActivity.EXTRA_FILE_PATH)?.let { currentSketchDir = File(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        findViewById<android.widget.Button>(R.id.btn_serial_monitor)?.setOnClickListener { startActivity(Intent(this, SerialMonitorActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_boards_manager)?.setOnClickListener { startActivity(Intent(this, BoardsManagerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_library_manager)?.setOnClickListener { startActivity(Intent(this, LibraryManagerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_settings)?.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_file_picker)?.setOnClickListener { openSketchPicker() }
        findViewById<android.widget.Button>(R.id.btn_logic_analyzer)?.setOnClickListener { startActivity(Intent(this, LogicAnalyzerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_multi_programmer)?.setOnClickListener { startActivity(Intent(this, MultiProgrammerActivity::class.java)) }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_new -> { currentSketchDir = workspaceManager.createProject("Sketch_${System.currentTimeMillis()}")?.let { File(it.path) }; true }
        R.id.action_open -> { openSketchPicker(); true }
        R.id.action_save -> { true }
        R.id.action_verify -> { compileCurrentSketch(); true }
        R.id.action_upload -> { uploadCurrentSketch(); true }
        R.id.action_serial_monitor -> { startActivity(Intent(this, SerialMonitorActivity::class.java)); true }
        R.id.action_boards_manager -> { startActivity(Intent(this, BoardsManagerActivity::class.java)); true }
        R.id.action_library_manager -> { startActivity(Intent(this, LibraryManagerActivity::class.java)); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        R.id.action_logic_analyzer -> { startActivity(Intent(this, LogicAnalyzerActivity::class.java)); true }
        R.id.action_multi_programmer -> { startActivity(Intent(this, MultiProgrammerActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openSketchPicker() {
        filePicker.launch(Intent(this, FilePickerActivity::class.java).putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_OPEN))
    }

    private fun compileCurrentSketch() {
        val sketch = currentSketchDir
        if (sketch == null) { openSketchPicker(); return }
        Thread {
            val success = toolchainManager.compileSketch(sketch, "arduino_uno")
            runOnUiThread { showToast(if (success) "Compile succeeded" else "Compile failed") }
        }.start()
    }

    private fun uploadCurrentSketch() {
        val sketch = currentSketchDir
        if (sketch == null) { openSketchPicker(); return }
        val hex = File(sketch, ".build").listFiles()?.firstOrNull { it.extension.equals("hex", true) }
        if (hex == null) { showToast("Compile the sketch first"); return }
        Thread {
            val success = toolchainManager.uploadToDevice(hex, "arduino_uno")
            runOnUiThread { showToast(if (success) "Upload succeeded" else "Upload failed") }
        }.start()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
