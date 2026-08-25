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
import com.arduinomobileworkshop.app.databinding.ActivityLibraryManagerBinding
import com.arduinomobileworkshop.app.databinding.ItemManagerBinding
import com.arduinomobileworkshop.toolchain.Library
import com.arduinomobileworkshop.toolchain.ToolchainManager

/**
 * Interactive Library Manager with a SearchView filter, a ProgressBar loading
 * indicator, and a RecyclerView whose rows each carry an Install button.  The
 * index is fetched over HTTP (Arduino library_index.json) via OkHttp inside
 * [ToolchainManager.refreshLibraryIndex] and [ToolchainManager.searchLibraries].
 */
class LibraryManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLibraryManagerBinding
    private val toolchainManager: ToolchainManager get() = ArduinoMobileWorkshopApp.instance.toolchainManager

    private val allLibraries = mutableListOf<Library>()
    private val filteredLibraries = mutableListOf<Library>()
    private lateinit var adapter: LibraryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = LibraryAdapter(filteredLibraries) { library -> installLibrary(library) }
        binding.librariesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.librariesRecyclerView.adapter = adapter

        binding.searchView.queryHint = "Search libraries..."
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { filter(query); return true }
            override fun onQueryTextChange(newText: String?): Boolean { filter(newText); return true }
        })

        binding.refreshButton.setOnClickListener {
            showLoading(true)
            showToast("Refreshing library index...")
            toolchainManager.refreshLibraryIndex { success, _ ->
                runOnUiThread {
                    showLoading(false)
                    showToast(if (success) "Library index updated" else "Index refresh unavailable (showing cached)")
                    loadLibraries()
                }
            }
        }

        loadLibraries()
    }

    private fun filter(query: String?) {
        val q = query?.lowercase()?.trim() ?: ""
        filteredLibraries.clear()
        if (q.isEmpty()) filteredLibraries.addAll(allLibraries)
        else filteredLibraries.addAll(allLibraries.filter {
            it.name.lowercase().contains(q) ||
            it.author.lowercase().contains(q) ||
            it.description.lowercase().contains(q)
        })
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun loadLibraries() {
        allLibraries.clear()
        allLibraries.addAll(toolchainManager.getInstalledLibraries())
        if (allLibraries.isEmpty()) {
            showLoading(true)
            toolchainManager.searchLibraries("") { results ->
                runOnUiThread {
                    showLoading(false)
                    allLibraries.clear()
                    allLibraries.addAll(if (results.isEmpty()) defaultPopularLibraries() else results)
                    filter(binding.searchView.query?.toString())
                }
            }
        } else {
            filter(binding.searchView.query?.toString())
        }
    }

    private fun installLibrary(library: Library) {
        showToast("Installing " + library.name + "...")
        toolchainManager.installLibrary(library.name) { success, output ->
            runOnUiThread {
                showToast(if (success) "Installed " + library.name else "Install failed")
                if (!success) showOutputDialog(output)
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

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateEmptyState() {
        binding.emptyMessage.visibility = if (filteredLibraries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showOutputDialog(output: String) {
        android.app.AlertDialog.Builder(this).setTitle("Arduino CLI").setMessage(output).setPositiveButton("OK", null).show()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }

    inner class LibraryAdapter(
        private val libraries: MutableList<Library>,
        private val onInstall: (Library) -> Unit
    ) : RecyclerView.Adapter<LibraryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
            val itemBinding = ItemManagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return LibraryViewHolder(itemBinding)
        }
        override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
            val lib = libraries[position]
            holder.binding.itemTitle.text = lib.name
            val sub = if (lib.version.isNotEmpty()) "v" + lib.version + "  -  " + lib.author else lib.author
            holder.binding.itemSubtitle.text = sub
            holder.binding.itemAction.text = "Install"
            holder.binding.itemAction.setOnClickListener { onInstall(lib) }
        }
        override fun getItemCount(): Int = libraries.size
    }

    inner class LibraryViewHolder(val binding: ItemManagerBinding) : RecyclerView.ViewHolder(binding.root)
}
