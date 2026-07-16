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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.autodoctor.aipro.ui.design.AutoDoctorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoDoctorTheme {
                DashboardScreen()
            }
        }
    }
}

@Composable
private fun DashboardScreen() {
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
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                HealthCard("Engine", "Needs analysis", 0.72, Modifier.weight(1f))
                HealthCard("Fuel system", "Trim drift", 0.61, Modifier.weight(1f))
            }
            DiagnosisCard()
            LiveDataPreview()
        }
    }
}

@Composable
private fun HealthCard(title: String, subtitle: String, value: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
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
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE101827)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Вероятная гипотеза", color = Color(0xFF9FB3C8), fontSize = 13.sp)
            Text("Подсос воздуха во впуске", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Вероятность: 91%  |  Серьезность: Warning", color = Color(0xFFFFD166), fontSize = 14.sp)
            Text(
                text = "Положительные топливные коррекции вместе с бедной смесью и заниженным расчетным наполнением цилиндров указывают, что ЭБУ добавляет топливо для компенсации неучтенного воздуха.",
                color = Color(0xFFD7E3F1),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun LiveDataPreview() {
    Card(
        shape = RoundedCornerShape(30.dp),
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
