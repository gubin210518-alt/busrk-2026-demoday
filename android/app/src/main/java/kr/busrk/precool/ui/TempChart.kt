package kr.busrk.precool.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.busrk.precool.data.Point
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 온도 그래프 (외부 라이브러리 없이 직접 그려요). AI 시뮬레이터 실시간 탭과 같은 모양이에요. */
@Composable
fun TempChart(points: List<Point>, targetC: Double, deadlineMin: Double, modifier: Modifier = Modifier) {
    val label = remember {
        android.graphics.Paint().apply { color = BoxColors.Muted.toArgb(); isAntiAlias = true }
    }
    Canvas(modifier) {
        label.textSize = 11.sp.toPx()
        val padL = 40.dp.toPx(); val padR = 8.dp.toPx(); val padT = 8.dp.toPx(); val padB = 22.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        if (w <= 0 || h <= 0) return@Canvas

        val tMax = max(deadlineMin + 5, (points.lastOrNull()?.tMin ?: 0.0) + 2)
        var lo = targetC - 2
        var hi = targetC + 10
        for (p in points) {
            p.berryC?.let { lo = min(lo, it); hi = max(hi, it) }
            p.coldC?.let { lo = min(lo, it); hi = max(hi, it) }
        }
        lo = floor(lo - 1); hi = ceil(hi + 1)
        fun x(t: Double) = (padL + w * (t / tMax)).toFloat()
        fun y(v: Double) = (padT + h * ((hi - v) / (hi - lo))).toFloat()

        // 눈금
        val yStep = max(1.0, ceil((hi - lo) / 6))
        var v = ceil(lo)
        while (v <= hi) {
            drawLine(BoxColors.Line, Offset(padL, y(v)), Offset(size.width - padR, y(v)), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText("${v.toInt()}℃", 0f, y(v) + 4.dp.toPx(), label)
            v += yStep
        }
        val tStep = if (tMax > 150) 30.0 else if (tMax > 60) 15.0 else 10.0
        var t = 0.0
        while (t <= tMax) {
            drawContext.canvas.nativeCanvas.drawText("${t.toInt()}분", x(t) - 8.dp.toPx(), size.height - 4.dp.toPx(), label)
            t += tStep
        }

        // 뚜껑 열린 구간
        for (i in 1 until points.size) {
            if (points[i].lidOpen) {
                val x0 = x(points[i - 1].tMin)
                drawRect(BoxColors.Red.copy(alpha = 0.08f), Offset(x0, padT), Size(x(points[i].tMin) - x0 + 1f, h))
            }
        }

        // 목표 온도(가로)와 제한 시간(세로) 점선
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        drawLine(BoxColors.Green, Offset(padL, y(targetC)), Offset(size.width - padR, y(targetC)), strokeWidth = 3f, pathEffect = dash)
        drawLine(BoxColors.Gray, Offset(x(deadlineMin), padT), Offset(x(deadlineMin), padT + h), strokeWidth = 3f, pathEffect = dash)

        // 온도 선 (값이 비면 끊어서 그려요)
        fun series(get: (Point) -> Double?, color: Color) {
            val path = Path()
            var on = false
            for (p in points) {
                val value = get(p)
                if (value == null) { on = false; continue }
                if (on) path.lineTo(x(p.tMin), y(value)) else path.moveTo(x(p.tMin), y(value))
                on = true
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        }
        series({ it.coldC }, BoxColors.Blue)
        series({ it.berryC }, BoxColors.Red)

        if (points.isEmpty()) {
            drawContext.canvas.nativeCanvas.drawText("데이터를 기다리는 중…", padL + 12.dp.toPx(), padT + h / 2, label)
        }
    }
}
