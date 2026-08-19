package com.arduinomobileworkshop.app.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.app.databinding.ActivitySerialMonitorBinding
import com.arduinomobileworkshop.usb.UsbManager

class SerialMonitorActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySerialMonitorBinding
    private val usbManager: UsbManager
        get() = ArduinoMobileWorkshopApp.instance.usbManager
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
        usbManager.scanForDevices()
        val deviceInfo = usbManager.getAvailableDevices().firstOrNull { it.driver != null } ?: run {
            showToast("No serial devices found")
            return
        }
        currentDevice = deviceInfo.device
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
        startReadPolling()
        showToast("Connected")
    }

    private fun disconnect() {
        currentDevice?.let { usbManager.disconnectFromDevice(it) }
        currentDevice = null
        isConnected = false
        handler.removeCallbacksAndMessages(null)
        updateUiState()
        appendOutput("Disconnected\n")
    }

    private fun startReadPolling() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isConnected) return
                val buffer = ByteArray(4096)
                val count = usbManager.readData(buffer, 25)
                if (count > 0) appendOutput(String(buffer, 0, count, Charsets.UTF_8))
                handler.postDelayed(this, 50)
            }
        })
    }

    private fun sendData() {
        if (!isConnected) { showToast("Not connected"); return }
        val text = binding.inputField.text.toString()
        if (text.isBlank()) return
        val newline = when (binding.newlineSpinner.selectedItemPosition) {
            0 -> "\n"
            1 -> "\r"
            2 -> "\r\n"
            else -> ""
        }
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}