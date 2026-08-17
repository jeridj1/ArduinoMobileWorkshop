package com.arduinomobileworkshop.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.arduinomobileworkshop.app.R

/**
 * Main Activity for Arduino Mobile Workshop
 * Provides navigation to all major features
 */
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Set up button click listeners for quick access
        findViewById<android.widget.Button>(R.id.btn_serial_monitor)?.setOnClickListener {
            startActivity(Intent(this, SerialMonitorActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_boards_manager)?.setOnClickListener {
            startActivity(Intent(this, BoardsManagerActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_library_manager)?.setOnClickListener {
            startActivity(Intent(this, LibraryManagerActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_file_picker)?.setOnClickListener {
            startActivity(Intent(this, FilePickerActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_logic_analyzer)?.setOnClickListener {
            startActivity(Intent(this, LogicAnalyzerActivity::class.java))
        }
        
        findViewById<android.widget.Button>(R.id.btn_multi_programmer)?.setOnClickListener {
            startActivity(Intent(this, MultiProgrammerActivity::class.java))
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new -> {
                Toast.makeText(this, "New sketch", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_open -> {
                Toast.makeText(this, "Open sketch", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_save -> {
                Toast.makeText(this, "Save sketch", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_verify -> {
                Toast.makeText(this, "Verify/Compile", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_upload -> {
                Toast.makeText(this, "Upload to device", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_serial_monitor -> {
                startActivity(Intent(this, SerialMonitorActivity::class.java))
                true
            }
            R.id.action_boards_manager -> {
                startActivity(Intent(this, BoardsManagerActivity::class.java))
                true
            }
            R.id.action_library_manager -> {
                startActivity(Intent(this, LibraryManagerActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_logic_analyzer -> {
                startActivity(Intent(this, LogicAnalyzerActivity::class.java))
                true
            }
            R.id.action_multi_programmer -> {
                startActivity(Intent(this, MultiProgrammerActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}