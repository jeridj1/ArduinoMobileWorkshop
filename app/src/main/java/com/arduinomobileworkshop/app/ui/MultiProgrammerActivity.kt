package com.arduinomobileworkshop.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arduinomobileworkshop.rp2040.services.RP2040ProgrammerService
import java.io.File

/**
 * Activity for Multi-Programmer mode.
 *
 * Allows programming multiple RP2040 devices simultaneously. Supports selecting
 * multiple devices, programming with UF2 files, and progress tracking.
 *
 * NOTE: device discovery is still mocked (returns placeholder devices) until the
 * real RP2040 USB enumeration is wired in.
 */
class MultiProgrammerActivity : AppCompatActivity() {

    private var programmerService: RP2040ProgrammerService? = null
    private var isServiceBound = false
    private var isProgramming = false

    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var programButton: Button
    private lateinit var cancelButton: Button
    private lateinit var statusTextView: TextView

    private val selectedDevices = mutableListOf<DeviceInfo>()

    private val deviceAdapter = DeviceAdapter(selectedDevices) { device, isSelected ->
        if (isSelected) selectedDevices.add(device) else selectedDevices.remove(device)
        updateProgramButton()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as RP2040ProgrammerService.LocalBinder
            programmerService = binder.getService()
            isServiceBound = true
            scanForDevices()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            programmerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_programmer)

        deviceRecyclerView = findViewById(R.id.multi_programmer_devices)
        programButton = findViewById(R.id.multi_programmer_program)
        cancelButton = findViewById(R.id.multi_programmer_cancel)
        statusTextView = findViewById(R.id.multi_programmer_status)

        deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceRecyclerView.adapter = deviceAdapter

        programButton.setOnClickListener { startProgramming() }
        cancelButton.setOnClickListener { cancelProgramming() }

        val intent = Intent(this, RP2040ProgrammerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        cancelProgramming()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_multi_programmer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_scan -> { scanForDevices(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun scanForDevices() {
        if (!isServiceBound) return
        Toast.makeText(this, "Scanning for RP2040 devices...", Toast.LENGTH_SHORT).show()

        // Mocked device list until real RP2040 USB enumeration is wired in.
        val devices = listOf(
            DeviceInfo("Device 1", "RP2040-001", true),
            DeviceInfo("Device 2", "RP2040-002", true),
            DeviceInfo("Device 3", "RP2040-003", true)
        )
        deviceAdapter.updateDevices(devices)
        statusTextView.text = "Found " + devices.size + " devices"
    }

    private fun startProgramming() {
        if (!isServiceBound || selectedDevices.isEmpty()) return
        isProgramming = true
        updateUI()
        statusTextView.text = "Programming " + selectedDevices.size + " devices..."

        // Mocked programming sequence.
        var progress = 0
        selectedDevices.forEachIndexed { index, device ->
            progress = ((index + 1) * 100 / selectedDevices.size)
            statusTextView.text = "Programming device " + device.name + "... (" + progress + "%)"
        }

        isProgramming = false
        updateUI()
        statusTextView.text = "Programming complete!"
        Toast.makeText(this, "All devices programmed", Toast.LENGTH_SHORT).show()
    }

    private fun cancelProgramming() {
        if (!isProgramming) return
        programmerService?.cancelProgramming()
        isProgramming = false
        updateUI()
        statusTextView.text = "Programming cancelled"
        Toast.makeText(this, "Programming cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        programButton.isEnabled = isServiceBound && selectedDevices.isNotEmpty() && !isProgramming
        cancelButton.isEnabled = isProgramming
    }

    private fun updateProgramButton() {
        programButton.isEnabled = selectedDevices.isNotEmpty() && !isProgramming
    }

    data class DeviceInfo(
        val name: String,
        val id: String,
        val isRp2040: Boolean
    )

    /**
     * Adapter backed by [selectedDevices] (the activity's selection list). Each
     * row uses android.R.layout.simple_list_item_multiple_choice, whose root is a
     * CheckedTextView (id text1); we toggle its checked state directly instead of
     * looking up a separate CheckBox view.
     */
    inner class DeviceAdapter(
        private val devices: MutableList<DeviceInfo>,
        private val onSelectionChanged: (DeviceInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<DeviceViewHolder>() {

        fun updateDevices(newDevices: List<DeviceInfo>) {
            devices.clear()
            devices.addAll(newDevices)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): DeviceViewHolder {
            val view = layoutInflater.inflate(
                android.R.layout.simple_list_item_multiple_choice, parent, false
            )
            return DeviceViewHolder(view)
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            holder.bind(devices[position], onSelectionChanged)
        }

        override fun getItemCount(): Int = devices.size
    }

    inner class DeviceViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val checkedText: CheckedTextView =
            itemView.findViewById(android.R.id.text1)

        fun bind(device: DeviceInfo, onSelectionChanged: (DeviceInfo, Boolean) -> Unit) {
            checkedText.text = device.name
            checkedText.isChecked = selectedDevices.contains(device)
            itemView.setOnClickListener {
                val isSelected = !selectedDevices.contains(device)
                onSelectionChanged(device, isSelected)
                checkedText.isChecked = isSelected
            }
        }
    }
}
