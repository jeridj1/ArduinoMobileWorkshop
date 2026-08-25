package com.arduinomobileworkshop.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivityBoardsManagerBinding
import com.arduinomobileworkshop.app.databinding.ItemManagerBinding
import com.arduinomobileworkshop.toolchain.Board
import com.arduinomobileworkshop.toolchain.ToolchainManager

/**
 * Interactive Boards Manager with a SearchView filter, a ProgressBar loading
 * indicator, and a RecyclerView whose rows each carry a Download/Install
 * button.  The index is fetched over HTTP (Arduino package_index.json) via
 * OkHttp inside [ToolchainManager.refreshBoardIndex].
 */
class BoardsManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBoardsManagerBinding
    private val toolchainManager: ToolchainManager get() = ArduinoMobileWorkshopApp.instance.toolchainManager

    private val allBoards = mutableListOf<Board>()
    private val filteredBoards = mutableListOf<Board>()
    private lateinit var adapter: BoardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoardsManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = BoardAdapter(filteredBoards) { board -> installBoard(board) }
        binding.boardsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.boardsRecyclerView.adapter = adapter

        binding.searchView.queryHint = "Search boards..."
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { filter(query); return true }
            override fun onQueryTextChange(newText: String?): Boolean { filter(newText); return true }
        })

        binding.refreshButton.setOnClickListener {
            showLoading(true)
            showToast("Downloading board index...")
            toolchainManager.refreshBoardIndex { success, _ ->
                runOnUiThread {
                    showLoading(false)
                    showToast(if (success) "Board index updated" else "Index refresh unavailable (showing cached/default)")
                    loadBoards()
                }
            }
        }

        loadBoards()
    }

    private fun filter(query: String?) {
        val q = query?.lowercase()?.trim() ?: ""
        filteredBoards.clear()
        if (q.isEmpty()) filteredBoards.addAll(allBoards)
        else filteredBoards.addAll(allBoards.filter {
            it.name.lowercase().contains(q) ||
            it.id.lowercase().contains(q) ||
            it.platform.lowercase().contains(q)
        })
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun loadBoards() {
        allBoards.clear()
        allBoards.addAll(toolchainManager.getAvailableBoards())
        filter(binding.searchView.query?.toString())
    }

    private fun installBoard(board: Board) {
        showToast("Installing " + board.name + "...")
        toolchainManager.installBoardPackage(board.packageName) { success, output ->
            runOnUiThread {
                showToast(if (success) "Installed " + board.name else "Install failed")
                if (!success) showOutputDialog(output)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateEmptyState() {
        binding.emptyMessage.visibility = if (filteredBoards.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showOutputDialog(output: String) {
        android.app.AlertDialog.Builder(this).setTitle("Arduino CLI").setMessage(output).setPositiveButton("OK", null).show()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }

    inner class BoardAdapter(
        private val boards: MutableList<Board>,
        private val onInstall: (Board) -> Unit
    ) : RecyclerView.Adapter<BoardViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
            val itemBinding = ItemManagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return BoardViewHolder(itemBinding)
        }
        override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
            val board = boards[position]
            holder.binding.itemTitle.text = board.name
            holder.binding.itemSubtitle.text = board.id + "  -  v" + board.version
            holder.binding.itemAction.text = "Download"
            holder.binding.itemAction.setOnClickListener { onInstall(board) }
        }
        override fun getItemCount(): Int = boards.size
    }

    inner class BoardViewHolder(val binding: ItemManagerBinding) : RecyclerView.ViewHolder(binding.root)
}
