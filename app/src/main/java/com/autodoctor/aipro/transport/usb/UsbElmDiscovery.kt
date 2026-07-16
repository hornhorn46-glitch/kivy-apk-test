package com.autodoctor.aipro.transport.usb

import android.content.Context
import android.hardware.usb.UsbManager
import com.autodoctor.aipro.transport.AdapterKind
import com.autodoctor.aipro.transport.ObdAdapterDescriptor
import com.autodoctor.aipro.transport.ObdAdapterDiscovery

class UsbElmDiscovery(
    private val context: Context,
) : ObdAdapterDiscovery {
    override suspend fun discover(): List<ObdAdapterDescriptor> {
        val manager = context.getSystemService(UsbManager::class.java) ?: return emptyList()
        return manager.deviceList.values.map { device ->
            ObdAdapterDescriptor(
                id = "${device.vendorId}:${device.productId}:${device.deviceId}",
                name = device.productName ?: "USB ELM327 adapter",
                kind = AdapterKind.UsbElm327,
                address = "${device.vendorId}:${device.productId}",
                signalQuality = null,
            )
        }
    }
}
