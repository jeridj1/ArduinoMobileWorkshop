package com.arduinomobileworkshop.app.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "ArduinoMobileWorkshopPrefs"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LINE_NUMBERS = "line_numbers"
        private const val KEY_AUTO_INDENT = "auto_indent"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        initializeUi()
        loadSettings()
    }

    private fun initializeUi() {
        val themes = arrayOf("System", "Light", "Dark")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.themeSpinner.adapter = themeAdapter

        val fontSizes = arrayOf("Small", "Medium", "Large")
        val fontSizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizes)
        fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fontSizeSpinner.adapter = fontSizeAdapter

        binding.aboutText.text = "Arduino Mobile Workshop\nVersion 0.1.0\n\nAn Android-first development environment for Arduino and microcontroller boards."
        binding.saveButton.setOnClickListener { saveSettings() }
    }

    private fun loadSettings() {
        binding.themeSpinner.setSelection(prefs.getInt(KEY_THEME, 0).coerceIn(0, 2))
        binding.fontSizeSpinner.setSelection(prefs.getInt(KEY_FONT_SIZE, 1).coerceIn(0, 2))
        binding.showLineNumbersSwitch.isChecked = prefs.getBoolean(KEY_LINE_NUMBERS, true)
        binding.autoIndentSwitch.isChecked = prefs.getBoolean(KEY_AUTO_INDENT, true)
    }

    private fun saveSettings() {
        prefs.edit()
            .putInt(KEY_THEME, binding.themeSpinner.selectedItemPosition)
            .putInt(KEY_FONT_SIZE, binding.fontSizeSpinner.selectedItemPosition)
            .putBoolean(KEY_LINE_NUMBERS, binding.showLineNumbersSwitch.isChecked)
            .putBoolean(KEY_AUTO_INDENT, binding.autoIndentSwitch.isChecked)
            .apply()
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}