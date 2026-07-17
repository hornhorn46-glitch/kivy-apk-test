package com.autodoctor.aipro.core.obd

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import com.autodoctor.aipro.transport.AdapterKind
import com.autodoctor.aipro.transport.ObdAdapterDescriptor
import com.autodoctor.aipro.transport.bluetooth.BluetoothElmConnection
import com.autodoctor.aipro.transport.bluetooth.BluetoothElmDiscovery
import com.autodoctor.aipro.transport.usb.UsbBulkElmConnection
import com.autodoctor.aipro.transport.usb.UsbElmDiscovery
import com.autodoctor.aipro.transport.wifi.WifiElmConnection
import com.autodoctor.aipro.transport.wifi.WifiElmDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ObdConnectStatus {
    Disconnected,
    Searching,
    Connected,
    PermissionRequired,
    Failed,
}

data class ObdConnectResult(
    val status: ObdConnectStatus,
    val message: String,
    val adapter: ObdAdapterDescriptor? = null,
    val connection: Elm327Connection? = null,
)

class ObdConnectionManager(
    private val context: Context,
) {
    suspend fun discoverAll(): List<ObdAdapterDescriptor> = withContext(Dispatchers.IO) {
        val bluetooth = if (hasBluetoothPermission()) BluetoothElmDiscovery(context).discover() else emptyList()
        bluetooth +
            WifiElmDiscovery(context).discover() +
            UsbElmDiscovery(context).discover()
    }.distinctBy { it.id }

    suspend fun connectFirstReady(): ObdConnectResult {
        val bluetoothPermission = hasBluetoothPermission()
        val adapters = discoverAll().sortedBy { adapter ->
            when (adapter.kind) {
                AdapterKind.WifiElm327 -> 0
                AdapterKind.BluetoothElm327 -> 1
                AdapterKind.UsbElm327 -> 2
            }
        }
        if (adapters.isEmpty()) {
            if (!bluetoothPermission) {
                return ObdConnectResult(
                    status = ObdConnectStatus.PermissionRequired,
                    message = "Нужны разрешения Bluetooth для поиска paired ELM327. Wi-Fi/USB адаптер сейчас не найден.",
                )
            }
            return ObdConnectResult(
                status = ObdConnectStatus.Failed,
                message = "OBD-II адаптер не найден. Включите зажигание, подключите ELM327 и проверьте Bluetooth/Wi-Fi/USB.",
            )
        }

        val errors = mutableListOf<String>()
        for (adapter in adapters) {
            val connection = createConnection(adapter) ?: continue
            val result = runCatching {
                connection.open()
                RobustElm327Session(connection).initialize()
                ObdConnectResult(
                    status = ObdConnectStatus.Connected,
                    message = "Подключено: ${adapter.name}. ELM327 отвечает и готов к чтению live data.",
                    adapter = adapter,
                    connection = connection,
                )
            }.getOrElse { error ->
                runCatching { connection.close() }
                errors += "${adapter.name}: ${error.message ?: error::class.java.simpleName}"
                null
            }
            if (result != null) return result
        }

        return ObdConnectResult(
            status = ObdConnectStatus.Failed,
            message = "Адаптеры найдены, но ELM327 не ответил корректно: ${errors.take(3).joinToString("; ")}",
            adapter = adapters.firstOrNull(),
        )
    }

    private fun createConnection(adapter: ObdAdapterDescriptor): Elm327Connection? {
        return when (adapter.kind) {
            AdapterKind.WifiElm327 -> {
                val parts = adapter.address.split(":")
                val host = parts.getOrNull(0) ?: return null
                val port = parts.getOrNull(1)?.toIntOrNull() ?: return null
                WifiElmConnection(host, port)
            }
            AdapterKind.BluetoothElm327 -> {
                val manager = context.getSystemService(BluetoothManager::class.java)
                val bluetoothAdapter = manager?.adapter ?: return null
                BluetoothElmConnection(context, bluetoothAdapter, adapter.address)
            }
            AdapterKind.UsbElm327 -> {
                val manager = context.getSystemService(UsbManager::class.java) ?: return null
                val device = manager.deviceList.values.firstOrNull { device ->
                    adapter.id == "${device.vendorId}:${device.productId}:${device.deviceId}"
                } ?: return null
                UsbBulkElmConnection(context, device)
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
}
