package com.autodoctor.aipro.core.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.autodoctor.aipro.core.ai.DriveabilityAnalysis
import com.autodoctor.aipro.core.model.PidSample
import com.autodoctor.aipro.core.model.VehicleProfile
import com.autodoctor.aipro.core.obd.ElmTraceEvent
import com.autodoctor.aipro.core.obd.Mode06Monitor
import com.autodoctor.aipro.core.obd.ObdConnectResult
import com.autodoctor.aipro.core.obd.ObdHealthScan
import com.autodoctor.aipro.core.obd.ObdTraceLog
import com.autodoctor.aipro.core.performance.CombinedPowerEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

@Serializable
enum class CloudDiagnosticMode {
    ConnectionTroubleshooting,
    DriveabilityAnalysis,
}

class OpenAiDiagnosticAssistant(
    private val context: Context,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    },
) {
    suspend fun analyze(
        apiKey: String,
        model: String,
        mode: CloudDiagnosticMode,
        profile: VehicleProfile?,
        connection: ObdConnectResult,
        healthScan: ObdHealthScan?,
        liveSamples: List<PidSample>,
        recordedSamples: List<PidSample>,
        localAnalysis: DriveabilityAnalysis?,
        power: CombinedPowerEstimate?,
    ): String = withContext(Dispatchers.IO) {
        val report = CloudDiagnosticReport.create(
            mode = mode,
            profile = profile,
            connection = connection,
            healthScan = healthScan,
            liveSamples = liveSamples,
            recordedSamples = recordedSamples,
            localAnalysis = localAnalysis,
            power = power,
            trace = ObdTraceLog.snapshot(),
        )
        val prompt = buildPrompt(report)
        val internetNetwork = selectValidatedInternetNetwork()
        val firstAttempt = runCatching {
            createResponse(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                internetNetwork = internetNetwork,
                enableWebSearch = true,
            )
        }
        firstAttempt.getOrElse { firstError ->
            createResponse(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                internetNetwork = internetNetwork,
                enableWebSearch = false,
                previousError = firstError.message,
            )
        }
    }

    private fun buildPrompt(report: CloudDiagnosticReport): String {
        val modeText = when (report.mode) {
            CloudDiagnosticMode.ConnectionTroubleshooting -> "Сфокусируйся на том, почему ELM327 подключен, но ECU/PID/live data читаются плохо или не читаются."
            CloudDiagnosticMode.DriveabilityAnalysis -> "Сфокусируйся на причинах потери тяги, неправильной смеси, пропусков, зажигания, датчиков и ограничений по данным."
        }
        return """
            Ты инженер-диагност OBD-II. Отвечай по-русски, как опытный автоэлектрик-диагност.
            $modeText
            
            Правила:
            1. Не делай вывод по одному параметру.
            2. Если данных мало, прямо напиши "недостаточно данных" и перечисли, что надо дочитать.
            3. Для каждой гипотезы дай вероятность, физическое объяснение, подтверждающие признаки, что против гипотезы и следующие проверки.
            4. Если проблема в подключении, разложи по слоям: телефон/сеть, TCP ELM, AT init, протокол ECU, PID support, зажигание/мотор, совместимость ELM.
            5. Не советуй опасные дорожные действия. Если нужен тест, формулируй безопасно и коротко.
            6. Используй интернет-поиск только для уточнения OBD/ELM/модельных особенностей, не вместо анализа предоставленных данных.
            
            Диагностический пакет JSON:
            ${json.encodeToString(report)}
        """.trimIndent()
    }

    private fun createResponse(
        apiKey: String,
        model: String,
        prompt: String,
        internetNetwork: Network?,
        enableWebSearch: Boolean,
        previousError: String? = null,
    ): String {
        if (apiKey.isBlank()) throw IOException("OpenAI API key is empty")
        val request = buildResponsesRequest(
            model = model.ifBlank { CloudAiSettingsStore.DefaultModel },
            prompt = if (previousError == null) prompt else "$prompt\n\nПервый запрос с web_search не прошёл: $previousError\nПовтори анализ без web_search.",
            enableWebSearch = enableWebSearch,
        )
        val url = URL("https://api.openai.com/v1/responses")
        val connection = ((internetNetwork?.openConnection(url) ?: url.openConnection()) as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        val bytes = request.toString().toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(bytes) }
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IOException("OpenAI API HTTP ${connection.responseCode}: ${errorBody.take(1_200)}")
        }
        return extractOutputText(body)
    }

    private fun buildResponsesRequest(
        model: String,
        prompt: String,
        enableWebSearch: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("store", false)
        put(
            "instructions",
            "You are a senior automotive OBD-II diagnostician. Give cautious, evidence-based analysis in Russian.",
        )
        if (enableWebSearch) {
            putJsonArray("tools") {
                add(buildJsonObject { put("type", "web_search") })
            }
        }
        putJsonArray("input") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(
                            buildJsonObject {
                                put("type", "input_text")
                                put("text", prompt)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun extractOutputText(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        root["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        val texts = root["output"]?.jsonArray.orEmpty()
            .flatMap { outputItem ->
                outputItem.jsonObject["content"]?.jsonArray.orEmpty().mapNotNull { content ->
                    val contentObject = content.jsonObject
                    contentObject["text"]?.jsonPrimitive?.contentOrNull
                        ?: contentObject["value"]?.jsonPrimitive?.contentOrNull
                }
            }
            .filter { it.isNotBlank() }
        return texts.joinToString("\n\n").ifBlank { body.take(4_000) }
    }

    @Suppress("DEPRECATION")
    private fun selectValidatedInternetNetwork(): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return manager.allNetworks.firstOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}

@Serializable
private data class CloudDiagnosticReport(
    val schema: String = "autodoctor.cloud.diagnostic.v1",
    val generatedAtMillis: Long,
    val mode: CloudDiagnosticMode,
    val app: CloudAppInfo,
    val vehicle: CloudVehicleProfile?,
    val connection: CloudConnection,
    val health: CloudHealth?,
    val liveData: CloudLiveData,
    val localAnalysis: CloudLocalAnalysis?,
    val rawElmTrace: List<ElmTraceEvent>,
) {
    companion object {
        fun create(
            mode: CloudDiagnosticMode,
            profile: VehicleProfile?,
            connection: ObdConnectResult,
            healthScan: ObdHealthScan?,
            liveSamples: List<PidSample>,
            recordedSamples: List<PidSample>,
            localAnalysis: DriveabilityAnalysis?,
            power: CombinedPowerEstimate?,
            trace: List<ElmTraceEvent>,
        ): CloudDiagnosticReport {
            val allSamples = (recordedSamples.ifEmpty { liveSamples }).takeLast(1_200)
            return CloudDiagnosticReport(
                generatedAtMillis = System.currentTimeMillis(),
                mode = mode,
                app = CloudAppInfo(version = "0.1.0", privacy = "user initiated; OpenAI store=false"),
                vehicle = profile?.let(CloudVehicleProfile::from),
                connection = CloudConnection(
                    status = connection.status.name,
                    message = connection.message,
                    adapterKind = connection.adapter?.kind?.name,
                    adapterName = connection.adapter?.name,
                    adapterAddress = connection.adapter?.address,
                ),
                health = healthScan?.let(CloudHealth::from),
                liveData = CloudLiveData.from(allSamples),
                localAnalysis = CloudLocalAnalysis.from(localAnalysis, power),
                rawElmTrace = trace.takeLast(80),
            )
        }
    }
}

@Serializable
private data class CloudAppInfo(
    val version: String,
    val privacy: String,
)

@Serializable
private data class CloudVehicleProfile(
    val id: String,
    val make: String,
    val model: String,
    val year: Int?,
    val engineCode: String?,
    val displacementLiters: Double?,
    val powerKw: Double?,
    val torqueNm: Double?,
    val massKg: Double?,
    val induction: String,
    val injection: String,
    val airMetering: String,
    val transmission: String,
    val obdType: String,
) {
    companion object {
        fun from(profile: VehicleProfile): CloudVehicleProfile = CloudVehicleProfile(
            id = profile.id,
            make = profile.make,
            model = profile.model,
            year = profile.year,
            engineCode = profile.engineCode,
            displacementLiters = profile.displacementLiters,
            powerKw = profile.powerKw,
            torqueNm = profile.torqueNm,
            massKg = profile.massKg,
            induction = profile.induction.name,
            injection = profile.injection.name,
            airMetering = profile.airMetering.name,
            transmission = profile.transmission.name,
            obdType = profile.obdType.name,
        )
    }
}

@Serializable
private data class CloudConnection(
    val status: String,
    val message: String,
    val adapterKind: String?,
    val adapterName: String?,
    val adapterAddress: String?,
)

@Serializable
private data class CloudHealth(
    val confirmedDtcs: List<String>,
    val pendingDtcs: List<String>,
    val permanentDtcs: List<String>,
    val freezeFrameCount: Int,
    val mode06Monitors: List<CloudMode06Monitor>,
    val sensorCoveragePercent: Int,
    val supportedPidIds: List<String>,
    val missingPidIds: List<String>,
    val warnings: List<String>,
) {
    companion object {
        fun from(scan: ObdHealthScan): CloudHealth = CloudHealth(
            confirmedDtcs = scan.confirmedDtcs.map { "${it.code} ${it.description}" },
            pendingDtcs = scan.pendingDtcs.map { "${it.code} ${it.description}" },
            permanentDtcs = scan.permanentDtcs.map { "${it.code} ${it.description}" },
            freezeFrameCount = scan.freezeFrames.size,
            mode06Monitors = scan.mode06Monitors.take(30).map(CloudMode06Monitor::from),
            sensorCoveragePercent = (scan.coverage.coverage * 100.0).roundToInt(),
            supportedPidIds = scan.coverage.supportedPidIds,
            missingPidIds = scan.coverage.missingPidIds,
            warnings = scan.warnings.takeLast(12),
        )
    }
}

@Serializable
private data class CloudMode06Monitor(
    val tid: String,
    val cid: String?,
    val rawHex: String,
) {
    companion object {
        fun from(monitor: Mode06Monitor): CloudMode06Monitor = CloudMode06Monitor(
            tid = monitor.tid,
            cid = monitor.cid,
            rawHex = monitor.rawHex,
        )
    }
}

@Serializable
private data class CloudLiveData(
    val sampleCount: Int,
    val durationSeconds: Double,
    val latestValues: Map<String, Double>,
    val stats: List<CloudPidStats>,
) {
    companion object {
        fun from(samples: List<PidSample>): CloudLiveData {
            val byPid = samples.groupBy { it.pid }
            val minTime = samples.minOfOrNull { it.timestampMillis }
            val maxTime = samples.maxOfOrNull { it.timestampMillis }
            return CloudLiveData(
                sampleCount = samples.size,
                durationSeconds = if (minTime != null && maxTime != null) (maxTime - minTime) / 1_000.0 else 0.0,
                latestValues = byPid.mapValues { (_, values) -> values.maxBy { it.timestampMillis }.value },
                stats = byPid.map { (pid, values) ->
                    val numeric = values.map { it.value }
                    CloudPidStats(
                        pid = pid,
                        unit = values.lastOrNull()?.unit.orEmpty(),
                        count = values.size,
                        min = numeric.minOrNull() ?: 0.0,
                        max = numeric.maxOrNull() ?: 0.0,
                        avg = numeric.average().takeIf { !it.isNaN() } ?: 0.0,
                    )
                }.sortedBy { it.pid },
            )
        }
    }
}

@Serializable
private data class CloudPidStats(
    val pid: String,
    val unit: String,
    val count: Int,
    val min: Double,
    val max: Double,
    val avg: Double,
)

@Serializable
private data class CloudLocalAnalysis(
    val insufficientData: Boolean?,
    val dataCompleteness: Double?,
    val topPredictions: List<CloudLocalPrediction>,
    val powerKw: Double?,
    val powerLowKw: Double?,
    val powerHighKw: Double?,
    val powerWarnings: List<String>,
) {
    companion object {
        fun from(analysis: DriveabilityAnalysis?, power: CombinedPowerEstimate?): CloudLocalAnalysis? {
            if (analysis == null && power == null) return null
            return CloudLocalAnalysis(
                insufficientData = analysis?.insufficientData,
                dataCompleteness = analysis?.dataCompleteness,
                topPredictions = analysis?.predictions.orEmpty().take(5).map {
                    CloudLocalPrediction(
                        rootCause = it.rootCause,
                        title = it.title,
                        probability = it.probability,
                        confidence = it.confidence,
                        evidence = it.evidence.map { evidence -> "${evidence.feature}=${evidence.rawValue}" },
                    )
                },
                powerKw = power?.meanKw,
                powerLowKw = power?.lowKw,
                powerHighKw = power?.highKw,
                powerWarnings = power?.warnings.orEmpty(),
            )
        }
    }
}

@Serializable
private data class CloudLocalPrediction(
    val rootCause: String,
    val title: String,
    val probability: Double,
    val confidence: Double,
    val evidence: List<String>,
)

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
