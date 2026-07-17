package com.autodoctor.aipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodoctor.aipro.core.ai.DriveabilityModel
import com.autodoctor.aipro.core.narration.GarageChiefNarrator
import com.autodoctor.aipro.core.narration.GarageChiefVerdict
import com.autodoctor.aipro.core.reference.ReferenceCurve
import com.autodoctor.aipro.core.reference.ReferenceCurveSet
import com.autodoctor.aipro.ui.design.AutoDoctorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as AutoDoctorApp).knowledgeRepository
        val referenceCurves = runCatching { repository.loadReferenceCurves() }.getOrDefault(emptyList())
        val profileCount = runCatching { repository.loadVehicleProfiles().size }.getOrDefault(0)
        val ruleCount = runCatching { repository.loadRules().size }.getOrDefault(0)
        val brandCount = runCatching {
            repository.loadBrandProfileMappings().sumOf { it.brands.size }
        }.getOrDefault(0)
        val model = runCatching { repository.loadDriveabilityModel() }.getOrNull()
        val garageVerdict = GarageChiefNarrator().preview()

        setContent {
            AutoDoctorTheme {
                DashboardScreen(
                    referenceCurves = referenceCurves,
                    profileCount = profileCount,
                    ruleCount = ruleCount,
                    brandCount = brandCount,
                    model = model,
                    garageVerdict = garageVerdict,
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    referenceCurves: List<ReferenceCurveSet>,
    profileCount: Int,
    ruleCount: Int,
    brandCount: Int,
    model: DriveabilityModel?,
    garageVerdict: GarageChiefVerdict,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07111F), Color(0xFF0F1B2D), Color(0xFF111827)),
                ),
            )
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "AutoDoctor AI Pro",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Инженерная диагностика OBD-II",
                color = Color(0xFF9FB3C8),
                fontSize = 15.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                HealthCard("Profiles", "$profileCount gasoline baselines", 0.86, Modifier.weight(1f))
                HealthCard("Rules", "$ruleCount expert checks", 0.78, Modifier.weight(1f))
            }
            ModelCard(model)
            CoverageCard(profileCount, brandCount)
            GarageChiefCard(garageVerdict)
            DiagnosisCard()
            LiveDataPreview()
            ReferenceCurvesPreview(referenceCurves.firstOrNull())
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ModelCard(model: DriveabilityModel?) {
    val accuracy = model?.validation?.measuredTestAccuracy ?: 0.0
    val graphCount = model?.validation?.testGraphCount ?: 0
    val macroF1 = model?.validation?.measuredMacroF1 ?: 0.0
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101827)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Root-cause model", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                text = "Benchmark: ${(accuracy * 100).toInt()}% accuracy, ${(macroF1 * 100).toInt()}% macro F1, $graphCount holdout graphs.",
                color = Color(0xFF42D392),
                fontSize = 14.sp,
            )
            Text(
                text = "Это измерение на контролируемом benchmark поверх реальных OBD-шаблонов. Полевую точность приложение повышает по мере накопления подтвержденных ремонтом сессий.",
                color = Color(0xFFD7E3F1),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun CoverageCard(profileCount: Int, brandCount: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101827)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Generic gasoline diagnosis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                text = "В базе $profileCount профилей: универсальные бензиновые PFI/MAF, PFI/MAP, GDI, turbo и hybrid по разным объемам. Mapping покрывает $brandCount марок; точный профиль повышает уверенность, но диагностика работает и через общий OBD-II fallback.",
                color = Color(0xFFD7E3F1),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun GarageChiefCard(verdict: GarageChiefVerdict) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0141C2C)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Garage Chief", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(verdict.headline, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(verdict.feeling, color = Color(0xFFD7E3F1), lineHeight = 20.sp)
            Text("Почему: ${verdict.why}", color = Color(0xFF9FB3C8), lineHeight = 19.sp, fontSize = 13.sp)
            Text("Следующий ход: ${verdict.nextMove}", color = Color(0xFF42D392), lineHeight = 19.sp, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HealthCard(title: String, subtitle: String, value: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC142033)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Color(0xFF9FB3C8), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Canvas(modifier = Modifier.size(74.dp)) {
                drawArc(
                    color = Color(0xFF23324A),
                    startAngle = 145f,
                    sweepAngle = 250f,
                    useCenter = false,
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = Color(0xFF42D392),
                    startAngle = 145f,
                    sweepAngle = (250f * value).toFloat(),
                    useCenter = false,
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun DiagnosisCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101827)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Пример гипотезы", color = Color(0xFF9FB3C8), fontSize = 13.sp)
            Text("Мотор не едет под нагрузкой", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Приложение ищет не один параметр, а связку причин", color = Color(0xFFFFD166), fontSize = 14.sp)
            Text(
                text = "Если throttle высокий, load низкий, MAF/MAP не растут, trims уходят в плюс, а зажигание откатывается поздно, приоритеты проверок меняются: топливо, воздух, выпуск, дроссель, зажигание, пропуски и питание.",
                color = Color(0xFFD7E3F1),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun LiveDataPreview() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD101827)),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Live Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val points = listOf(0.3f, 0.42f, 0.38f, 0.6f, 0.54f, 0.76f, 0.7f, 0.86f)
                val step = size.width / (points.size - 1)
                for (i in 0 until points.lastIndex) {
                    drawLine(
                        color = Color(0xFF2F80FF),
                        start = Offset(step * i, size.height * (1f - points[i])),
                        end = Offset(step * (i + 1), size.height * (1f - points[i + 1])),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceCurvesPreview(curveSet: ReferenceCurveSet?) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD101827)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Reference curves", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                text = curveSet?.let { "Public sample source: ${it.title}. Generic rules do not depend on this vehicle." }
                    ?: "Reference data is not loaded",
                color = Color(0xFF9FB3C8),
                fontSize = 13.sp,
            )
            curveSet?.curves
                ?.filter { it.xMetric == "rpm" && it.points.size >= 2 }
                ?.take(3)
                ?.forEach { curve ->
                    ReferenceCurveChart(curve)
                }
        }
    }
}

@Composable
private fun ReferenceCurveChart(curve: ReferenceCurve) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${curve.metric} / ${curve.xMetric}",
            color = Color(0xFFD7E3F1),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val points = curve.points.sortedBy { it.x }
            val minX = points.first().x
            val maxX = points.last().x.coerceAtLeast(minX + 1.0)
            val minY = points.minOf { it.p10 }
            val maxY = points.maxOf { it.p90 }.coerceAtLeast(minY + 1.0)
            fun x(value: Double): Float = ((value - minX) / (maxX - minX) * size.width).toFloat()
            fun y(value: Double): Float = (size.height - (value - minY) / (maxY - minY) * size.height).toFloat()

            for (i in 0 until points.lastIndex) {
                val a = points[i]
                val b = points[i + 1]
                drawLine(
                    color = Color(0x6642D392),
                    start = Offset(x(a.x), y(a.p10)),
                    end = Offset(x(b.x), y(b.p10)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0x6642D392),
                    start = Offset(x(a.x), y(a.p90)),
                    end = Offset(x(b.x), y(b.p90)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFF42D392),
                    start = Offset(x(a.x), y(a.p50)),
                    end = Offset(x(b.x), y(b.p50)),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
