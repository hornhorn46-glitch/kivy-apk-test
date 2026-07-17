package com.autodoctor.aipro.transport.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.autodoctor.aipro.core.obd.Elm327Connection
import com.autodoctor.aipro.core.obd.ElmCommand
import com.autodoctor.aipro.core.obd.ElmConnectionState
import com.autodoctor.aipro.core.obd.ElmResponse
import com.autodoctor.aipro.core.obd.ObdPermissionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.UUID

class BluetoothElmConnection(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val deviceAddress: String,
    private val readTimeoutMillis: Long = 7_000L,
) : Elm327Connection {
    private val mutableState = MutableStateFlow(ElmConnectionState.Disconnected)
    override val state: StateFlow<ElmConnectionState> = mutableState
    private var socket: BluetoothSocket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true) {
            mutableState.value = ElmConnectionState.Ready
            return@withContext
        }
        if (!ObdPermissionPolicy.hasBluetoothConnectPermission(context)) {
            error("BLUETOOTH_CONNECT permission is required")
        }
        mutableState.value = ElmConnectionState.Connecting
        adapter.cancelDiscovery()
        val device = adapter.getRemoteDevice(deviceAddress)
        socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE).also { it.connect() }
        mutableState.value = ElmConnectionState.Ready
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        mutableState.value = ElmConnectionState.Disconnected
    }

    override suspend fun send(command: ElmCommand): ElmResponse = withContext(Dispatchers.IO) {
        val activeSocket = requireNotNull(socket) { "Bluetooth ELM327 connection is not open" }
        val output = activeSocket.outputStream
        val input = activeSocket.inputStream
        drainInput(input)
        output.write((command.request.trim() + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val raw = readUntilPrompt(input)
        ElmResponse(raw = raw, lines = raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() })
    }

    private fun drainInput(input: InputStream) {
        val buffer = ByteArray(256)
        while (input.available() > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, input.available().coerceAtLeast(1)))
            if (read <= 0) break
        }
    }

    private fun readUntilPrompt(input: InputStream): String {
        val deadline = System.currentTimeMillis() + readTimeoutMillis
        return buildString {
            val buffer = ByteArray(1)
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    throw SocketTimeoutException("Bluetooth ELM327 read timed out")
                }
                if (input.available() <= 0) {
                    Thread.sleep(8L)
                    continue
                }
                val read = input.read(buffer)
                if (read <= 0) break
                val char = buffer[0].toInt().toChar()
                if (char == '>') break
                append(char)
            }
        }
    }

    private companion object {
        val SERIAL_PORT_PROFILE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
