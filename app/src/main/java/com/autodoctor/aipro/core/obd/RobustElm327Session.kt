package com.autodoctor.aipro.core.obd

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException

class RobustElm327Session(
    private val connection: Elm327Connection,
    private val timeoutMillis: Long = 2_800L,
    private val retryCount: Int = 3,
    private val retryDelayMillis: Long = 120L,
) {
    private var initialized = false
    private var initializing = false
    private var lastKeepAliveMillis = 0L

    suspend fun initialize(): ElmResponse {
        connection.open()
        initialized = false
        initializing = true
        try {
            val commands = listOf(
                Elm327Commands.Reset,
                Elm327Commands.EchoOff,
                Elm327Commands.LinefeedsOff,
                Elm327Commands.SpacesOff,
                Elm327Commands.HeadersOff,
                Elm327Commands.AdaptiveTimingAuto,
                Elm327Commands.ProtocolAuto,
            )
            var last = ElmResponse("", emptyList())
            for (command in commands) {
                last = sendWithRetry(command, validateObdPayload = false)
            }
            initialized = true
            return last
        } finally {
            initializing = false
        }
    }

    suspend fun sendWithRetry(
        command: ElmCommand,
        validateObdPayload: Boolean = true,
    ): ElmResponse {
        var lastError: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                ensureOpen()
                val response = withTimeout(timeoutMillis) {
                    connection.send(command)
                }
                if (validateObdPayload) validate(response)
                return response
            } catch (error: Throwable) {
                lastError = error
                recover(attempt)
            }
        }
        throw IOException("ELM327 command ${command.request} failed after $retryCount attempts", lastError)
    }

    suspend fun keepAliveIfNeeded(periodMillis: Long = 3_000L) {
        val now = System.currentTimeMillis()
        if (now - lastKeepAliveMillis >= periodMillis) {
            sendWithRetry(Elm327Commands.DescribeProtocol, validateObdPayload = false)
            lastKeepAliveMillis = now
        }
    }

    suspend fun close() {
        initialized = false
        connection.close()
    }

    private suspend fun ensureOpen() {
        if (!initialized && !initializing) {
            initialize()
        }
    }

    private suspend fun recover(attempt: Int) {
        runCatching { connection.close() }
        initialized = false
        if (attempt < retryCount - 1) {
            delay(retryDelayMillis * (attempt + 1))
            if (initializing) {
                runCatching { connection.open() }
            }
        }
    }

    private fun validate(response: ElmResponse) {
        val text = response.raw.uppercase()
        val invalid = listOf(
            "NO DATA",
            "STOPPED",
            "UNABLE TO CONNECT",
            "BUS INIT",
            "CAN ERROR",
            "ERROR",
            "?",
        )
        if (invalid.any { it in text }) {
            throw IOException("ELM327 returned invalid payload: ${response.raw.trim()}")
        }
    }
}
