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
    private val toolchainManager: ToolchainManager get() = ArduinoMobileWorkshopApp.instance.toolchainManager
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
        boardAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        binding.boardsListView.adapter = boardAdapter
        binding.boardsListView.setOnItemClickListener { _, _, position, _ -> boardAdapter.getItem(position)?.let(::showBoardDetails) }
        binding.refreshButton.setOnClickListener {
            showToast("Downloading board index...")
            toolchainManager.refreshBoardIndex { success, _ ->
                runOnUiThread {
                    showToast(if (success) "Board index updated" else "Index refresh unavailable (showing cached/default)")
                    loadBoards()
                }
            }
        }
        binding.installButton.setOnClickListener { showInstallDialog() }
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
            .setMessage("FQBN: ${board.id}\nPlatform: ${board.platform}\nPackage: ${board.packageName}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInstallDialog() {
        val boards = toolchainManager.getAvailableBoards()
        val names = boards.map { it.name + " (" + it.packageName + ")" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Install Board Package")
            .setItems(names.toTypedArray()) { _, which ->
                val board = boards[which]
                showToast("Installing " + board.packageName + "...")
                toolchainManager.installBoardPackage(board.packageName) { success, output ->
                    runOnUiThread {
                        showToast(if (success) "Installed " + board.name else "Install failed")
                        if (!success) showOutputDialog(output)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOutputDialog(output: String) {
        android.app.AlertDialog.Builder(this).setTitle("Arduino CLI").setMessage(output).setPositiveButton("OK", null).show()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
