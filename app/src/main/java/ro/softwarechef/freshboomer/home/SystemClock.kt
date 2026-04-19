package ro.softwarechef.freshboomer.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ro.softwarechef.freshboomer.data.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SystemClock(
    modifier: Modifier = Modifier,
    timeFormat: String = "HH:mm"
) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            hours = now.get(Calendar.HOUR)
            minutes = now.get(Calendar.MINUTE)
            seconds = now.get(Calendar.SECOND)
            val sdf = SimpleDateFormat(timeFormat, Locale.getDefault())
            val sdf2 = SimpleDateFormat("EEEE, d MMMM yyyy", LocaleHelper.getLocale())
            currentTime = sdf.format(Date())
            currentDate = sdf2.format(Date())
            delay(1000)
        }
    }

    val clockColor = MaterialTheme.colorScheme.onBackground
    val clockSize = 40.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(clockSize)
            .padding(bottom = 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalogClock(
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            color = clockColor,
            modifier = Modifier.size(clockSize)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = currentTime,
            style = MaterialTheme.typography.titleLarge,
            color = clockColor,
            lineHeight = clockSize.value.sp
        )
        Spacer(Modifier.width(50.dp))
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = clockColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = currentDate,
            style = MaterialTheme.typography.headlineLarge,
            color = clockColor,
            lineHeight = clockSize.value.sp
        )
    }
    Spacer(androidx.compose.ui.Modifier.height(32.dp))
}

@Composable
private fun AnalogClock(
    hours: Int,
    minutes: Int,
    seconds: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val hourAngle = Math.toRadians(((hours % 12) * 30.0 + minutes * 0.5) - 90.0).toFloat()
    val minuteAngle = Math.toRadians((minutes * 6.0 + seconds * 0.1) - 90.0).toFloat()
    val secondAngle = Math.toRadians((seconds * 6.0) - 90.0).toFloat()

    val accent = MaterialTheme.colorScheme.primary
    val darkMode = isSystemInDarkTheme()

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f

        if (darkMode) {
            drawCircle(
                color = accent.copy(alpha = 0.12f),
                radius = radius * 1.1f,
                center = Offset(cx, cy)
            )
        }

        drawCircle(
            color = color.copy(alpha = 0.25f),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = accent.copy(alpha = 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )

        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0 - 90.0).toFloat()
            val isMajor = i % 3 == 0
            val innerR = radius * (if (isMajor) 0.78f else 0.82f)
            val outerR = radius * 0.95f
            drawLine(
                color = if (isMajor) accent else color.copy(alpha = 0.5f),
                start = Offset(cx + innerR * kotlin.math.cos(angle), cy + innerR * kotlin.math.sin(angle)),
                end = Offset(cx + outerR * kotlin.math.cos(angle), cy + outerR * kotlin.math.sin(angle)),
                strokeWidth = if (isMajor) 2.5f else 1.5f,
                cap = StrokeCap.Round
            )
        }

        val hourLen = radius * 0.5f
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + hourLen * kotlin.math.cos(hourAngle), cy + hourLen * kotlin.math.sin(hourAngle)),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        val minuteLen = radius * 0.72f
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + minuteLen * kotlin.math.cos(minuteAngle), cy + minuteLen * kotlin.math.sin(minuteAngle)),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        val secondLen = radius * 0.8f
        drawLine(
            color = accent.copy(alpha = 0.85f),
            start = Offset(cx, cy),
            end = Offset(cx + secondLen * kotlin.math.cos(secondAngle), cy + secondLen * kotlin.math.sin(secondAngle)),
            strokeWidth = 1f,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = accent,
            radius = 3f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color.White,
            radius = 1.5f,
            center = Offset(cx, cy)
        )
    }
}
