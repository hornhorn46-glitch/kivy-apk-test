package com.autodoctor.aipro

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodoctor.aipro.core.ai.DriveabilityAnalysis
import com.autodoctor.aipro.core.ai.DriveabilityModel
import com.autodoctor.aipro.core.ai.NeuroSymbolicDriveabilityAnalyzer
import com.autodoctor.aipro.core.diagnostics.FactExtractor
import com.autodoctor.aipro.core.knowledge.DiagnosticCasePatternSummary
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
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as AutoDoctorApp).knowledgeRepository
        val referenceCurves = runCatching { repository.loadReferenceCurves() }.getOrDefault(emptyList())
        val profiles = runCatching { repository.loadVehicleProfiles() }.getOrDefault(emptyList())
        val pids = runCatching { repository.loadPidDefinitions() }.getOrDefault(emptyList())
        val profileCount = profiles.size
        val ruleCount = runCatching { repository.loadRules().size }.getOrDefault(0)
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
    val validation: EngineTestValidation,
    val power: CombinedPowerEstimate,
    val comparison: ReferenceComparisonReport?,
    val analysis: DriveabilityAnalysis?,
    val session: ObdSession,
)

@Composable
private fun DiagnosticCockpit(
    referenceCurves: List<ReferenceCurveSet>,
    profiles: List<VehicleProfile>,
    pids: List<PidDefinition>,
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
        profiles.firstOrNull { it.id == "universal-gasoline-pfi-maf-na" }
            ?: profiles.firstOrNull()
    }
    val reference = referenceCurves.firstOrNull()
    val validator = remember { EngineTestValidator() }
    val powerAnalyzer = remember { AccelerationAnalyzer() }
    val referenceComparator = remember { ReferenceCurveComparator() }
    val factExtractor = remember { FactExtractor() }
    val driveabilityAnalyzer = remember(model) { model?.let(::NeuroSymbolicDriveabilityAnalyzer) }

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
    var recordingStepIndex by remember { mutableIntStateOf(0) }
    var recordingSince by remember { mutableStateOf<Long?>(null) }
    var testResult by remember { mutableStateOf<EngineTestUiResult?>(null) }

    fun closeConnection() {
        scope.launch {
            samplingJob?.cancel()
            runCatching { connection?.close() }
            connection = null
            liveSamples = emptyList()
            recordedSamples = emptyList()
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
            dtcs = emptyList<DiagnosticTroubleCode>(),
            freezeFrames = emptyList<FreezeFrame>(),
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
        val analysis = driveabilityAnalyzer?.analyze(factExtractor.extract(selectedProfile, session))
        testResult = EngineTestUiResult(step, validation, power, comparison, analysis, session)
        recordingSince = null
    }

    fun startSampling(activeConnection: Elm327Connection) {
        samplingJob?.cancel()
        samplingJob = scope.launch {
            LiveDataSampler(activeConnection)
                .sample(pids, intervalMillis = 140L)
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
            connectResult = ObdConnectResult(ObdConnectStatus.Searching, "Ищу Bluetooth, Wi-Fi и USB ELM327...")
            val result = connectionManager.connectFirstReady()
            connectResult = result
            connection = result.connection
            result.connection?.let(::startSampling)
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
            .background(Brush.verticalGradient(listOf(Color(0xFF06101E), Color(0xFF0E1728), Color(0xFF141A24))))
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(profileCount, ruleCount, brandCount, encyclopediaCount, physicsPrincipleCount, caseSummary)
            ConnectionCard(
                result = connectResult,
                connected = connection != null,
                onConnect = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                },
                onDisconnect = ::closeConnection,
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
    profileCount: Int,
    ruleCount: Int,
    brandCount: Int,
    encyclopediaCount: Int,
    physicsPrincipleCount: Int,
    caseSummary: DiagnosticCasePatternSummary?,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xEE101827))) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AutoDoctor AI Pro", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Инженерная диагностика OBD-II: подключение, тест, графики, причины и проверка ремонта.", color = Color(0xFFD7E3F1), lineHeight = 20.sp)
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
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF172235))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Color(0xFF9FB3C8), fontSize = 12.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ConnectionCard(
    result: ObdConnectResult,
    connected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val statusColor = when (result.status) {
        ObdConnectStatus.Connected -> Color(0xFF42D392)
        ObdConnectStatus.Searching -> Color(0xFFFFD166)
        ObdConnectStatus.PermissionRequired -> Color(0xFFFFD166)
        ObdConnectStatus.Failed -> Color(0xFFFF6B6B)
        ObdConnectStatus.Disconnected -> Color(0xFF9FB3C8)
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xEE111C2D))) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(18.dp)) { drawCircle(statusColor) }
                Column(Modifier.weight(1f)) {
                    Text(if (connected) "OBD подключен" else "OBD не подключен", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(result.message, color = Color(0xFFD7E3F1), lineHeight = 19.sp, fontSize = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = result.status != ObdConnectStatus.Searching,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F80FF)),
                ) {
                    Text(if (connected) "Переподключить" else "Подключиться к OBD")
                }
                OutlinedButton(onClick = onDisconnect, enabled = connected) {
                    Text("Отключить")
                }
                if (result.status == ObdConnectStatus.Searching) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFFFFD166), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun LiveGaugeGrid(samples: List<PidSample>, connected: Boolean) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xEE101827))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Live data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(if (connected) "streaming" else "waiting", color = if (connected) Color(0xFF42D392) else Color(0xFF9FB3C8), fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Gauge("RPM", samples.latestValue("RPM"), 0.0, 7_000.0, "rpm", Color(0xFF2F80FF), Modifier.weight(1f))
                Gauge("Speed", samples.latestValue("SPEED"), 0.0, 160.0, "km/h", Color(0xFF42D392), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Gauge("Gas", samples.latestValue("THROTTLE"), 0.0, 100.0, "%", Color(0xFFFFD166), Modifier.weight(1f))
                Gauge("Load", samples.latestValue("LOAD"), 0.0, 100.0, "%", Color(0xFFFF6B6B), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Gauge("MAP", samples.latestValue("MAP"), 0.0, 220.0, "kPa", Color(0xFFB58CFF), Modifier.weight(1f))
                Gauge("MAF", samples.latestValue("MAF"), 0.0, 180.0, "g/s", Color(0xFF55DDE0), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Gauge(
    label: String,
    value: Double?,
    min: Double,
    maxValue: Double,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val normalized = (((value ?: min) - min) / (maxValue - min).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF172235))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFF9FB3C8), fontSize = 12.sp)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color(0xFF2B374B),
                        startAngle = 145f,
                        sweepAngle = 250f,
                        useCenter = false,
                        style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = 145f,
                        sweepAngle = (250f * normalized).toFloat(),
                        useCenter = false,
                        style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value?.roundDisplay() ?: "--", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(unit, color = Color(0xFF9FB3C8), fontSize = 11.sp)
                }
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xEE101827))) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Тестировать двигатель", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EngineTestPlan.safeSteps.forEachIndexed { index, candidate ->
                    OutlinedButton(
                        onClick = { if (!recording) onSelectStep(index) },
                        enabled = !recording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("${index + 1}", fontSize = 13.sp)
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
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF42D392))
                Text("Запись идет. Если газ не будет нажат или скорость/RPM не попадут в окно, результат будет помечен как невалидный.", color = Color(0xFFFFD166), lineHeight = 18.sp, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = connected && !recording,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42D392), disabledContainerColor = Color(0xFF334155)),
                ) {
                    Text("Начать тест")
                }
                OutlinedButton(onClick = onStop, enabled = recording) {
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xEE111C2D))) {
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
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xDD101827))) {
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
