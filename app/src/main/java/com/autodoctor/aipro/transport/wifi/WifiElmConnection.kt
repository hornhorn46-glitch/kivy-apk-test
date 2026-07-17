package com.autodoctor.aipro.transport.wifi

import com.autodoctor.aipro.core.obd.Elm327Connection
import com.autodoctor.aipro.core.obd.ElmCommand
import com.autodoctor.aipro.core.obd.ElmConnectionState
import com.autodoctor.aipro.core.obd.ElmResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class WifiElmConnection(
    private val host: String,
    private val port: Int,
    private val timeoutMillis: Int = 2_000,
) : Elm327Connection {
    private val mutableState = MutableStateFlow(ElmConnectionState.Disconnected)
    override val state: StateFlow<ElmConnectionState> = mutableState
    private var socket: Socket? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true && socket?.isClosed == false) {
            mutableState.value = ElmConnectionState.Ready
            return@withContext
        }
        mutableState.value = ElmConnectionState.Connecting
        socket = Socket().also {
            it.connect(InetSocketAddress(host, port), timeoutMillis)
            it.soTimeout = timeoutMillis
            it.tcpNoDelay = true
        }
        mutableState.value = ElmConnectionState.Ready
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        mutableState.value = ElmConnectionState.Disconnected
    }

    override suspend fun send(command: ElmCommand): ElmResponse = withContext(Dispatchers.IO) {
        val activeSocket = requireNotNull(socket) { "Wi-Fi ELM327 connection is not open" }
        val output = activeSocket.getOutputStream()
        val input = activeSocket.getInputStream()
        output.write((command.request.trim() + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val raw = readUntilPrompt(input)
        ElmResponse(raw = raw, lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() })
    }

    private fun readUntilPrompt(input: InputStream): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        return buildString {
            val buffer = ByteArray(1)
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    throw SocketTimeoutException("Wi-Fi ELM327 read timed out")
                }
                val read = input.read(buffer)
                if (read <= 0) break
                val char = buffer[0].toInt().toChar()
                if (char == '>') break
                append(char)
            }
        }
    }
}
