package com.autodoctor.aipro.transport

enum class AdapterKind {
    BluetoothElm327,
    WifiElm327,
    UsbElm327,
}

data class ObdAdapterDescriptor(
    val id: String,
    val name: String,
    val kind: AdapterKind,
    val address: String,
    val signalQuality: Int?,
)

interface ObdAdapterDiscovery {
    suspend fun discover(): List<ObdAdapterDescriptor>
}
