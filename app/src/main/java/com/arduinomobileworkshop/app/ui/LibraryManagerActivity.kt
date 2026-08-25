package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivityLibraryManagerBinding
import com.arduinomobileworkshop.toolchain.Library
import com.arduinomobileworkshop.toolchain.ToolchainManager

class LibraryManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLibraryManagerBinding
    private val toolchainManager: ToolchainManager get() = ArduinoMobileWorkshopApp.instance.toolchainManager
    private lateinit var libraryAdapter: ArrayAdapter<Library>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initializeUi()
        loadLibraries()
    }

    private fun initializeUi() {
        libraryAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        binding.librariesListView.adapter = libraryAdapter
        binding.librariesListView.setOnItemClickListener { _, _, position, _ -> libraryAdapter.getItem(position)?.let(::showLibraryDetails) }
        binding.refreshButton.setOnClickListener {
            showToast("Refreshing library index...")
            toolchainManager.refreshLibraryIndex { success, _ ->
                runOnUiThread {
                    showToast(if (success) "Library index updated" else "Index refresh unavailable (showing cached)")
                    loadLibraries()
                }
            }
        }
        binding.installButton.setOnClickListener { startLibrarySearch() }
    }

    private fun loadLibraries() {
        val libraries = toolchainManager.getInstalledLibraries()
        libraryAdapter.clear()
        libraryAdapter.addAll(libraries)
        libraryAdapter.notifyDataSetChanged()
        binding.emptyMessage.visibility = if (libraries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showLibraryDetails(library: Library) {
        android.app.AlertDialog.Builder(this)
            .setTitle(library.name)
            .setMessage("Version: ${library.version}\nAuthor: ${library.author}\nDescription: ${library.description}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startLibrarySearch() {
        showToast("Searching library index...")
        toolchainManager.searchLibraries("") { results ->
            runOnUiThread {
                val libs = if (results.isEmpty()) defaultPopularLibraries() else results
                showLibraryInstallDialog(libs)
            }
        }
    }

    private fun defaultPopularLibraries(): List<Library> = listOf(
        Library("FastLED", "FastLED", "3.6.0", "FastLED", "Parallel output of LED strips", "FastLED"),
        Library("Adafruit GFX Library", "Adafruit GFX Library", "", "Adafruit", "Core graphics library for Adafruit displays", "Adafruit_GFX"),
        Library("Adafruit BusIO", "Adafruit BusIO", "", "Adafruit", "I2C/SPI abstraction for Adafruit devices", "Adafruit_BusIO"),
        Library("OneWire", "OneWire", "", "Paul Stoffregen", "1-wire bus protocol", "OneWire"),
        Library("DallasTemperature", "DallasTemperature", "", "Miles Burton", "DS18B20 temperature sensors", "DallasTemperature"),
        Library("Servo", "Servo", "", "Arduino", "Standard RC servo control", "Servo")
    )

    private fun showLibraryInstallDialog(libs: List<Library>) {
        val names = libs.map { it.name + if (it.version.isNotEmpty()) " " + it.version else "" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Install Library")
            .setItems(names) { _, which ->
                val library = libs[which]
                showToast("Installing " + library.name + "...")
                toolchainManager.installLibrary(library.name) { success, output ->
                    runOnUiThread {
                        showToast(if (success) "Installed " + library.name else "Install failed")
                        if (!success) android.app.AlertDialog.Builder(this).setTitle("Arduino CLI").setMessage(output).setPositiveButton("OK", null).show()
                        loadLibraries()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
