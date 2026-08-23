package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivityBoardsManagerBinding
import com.arduinomobileworkshop.toolchain.Board
import com.arduinomobileworkshop.toolchain.ToolchainManager

class BoardsManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBoardsManagerBinding
    private val toolchainManager: ToolchainManager
        get() = ArduinoMobileWorkshopApp.instance.toolchainManager
    private lateinit var boardAdapter: ArrayAdapter<Board>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoardsManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initializeUi()
        loadBoards()
    }

    private fun initializeUi() {
        boardAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1)
        binding.boardsListView.adapter = boardAdapter
        binding.boardsListView.setOnItemClickListener { _, _, position, _ ->
            boardAdapter.getItem(position)?.let { showBoardDetails(it) }
        }
        binding.refreshButton.setOnClickListener { loadBoards() }
        binding.installButton.setOnClickListener { prepareToolchainAndInstall() }
    }

    private fun loadBoards() {
        val boards = toolchainManager.getAvailableBoards()
        boardAdapter.clear()
        boardAdapter.addAll(boards)
        boardAdapter.notifyDataSetChanged()
        binding.emptyMessage.visibility = if (boards.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showBoardDetails(board: Board) {
        android.app.AlertDialog.Builder(this)
            .setTitle(board.name)
            .setMessage("FQBN: ${board.fqbn}\nPackage: ${board.packageName}\nArchitecture: ${board.architecture}")
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
                    } else {
                        showToast("Arduino CLI failed: $message")
                    }
                }
            }
        } else {
            showInstallDialog()
        }
    }

    private fun showInstallDialog() {
        val packages = arrayOf(
            "Arduino AVR Boards" to "arduino:avr",
            "ESP32 by Espressif Systems" to "esp32:esp32",
            "ESP8266 by ESP8266 Community" to "esp8266:esp8266",
            "Raspberry Pi Pico / RP2040" to "rp2040:rp2040"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Install Board Package")
            .setItems(packages.map { it.first }.toTypedArray()) { _, which ->
                val packageId = packages[which].second
                showToast("Installing ${packages[which].first}…")
                toolchainManager.installBoardPackage(packageId) { success, message ->
                    runOnUiThread {
                        showToast(if (success) "Installed" else "Failed: $message")
                        if (success) loadBoards()
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
