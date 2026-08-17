package com.arduinomobileworkshop.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView

/**
 * Main Activity for Arduino Mobile Workshop
 * Provides navigation to all major features:
 * - Serial Monitor
 * - Boards Manager
 * - Library Manager
 * - Settings
 * - File Picker
 * - Logic Analyzer
 * - Multi-Programmer
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Setup navigation drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        
        val navController = findNavController(R.id.nav_host_fragment)
        
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_serial_monitor,
                R.id.nav_boards_manager,
                R.id.nav_library_manager,
                R.id.nav_settings,
                R.id.nav_file_picker,
                R.id.nav_logic_analyzer,
                R.id.nav_multi_programmer
            ),
            drawerLayout
        )
        
        setupActionBarWithNavController(navController, appBarConfiguration)
        navigationView.setupWithNavController(navController)
        
        // Handle navigation item clicks
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_serial_monitor -> {
                    startActivity(Intent(this, SerialMonitorActivity::class.java))
                    true
                }
                R.id.nav_boards_manager -> {
                    startActivity(Intent(this, BoardsManagerActivity::class.java))
                    true
                }
                R.id.nav_library_manager -> {
                    startActivity(Intent(this, LibraryManagerActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.nav_file_picker -> {
                    startActivity(Intent(this, FilePickerActivity::class.java))
                    true
                }
                R.id.nav_logic_analyzer -> {
                    startActivity(Intent(this, LogicAnalyzerActivity::class.java))
                    true
                }
                R.id.nav_multi_programmer -> {
                    startActivity(Intent(this, MultiProgrammerActivity::class.java))
                    true
                }
                else -> false
            }
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
    
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}