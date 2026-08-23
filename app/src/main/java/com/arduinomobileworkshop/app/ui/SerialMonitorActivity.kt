package com.arduinomobileworkshop.app.ui

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivitySerialMonitorBinding
import com.arduinomobileworkshop.usb.UsbManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class SerialMonitorActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySerialMonitorBinding
    private val usbManager: UsbManager get() = ArduinoMobileWorkshopApp.instance.usbManager
    private var currentDevice: android.hardware.usb.UsbDevice? = null
    @Volatile private var isConnected = false
    private var isAutoScroll = true
    private val readExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var readTask: Future<*>? = null
    private val baudRates = arrayOf(300, 600, 1200, 2400, 4800, 9600, 14400, 19200, 28800, 38400, 57600, 115200, 230400, 250000, 460800, 500000, 921600)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySerialMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initializeUi()
        setupEventListeners()
    }

    private fun initializeUi() {
        val baudAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, baudRates)
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.baudRateSpinner.adapter = baudAdapter
        binding.baudRateSpinner.setSelection(baudRates.indexOf(9600))
        binding.autoScrollSwitch.isChecked = true
        val newlineAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("\\n (LF)", "\\r (CR)", "\\r\\n (CRLF)", "None"))
        newlineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.newlineSpinner.adapter = newlineAdapter
        updateUiState()
    }

    private fun setupEventListeners() {
        binding.connectButton.setOnClickListener { if (isConnected) disconnect() else connect() }
        binding.clearButton.setOnClickListener { binding.serialOutput.text = "" }
        binding.sendButton.setOnClickListener { sendData() }
        binding.inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendData(); true } else false
        }
        binding.autoScrollSwitch.setOnCheckedChangeListener { _, checked -> isAutoScroll = checked }
    }

    private fun connect() {
        usbManager.scanForDevices()
        val deviceInfo = usbManager.getAvailableDevices().firstOrNull { it.driver != null } ?: run {
            showToast("No serial devices found")
            return
        }
        currentDevice = deviceInfo.device
        if (!usbManager.hasPermission(currentDevice!!)) {
            usbManager.requestPermission(currentDevice!!)
            showToast("USB permission requested")
            return
        }
        val baudRate = getSelectedBaudRate()
        if (!usbManager.connectToDevice(currentDevice!!)) {
            showToast("Unable to open USB serial device")
            currentDevice = null
            return
        }
        if (!usbManager.setBaudRate(baudRate)) {
            usbManager.disconnectFromDevice(currentDevice!!)
            currentDevice = null
            showToast("Unable to set baud rate")
            return
        }
        isConnected = true
        updateUiState()
        appendOutput("Connected to ${currentDevice!!.deviceName} at $baudRate baud\n")
        startReadLoop()
    }

    private fun disconnect() {
        isConnected = false
        readTask?.cancel(true)
        readTask = null
        currentDevice?.let { usbManager.disconnectFromDevice(it) }
        currentDevice = null
        updateUiState()
        if (binding.serialOutput.text.isNotEmpty()) appendOutput("Disconnected\n")
    }

    private fun startReadLoop() {
        readTask?.cancel(true)
        readTask = readExecutor.submit {
            val buffer = ByteArray(4096)
            while (isConnected && !Thread.currentThread().isInterrupted) {
                val count = usbManager.readData(buffer, 100)
                if (count > 0) {
                    val text = String(buffer, 0, count, Charsets.UTF_8)
                    runOnUiThread { if (!isFinishing && !isDestroyed) appendOutput(text) }
                }
            }
        }
    }

    private fun sendData() {
        if (!isConnected) { showToast("Not connected"); return }
        val text = binding.inputField.text.toString()
        if (text.isBlank()) return
        val newline = when (binding.newlineSpinner.selectedItemPosition) { 0 -> "\n"; 1 -> "\r"; 2 -> "\r\n"; else -> "" }
        if (usbManager.writeString(text + newline)) {
            appendOutput(">> $text$newline")
            binding.inputField.text?.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.inputField.windowToken, 0)
        } else showToast("Failed to send data")
    }

    private fun appendOutput(text: String) {
        binding.serialOutput.append(text)
        if (isAutoScroll) scrollToBottom()
    }

    private fun scrollToBottom() {
        val layout = binding.serialOutput.layout ?: return
        val scrollAmount = layout.getLineTop(binding.serialOutput.lineCount) - binding.serialOutput.height
        if (scrollAmount > 0) binding.serialOutput.scrollTo(0, scrollAmount)
    }

    private fun getSelectedBaudRate(): Int = baudRates[binding.baudRateSpinner.selectedItemPosition]

    private fun updateUiState() {
        binding.connectButton.text = if (isConnected) "Disconnect" else "Connect"
        binding.inputField.isEnabled = isConnected
        binding.sendButton.isEnabled = isConnected
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }

    override fun onDestroy() {
        disconnect()
        readExecutor.shutdownNow()
        super.onDestroy()
    }
}
