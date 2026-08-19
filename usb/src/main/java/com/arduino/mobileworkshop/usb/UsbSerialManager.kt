package com.arduino.mobileworkshop.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class UsbSerialManager(context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    fun findSerialPort(device: UsbDevice): UsbSerialPort? {
        val driver: UsbSerialDriver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: return null
        return driver.ports.firstOrNull()
    }

    fun findFirstSerialPort(): UsbSerialPort? {
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull()
            ?.ports
            ?.firstOrNull()
    }
}
