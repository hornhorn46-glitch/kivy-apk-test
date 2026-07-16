package com.autodoctor.aipro.transport.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import com.autodoctor.aipro.transport.AdapterKind
import com.autodoctor.aipro.transport.ObdAdapterDescriptor
import com.autodoctor.aipro.transport.ObdAdapterDiscovery

class BluetoothElmDiscovery(
    private val context: Context,
) : ObdAdapterDiscovery {
    override suspend fun discover(): List<ObdAdapterDescriptor> {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return emptyList()
        return adapter.bondedDevices.map { device ->
            ObdAdapterDescriptor(
                id = device.address,
                name = device.name ?: "Bluetooth OBD adapter",
                kind = AdapterKind.BluetoothElm327,
                address = device.address,
                signalQuality = null,
            )
        }.filter { descriptor ->
            val name = descriptor.name.lowercase()
            name.contains("obd") || name.contains("elm") || name.contains("vlink") || name.contains("car")
        }
    }
}
