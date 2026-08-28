package com.arduinomobileworkshop.app.ui

import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.R
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager as AndroidUsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arduinomobileworkshop.rp2040.RP2040Manager
import com.arduinomobileworkshop.rp2040.services.RP2040ProgrammerService
import java.io.File

/**
 * Activity for Multi-Programmer mode.
 *
 * Lists physical RP2040 devices discovered through the Android USB host
 * descriptor table (Raspberry Pi vendor id 0x2E8A), natively targeting
 * BOOTSEL mass-storage / PICOBOOT bootloader devices (product id 0x0003).
 * A mode selector (SWD, JTAG, AVR-ISP) updates a dynamic hookup-guide panel
 * and the "Prepare Pico" button flashes the matching helper firmware image
 * to put the Pico into the chosen programmer state machine.
 *
 * The user selects one or more devices and a chosen UF2 file is streamed to
 * each device in turn via [RP2040ProgrammerService] over the PICOBOOT bulk
 * endpoints.
 */
class MultiProgrammerActivity : AppCompatActivity() {

    /** Programmer modes with their hookup guides and helper-firmware assets. */
    enum class ProgrammerMode(
        val displayName: String,
        val assetName: String,
        val hookupGuide: String
    ) {
        SWD("SWD", "firmware/swd_helper.uf2",
            "MODE: SWD (Serial Wire Debug)\n\n" +
            "Connect Pico GP2 -> Target SWCLK\n" +
            "Connect Pico GP3 -> Target SWDIO\n" +
            "Connect Pico GND -> Target GND\n\n" +
            "Flash helper firmware to prepare the Pico as an SWD programmer."),
        JTAG("JTAG", "firmware/jtag_helper.uf2",
            "MODE: JTAG\n\n" +
            "Connect Pico GP2 -> Target TCK\n" +
            "Connect Pico GP3 -> Target TMS\n" +
            "Connect Pico GP4 -> Target TDI\n" +
            "Connect Pico GP5 -> Target TDO\n" +
            "Connect Pico GND -> Target GND\n\n" +
            "Flash helper firmware to prepare the Pico as a JTAG programmer."),
        AVR_ISP("AVR-ISP", "firmware/avr_isp_helper.uf2",
            "MODE: AVR-ISP\n\n" +
            "Connect Pico GP2 -> Target RESET\n" +
            "Connect Pico GP3 -> Target SCK\n" +
            "Connect Pico GP4 -> Target MISO\n" +
            "Connect Pico GP5 -> Target MOSI\n" +
            "Connect Pico GND -> Target GND\n\n" +
            "Flash helper firmware to prepare the Pico as an AVR-ISP programmer."),
        UPDI("UPDI", "firmware/updi_helper.uf2",
            "MODE: UPDI (Unified Program + Debug Interface)\n\n" +
            "Connect Pico GP2 -> Target UPDI (single-wire data line)\n" +
            "Connect Pico GND -> Target GND\n\n" +
            "Flash helper firmware to prepare the Pico as a UPDI programmer.")
    }

    private var programmerService: RP2040ProgrammerService? = null
    private var isServiceBound = false
    private var isProgramming = false

    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var programButton: Button
    private lateinit var cancelButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var modeSpinner: Spinner
    private lateinit var hookupGuideView: TextView
    private lateinit var prepareButton: Button

    private var selectedMode: ProgrammerMode = ProgrammerMode.SWD

