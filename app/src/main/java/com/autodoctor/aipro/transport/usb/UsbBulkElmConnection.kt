package com.autodoctor.aipro.transport.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.autodoctor.aipro.core.obd.Elm327Connection
import com.autodoctor.aipro.core.obd.ElmCommand
import com.autodoctor.aipro.core.obd.ElmConnectionState
import com.autodoctor.aipro.core.obd.ElmResponse
import com.autodoctor.aipro.core.obd.ElmTraceEvent
import com.autodoctor.aipro.core.obd.ObdTraceLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class UsbBulkElmConnection(
    private val context: Context,
    private val device: UsbDevice,
    private val timeoutMillis: Int = 5_000,
) : Elm327Connection {
    private val mutableState = MutableStateFlow(ElmConnectionState.Disconnected)
    override val state: StateFlow<ElmConnectionState> = mutableState
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inputEndpoint: UsbEndpoint? = null
    private var outputEndpoint: UsbEndpoint? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        if (connection != null && inputEndpoint != null && outputEndpoint != null) {
            mutableState.value = ElmConnectionState.Ready
            return@withContext
        }
        mutableState.value = ElmConnectionState.Connecting
        val manager = context.getSystemService(UsbManager::class.java) ?: error("USB manager is not available")
        val opened = manager.openDevice(device) ?: error("USB permission is required for ${device.deviceName}")
        val selected = findBulkInterface(device) ?: error("No USB bulk interface found")
        if (!opened.claimInterface(selected.usbInterface, true)) {
            opened.close()
            error("Unable to claim USB interface")
        }
        connection = opened
        usbInterface = selected.usbInterface
        inputEndpoint = selected.input
        outputEndpoint = selected.output
        mutableState.value = ElmConnectionState.Ready
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        val opened = connection
        val claimed = usbInterface
        if (opened != null && claimed != null) {
            opened.releaseInterface(claimed)
        }
        opened?.close()
        connection = null
        usbInterface = null
        inputEndpoint = null
        outputEndpoint = null
        mutableState.value = ElmConnectionState.Disconnected
    }

    override suspend fun send(command: ElmCommand): ElmResponse = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val opened = requireNotNull(connection) { "USB ELM327 connection is not open" }
        val out = requireNotNull(outputEndpoint) { "USB output endpoint is missing" }
        val input = requireNotNull(inputEndpoint) { "USB input endpoint is missing" }
        try {
            drainInput(opened, input)
            val request = (command.request.trim() + "\r").toByteArray(Charsets.US_ASCII)
            val written = opened.bulkTransfer(out, request, request.size, timeoutMillis)
            require(written == request.size) { "USB write failed: $written/${request.size}" }

            val raw = buildString {
                val buffer = ByteArray(64)
                while (true) {
                    val read = opened.bulkTransfer(input, buffer, buffer.size, timeoutMillis)
                    if (read <= 0) break
                    val chunk = buffer.decodeToString(endIndex = read)
                    val prompt = chunk.indexOf('>')
                    if (prompt >= 0) {
                        append(chunk.substring(0, prompt))
                        break
                    }
                    append(chunk)
                }
            }
            ObdTraceLog.record(
                ElmTraceEvent(
                    timestampMillis = started,
                    transport = "usb",
                    command = command.request,
                    description = command.description,
                    rawResponse = raw,
                    durationMillis = System.currentTimeMillis() - started,
                ),
            )
            ElmResponse(raw = raw, lines = raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() })
        } catch (error: Throwable) {
            ObdTraceLog.record(
                ElmTraceEvent(
                    timestampMillis = started,
                    transport = "usb",
                    command = command.request,
                    description = command.description,
                    rawResponse = "",
                    durationMillis = System.currentTimeMillis() - started,
                    error = error.message ?: error::class.java.simpleName,
                ),
            )
            throw error
        }
    }

    private fun drainInput(opened: UsbDeviceConnection, input: UsbEndpoint) {
        val buffer = ByteArray(64)
        repeat(4) {
            val read = opened.bulkTransfer(input, buffer, buffer.size, 20)
            if (read <= 0) return
        }
    }

    private fun findBulkInterface(device: UsbDevice): BulkInterface? {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            var input: UsbEndpoint? = null
            var output: UsbEndpoint? = null
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) input = endpoint
                    if (endpoint.direction == UsbConstants.USB_DIR_OUT) output = endpoint
                }
            }
            if (input != null && output != null) {
                return BulkInterface(usbInterface, input, output)
            }
        }
        return null
    }
}

private data class BulkInterface(
    val usbInterface: UsbInterface,
    val input: UsbEndpoint,
    val output: UsbEndpoint,
)
