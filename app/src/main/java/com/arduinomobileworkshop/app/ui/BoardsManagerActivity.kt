package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.util.Log
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
        binding.installButton.setOnClickListener { showInstallDialog() }
    }
    
    private fun loadBoards() {
        val boards = toolchainManager.getInstalledBoards()
        runOnUiThread {
            boardAdapter.clear()
            boardAdapter.addAll(boards)
            boardAdapter.notifyDataSetChanged()
            binding.emptyMessage.visibility = if (boards.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    
    private fun showBoardDetails(board: Board) {
        android.app.AlertDialog.Builder(this)
            .setTitle(board.name)
            .setMessage("ID: ${board.id}\nPlatform: ${board.platform}\nPackage: ${board.packageName}\nVersion: ${board.version}")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }
    
    private fun showInstallDialog() {
        val packages = listOf("Arduino AVR Boards", "ESP32 Boards", "ESP8266 Boards", "RP2040 Boards")
        android.app.AlertDialog.Builder(this)
            .setTitle("Install Board Package")
            .setItems(packages.toTypedArray()) { _, which ->
                showToast("Installing: ${packages[which]}")
                toolchainManager.installBoardPackage("", { success, _ ->
                    runOnUiThread { if (success) { showToast("Package installed"); loadBoards() } else showToast("Failed") }
                })
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }
    
    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
