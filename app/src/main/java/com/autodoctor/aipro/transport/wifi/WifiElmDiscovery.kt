package com.autodoctor.aipro.transport.wifi

import android.content.Context
import android.net.wifi.WifiManager
import com.autodoctor.aipro.transport.AdapterKind
import com.autodoctor.aipro.transport.ObdAdapterDescriptor
import com.autodoctor.aipro.transport.ObdAdapterDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class WifiElmDiscovery(
    private val context: Context,
) : ObdAdapterDiscovery {
    override suspend fun discover(): List<ObdAdapterDescriptor> {
        return withContext(Dispatchers.IO) {
            candidateAddresses()
                .distinct()
                .filter { isOpen(it.host, it.port) }
                .map { endpoint ->
                    ObdAdapterDescriptor(
                        id = "wifi-${endpoint.host}:${endpoint.port}",
                        name = "Wi-Fi ELM327 ${endpoint.host}:${endpoint.port}",
                        kind = AdapterKind.WifiElm327,
                        address = "${endpoint.host}:${endpoint.port}",
                        signalQuality = null,
                    )
                }
        }
    }

    private fun candidateAddresses(): List<Endpoint> {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        val gateway = wifiManager?.dhcpInfo?.gateway?.takeIf { it != 0 }?.let { intToIp(it) }
        val hosts = listOfNotNull(gateway, "192.168.0.10", "192.168.0.1", "192.168.4.1")
        val ports = listOf(35000, 23)
        return hosts.flatMap { host -> ports.map { port -> Endpoint(host, port) } }
    }

    private fun isOpen(host: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 450)
            }
        }.isSuccess
    }

    private fun intToIp(value: Int): String {
        return listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff,
        ).joinToString(".")
    }
}

private data class Endpoint(val host: String, val port: Int)
