package com.arduinomobileworkshop.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.arduinomobileworkshop.app.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        findViewById<android.widget.Button>(R.id.btn_editor).setOnClickListener { openEditor() }
        findViewById<android.widget.Button>(R.id.btn_serial_monitor).setOnClickListener { startActivity(Intent(this, SerialMonitorActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_boards_manager).setOnClickListener { startActivity(Intent(this, BoardsManagerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_library_manager).setOnClickListener { startActivity(Intent(this, LibraryManagerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_file_picker).setOnClickListener { startActivity(Intent(this, FilePickerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_logic_analyzer).setOnClickListener { startActivity(Intent(this, LogicAnalyzerActivity::class.java)) }
        findViewById<android.widget.Button>(R.id.btn_multi_programmer).setOnClickListener { startActivity(Intent(this, MultiProgrammerActivity::class.java)) }
    }

    private fun openEditor() = startActivity(Intent(this, EditorActivity::class.java))

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_new, R.id.action_open, R.id.action_save, R.id.action_verify, R.id.action_upload -> { openEditor(); true }
        R.id.action_serial_monitor -> { startActivity(Intent(this, SerialMonitorActivity::class.java)); true }
        R.id.action_boards_manager -> { startActivity(Intent(this, BoardsManagerActivity::class.java)); true }
        R.id.action_library_manager -> { startActivity(Intent(this, LibraryManagerActivity::class.java)); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        R.id.action_logic_analyzer -> { startActivity(Intent(this, LogicAnalyzerActivity::class.java)); true }
        R.id.action_multi_programmer -> { startActivity(Intent(this, MultiProgrammerActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }
}
