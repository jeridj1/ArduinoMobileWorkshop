package com.arduinomobileworkshop.app.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp

/**
 * Listens for USB_DEVICE_ATTACHED / USB_DEVICE_DETACHED system events,
 * verifies the hardware vendor ID against the supported microcontroller
 * device profile, and hands the connection context off to the application's
 * central USB execution loop ([com.arduinomobileworkshop.usb.UsbManager]).
 *
 * The receiver is declared (with a device_filter resource) in the app
 * AndroidManifest so the system delivers attach/detach broadcasts for the
 * supported boards.
 */
class UsbDeviceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AMW_USB_Receiver"

        /**
         * Supported hobbyist microcontroller vendor IDs (decimal).
         * Kept in sync with res/xml/device_filter.xml.
         */
        val SUPPORTED_VENDOR_IDS: Set<Int> = setOf(
            0x2341, // 9025  - Official Arduino
            0x2A03, // 10755 - Arduino.org
            0x10C4, // 4292  - Silicon Labs CP210x
            0x1A86, // 6790  - Qinheng CH340 / CH341 clones
            0x2E8A, // 11914 - Raspberry Pi RP2040 (Pico)
            0x0403, // 1027  - FTDI FT232
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                extractDevice(intent)?.let { onDeviceAttached(context, it) }
            UsbManager.ACTION_USB_DEVICE_DETACHED ->
                extractDevice(intent)?.let { onDeviceDetached(context, it) }
        }
    }

    private fun onDeviceAttached(context: Context, device: UsbDevice) {
        Log.d(
            TAG,
            "Device attached: ${device.deviceName} vid=${device.vendorId} pid=${device.productId}"
        )
        if (!matchesDeviceProfile(device)) {
            Log.d(TAG, "Ignoring unsupported device (vid=${device.vendorId})")
            return
        }
        // Pass the connection context directly to the main USB execution loop.
        val usbManager = ArduinoMobileWorkshopApp.instance.usbManager
        usbManager.onDeviceAttached(device)
        // Proactively request USB permission for the recognised serial device.
        if (!usbManager.hasPermission(device)) {
            usbManager.requestPermission(device)
        }
    }

    private fun onDeviceDetached(context: Context, device: UsbDevice) {
        Log.d(TAG, "Device detached: ${device.deviceName}")
        ArduinoMobileWorkshopApp.instance.usbManager.onDeviceDetached(device)
    }

    private fun matchesDeviceProfile(device: UsbDevice): Boolean =
        SUPPORTED_VENDOR_IDS.contains(device.vendorId)

    private fun extractDevice(intent: Intent): UsbDevice? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }
}
