package com.arduinomobileworkshop.app.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivitySerialMonitorBinding
import com.arduinomobileworkshop.usb.SerialPortManager
import com.arduinomobileworkshop.usb.UsbManager

class SerialMonitorActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySerialMonitorBinding
    private val usbManager: UsbManager
        get() = ArduinoMobileWorkshopApp.instance.usbManager
    private var serialPort: SerialPortManager? = null
    private var currentDevice: android.hardware.usb.UsbDevice? = null
    private var isConnected = false
    private var isAutoScroll = true
    private val handler = Handler(Looper.getMainLooper())
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
        val newlineOptions = arrayOf("\\n (LF)", "\\r (CR)", "\\r\\n (CRLF)", "None")
        val newlineAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, newlineOptions)
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
        val devices = usbManager.getSerialDevices()
        if (devices.isEmpty()) { showToast("No serial devices found"); return }
        currentDevice = devices[0]
        val device = currentDevice ?: return
        if (!usbManager.hasPermission(device)) {
            usbManager.requestPermission(device)
            showToast("USB permission requested")
            handler.postDelayed({ if (!isFinishing && !isConnected) connect() }, 1000)
            return
        }
        val baudRate = getSelectedBaudRate()
        serialPort = usbManager.openSerialPort(device, baudRate)
        if (serialPort == null) { showToast("Failed to open serial port"); return }
        isConnected = true
        serialPort!!.addListener(object : SerialPortManager.SerialPortListener {
            override fun onDataReceived(data: ByteArray) = runOnUiThread { appendOutput(String(data, Charsets.UTF_8)) }
            override fun onError(error: String) = runOnUiThread { showToast("Error: $error"); disconnect() }
        })
        serialPort!!.startReceiving()
        updateUiState()
        showToast("Connected to ${device.deviceName}")
        appendOutput("Connected to ${device.deviceName} at $baudRate baud\n")
    }

    private fun disconnect() {
        serialPort?.stopReceiving()
        serialPort?.close()
        serialPort = null
        currentDevice = null
        isConnected = false
        updateUiState()
        showToast("Disconnected")
        appendOutput("Disconnected\n")
    }

    private fun sendData() {
        val port = serialPort ?: run { showToast("Not connected"); return }
        val text = binding.inputField.text.toString()
        if (text.isBlank()) return
        val newline = when (binding.newlineSpinner.selectedItemPosition) {
            0 -> "\n"
            1 -> "\r"
            2 -> "\r\n"
            else -> ""
        }
        val data = (text + newline).toByteArray()
        if (port.write(data) > 0) {
            appendOutput(">> $text$newline")
            binding.inputField.text?.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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

    private fun getSelectedBaudRate() = baudRates[binding.baudRateSpinner.selectedItemPosition]
    private fun updateUiState() {
        binding.connectButton.text = if (isConnected) "Disconnect" else "Connect"
        binding.inputField.isEnabled = isConnected
        binding.sendButton.isEnabled = isConnected
    }
    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
    override fun onDestroy() { if (isConnected) disconnect(); super.onDestroy() }
}