    private val scannedUsbDevices = mutableMapOf<String, UsbDevice>()
    private var permissionCallback: ((Boolean) -> Unit)? = null

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

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != com.arduinomobileworkshop.usb.UsbManager.ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(AndroidUsbManager.EXTRA_PERMISSION_GRANTED, false)
            val cb = permissionCallback
            permissionCallback = null
            cb?.invoke(granted)
        }
    }

    private val uf2Picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val file = copyUriToCache(uri)
        if (file != null) {
            programSelectedDevices(file)
        } else {
            Toast.makeText(this, "Failed to load UF2 file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_programmer)

        deviceRecyclerView = findViewById(R.id.multi_programmer_devices)
        programButton = findViewById(R.id.multi_programmer_program)
        cancelButton = findViewById(R.id.multi_programmer_cancel)
        statusTextView = findViewById(R.id.multi_programmer_status)
        modeSpinner = findViewById(R.id.multi_programmer_mode)
        hookupGuideView = findViewById(R.id.multi_programmer_hookup_guide)
        prepareButton = findViewById(R.id.multi_programmer_prepare)

        findViewById<Button>(R.id.multi_programmer_scan).setOnClickListener { scanForDevices() }

        deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        deviceRecyclerView.adapter = deviceAdapter

        programButton.setOnClickListener { startProgramming() }
        cancelButton.setOnClickListener { cancelProgramming() }

        val modeNames = ProgrammerMode.entries.map { it.displayName }
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeNames)
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedMode = ProgrammerMode.entries[position]
                hookupGuideView.text = selectedMode.hookupGuide
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        hookupGuideView.text = ProgrammerMode.SWD.hookupGuide

        prepareButton.setOnClickListener { preparePico() }

        val intent = Intent(this, RP2040ProgrammerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(com.arduinomobileworkshop.usb.UsbManager.ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 26) {
            registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
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
            R.id.action_select_all -> {
                selectedDevices.clear()
                selectedDevices.addAll(deviceAdapter.currentList())
                deviceAdapter.notifyDataSetChanged()
                updateProgramButton()
                true
            }
            R.id.action_deselect_all -> {
                selectedDevices.clear()
                deviceAdapter.notifyDataSetChanged()
                updateProgramButton()
                true
            }
            R.id.action_add_file -> { startProgramming(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun preparePico() {
        val service = programmerService
        if (service == null) {
            Toast.makeText(this, "Service not connected yet", Toast.LENGTH_SHORT).show()
            return
        }
        val devices = service.scanForBootloaderDevices()
        if (devices.isEmpty()) {
            statusTextView.text = "No BOOTSEL device found. Hold BOOTSEL while plugging in the Pico."
            Toast.makeText(this, "No BOOTSEL device found", Toast.LENGTH_SHORT).show()
            return
        }
        val device = devices[0]
        ensurePermission(device) { granted ->
            if (!granted) {
                statusTextView.text = "USB permission denied for helper firmware"
                return@ensurePermission
            }
            statusTextView.text = "Flashing " + selectedMode.displayName + " helper firmware..."
            service.flashHelperFirmware(selectedMode.assetName, device) { success, msg ->
                runOnUiThread {
                    statusTextView.text = if (success)
                        selectedMode.displayName + " helper firmware flashed. Pico ready."
                    else "Helper firmware flash failed: " + msg
                    Toast.makeText(this, if (success) "Pico prepared" else "Flash failed", Toast.LENGTH_SHORT).show()
                    scanForDevices()
                }
            }
        }
    }

    private fun scanForDevices() {
        if (!isServiceBound) return
        val service = programmerService ?: return
        Toast.makeText(this, "Scanning for RP2040 devices...", Toast.LENGTH_SHORT).show()

        val bootDevices = service.scanForBootloaderDevices()
        val usbDevices = if (bootDevices.isNotEmpty()) bootDevices else service.scanForDevices()
        scannedUsbDevices.clear()
        usbDevices.forEach { scannedUsbDevices[it.deviceName] = it }

        val devices = usbDevices.map { d ->
            val inBoot = d.productId == RP2040Manager.RP2040_PID_BOOTLOADER
            val mode = if (inBoot) "BOOTSEL" else "SERIAL"
            val label = (d.productName ?: "RP2040") + " [" + mode + "]"
            DeviceInfo(label, d.deviceName, true, d.productId, inBoot)
        }

        selectedDevices.clear()
        deviceAdapter.updateDevices(devices)
        updateProgramButton()
        statusTextView.text = if (devices.isEmpty()) {
            "No RP2040 devices found. Hold BOOTSEL while plugging in a Pico and tap Scan."
        } else if (bootDevices.isEmpty()) {
            "Found " + devices.size + " RP2040 device(s) in serial mode. Hold BOOTSEL to flash."
        } else {
            "Found " + devices.size + " BOOTSEL device(s) ready to flash."
        }
    }

    private fun startProgramming() {
        if (!isServiceBound || selectedDevices.isEmpty()) {
            Toast.makeText(this, "Select at least one device first", Toast.LENGTH_SHORT).show()
            return
        }
        uf2Picker.launch(arrayOf("*/*"))
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val out = File(cacheDir, "program_" + System.currentTimeMillis() + ".uf2")
            contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return null
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun ensurePermission(device: UsbDevice, callback: (Boolean) -> Unit) {
        val appUsbManager = (application as ArduinoMobileWorkshopApp).usbManager
        if (appUsbManager.hasPermission(device)) {
            callback(true)
            return
        }
        permissionCallback = callback
        appUsbManager.requestPermission(device)
    }

    private fun programSelectedDevices(uf2File: File) {
        val service = programmerService
        if (service == null || selectedDevices.isEmpty()) return
        isProgramming = true
        updateUI()
        val queue = selectedDevices.toList()
        statusTextView.text = "Programming " + queue.size + " device(s)..."

        fun programNext(index: Int) {
            if (index >= queue.size) {
                isProgramming = false
                updateUI()
                statusTextView.text = "Programming complete!"
                Toast.makeText(this@MultiProgrammerActivity, "All devices programmed", Toast.LENGTH_SHORT).show()
                scanForDevices()
                return
            }
            val info = queue[index]
            val usbDevice = scannedUsbDevices[info.id]
            if (usbDevice == null) {
                statusTextView.text = info.name + " no longer connected"
                programNext(index + 1)
                return
            }
            ensurePermission(usbDevice) { granted ->
                if (!granted) {
                    statusTextView.text = "Permission denied: " + info.name
                    programNext(index + 1)
                    return@ensurePermission
                }
                service.programDevice(usbDevice, uf2File) { progress, msg, terminal ->
                    statusTextView.text = info.name + ": " + msg
                    if (terminal) programNext(index + 1)
                }
            }
        }
        programNext(0)
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
        val isRp2040: Boolean,
        val productId: Int = 0,
        val inBootloader: Boolean = false
    )

    inner class DeviceAdapter(
        private val devices: MutableList<DeviceInfo>,
        private val onSelectionChanged: (DeviceInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<DeviceViewHolder>() {

        fun updateDevices(newDevices: List<DeviceInfo>) {
            devices.clear()
            devices.addAll(newDevices)
            notifyDataSetChanged()
        }

        fun currentList(): List<DeviceInfo> = devices.toList()

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
