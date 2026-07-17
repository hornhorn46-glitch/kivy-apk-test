package com.autodoctor.aipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodoctor.aipro.core.ai.DriveabilityAnalysis
import com.autodoctor.aipro.core.ai.DriveabilityModel
import com.autodoctor.aipro.core.ai.NeuroSymbolicDriveabilityAnalyzer
import com.autodoctor.aipro.core.cloud.CloudAiSettingsStore
import com.autodoctor.aipro.core.cloud.CloudDiagnosticMode
import com.autodoctor.aipro.core.cloud.OpenAiDiagnosticAssistant
import com.autodoctor.aipro.core.diagnostics.FactExtractor
import com.autodoctor.aipro.core.diagnostics.DiagnosticReport
import com.autodoctor.aipro.core.diagnostics.InferenceEngine
import com.autodoctor.aipro.core.knowledge.DiagnosticCasePatternSummary
import com.autodoctor.aipro.core.diagnostics.DiagnosticRule
import com.autodoctor.aipro.core.model.DiagnosticTroubleCode
import com.autodoctor.aipro.core.model.FreezeFrame
import com.autodoctor.aipro.core.model.ObdSession
import com.autodoctor.aipro.core.model.PidDefinition
import com.autodoctor.aipro.core.model.PidSample
import com.autodoctor.aipro.core.model.VehicleProfile
import com.autodoctor.aipro.core.obd.Elm327Connection
import com.autodoctor.aipro.core.obd.LiveDataSampler
import com.autodoctor.aipro.core.obd.ObdConnectResult
import com.autodoctor.aipro.core.obd.ObdConnectStatus
import com.autodoctor.aipro.core.obd.ObdConnectionManager
import com.autodoctor.aipro.core.obd.ObdHealthScan
import com.autodoctor.aipro.core.obd.ObdHealthScanner
import com.autodoctor.aipro.core.obd.ObdPermissionPolicy
import com.autodoctor.aipro.core.obd.ObdTraceLog
import com.autodoctor.aipro.core.obd.PidSelection
import com.autodoctor.aipro.core.performance.AccelerationAnalyzer
import com.autodoctor.aipro.core.performance.AccelerationTestConfig
import com.autodoctor.aipro.core.performance.CombinedPowerEstimate
import com.autodoctor.aipro.core.performance.EngineTestPlan
import com.autodoctor.aipro.core.performance.EngineTestStep
import com.autodoctor.aipro.core.performance.EngineTestValidation
import com.autodoctor.aipro.core.performance.EngineTestValidator
import com.autodoctor.aipro.core.reference.ReferenceComparisonReport
import com.autodoctor.aipro.core.reference.ReferenceCurve
import com.autodoctor.aipro.core.reference.ReferenceCurveComparator
import com.autodoctor.aipro.core.reference.ReferenceCurveSet
import com.autodoctor.aipro.ui.design.AutoDoctorTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as AutoDoctorApp).knowledgeRepository
        val referenceCurves = runCatching { repository.loadReferenceCurves() }.getOrDefault(emptyList())
        val profiles = runCatching { repository.loadVehicleProfiles() }.getOrDefault(emptyList())
        val pids = runCatching { repository.loadPidDefinitions() }.getOrDefault(emptyList())
        val rules = runCatching { repository.loadRules() }.getOrDefault(emptyList())
        val profileCount = profiles.size
        val ruleCount = rules.size
        val brandCount = runCatching {
            repository.loadBrandProfileMappings().sumOf { it.brands.size }
        }.getOrDefault(0)
        val model = runCatching { repository.loadDriveabilityModel() }.getOrNull()
        val encyclopediaCount = runCatching { repository.loadEncyclopediaArticles().size }.getOrDefault(0)
        val physicsPrincipleCount = runCatching { repository.loadEnginePhysicsPrinciples().size }.getOrDefault(0)
        val caseSummary = runCatching { repository.loadDiagnosticCasePatternSummary() }.getOrNull()

        setContent {
            AutoDoctorTheme {
                DiagnosticCockpit(
                    referenceCurves = referenceCurves,
                    profiles = profiles,
                    pids = pids,
                    rules = rules,
                    profileCount = profileCount,
                    ruleCount = ruleCount,
                    brandCount = brandCount,
                    model = model,
                    encyclopediaCount = encyclopediaCount,
                    physicsPrincipleCount = physicsPrincipleCount,
                    caseSummary = caseSummary,
                )
            }
        }
    }
}

private data class EngineTestUiResult(
    val step: EngineTestStep,
    val profile: VehicleProfile,
    val validation: EngineTestValidation,
    val power: CombinedPowerEstimate,
    val comparison: ReferenceComparisonReport?,
    val analysis: DriveabilityAnalysis?,
    val diagnosticReport: DiagnosticReport?,
    val session: ObdSession,
)

private val RacingLime = Color(0xFFB7FF2A)
private val RacingGreen = Color(0xFF39FF14)
private val RacingCyan = Color(0xFF27F4FF)
private val RacingRed = Color(0xFFFF2D1F)
private val RacingOrange = Color(0xFFFF7A1A)
private val RacingSurface = Color(0xF0141518)
private val RacingPanel = Color(0xFF202124)
private val RacingPanelDark = Color(0xFF0A0B0D)
private val RacingMuted = Color(0xFF9EA4AA)

@Composable
private fun RacingCardFrame(
    modifier: Modifier = Modifier,
    accent: Color = RacingLime,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.52f),
                        RacingCyan.copy(alpha = 0.18f),
                        RacingRed.copy(alpha = 0.24f),
                    ),
                ),
                shape = shape,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = RacingSurface),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xEE1A1B1F),
                        Color(0xF00B0C0F),
                    ),
                ),
            ),
        ) {
            content()
        }
    }
}

