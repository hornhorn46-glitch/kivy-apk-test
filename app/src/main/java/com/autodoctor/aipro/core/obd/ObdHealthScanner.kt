package com.autodoctor.aipro.core.obd

import com.autodoctor.aipro.core.model.DiagnosticTroubleCode
import com.autodoctor.aipro.core.model.DtcStatus
import com.autodoctor.aipro.core.model.FreezeFrame
import com.autodoctor.aipro.core.model.PidDefinition
import com.autodoctor.aipro.core.model.PidSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SensorCoverageReport(
    val requiredPidIds: List<String>,
    val supportedPidIds: List<String>,
    val missingPidIds: List<String>,
    val coverage: Double,
)

data class ObdHealthScan(
    val confirmedDtcs: List<DiagnosticTroubleCode>,
    val pendingDtcs: List<DiagnosticTroubleCode>,
    val permanentDtcs: List<DiagnosticTroubleCode>,
    val freezeFrames: List<FreezeFrame>,
    val mode06Monitors: List<Mode06Monitor>,
    val coverage: SensorCoverageReport,
    val warnings: List<String>,
) {
    val allDtcs: List<DiagnosticTroubleCode>
        get() = confirmedDtcs + pendingDtcs + permanentDtcs
}

class ObdHealthScanner(
    private val parser: ElmResponseParser = ElmResponseParser(),
    private val decoder: PidDecoder = PidDecoder(),
) {
    suspend fun scan(connection: Elm327Connection, pids: List<PidDefinition>): ObdHealthScan = withContext(Dispatchers.IO) {
        val session = RobustElm327Session(connection)
        val warnings = mutableListOf<String>()
        runCatching { session.initialize() }.onFailure { warnings += "ELM init warning: ${it.message}" }

        val supportedPidHex = discoverSupportedPids(session, warnings)
        val selectedPids = PidSelection.engineTest(pids).ifEmpty { pids.filter { it.service == "01" }.take(12) }
        val coverage = coverage(selectedPids, supportedPidHex)

        val confirmed = readDtcs(session, Elm327Commands.service03(), 0x43, DtcStatus.Confirmed, warnings)
        val pending = readDtcs(session, Elm327Commands.service07(), 0x47, DtcStatus.Pending, warnings)
        val permanent = readDtcs(session, Elm327Commands.service0A(), 0x4A, DtcStatus.Permanent, warnings)
        val freezeFrames = readFreezeFrame(session, selectedPids, warnings)
        val mode06 = readMode06(session, warnings)

        ObdHealthScan(
            confirmedDtcs = confirmed,
            pendingDtcs = pending,
            permanentDtcs = permanent,
            freezeFrames = freezeFrames,
            mode06Monitors = mode06,
            coverage = coverage,
            warnings = warnings,
        )
    }

    private suspend fun discoverSupportedPids(
        session: RobustElm327Session,
        warnings: MutableList<String>,
    ): Set<String> {
        val bases = listOf("00", "20", "40", "60", "80", "A0", "C0")
        val supported = mutableSetOf<String>()
        for (base in bases) {
            val response = try {
                session.sendWithRetry(Elm327Commands.service01(base))
            } catch (error: Throwable) {
                warnings += "Supported PID $base not available: ${error.message}"
                break
            }
            val chunk = parser.parseSupportedService01Pids(response, base)
            supported += chunk
            if ((base.toInt(16) + 0x20).toString(16).uppercase().padStart(2, '0') !in chunk) break
        }
        return supported
    }

    private fun coverage(selectedPids: List<PidDefinition>, supportedPidHex: Set<String>): SensorCoverageReport {
        val required = selectedPids.map { it.id }
        val supportedIds = selectedPids.filter { it.pid.uppercase() in supportedPidHex }.map { it.id }
        val missing = required - supportedIds.toSet()
        val value = supportedIds.size.toDouble() / required.size.coerceAtLeast(1)
        return SensorCoverageReport(
            requiredPidIds = required,
            supportedPidIds = supportedIds,
            missingPidIds = missing,
            coverage = value,
        )
    }

    private suspend fun readDtcs(
        session: RobustElm327Session,
        command: ElmCommand,
        responseMode: Int,
        status: DtcStatus,
        warnings: MutableList<String>,
    ): List<DiagnosticTroubleCode> {
        return runCatching {
            val response = session.sendWithRetry(command, validateObdPayload = false)
            parser.parseDtcCodes(response, responseMode).map { code ->
                DiagnosticTroubleCode(code = code, description = genericDtcDescription(code), status = status)
            }
        }.getOrElse {
            warnings += "${command.description} unavailable: ${it.message}"
            emptyList()
        }
    }

    private suspend fun readFreezeFrame(
        session: RobustElm327Session,
        selectedPids: List<PidDefinition>,
        warnings: MutableList<String>,
    ): List<FreezeFrame> {
        val trigger = runCatching {
            val response = session.sendWithRetry(Elm327Commands.service02("02"), validateObdPayload = false)
            parser.parseFreezeFrameTriggerDtc(response)
        }.getOrNull() ?: return emptyList()

        val samples = selectedPids.mapNotNull { pid ->
            runCatching {
                val response = session.sendWithRetry(Elm327Commands.service02(pid.pid), validateObdPayload = false)
                val bytes = parser.parseService02Bytes(pid.pid, response)
                val value = decoder.decodeService01(pid.pid, bytes) ?: return@runCatching null
                PidSample(pid = pid.id, value = value, unit = pid.unit, timestampMillis = System.currentTimeMillis())
            }.getOrNull()
        }
        if (samples.isEmpty()) warnings += "Freeze-frame trigger exists but no decodable freeze-frame PIDs were returned"
        return listOf(FreezeFrame(triggerCode = trigger, samples = samples))
    }

    private suspend fun readMode06(
        session: RobustElm327Session,
        warnings: MutableList<String>,
    ): List<Mode06Monitor> {
        return runCatching {
            val response = session.sendWithRetry(Elm327Commands.service06(), validateObdPayload = false)
            parser.parseMode06Monitors(response)
        }.getOrElse {
            warnings += "Mode 06 unavailable: ${it.message}"
            emptyList()
        }
    }

    private fun genericDtcDescription(code: String): String {
        return when {
            code.startsWith("P030") -> "Misfire detected"
            code in setOf("P0171", "P0174") -> "System too lean"
            code in setOf("P0172", "P0175") -> "System too rich"
            code == "P0420" || code == "P0430" -> "Catalyst system efficiency below threshold"
            code.startsWith("P01") -> "Fuel and air metering related DTC"
            code.startsWith("P02") -> "Fuel/air/injector related DTC"
            code.startsWith("P03") -> "Ignition or misfire related DTC"
            code.startsWith("P04") -> "Auxiliary emissions control related DTC"
            else -> "Diagnostic trouble code"
        }
    }
}
