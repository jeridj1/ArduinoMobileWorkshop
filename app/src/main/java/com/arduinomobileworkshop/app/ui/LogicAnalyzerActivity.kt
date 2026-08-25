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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.rp2040.services.LogicAnalyzerService
import com.arduinomobileworkshop.rp2040.services.RP2040ProgrammerService

class LogicAnalyzerActivity : AppCompatActivity() {

    private var logicAnalyzerService: LogicAnalyzerService? = null
    private var isServiceBound = false
    private var isCapturing = false

    private var programmerService: RP2040ProgrammerService? = null
    private var isProgrammerBound = false

    private lateinit var startCaptureButton: Button
    private lateinit var stopCaptureButton: Button
    private lateinit var sampleRateSpinner: Spinner
    private lateinit var channelsSpinner: Spinner
    private lateinit var statusTextView: TextView
    private lateinit var prepareButton: Button

    private var permissionCallback: ((Boolean) -> Unit)? = null

    companion object {
        private const val LA_ASSET = "firmware/logic_analyzer_helper.uf2"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LogicAnalyzerService.LocalBinder
            logicAnalyzerService = binder.getService()
            isServiceBound = true
            updateUI()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            logicAnalyzerService = null
        }
    }

    private val programmerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as RP2040ProgrammerService.LocalBinder
            programmerService = binder.getService()
            isProgrammerBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isProgrammerBound = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logic_analyzer)

        startCaptureButton = findViewById(R.id.logic_analyzer_start)
        stopCaptureButton = findViewById(R.id.logic_analyzer_stop)
        sampleRateSpinner = findViewById(R.id.logic_analyzer_sample_rate)
        channelsSpinner = findViewById(R.id.logic_analyzer_channels)
        statusTextView = findViewById(R.id.logic_analyzer_status)
        prepareButton = findViewById(R.id.logic_analyzer_prepare)

        startCaptureButton.setOnClickListener { startCapture() }
        stopCaptureButton.setOnClickListener { stopCapture() }
        prepareButton.setOnClickListener { preparePico() }

        val intent = Intent(this, LogicAnalyzerService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        val progIntent = Intent(this, RP2040ProgrammerService::class.java)
        startService(progIntent)
        bindService(progIntent, programmerConnection, Context.BIND_AUTO_CREATE)
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
        if (isProgrammerBound) {
            unbindService(programmerConnection)
            isProgrammerBound = false
        }
        stopCapture()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logic_analyzer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun preparePico() {
        val service = programmerService
        if (service == null) {
            Toast.makeText(this, "Programmer service not connected yet", Toast.LENGTH_SHORT).show()
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
            statusTextView.text = "Flashing logic-analyzer helper firmware..."
            service.flashHelperFirmware(LA_ASSET, device) { success, msg ->
                runOnUiThread {
                    statusTextView.text = if (success)
                        "LA helper firmware flashed. Pico ready as a logic analyzer."
                    else "Helper firmware flash failed: " + msg
                    Toast.makeText(this, if (success) "Pico prepared" else "Flash failed", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun startCapture() {
        if (!isServiceBound || isCapturing) return

        val sampleRate = when (sampleRateSpinner.selectedItemPosition) {
            0 -> 100000
            1 -> 500000
            2 -> 1000000
            3 -> 2000000
            else -> 1000000
        }

        val channels = when (channelsSpinner.selectedItemPosition) {
            0 -> 4
            1 -> 8
            2 -> 16
            else -> 8
        }

        logicAnalyzerService?.configure(sampleRate, channels)

        logicAnalyzerService?.startCapture { capturedData ->
            runOnUiThread {
                val processedChannels = logicAnalyzerService?.processCaptureData(capturedData)
                if (processedChannels != null) {
                    statusTextView.text = "Captured " + capturedData.size + " bytes"
                }
            }
        }

        isCapturing = true
        updateUI()
        Toast.makeText(this, "Capture started", Toast.LENGTH_SHORT).show()
    }

    private fun stopCapture() {
        if (!isServiceBound || !isCapturing) return

        logicAnalyzerService?.stopCapture()
        isCapturing = false
        updateUI()
        Toast.makeText(this, "Capture stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        startCaptureButton.isEnabled = isServiceBound && !isCapturing
        stopCaptureButton.isEnabled = isServiceBound && isCapturing

        val config = logicAnalyzerService?.getConfiguration()
        if (config != null) {
            statusTextView.text = "Ready - " + config["sample_rate"] + "Hz, " + config["channels"] + " channels"
        } else {
            statusTextView.text = "Service not connected"
        }
    }
}