private val coreLivePidIds = listOf(
    "RPM",
    "SPEED",
    "THROTTLE",
    "LOAD",
    "MAF",
    "MAP",
    "COOLANT_TEMP",
    "INTAKE_TEMP",
    "STFT_B1",
    "LTFT_B1",
)

private fun selectLiveStreamPids(
    pids: List<PidDefinition>,
    healthScan: ObdHealthScan?,
): List<PidDefinition> {
    val selected = PidSelection.engineTest(pids).ifEmpty { pids.filter { it.service == "01" } }
    val supportedIds = healthScan?.coverage?.supportedPidIds?.toSet().orEmpty()
    val byId = selected.associateBy { it.id }
    if (supportedIds.isNotEmpty()) {
        val supportedCore = coreLivePidIds.mapNotNull { id -> byId[id]?.takeIf { id in supportedIds } }
        val supportedExtra = selected.filter { it.id in supportedIds && it.id !in coreLivePidIds }
        return (supportedCore + supportedExtra).take(10).ifEmpty {
            coreLivePidIds.mapNotNull { byId[it] }.take(6)
        }
    }
    return coreLivePidIds.mapNotNull { byId[it] }.take(8).ifEmpty { selected.take(6) }
}

@Composable
private fun DiagnosticCockpit(
    referenceCurves: List<ReferenceCurveSet>,
    profiles: List<VehicleProfile>,
    pids: List<PidDefinition>,
    rules: List<DiagnosticRule>,
    profileCount: Int,
    ruleCount: Int,
    brandCount: Int,
    model: DriveabilityModel?,
    encyclopediaCount: Int,
    physicsPrincipleCount: Int,
    caseSummary: DiagnosticCasePatternSummary?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionManager = remember { ObdConnectionManager(context.applicationContext) }
    val profile = remember(profiles) {
        profiles.firstOrNull { it.id == "hyundai-santa-fe-classic-2-4-mpi" }
            ?: profiles.firstOrNull { it.id.startsWith("hyundai-santa-fe-classic") }
            ?: profiles.firstOrNull { it.id == "universal-gasoline-pfi-maf-na" }
            ?: profiles.firstOrNull()
    }
    val reference = referenceCurves.firstOrNull()
    val validator = remember { EngineTestValidator() }
    val powerAnalyzer = remember { AccelerationAnalyzer() }
    val referenceComparator = remember { ReferenceCurveComparator() }
    val factExtractor = remember { FactExtractor() }
    val inferenceEngine = remember(rules) { InferenceEngine(rules) }
    val driveabilityAnalyzer = remember(model) { model?.let(::NeuroSymbolicDriveabilityAnalyzer) }
    val cloudSettingsStore = remember { CloudAiSettingsStore(context.applicationContext) }
    val cloudAssistant = remember { OpenAiDiagnosticAssistant(context.applicationContext) }

    var connectResult by remember {
        mutableStateOf(
            ObdConnectResult(
                status = ObdConnectStatus.Disconnected,
                message = "OBD-II адаптер не подключен.",
            ),
        )
    }
    var connection by remember { mutableStateOf<Elm327Connection?>(null) }
    var samplingJob by remember { mutableStateOf<Job?>(null) }
    var liveSamples by remember { mutableStateOf<List<PidSample>>(emptyList()) }
    var recordedSamples by remember { mutableStateOf<List<PidSample>>(emptyList()) }
    var streamStartedAt by remember { mutableStateOf<Long?>(null) }
    var streamPidCount by remember { mutableIntStateOf(0) }
    var healthScan by remember { mutableStateOf<ObdHealthScan?>(null) }
    var scanningHealth by remember { mutableStateOf(false) }
    var recordingStepIndex by remember { mutableIntStateOf(0) }
    var recordingSince by remember { mutableStateOf<Long?>(null) }
    var testResult by remember { mutableStateOf<EngineTestUiResult?>(null) }
    var cloudApiKey by remember { mutableStateOf(cloudSettingsStore.apiKey) }
    var cloudModel by remember { mutableStateOf(cloudSettingsStore.model) }
    var cloudConsent by remember { mutableStateOf(false) }
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudAnswer by remember { mutableStateOf<String?>(null) }
    var cloudError by remember { mutableStateOf<String?>(null) }

    fun closeConnection() {
        scope.launch {
            samplingJob?.cancel()
            runCatching { connection?.close() }
            connection = null
            liveSamples = emptyList()
            recordedSamples = emptyList()
            streamStartedAt = null
            streamPidCount = 0
            healthScan = null
            scanningHealth = false
            recordingSince = null
            connectResult = ObdConnectResult(ObdConnectStatus.Disconnected, "OBD-II адаптер отключен.")
        }
    }

    fun finishTest() {
        val selectedProfile = profile ?: return
        val step = EngineTestPlan.safeSteps[recordingStepIndex]
        val session = ObdSession(
            startedAtMillis = recordingSince ?: System.currentTimeMillis(),
            samples = recordedSamples,
            dtcs = healthScan?.allDtcs.orEmpty(),
            freezeFrames = healthScan?.freezeFrames.orEmpty(),
        )
        val validation = validator.validate(session, step)
        val power = powerAnalyzer.estimatePower(
            selectedProfile,
            session,
            AccelerationTestConfig(
                startRpm = step.minRpm,
                endRpm = step.maxRpm,
                requestedGear = step.targetGear ?: 2,
                requireWideOpenThrottlePercent = step.minThrottlePercent,
            ),
        )
        val comparison = reference?.let { referenceComparator.compare(session, it) }
        val diagnosticReport = inferenceEngine.analyze(selectedProfile, session)
        val analysis = driveabilityAnalyzer?.analyze(factExtractor.extract(selectedProfile, session))
        testResult = EngineTestUiResult(step, selectedProfile, validation, power, comparison, analysis, diagnosticReport, session)
        recordingSince = null
    }

    fun startSampling(activeConnection: Elm327Connection) {
        samplingJob?.cancel()
        streamStartedAt = System.currentTimeMillis()
        samplingJob = scope.launch {
            val streamPids = selectLiveStreamPids(pids, healthScan)
            streamPidCount = streamPids.size
            LiveDataSampler(activeConnection)
                .sample(streamPids, intervalMillis = 120L)
                .catch { error ->
                    connectResult = ObdConnectResult(
                        status = ObdConnectStatus.Failed,
                        message = "Поток OBD остановлен: ${error.message ?: error::class.java.simpleName}",
                    )
                }
                .collect { sample ->
                    liveSamples = (liveSamples + sample).takeLast(1_600)
                    val started = recordingSince
                    if (started != null && sample.timestampMillis >= started) {
                        recordedSamples = (recordedSamples + sample).takeLast(1_000)
                    }
                }
        }
    }

    fun connect() {
        scope.launch {
            ObdTraceLog.clear()
            samplingJob?.cancel()
            runCatching { connection?.close() }
            connection = null
            liveSamples = emptyList()
            recordedSamples = emptyList()
            streamStartedAt = null
            streamPidCount = 0
            healthScan = null
            scanningHealth = false
            recordingSince = null
            connectResult = ObdConnectResult(ObdConnectStatus.Searching, "Ищу Bluetooth, Wi-Fi и USB ELM327...")
            val result = connectionManager.connectFirstReady()
            connectResult = result
            connection = result.connection
            result.connection?.let { activeConnection ->
                scanningHealth = true
                healthScan = runCatching { ObdHealthScanner().scan(activeConnection, pids) }
                    .getOrElse { error ->
                        connectResult = ObdConnectResult(
                            status = ObdConnectStatus.Failed,
                            message = "Подключено, но health scan не прошел: ${error.message ?: error::class.java.simpleName}",
                            adapter = result.adapter,
                            connection = activeConnection,
                        )
                        null
                    }
                healthScan?.let { scan ->
                    if (scan.coverage.supportedPidIds.isEmpty()) {
                        connectResult = result.copy(
                            message = "${result.message} ECU пока не отдал карту PID; пробую базовые live-датчики. Если значения останутся пустыми, включите зажигание или заведите двигатель.",
                        )
                    }
                }
                scanningHealth = false
                startSampling(activeConnection)
            }
        }
    }

    fun requestCloudAnalysis(mode: CloudDiagnosticMode) {
        scope.launch {
            val key = cloudApiKey.trim()
            if (key.isBlank()) {
                cloudError = "Введите OpenAI API key. Без ключа приложение не может отправить диагностический пакет в ChatGPT/OpenAI."
                return@launch
            }
            if (!cloudConsent) {
                cloudError = "Нужно явно разрешить отправку диагностического пакета: raw ELM, DTC, health scan и live-графики."
                return@launch
            }
            cloudBusy = true
            cloudError = null
            cloudAnswer = null
            val selectedModel = cloudModel.trim().ifBlank { CloudAiSettingsStore.DefaultModel }
            cloudSettingsStore.save(key, selectedModel)
            val result = testResult
            cloudAnswer = runCatching {
                cloudAssistant.analyze(
                    apiKey = key,
                    model = selectedModel,
                    mode = mode,
                    profile = profile,
                    connection = connectResult,
                    healthScan = healthScan,
                    liveSamples = liveSamples,
                    recordedSamples = recordedSamples,
                    localAnalysis = result?.analysis,
                    power = result?.power,
                )
            }.getOrElse { error ->
                cloudError = error.message ?: error::class.java.simpleName
                null
            }
            cloudBusy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        connect()
    }

    LaunchedEffect(liveSamples.size, recordedSamples.size, recordingSince, recordingStepIndex) {
        val started = recordingSince ?: return@LaunchedEffect
        val step = EngineTestPlan.safeSteps[recordingStepIndex]
        val maxSpeed = recordedSamples.latestValue("SPEED") ?: 0.0
        val duration = (System.currentTimeMillis() - started) / 1_000.0
        if (maxSpeed >= step.maxSpeedKph && duration >= step.minDurationSeconds) {
            finishTest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050506),
                        Color(0xFF111214),
                        Color(0xFF050506),
                    ),
                ),
            )
            .padding(18.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = RacingRed.copy(alpha = 0.18f),
                radius = size.width * 0.72f,
                center = Offset(size.width * 1.08f, size.height * 0.12f),
            )
            drawCircle(
                color = RacingCyan.copy(alpha = 0.06f),
                radius = size.width * 0.62f,
                center = Offset(-size.width * 0.10f, size.height * 0.42f),
            )
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(profile, profileCount, ruleCount, brandCount, encyclopediaCount, physicsPrincipleCount, caseSummary)
            ConnectionCard(
                result = connectResult,
                connected = connection != null,
                sampleRateHz = streamRateHz(liveSamples, streamStartedAt),
                pidCount = streamPidCount.takeIf { it > 0 }
                    ?: selectLiveStreamPids(pids, healthScan).size,
                onConnect = {
                    permissionLauncher.launch(ObdPermissionPolicy.runtimePermissions())
                },
                onDisconnect = ::closeConnection,
            )
            HealthScanCard(healthScan, scanningHealth)
            CloudAiAssistCard(
                apiKey = cloudApiKey,
                onApiKeyChange = { cloudApiKey = it },
                model = cloudModel,
                onModelChange = { cloudModel = it },
                consent = cloudConsent,
                onConsentChange = { cloudConsent = it },
                busy = cloudBusy,
                answer = cloudAnswer,
                error = cloudError,
                connected = connection != null,
                onConnectionHelp = { requestCloudAnalysis(CloudDiagnosticMode.ConnectionTroubleshooting) },
                onDriveabilityHelp = { requestCloudAnalysis(CloudDiagnosticMode.DriveabilityAnalysis) },
            )
            LiveGaugeGrid(liveSamples, connected = connection != null)
            EngineTestWizard(
                connected = connection != null,
                liveSamples = liveSamples,
                recordingStepIndex = recordingStepIndex,
                recording = recordingSince != null,
                recordedSamples = recordedSamples,
                onSelectStep = { recordingStepIndex = it },
                onStart = {
                    recordedSamples = emptyList()
                    testResult = null
                    recordingSince = System.currentTimeMillis()
                },
                onStop = ::finishTest,
            )
            TestResultCard(testResult, reference, model)
            ReferencePreview(reference)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeaderCard(
    activeProfile: VehicleProfile?,
    profileCount: Int,
    ruleCount: Int,
    brandCount: Int,
    encyclopediaCount: Int,
    physicsPrincipleCount: Int,
    caseSummary: DiagnosticCasePatternSummary?,
) {
    RacingCardFrame(accent = RacingRed) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AutoDoctor AI Pro", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Инженерная диагностика OBD-II: подключение, тест, графики, причины и проверка ремонта.", color = Color(0xFFD7E3F1), lineHeight = 20.sp)
            activeProfile?.let {
                Text(
                    text = "Активный профиль: ${it.make} ${it.model} ${it.engineCode.orEmpty()} ${it.displacementLiters?.let { value -> "${value.roundDisplay()}L" }.orEmpty()}.",
                    color = Color(0xFFFFD166),
                    fontSize = 13.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactStat("Profiles", profileCount.toString(), Modifier.weight(1f))
                CompactStat("Rules", ruleCount.toString(), Modifier.weight(1f))
                CompactStat("Brands", brandCount.toString(), Modifier.weight(1f))
            }
            Text(
                text = "${caseSummary?.totalCases ?: 0} case patterns, $encyclopediaCount encyclopedia articles, $physicsPrincipleCount graph physics principles.",
                color = Color(0xFF42D392),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(RacingPanel, RacingPanelDark)), shape)
            .border(1.dp, RacingLime.copy(alpha = 0.18f), shape)
            .padding(12.dp),
    ) {
        Column {
            Text(label, color = RacingMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = RacingLime, fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
    }
}

@Composable
private fun ConnectionCard(
    result: ObdConnectResult,
    connected: Boolean,
    sampleRateHz: Double,
    pidCount: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val statusColor = when (result.status) {
        ObdConnectStatus.Connected -> RacingGreen
        ObdConnectStatus.Searching -> Color(0xFFFFD166)
        ObdConnectStatus.PermissionRequired -> Color(0xFFFFD166)
        ObdConnectStatus.Failed -> RacingRed
        ObdConnectStatus.Disconnected -> RacingMuted
    }
    RacingCardFrame(accent = statusColor) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(18.dp)) { drawCircle(statusColor) }
                Column(Modifier.weight(1f)) {
                    Text(if (connected) "OBD подключен" else "OBD не подключен", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(result.message, color = Color(0xFFD7E3F1), lineHeight = 19.sp, fontSize = 13.sp)
                    if (connected) {
                        val streamReady = sampleRateHz >= 0.05
                        Text(
                            text = if (streamReady) {
                                "Поток: ${sampleRateHz.roundDisplay()} samples/s, быстрый профиль $pidCount PID."
                            } else {
                                "Поток: нет live data. Заведите двигатель; если не оживет, нажмите Заново."
                            },
                            color = if (sampleRateHz >= 4.0) Color(0xFF42D392) else Color(0xFFFFD166),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = result.status != ObdConnectStatus.Searching,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RacingLime,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF2A2D31),
                        disabledContentColor = RacingMuted,
                    ),
                ) {
                    Text(if (connected) "Заново" else "Подключить", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = connected,
                    modifier = Modifier.weight(0.72f).height(54.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RacingLime,
                        disabledContentColor = RacingMuted,
                    ),
                ) {
                    Text("Стоп", maxLines = 1)
                }
                if (result.status == ObdConnectStatus.Searching) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFFFFD166), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun HealthScanCard(scan: ObdHealthScan?, scanning: Boolean) {
    RacingCardFrame(accent = if (scan?.allDtcs?.isNotEmpty() == true) RacingOrange else RacingLime) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Health scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (scanning) CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFFFFD166), strokeWidth = 3.dp)
            }
            if (scan == null) {
                Text(
                    text = if (scanning) "Читаю DTC, freeze-frame, Mode 06 и карту поддерживаемых PID..." else "Скан появится сразу после подключения к ELM327.",
                    color = Color(0xFF9FB3C8),
                    lineHeight = 19.sp,
                )
                return@Column
            }
            val dtcCount = scan.allDtcs.size
            val dtcColor = if (dtcCount == 0) Color(0xFF42D392) else Color(0xFFFFD166)
            Text(
                text = "DTC: confirmed ${scan.confirmedDtcs.size}, pending ${scan.pendingDtcs.size}, permanent ${scan.permanentDtcs.size}.",
                color = dtcColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (scan.allDtcs.isNotEmpty()) {
                Text(
                    text = scan.allDtcs.take(6).joinToString("; ") { "${it.code} ${it.description}" },
                    color = Color(0xFFD7E3F1),
                    lineHeight = 18.sp,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = "Sensor coverage: ${(scan.coverage.coverage * 100).roundToInt()}% (${scan.coverage.supportedPidIds.size}/${scan.coverage.requiredPidIds.size})",
                color = if (scan.coverage.coverage >= 0.75) Color(0xFF42D392) else Color(0xFFFFD166),
                fontSize = 13.sp,
            )
            if (scan.coverage.missingPidIds.isNotEmpty()) {
                Text(
                    text = "Не отдаются: ${scan.coverage.missingPidIds.take(8).joinToString(", ")}.",
                    color = Color(0xFF9FB3C8),
                    lineHeight = 18.sp,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "Freeze-frame: ${scan.freezeFrames.size}; Mode 06 monitors: ${scan.mode06Monitors.size}.",
                color = Color(0xFFD7E3F1),
                fontSize = 13.sp,
            )
            val misfireCodes = scan.allDtcs.filter { it.code == "P0300" || Regex("P030[1-8]").matches(it.code) }
            Text(
                text = if (misfireCodes.isNotEmpty()) {
                    "Пропуски зажигания подтверждены DTC: ${misfireCodes.joinToString(", ") { it.code }}."
                } else if (scan.mode06Monitors.isNotEmpty()) {
                    "Mode 06 прочитан; raw monitors сохранены, но cylinder misfire TID/CID зависят от производителя."
                } else {
                    "Данных Mode 06 по пропускам нет; вывод по misfire будет только по DTC/live-графику."
                },
                color = if (misfireCodes.isNotEmpty()) Color(0xFFFF6B6B) else Color(0xFF9FB3C8),
                lineHeight = 18.sp,
                fontSize = 13.sp,
            )
            scan.warnings.take(2).forEach { warning ->
                Text(warning, color = Color(0xFFFFD166), lineHeight = 17.sp, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CloudAiAssistCard(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    consent: Boolean,
    onConsentChange: (Boolean) -> Unit,
    busy: Boolean,
    answer: String?,
    error: String?,
    connected: Boolean,
    onConnectionHelp: () -> Unit,
    onDriveabilityHelp: () -> Unit,
) {
    val canSend = apiKey.isNotBlank() && consent && !busy
    RacingCardFrame(accent = RacingCyan) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("AI Assist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        text = if (connected) "Можно отправить raw ELM, DTC, Mode 06 и live-графики в OpenAI для разбора." else "Можно отправить лог подключения и ошибки ELM в OpenAI для разбора.",
                        color = Color(0xFF9FB3C8),
                        lineHeight = 18.sp,
                        fontSize = 12.sp,
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFFFFD166), strokeWidth = 3.dp)
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenAI API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                placeholder = { Text(CloudAiSettingsStore.DefaultModel) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = consent, onCheckedChange = onConsentChange)
                Text(
                    text = "Разрешаю отправить диагностический пакет. Запрос идёт с store=false; ключ сохраняется только на этом устройстве.",
                    color = Color(0xFFD7E3F1),
                    lineHeight = 17.sp,
                    fontSize = 12.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onConnectionHelp,
                    enabled = canSend,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RacingCyan,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF2A2D31),
                        disabledContentColor = RacingMuted,
                    ),
                ) {
                    Text("Разобрать подключение", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDriveabilityHelp,
                    enabled = canSend,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RacingCyan,
                        disabledContentColor = RacingMuted,
                    ),
                ) {
                    Text("Анализ графиков", fontSize = 12.sp, maxLines = 1)
                }
            }
            error?.let {
                Text(it, color = Color(0xFFFF6B6B), lineHeight = 18.sp, fontSize = 12.sp)
            }
            answer?.let {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = RacingPanelDark)) {
                    Text(
                        text = it,
                        color = Color(0xFFD7E3F1),
                        lineHeight = 18.sp,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveGaugeGrid(samples: List<PidSample>, connected: Boolean) {
    val liveReady = connected && samples.isNotEmpty()
    val shape = RoundedCornerShape(8.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(RacingLime.copy(alpha = 0.65f), RacingCyan.copy(alpha = 0.30f), RacingRed.copy(alpha = 0.45f))),
                shape = shape,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = RacingSurface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("OBD DASH", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Gauge  Digital  Diagnosis", color = RacingMuted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                StatusPill(
                    label = when {
                        liveReady -> "LIVE"
                        connected -> "NO DATA"
                        else -> "WAIT"
                    },
                    color = when {
                        liveReady -> RacingLime
                        connected -> Color(0xFFFFD166)
                        else -> RacingMuted
                    },
                )
            }
            RacingSpeedometer(samples, liveReady)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TelemetryTile("RPM", samples.latestValue("RPM"), "rpm", RacingLime, Modifier.weight(1f))
                TelemetryTile("Load", samples.latestValue("LOAD"), "%", RacingOrange, Modifier.weight(1f))
                TelemetryTile("Gas", samples.latestValue("THROTTLE"), "%", RacingCyan, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TelemetryTile("MAP", samples.latestValue("MAP"), "kPa", Color(0xFFB58CFF), Modifier.weight(1f))
                TelemetryTile("MAF", samples.latestValue("MAF"), "g/s", RacingCyan, Modifier.weight(1f))
                TelemetryTile("ECT", samples.latestValue("COOLANT_TEMP"), "C", Color(0xFFFFFFFF), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun RacingSpeedometer(samples: List<PidSample>, liveReady: Boolean) {
    val speed = samples.latestValue("SPEED")
    val rpm = samples.latestValue("RPM")
    val speedProgress = ((speed ?: 0.0) / 260.0).coerceIn(0.0, 1.0)
    val rpmProgress = ((rpm ?: 0.0) / 7_000.0).coerceIn(0.0, 1.0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(318.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) * 0.43f
            val center = Offset(size.width / 2f, size.height * 0.55f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            drawCircle(Color.Black.copy(alpha = 0.78f), radius * 1.16f, center)
            drawArc(
                color = Color(0xFF2B2D30),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(28.dp.toPx(), cap = StrokeCap.Butt),
            )
            drawArc(
                color = RacingRed,
                startAngle = 140f + 260f * 0.78f,
                sweepAngle = 260f * 0.22f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(28.dp.toPx(), cap = StrokeCap.Butt),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(RacingLime, RacingLime, RacingOrange, RacingRed), center),
                startAngle = 140f,
                sweepAngle = (260f * speedProgress).toFloat(),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(22.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = RacingCyan.copy(alpha = 0.80f),
                startAngle = 140f,
                sweepAngle = (260f * rpmProgress).toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.70f, center.y - radius * 0.70f),
                size = androidx.compose.ui.geometry.Size(radius * 1.40f, radius * 1.40f),
                style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
            )
            for (tick in 0..13) {
                val angle = (140.0 + tick * (260.0 / 13.0)) * PI / 180.0
                val tickOuter = radius * if (tick % 2 == 0) 1.08f else 1.02f
                val tickInner = radius * if (tick % 2 == 0) 0.94f else 0.97f
                drawLine(
                    color = if (tick >= 10) RacingRed else RacingCyan,
                    start = Offset(center.x + cos(angle).toFloat() * tickInner, center.y + sin(angle).toFloat() * tickInner),
                    end = Offset(center.x + cos(angle).toFloat() * tickOuter, center.y + sin(angle).toFloat() * tickOuter),
                    strokeWidth = if (tick % 2 == 0) 4.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = speed?.roundToInt()?.toString() ?: "--",
                color = if (liveReady) RacingLime else RacingMuted,
                fontWeight = FontWeight.Black,
                fontSize = 76.sp,
            )
            Text("km/h", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
            Text(
                text = "RPM ${rpm?.roundToInt()?.toString() ?: "--"}",
                color = RacingCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun TelemetryTile(label: String, value: Double?, unit: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(88.dp)
            .background(Brush.verticalGradient(listOf(RacingPanel, RacingPanelDark)), shape)
            .border(1.dp, color.copy(alpha = 0.22f), shape)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Text(label, color = RacingMuted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Column {
                Text(value?.roundDisplay() ?: "--", color = color, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 1)
                Text(unit, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EngineTestWizard(
    connected: Boolean,
    liveSamples: List<PidSample>,
    recordingStepIndex: Int,
    recording: Boolean,
    recordedSamples: List<PidSample>,
    onSelectStep: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val step = EngineTestPlan.safeSteps[recordingStepIndex]
    val speed = liveSamples.latestValue("SPEED") ?: 0.0
    val throttle = liveSamples.latestValue("THROTTLE") ?: 0.0
    val rpm = liveSamples.latestValue("RPM") ?: 0.0
    val progress = if (recording) {
        ((recordedSamples.latestValue("SPEED") ?: speed) / step.maxSpeedKph).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    RacingCardFrame(accent = RacingLime) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Тестировать двигатель", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EngineTestPlan.safeSteps.forEachIndexed { index, _ ->
                    val selected = index == recordingStepIndex
                    OutlinedButton(
                        onClick = { if (!recording) onSelectStep(index) },
                        enabled = !recording,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (selected) RacingLime else RacingMuted,
                            disabledContentColor = if (selected) RacingLime.copy(alpha = 0.62f) else RacingMuted,
                        ),
                    ) {
                        Text("${index + 1}", fontSize = 13.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Bold)
                    }
                }
            }
            Text(step.title, color = Color(0xFFFFD166), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(step.instruction, color = Color(0xFFD7E3F1), lineHeight = 20.sp)
            Text(
                text = "Текущие условия: ${speed.roundDisplay()} км/ч, газ ${throttle.roundDisplay()}%, ${rpm.roundDisplay()} RPM.",
                color = Color(0xFF9FB3C8),
                fontSize = 13.sp,
            )
            if (recording) {
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth(), color = RacingLime, trackColor = Color(0xFF2B2D30))
                Text("Запись идет. Если газ не будет нажат или скорость/RPM не попадут в окно, результат будет помечен как невалидный.", color = Color(0xFFFFD166), lineHeight = 18.sp, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = connected && !recording,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RacingLime,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF334155),
                        disabledContentColor = RacingMuted,
                    ),
                ) {
                    Text("START TEST", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = recording,
                    modifier = Modifier.weight(0.72f).height(58.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Завершить вручную")
                }
            }
        }
    }
}

@Composable
private fun TestResultCard(
    result: EngineTestUiResult?,
    reference: ReferenceCurveSet?,
    model: DriveabilityModel?,
) {
    RacingCardFrame(accent = RacingOrange) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Результат теста", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (result == null) {
                Text("После завершения теста здесь появятся графики, сравнение с эталоном и инженерное объяснение причин.", color = Color(0xFF9FB3C8), lineHeight = 20.sp)
                return@Column
            }
            val statusColor = if (result.validation.valid) Color(0xFF42D392) else Color(0xFFFF6B6B)
            Text(
                text = if (result.validation.valid) "Тест валиден" else "Тест невалиден для уверенного диагноза",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text("Качество записи: ${(result.validation.score * 100).roundToInt()}%", color = Color(0xFFD7E3F1))
            result.validation.failedChecks.takeIf { it.isNotEmpty() }?.let { failures ->
                Text("Что исправить: ${failures.joinToString("; ")}.", color = Color(0xFFFFD166), lineHeight = 19.sp)
            }
            PowerBlock(result.power)
            PowerLossBlock(result)
            DeviationSummaryBlock(result.comparison)
            LocalDiagnosticBlock(result.diagnosticReport)
            if (!result.validation.valid) {
                Text(
                    text = "Диагноз не фиксирую: график не прошел контроль качества. Повторите этот же тест, иначе приложение будет угадывать вместо диагностики.",
                    color = Color(0xFFFFD166),
                    lineHeight = 20.sp,
                )
                ReferenceVsActualBlock(reference, result.session)
                return@Column
            }
            result.analysis?.predictions?.firstOrNull()?.let { prediction ->
                Text(prediction.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    "Вероятность: ${(prediction.probability * 100).roundToInt()}%, уверенность с учетом данных: ${(prediction.confidence * 100).roundToInt()}%.",
                    color = Color(0xFF42D392),
                )
                Text(prediction.physicalExplanation, color = Color(0xFFD7E3F1), lineHeight = 20.sp)
                Text("Следующие проверки: ${prediction.recommendedChecks.take(3).joinToString("; ")}.", color = Color(0xFF9FB3C8), lineHeight = 19.sp)
            } ?: Text(
                text = "Root-cause модель не загружена. Benchmark target: ${model?.validation?.targetAccuracy ?: 0.98}.",
                color = Color(0xFF9FB3C8),
            )
            ReferenceVsActualBlock(reference, result.session)
        }
    }
}

@Composable
private fun PowerLossBlock(result: EngineTestUiResult) {
    val ratedPower = result.profile.powerKw
    if (ratedPower == null || ratedPower <= 0.0 || result.power.meanKw <= 0.0) {
        Text("Падение мощности относительно профиля пока не считаю: нет паспортной мощности профиля или валидной оценки.", color = Color(0xFF9FB3C8), lineHeight = 18.sp, fontSize = 13.sp)
        return
    }
    val lossPercent = ((1.0 - result.power.meanKw / ratedPower) * 100.0).coerceAtLeast(0.0)
    val color = when {
        lossPercent >= 25.0 -> Color(0xFFFF6B6B)
        lossPercent >= 12.0 -> Color(0xFFFFD166)
        else -> Color(0xFF42D392)
    }
    Text(
        text = "Оценка потери мощности: ${lossPercent.roundDisplay()}% от профиля (${result.power.meanKw.roundDisplay()} кВт из ${ratedPower.roundDisplay()} кВт).",
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DeviationSummaryBlock(comparison: ReferenceComparisonReport?) {
    val deviations = comparison?.deviations.orEmpty().take(4)
    if (deviations.isEmpty()) {
        Text("Критичных отклонений от эталонных кривых пока нет или данных мало.", color = Color(0xFF9FB3C8), lineHeight = 18.sp, fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Главные отклонения от эталона:", color = Color.White, fontWeight = FontWeight.SemiBold)
        deviations.forEach { deviation ->
            val color = if (deviation.severity.name == "Significant") Color(0xFFFF6B6B) else Color(0xFFFFD166)
            Text(
                text = "${deviation.metric}: факт ${deviation.observed.roundDisplay()}, эталон ${deviation.expectedLow.roundDisplay()}-${deviation.expectedHigh.roundDisplay()} при ${deviation.x.roundDisplay()} ${deviation.xMetric}.",
                color = color,
                lineHeight = 18.sp,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun LocalDiagnosticBlock(report: DiagnosticReport?) {
    if (report == null) {
        Text("Локальный эксперт пока не дал отчёт.", color = Color(0xFF9FB3C8), lineHeight = 18.sp, fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Локальная диагностика без интернета", color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(report.message, color = if (report.insufficientData) Color(0xFFFFD166) else Color(0xFF42D392), lineHeight = 18.sp, fontSize = 13.sp)
        report.hypotheses.take(3).forEach { hypothesis ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = RacingPanelDark)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(hypothesis.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Вероятность ${(hypothesis.probability * 100).roundToInt()}%, confidence ${(hypothesis.confidence * 100).roundToInt()}%, severity ${hypothesis.severity}.",
                        color = Color(0xFF42D392),
                        fontSize = 12.sp,
                    )
                    if (hypothesis.evidence.isNotEmpty()) {
                        Text(
                            "Зацепки: ${hypothesis.evidence.take(4).joinToString("; ") { "${it.label}=${it.value}" }}.",
                            color = Color(0xFFD7E3F1),
                            lineHeight = 17.sp,
                            fontSize = 12.sp,
                        )
                    }
                    if (hypothesis.missingData.isNotEmpty()) {
                        Text(
                            "Не хватает: ${hypothesis.missingData.take(5).joinToString(", ")}.",
                            color = Color(0xFFFFD166),
                            lineHeight = 17.sp,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "Проверить: ${hypothesis.recommendedChecks.take(3).joinToString("; ")}.",
                        color = Color(0xFF9FB3C8),
                        lineHeight = 17.sp,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerBlock(power: CombinedPowerEstimate) {
    val warning = power.warnings.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Оценка мощности: ${power.meanKw.roundDisplay()} кВт (${power.lowKw.roundDisplay()}-${power.highKw.roundDisplay()} кВт), confidence ${(power.confidence * 100).roundToInt()}%.",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        warning?.let { Text(it, color = Color(0xFFFFD166), lineHeight = 18.sp, fontSize = 13.sp) }
    }
}

@Composable
private fun ReferenceVsActualBlock(reference: ReferenceCurveSet?, session: ObdSession) {
    if (reference == null) {
        Text("Эталонные графики не загружены для выбранного профиля.", color = Color(0xFF9FB3C8))
        return
    }
    val curves = reference.curves.filter { it.xMetric == "rpm" }.take(2)
    Text("Эталон vs факт: ${reference.title}", color = Color(0xFF9FB3C8), fontSize = 13.sp)
    curves.forEach { curve ->
        ActualReferenceChart(curve, session.samples)
    }
}

@Composable
private fun ReferencePreview(reference: ReferenceCurveSet?) {
    RacingCardFrame(accent = RacingCyan) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Эталонные графики", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                text = reference?.let { "Загружено: ${it.title}. Если точного авто нет, используется ближайший универсальный профиль и уверенность снижается." }
                    ?: "Эталоны не найдены.",
                color = Color(0xFF9FB3C8),
                lineHeight = 19.sp,
                fontSize = 13.sp,
            )
            reference?.curves?.filter { it.xMetric == "rpm" }?.take(1)?.forEach { curve ->
                ActualReferenceChart(curve, emptyList())
            }
        }
    }
}

@Composable
private fun ActualReferenceChart(curve: ReferenceCurve, samples: List<PidSample>) {
    val rpmSamples = samples.filter { it.pid == "RPM" }
    val metricPid = curve.metric.toPid()
    val metricSamples = samples.filter { it.pid == metricPid }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${curve.metric} / ${curve.xMetric}", color = Color(0xFFD7E3F1), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val referencePoints = curve.points.sortedBy { it.x }
            if (referencePoints.size < 2) return@Canvas
            val actualPoints = metricSamples.mapNotNull { sample ->
                val rpm = rpmSamples.nearest(sample.timestampMillis, 800L)?.value ?: return@mapNotNull null
                rpm to sample.value
            }
            val minX = minOf(referencePoints.first().x, actualPoints.minOfOrNull { it.first } ?: referencePoints.first().x)
            val maxX = max(referencePoints.last().x, actualPoints.maxOfOrNull { it.first } ?: referencePoints.last().x).coerceAtLeast(minX + 1.0)
            val minY = minOf(referencePoints.minOf { it.p10 }, actualPoints.minOfOrNull { it.second } ?: referencePoints.minOf { it.p10 })
            val maxY = max(referencePoints.maxOf { it.p90 }, actualPoints.maxOfOrNull { it.second } ?: referencePoints.maxOf { it.p90 }).coerceAtLeast(minY + 1.0)
            fun x(value: Double): Float = ((value - minX) / (maxX - minX) * size.width).toFloat()
            fun y(value: Double): Float = (size.height - (value - minY) / (maxY - minY) * size.height).toFloat()

            for (i in 0 until referencePoints.lastIndex) {
                val a = referencePoints[i]
                val b = referencePoints[i + 1]
                drawLine(Color(0x6642D392), Offset(x(a.x), y(a.p10)), Offset(x(b.x), y(b.p10)), 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color(0x6642D392), Offset(x(a.x), y(a.p90)), Offset(x(b.x), y(b.p90)), 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color(0xFF42D392), Offset(x(a.x), y(a.p50)), Offset(x(b.x), y(b.p50)), 4.dp.toPx(), cap = StrokeCap.Round)
            }
            for (i in 0 until actualPoints.lastIndex) {
                val a = actualPoints[i]
                val b = actualPoints[i + 1]
                val outside = a.second < curve.lowAt(a.first) || a.second > curve.highAt(a.first)
                drawLine(
                    color = if (outside) Color(0xFFFF6B6B) else Color(0xFF2F80FF),
                    start = Offset(x(a.first), y(a.second)),
                    end = Offset(x(b.first), y(b.second)),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun List<PidSample>.latestValue(pid: String): Double? =
    filter { it.pid == pid }.maxByOrNull { it.timestampMillis }?.value

private fun streamRateHz(samples: List<PidSample>, startedAt: Long?): Double {
    val now = System.currentTimeMillis()
    val recent = samples.filter { it.timestampMillis >= now - 10_000L }
    if (recent.size >= 2) {
        val seconds = ((recent.maxOf { it.timestampMillis } - recent.minOf { it.timestampMillis }) / 1_000.0).coerceAtLeast(1.0)
        return recent.size / seconds
    }
    val start = startedAt ?: return 0.0
    val seconds = ((now - start) / 1_000.0).coerceAtLeast(1.0)
    return samples.size / seconds
}

private fun List<PidSample>.nearest(timestampMillis: Long, maxDistanceMillis: Long): PidSample? =
    minByOrNull { kotlin.math.abs(it.timestampMillis - timestampMillis) }
        ?.takeIf { kotlin.math.abs(it.timestampMillis - timestampMillis) <= maxDistanceMillis }

private fun Double.roundDisplay(): String {
    return if (this >= 100.0) roundToInt().toString() else ((this * 10.0).roundToInt() / 10.0).toString()
}

private fun String.toPid(): String {
    return when (this) {
        "rpm" -> "RPM"
        "speed_kph" -> "SPEED"
        "throttle_percent" -> "THROTTLE"
        "load_percent" -> "LOAD"
        "coolant_c" -> "COOLANT_TEMP"
        "ltft_b1" -> "LTFT_B1"
        "stft_b1" -> "STFT_B1"
        "map_kpa" -> "MAP"
        "iat_c" -> "INTAKE_TEMP"
        "timing_deg" -> "TIMING_ADVANCE"
        "module_voltage_v" -> "CONTROL_MODULE_VOLTAGE"
        else -> uppercase()
    }
}

private fun ReferenceCurve.lowAt(x: Double): Double = points.nearestBand(x)?.p10 ?: Double.NEGATIVE_INFINITY
private fun ReferenceCurve.highAt(x: Double): Double = points.nearestBand(x)?.p90 ?: Double.POSITIVE_INFINITY

private fun List<com.autodoctor.aipro.core.reference.ReferenceCurvePoint>.nearestBand(x: Double) =
    minByOrNull { kotlin.math.abs(it.x - x) }
