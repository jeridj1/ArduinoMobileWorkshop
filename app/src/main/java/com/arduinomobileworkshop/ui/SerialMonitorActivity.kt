package com.arduinomobileworkshop.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arduinomobileworkshop.R
import com.arduinomobileworkshop.usb.UsbSerialManager

class SerialMonitorActivity : AppCompatActivity() {
    private lateinit var usbSerialManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serial_monitor)
        usbSerialManager = UsbSerialManager(this)
    }

    override fun onDestroy() {
        usbSerialManager.closeConnection()
        super.onDestroy()
    }
}
