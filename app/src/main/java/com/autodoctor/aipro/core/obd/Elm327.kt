package com.autodoctor.aipro.core.obd

import kotlinx.coroutines.flow.Flow

data class ElmCommand(
    val request: String,
    val description: String,
)

data class ElmResponse(
    val raw: String,
    val lines: List<String>,
)

interface Elm327Connection {
    val state: Flow<ElmConnectionState>
    suspend fun open()
    suspend fun close()
    suspend fun send(command: ElmCommand): ElmResponse
}

enum class ElmConnectionState {
    Disconnected,
    Connecting,
    Initializing,
    Ready,
    Failed,
}

object Elm327Commands {
    val Reset = ElmCommand("ATZ", "Reset adapter")
    val EchoOff = ElmCommand("ATE0", "Disable command echo")
    val LinefeedsOff = ElmCommand("ATL0", "Disable line feeds")
    val SpacesOff = ElmCommand("ATS0", "Disable spaces")
    val HeadersOff = ElmCommand("ATH0", "Disable headers")
    val AdaptiveTimingAuto = ElmCommand("ATAT1", "Enable adaptive timing")
    val ProtocolAuto = ElmCommand("ATSP0", "Automatic protocol search")
    val DescribeProtocol = ElmCommand("ATDP", "Describe current protocol")

    fun service01(pidHex: String): ElmCommand =
        ElmCommand("01$pidHex", "Read current data PID $pidHex")

    fun service03(): ElmCommand =
        ElmCommand("03", "Read stored diagnostic trouble codes")
}

class Elm327Initializer {
    suspend fun initialize(connection: Elm327Connection): ElmResponse {
        connection.open()
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
            last = connection.send(command)
        }
        return last
    }
}
