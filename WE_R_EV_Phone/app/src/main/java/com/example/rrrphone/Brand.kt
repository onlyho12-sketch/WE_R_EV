package com.example.rrrphone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * WE'R EV 브랜드 요소 — 차량 앱(rrr_pleos_3/app)과 같은 로고·제목 스타일·라인 아이콘.
 * 차량 앱 MainActivity.kt 의 WerEvLogo / werEvTitle / menuBrandTitle, LineIcons.kt 와 맞춰 둘 것.
 */

/** W·' 는 연한 색, E·R 는 네이비 — 차량 앱과 같은 색 */
private val WerMuted = Color(0xFFA7B3C6)

/** "WE'R EV" 로고 글자 */
fun werEvTitle(): AnnotatedString = buildAnnotatedString {
    val muted = SpanStyle(color = WerMuted)
    val strong = SpanStyle(color = Navy)
    withStyle(muted) { append("W") }
    withStyle(strong) { append("E") }
    withStyle(muted) { append("'") }
    withStyle(strong) { append("R EV") }
}

/** 페이지 제목 "WE'R Strategy" 처럼 WE'R 를 브랜드 색으로, 나머지는 기본 글자색 */
fun brandTitle(title: String): AnnotatedString = buildAnnotatedString {
    if (title.startsWith("WE'R")) {
        withStyle(SpanStyle(color = WerMuted)) { append("W") }
        withStyle(SpanStyle(color = Navy)) { append("E") }
        withStyle(SpanStyle(color = WerMuted)) { append("'") }
        withStyle(SpanStyle(color = Navy)) { append("R") }
        append(title.removePrefix("WE'R"))
    } else append(title)
}

/** 계기판 모양 로고 + WE'R EV */
@Composable
fun WerEvLogo(size: Dp = 30.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * .105f
            val inset = stroke / 2
            val dial = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Navy, 145f, 218f, false, Offset(inset, inset), dial, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Muted, -30f, 65f, false, Offset(inset, inset), dial, style = Stroke(stroke, cap = StrokeCap.Round))
            val c = Offset(this.size.width * .5f, this.size.height * .53f)
            drawLine(Navy, c, Offset(this.size.width * .67f, this.size.height * .26f), stroke * .62f, cap = StrokeCap.Round)
            drawCircle(Navy, stroke * .67f, c)
        }
        Spacer(Modifier.width(size * 0.27f))
        Text(werEvTitle(), color = Navy, fontSize = (size.value * 0.6f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}

// ───────────── 라인 아이콘 (이모지 대신, 24 그리드) ─────────────

enum class Ic { Home, Calendar, Car, Bolt, Chart, Plug, Pin, Fuel, Fan, Phone, ArrowRight, Check }

@Composable
fun LineIcon(icon: Ic, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun DrawScope.line(vararg pts: Pair<Float, Float>, close: Boolean = false) {
            val path = Path().apply {
                pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
                if (close) close()
            }
            drawPath(path, color, style = stroke)
        }
        fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)

        when (icon) {
            Ic.Home -> {
                line(3f to 11f, 12f to 3.5f, 21f to 11f)
                line(5.5f to 9f, 5.5f to 20f, 18.5f to 20f, 18.5f to 9f)
                line(10f to 20f, 10f to 14f, 14f to 14f, 14f to 20f)
            }
            Ic.Calendar -> {
                rect(3.5f, 5f, 17f, 15.5f, 2.5f)
                line(3.5f to 10f, 20.5f to 10f)
                line(8f to 3f, 8f to 7f); line(16f to 3f, 16f to 7f)
            }
            Ic.Car -> {
                line(3.5f to 15.5f, 3.5f to 12f, 6f to 6.5f, 18f to 6.5f, 20.5f to 12f, 20.5f to 15.5f, close = true)
                line(3.5f to 12f, 20.5f to 12f)
                drawCircle(color, 1.8f * u, p(7.5f, 17.5f), style = stroke)
                drawCircle(color, 1.8f * u, p(16.5f, 17.5f), style = stroke)
            }
            Ic.Bolt -> line(13.5f to 2.5f, 5f to 13.5f, 11.5f to 13.5f, 10.5f to 21.5f, 19f to 10.5f, 12.5f to 10.5f, close = true)
            Ic.Chart -> {
                line(3.5f to 20.5f, 20.5f to 20.5f)
                line(7f to 17f, 7f to 12f); line(12f to 17f, 12f to 6f); line(17f to 17f, 17f to 9.5f)
            }
            Ic.Plug -> {
                line(9f to 3f, 9f to 7.5f); line(15f to 3f, 15f to 7.5f)
                line(6f to 7.5f, 18f to 7.5f, 18f to 11f, 15f to 15f, 9f to 15f, 6f to 11f, close = true)
                line(12f to 15f, 12f to 21f)
            }
            Ic.Pin -> {
                drawCircle(color, 2.5f * u, p(12f, 10f), style = stroke)
                line(12f to 21f, 6.5f to 14f); line(12f to 21f, 17.5f to 14f)
                drawArc(color, 140f, 260f, false, p(5f, 3f), Size(14f * u, 14f * u), style = stroke)
            }
            Ic.Fuel -> {
                rect(4.5f, 3.5f, 10f, 17f, 1.5f)
                line(4.5f to 10f, 14.5f to 10f)
                line(14.5f to 8f, 18f to 11f, 18f to 18f, 19.5f to 19f)
            }
            Ic.Fan -> {
                drawCircle(color, 1.6f * u, p(12f, 12f), style = stroke)
                line(12f to 10.4f, 12f to 4f); line(13.4f to 12.8f, 19f to 16f); line(10.6f to 12.8f, 5f to 16f)
            }
            Ic.Phone -> {
                rect(7f, 2.5f, 10f, 19f, 2f)
                line(10.5f to 18.5f, 13.5f to 18.5f)
            }
            Ic.ArrowRight -> { line(4.5f to 12f, 19f to 12f); line(13f to 6f, 19f to 12f, 13f to 18f) }
            Ic.Check -> line(5f to 12.5f, 10f to 17.5f, 19f to 7f)
        }
    }
}
