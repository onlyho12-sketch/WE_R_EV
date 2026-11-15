package com.example.rrr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 유니코드 기호·이모지 대신 쓰는 얇은 라인 아이콘 (24 그리드 기준) */
enum class Ic { Home, Bolt, Chart, Reward, Nav, Swap, ChevronLeft, Menu, ArrowRight, Mic, Battery, Fuel }

@Composable
fun LineIcon(icon: Ic, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun path(vararg pts: Pair<Float, Float>, close: Boolean = false) = Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
            if (close) close()
        }
        fun DrawScope.line(vararg pts: Pair<Float, Float>, close: Boolean = false) =
            drawPath(path(*pts, close = close), color, style = stroke)

        when (icon) {
            Ic.Home -> {
                line(3f to 11f, 12f to 3.5f, 21f to 11f)
                line(5.5f to 9f, 5.5f to 20f, 18.5f to 20f, 18.5f to 9f)
                line(10f to 20f, 10f to 14f, 14f to 14f, 14f to 20f)
            }
            Ic.Bolt -> line(13.5f to 2.5f, 5f to 13.5f, 11.5f to 13.5f, 10.5f to 21.5f, 19f to 10.5f, 12.5f to 10.5f, close = true)
            Ic.Chart -> {
                line(3.5f to 20.5f, 20.5f to 20.5f)
                line(7f to 17f, 7f to 12f)
                line(12f to 17f, 12f to 6f)
                line(17f to 17f, 17f to 9.5f)
            }
            Ic.Reward -> {
                line(4f to 9f, 20f to 9f, 20f to 12.5f, 4f to 12.5f, close = true)
                line(5.5f to 12.5f, 5.5f to 20.5f, 18.5f to 20.5f, 18.5f to 12.5f)
                line(12f to 9f, 12f to 20.5f)
                drawArc(color, 180f, 270f, false, p(7.5f, 4f), Size(4.5f * u, 5f * u), style = stroke)
                drawArc(color, 90f, 270f, false, p(12f, 4f), Size(4.5f * u, 5f * u), style = stroke)
            }
            Ic.Nav -> line(20.5f to 3.5f, 3.5f to 10.5f, 11f to 13f, 13.5f to 20.5f, close = true)
            Ic.Swap -> {
                line(8f to 4f, 8f to 20f); line(4.5f to 7.5f, 8f to 4f, 11.5f to 7.5f)
                line(16f to 20f, 16f to 4f); line(12.5f to 16.5f, 16f to 20f, 19.5f to 16.5f)
            }
            Ic.ChevronLeft -> line(15f to 5f, 8f to 12f, 15f to 19f)
            Ic.Menu -> { line(4f to 7f, 20f to 7f); line(4f to 12f, 20f to 12f); line(4f to 17f, 20f to 17f) }
            Ic.ArrowRight -> { line(4.5f to 12f, 19f to 12f); line(13f to 6f, 19f to 12f, 13f to 18f) }
            Ic.Mic -> {
                drawRoundRect(color, p(9f, 3f), Size(6f * u, 11f * u),
                    androidx.compose.ui.geometry.CornerRadius(3f * u), style = stroke)
                drawArc(color, 0f, 180f, false, p(5.5f, 6.5f), Size(13f * u, 11f * u), style = stroke)
                line(12f to 17.5f, 12f to 21f)
            }
            Ic.Battery -> {
                // 세로 배터리 + 단자 + 번개
                drawRoundRect(color, p(6.5f, 5f), Size(11f * u, 16f * u),
                    androidx.compose.ui.geometry.CornerRadius(2f * u), style = stroke)
                line(10f to 2.8f, 14f to 2.8f)
                line(13f to 8.5f, 10f to 13.5f, 14f to 13.5f, 11f to 18f)
            }
            Ic.Fuel -> {
                // 주유기 몸통 + 창 + 호스
                drawRoundRect(color, p(4f, 3.5f), Size(10f * u, 17f * u),
                    androidx.compose.ui.geometry.CornerRadius(1.5f * u), style = stroke)
                line(6.5f to 6.5f, 11.5f to 6.5f, 11.5f to 10f, 6.5f to 10f, close = true)
                line(14f to 8f, 17.5f to 10.5f, 17.5f to 17f, 19.5f to 17f, 19.5f to 9f, 17.5f to 6.5f)
                line(2.5f to 20.5f, 15.5f to 20.5f)
            }
        }
    }
}
