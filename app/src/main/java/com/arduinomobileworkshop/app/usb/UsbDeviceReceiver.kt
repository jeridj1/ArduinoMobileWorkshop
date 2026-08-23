package com.arduinomobileworkshop.app.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.arduinomobileworkshop.app.ArduinoMobileWorkshopApp
import com.arduinomobileworkshop.usb.UsbSerialManager

class UsbDeviceReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AMW_USB_Receiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> device?.let {
                ArduinoMobileWorkshopApp.instance.usbManager.onDeviceAttached(it)
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> device?.let {
                ArduinoMobileWorkshopApp.instance.usbManager.onDeviceDetached(it)
            }
            UsbSerialManager.ACTION_USB_PERMISSION -> if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                ArduinoMobileWorkshopApp.instance.usbManager.onPermissionGranted(device)
            } else {
                Log.w(TAG, "USB permission denied")
            }
        }
    }
}
