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
    private val toolchainManager: ToolchainManager
        get() = ArduinoMobileWorkshopApp.instance.toolchainManager
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
        libraryAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1)
        binding.librariesListView.adapter = libraryAdapter
        binding.librariesListView.setOnItemClickListener { _, _, position, _ ->
            libraryAdapter.getItem(position)?.let { showLibraryDetails(it) }
        }
        binding.refreshButton.setOnClickListener { loadLibraries() }
        binding.installButton.setOnClickListener { prepareToolchainAndInstall() }
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

    private fun prepareToolchainAndInstall() {
        if (!toolchainManager.isArduinoCliAvailable()) {
            showToast("Installing Arduino CLI…")
            toolchainManager.installArduinoCli { success, message ->
                runOnUiThread {
                    if (success) {
                        showToast("Arduino CLI ready")
                        showInstallDialog()
                    } else showToast("Arduino CLI failed: $message")
                }
            }
        } else showInstallDialog()
    }

    private fun showInstallDialog() {
        val libraries = arrayOf(
            "FastLED" to "FastLED",
            "Adafruit GFX Library" to "Adafruit GFX Library",
            "Adafruit BusIO" to "Adafruit BusIO",
            "OneWire" to "OneWire",
            "DallasTemperature" to "DallasTemperature"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Install Library")
            .setItems(libraries.map { it.first }.toTypedArray()) { _, which ->
                val libraryId = libraries[which].second
                showToast("Installing ${libraries[which].first}…")
                toolchainManager.installLibrary(libraryId) { success, message ->
                    runOnUiThread {
                        showToast(if (success) "Installed" else "Failed: $message")
                        if (success) loadLibraries()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
