package com.example.rrrphone

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * 르르르 상태 위젯 애니메이션 — rereureu_A_발전모드.html / rereureu_B_EV모드.html 을 Compose Canvas 로 옮긴 것.
 * SVG 좌표(viewBox 0 0 320 280)와 키프레임 값을 그대로 사용한다. WebView 없이 그려서 기기를 타지 않는다.
 *
 * 폰 앱에서는 '주행 중 내비 위젯에 이렇게 뜬다'는 미리보기로 쓴다 (NaviWidgetCard).
 * 차량 앱 .../rrr/RrrWidgetAnimation.kt 에서 가져왔고, 위젯 모션 설정(WidgetMotion)만 뺐다.
 */

/**
 * @param mode  "gen"(발전) | "ev"
 * @param cycle true 면 3.5초마다 EV ↔ 발전 을 번갈아 보여줌 (자동 전환 미리보기)
 */
@Composable
fun RrrWidgetAnimation(mode: String, modifier: Modifier = Modifier, cycle: Boolean = false) {
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last >= 0) clock += (now - last)
                last = now
            }
        }
    }

    var shown by remember(mode) { mutableStateOf(mode) }
    if (cycle) {
        LaunchedEffect(Unit) {
            while (true) { delay(3500); shown = if (shown == "gen") "ev" else "gen" }
        }
    }
    val genAlpha by animateFloatAsState(if (shown == "gen") 1f else 0f, tween(700), label = "rrrMode")

    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = genAlpha }) {
            if (genAlpha > 0.01f) fitViewBox { drawGen(clock) }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - genAlpha }) {
            if (genAlpha < 0.99f) fitViewBox { drawEv(clock) }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 640, heightDp = 280, showBackground = true)
@Composable
private fun RrrWidgetAnimationPreview() {
    androidx.compose.foundation.layout.Row {
        RrrWidgetAnimation("gen", Modifier.weight(1f).fillMaxSize())
        RrrWidgetAnimation("ev", Modifier.weight(1f).fillMaxSize())
    }
}

// ───────────────────────── 공통 ─────────────────────────

private val WInk = Color(0xFF1E1E1E)
private val Skin = Color(0xFFFDE4BE)
private val Shell = Color(0xFF9BC9FF)

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

private object Paths {
    val antL = p("M 124 96 L 114 75 A 9 9 0 1 1 125 71 L 135 90")
    val antR = p("M 176 90 L 186 71 A 9 9 0 1 1 197 75 L 187 96")
    val body = p("M 124 95 C 142 82 169 82 187 95 C 199 109 204 128 204 148 L 202 224 C 203 233 206 238 201 242 C 194 246 164 246 161 241 C 159 237 159 229 155 229 C 151 229 151 237 149 241 C 146 246 116 246 109 242 C 104 238 107 233 108 224 L 107 148 C 107 128 112 109 124 95 Z")
    val straps = listOf(
        p("M 108 145 L 126 160 L 121 169 L 106 155 Z"),
        p("M 203 145 L 185 160 L 190 169 L 205 155 Z"),
        p("M 107 196 L 122 190 L 124 200 L 107 207 Z"),
        p("M 203 196 L 188 190 L 186 200 L 203 207 Z"),
    )
    val swirl = p("M 155 186 C 158 183 163 185 162 190 C 160 196 150 197 147 191 C 143 183 151 174 160 176 C 171 178 174 192 167 200 C 158 209 141 206 137 195 C 132 181 143 166 158 166 C 175 166 184 182 179 198")
    val flap = p("M 117 177 C 124 203 186 203 193 177")
    val buckle = p("M 146 196 C 143 191 138 193 138 199 C 138 205 143 207 146 202 L 164 202 C 167 207 172 205 172 199 C 172 193 167 191 164 196 Z")
    val mouth = p("M 145 128 Q 150 134 155 129 Q 160 134 165 128")
    val eyeGenL = p("M 132 115 L 140 119 L 132 123")
    val eyeGenR = p("M 178 115 L 170 119 L 178 123")
    val eyeEvL = p("M 130 118 Q 136 123 142 118")
    val eyeEvR = p("M 168 118 Q 174 123 180 118")
    val sweat = p("M 185 98 C 185 98 190 105 190 108.5 A 5 5 0 1 1 180 108.5 C 180 105 185 98 185 98 Z")
    val guideL = p("M 24 176 Q 56 174 86 184")
    val guideR = p("M 244 166 Q 251 163 257 168")
    val badgeDrop = p("M 11 10 C 11 10 16 16.5 16 19.5 A 5 5 0 1 1 6 19.5 C 6 16.5 11 10 11 10 Z")
    val fuel1 = p("M 44 166 C 44 166 50 174 50 177.5 A 6 6 0 1 1 38 177.5 C 38 174 44 166 44 166 Z")
    val fuel2 = p("M 44 168 C 44 168 49 174.5 49 177.5 A 5 5 0 1 1 39 177.5 C 39 174.5 44 168 44 168 Z")
    val bolt1 = p("M 207 146 L 198 159 L 205 159 L 202 171 L 213 157 L 206 157 Z")
    val bolt2 = p("M 196 144 L 190 153 L 195 153 L 193 161 L 200 152 L 195 152 Z")
    val sparkBolt = p("M 290 131 l -3 5 h 3 l -2 5 l 5 -6 h -3 l 2 -4 z")
    val star = p("M 236 100 L 238 105 L 243 107 L 238 109 L 236 114 L 234 109 L 229 107 L 234 105 Z")
}

/** CSS 키프레임: kf(p, t0, v0, t1, v1, …) — 구간마다 ease-in-out */
private fun kf(p: Float, vararg k: Float): Float {
    if (p <= k[0]) return k[1]
    var i = 0
    while (i + 2 < k.size) {
        val t0 = k[i]; val t1 = k[i + 2]
        if (p <= t1) {
            val f = if (t1 > t0) (p - t0) / (t1 - t0) else 1f
            val e = f * f * (3 - 2 * f)
            return k[i + 1] + (k[i + 3] - k[i + 1]) * e
        }
        i += 2
    }
    return k[k.size - 1]
}

private fun phase(t: Float, period: Float) = (t % period) / period

private fun DrawScope.fitViewBox(block: DrawScope.() -> Unit) {
    val s = min(size.width / 320f, size.height / 280f)
    withTransform({
        translate((size.width - 320f * s) / 2f, (size.height - 280f * s) / 2f)
        scale(s, s, Offset.Zero)
    }) { block() }
}

private fun DrawScope.shape(path: Path, fill: Color?, stroke: Color? = WInk, sw: Float = 3f, a: Float = 1f) {
    fill?.let { drawPath(path, it, alpha = a) }
    stroke?.let { drawPath(path, it, alpha = a, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

private fun DrawScope.rrect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color?, stroke: Color? = WInk, sw: Float = 3f, a: Float = 1f) {
    fill?.let { drawRoundRect(it, Offset(x, y), Size(w, h), CornerRadius(r), alpha = a) }
    stroke?.let { drawRoundRect(it, Offset(x, y), Size(w, h), CornerRadius(r), alpha = a, style = Stroke(sw)) }
}

private fun DrawScope.circle(cx: Float, cy: Float, r: Float, fill: Color?, stroke: Color? = WInk, sw: Float = 3f, a: Float = 1f) {
    fill?.let { drawCircle(it, r, Offset(cx, cy), alpha = a) }
    stroke?.let { drawCircle(it, r, Offset(cx, cy), alpha = a, style = Stroke(sw)) }
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, w: Float, a: Float = 1f) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round, alpha = a)

private fun DrawScope.arm(sx: Float, sy: Float, hx: Float, hy: Float) {
    line(sx, sy, hx, hy, WInk, 17f)
    circle(hx, hy, 7.5f, Skin, WInk, 3f)
    line(sx, sy, hx, hy, Skin, 11f)
}

private fun DrawScope.generator(kx: Float, ky: Float, a: Float) {
    rrect(222f, 194f, 30f, 48f, 6f, Color(0xFF64748B), WInk, 2.8f, a)
    line(229f, 210f, 245f, 210f, Color(0xFF94A3B8), 2.4f, a)
    line(229f, 218f, 245f, 218f, Color(0xFF94A3B8), 2.4f, a)
    circle(237f, 178f, 15f, Color(0xFFF8FAFC), WInk, 3f, a)
    circle(237f, 178f, 8f, Color(0xFFFDE68A), Color(0xFFF59E0B), 2f, a)
    line(237f, 178f, kx, ky, WInk, 3f, a)
    circle(kx, ky, 4f, Color(0xFFF5A623), WInk, 2.4f, a)
}

private fun DrawScope.shadow() =
    drawOval(Color(0xFFCBD5E1), Offset(90f, 238f), Size(160f, 12f), alpha = 0.45f)

/** 몸통 (더듬이·몸·가방·얼굴). 호출 측에서 bob 만큼 translate 한 상태 */
private fun DrawScope.rereureu(
    antenna: Float, swirl: Color, cheeks: Float, gen: Boolean, extra: DrawScope.() -> Unit = {},
) {
    withTransform({ rotate(antenna, Offset(127f, 92f)) }) { shape(Paths.antL, Skin, WInk, 3.2f) }
    withTransform({ rotate(-antenna, Offset(183f, 92f)) }) { shape(Paths.antR, Skin, WInk, 3.2f) }
    shape(Paths.body, Skin, WInk, 3.2f)
    Paths.straps.forEach { shape(it, Shell, WInk, 3f) }
    drawOval(Shell, Offset(115f, 149f), Size(80f, 74f))
    drawOval(WInk, Offset(115f, 149f), Size(80f, 74f), style = Stroke(3.2f))
    shape(Paths.swirl, null, swirl, 2.3f)
    shape(Paths.flap, null, WInk, 3f)
    shape(Paths.buckle, Color.White, WInk, 3f)
    drawOval(Color(0xFFFF8A80), Offset(120.5f, 123.2f), Size(13f, 7.6f), alpha = cheeks)
    drawOval(Color(0xFFFF8A80), Offset(176.5f, 123.2f), Size(13f, 7.6f), alpha = cheeks)
    if (gen) { shape(Paths.eyeGenL, null, WInk, 3.2f); shape(Paths.eyeGenR, null, WInk, 3.2f) }
    else { shape(Paths.eyeEvL, null, WInk, 3.2f); shape(Paths.eyeEvR, null, WInk, 3.2f) }
    shape(Paths.mouth, null, WInk, 3f)
    extra()
}

private fun DrawScope.batteryShell() {
    rrect(272f, 143f, 16f, 6f, 2.5f, Color(0xFF475569), WInk, 2.5f)
    rrect(261f, 149f, 38f, 64f, 8f, Color.White, WInk, 3f)
}

private fun DrawScope.fuelBadge(dim: Boolean) {
    val a = if (dim) 0.3f else 1f
    val fill = if (dim) Color(0xFFE5E7EB) else Color(0xFFFEF3C7)
    val accent = if (dim) Color(0xFF9CA3AF) else Color(0xFFF5A623)
    withTransform({ translate(14f, 156f) }) {
        rrect(0f, 4f, 22f, 26f, 5f, fill, accent, 2.2f, a)
        drawPath(Paths.badgeDrop, accent, alpha = a)
    }
}

// ───────────────────────── A. 발전 모드 (2.6초 루프) ─────────────────────────

private fun DrawScope.drawGen(t: Float) {
    val p = phase(t, 2600f)
    val half = phase(t, 1300f)

    // 흐름 점선
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), (8f - (half * 16f) % 8f) % 8f)
    drawPath(Paths.guideL, Color(0xFFF5A623), alpha = 0.45f, style = Stroke(2.2f, cap = StrokeCap.Round, pathEffect = dash))
    drawPath(Paths.guideR, Color(0xFFFBBF24), alpha = 0.45f, style = Stroke(2.2f, cap = StrokeCap.Round, pathEffect = dash))

    // 크랭크 한 바퀴 1.3초, 몸이 한 번 들썩
    val a = half * 2f * PI.toFloat()
    val kx = 237f + cos(a) * 10f
    val ky = 178f + sin(a) * 10f
    val bob = -2.2f * (0.5f - 0.5f * cos(a))

    withTransform({ translate(-12f, 0f) }) {
        shadow()
        generator(kx, ky, 1f)
        arm(196f, 160f + bob, kx, ky)
        arm(114f, 160f + bob, 91f, 188f + bob * 0.6f + sin(a) * 1.5f)
        withTransform({ translate(0f, bob) }) {
            rereureu(
                antenna = kf(half, 0f, 0f, 0.5f, -5f, 1f, 0f),
                swirl = Color(0xFF7FB4F5),
                cheeks = kf(half, 0f, 0.55f, 0.5f, 0.85f, 1f, 0.55f),
                gen = true,
            ) {
                // 이마 땀방울
                val o = kf(p, 0f, 0f, 0.3f, 0.95f, 0.8f, 0.9f, 1f, 0f)
                val ty = kf(p, 0f, -2f, 0.3f, 0f, 0.8f, 4f, 1f, 6f)
                val s = kf(p, 0f, 0.85f, 0.3f, 1f, 0.8f, 1f, 1f, 0.85f)
                withTransform({ translate(0f, ty); scale(s, s, Offset(184f, 104f)) }) {
                    shape(Paths.sweat, Color(0xFF60A5FA), WInk, 2.2f, o)
                }
            }
        }
    }

    // 배터리 (윗칸이 천천히 차오름)
    batteryShell()
    rrect(267f, 193f, 26f, 13f, 3f, Color(0xFFF59E0B), null)
    rrect(267f, 176f, 26f, 13f, 3f, Color(0xFFFBBF24), null)
    val co = kf(p, 0f, 0.22f, 0.45f, 0.22f, 0.72f, 1f, 0.9f, 1f, 1f, 0.22f)
    val cs = kf(p, 0f, 0.85f, 0.45f, 0.85f, 0.72f, 1f, 0.9f, 1f, 1f, 0.85f)
    withTransform({ scale(1f, cs, Offset(280f, 168f)) }) {
        rrect(267f, 159f, 26f, 13f, 3f, Color(0xFFFDE047), Color(0xFFF59E0B), 1.5f, co)
    }

    // 연료 → 발전기
    fuelBadge(dim = false)
    withTransform({
        translate(kf(p, 0f, 0f, 0.2f, 16f, 0.75f, 46f, 0.95f, 54f, 1f, 0f), kf(p, 0f, 0f, 0.2f, 2f, 0.75f, 6f, 0.95f, 8f, 1f, 0f))
        val s = kf(p, 0f, 0.8f, 0.2f, 1f, 0.75f, 0.95f, 0.95f, 0.6f, 1f, 0.8f); scale(s, s, Offset.Zero)
    }) { shape(Paths.fuel1, Color(0xFFF5A623), Color(0xFFD97706), 1.5f, kf(p, 0f, 0f, 0.2f, 1f, 0.75f, 1f, 0.95f, 0f, 1f, 0f)) }
    withTransform({
        translate(kf(p, 0f, 0f, 0.4f, 0f, 0.55f, 18f, 0.88f, 46f, 1f, 54f), kf(p, 0f, 0f, 0.4f, 0f, 0.55f, -1f, 0.88f, 6f, 1f, 8f))
        val s = kf(p, 0f, 0.8f, 0.4f, 0.8f, 0.55f, 1f, 0.88f, 0.95f, 1f, 0.6f); scale(s, s, Offset.Zero)
    }) { shape(Paths.fuel2, Color(0xFFFBBF24), Color(0xFFD97706), 1.5f, kf(p, 0f, 0f, 0.4f, 0f, 0.55f, 1f, 0.88f, 1f, 1f, 0f)) }

    // 번개: 발전기 위로 톡 → 배터리로
    withTransform({
        translate(22f, -6f)
        translate(
            kf(p, 0f, 0f, 0.15f, 0f, 0.32f, 4f, 0.55f, 26f, 0.82f, 54f, 0.95f, 62f, 1f, 0f),
            kf(p, 0f, 6f, 0.15f, 6f, 0.32f, -8f, 0.55f, -6f, 0.82f, 4f, 0.95f, 8f, 1f, 6f),
        )
        val s = kf(p, 0f, 0.6f, 0.15f, 0.6f, 0.32f, 1.05f, 0.55f, 1f, 0.82f, 0.92f, 0.95f, 0.65f, 1f, 0.6f)
        scale(s, s, Offset(205f, 158f))
    }) {
        shape(Paths.bolt1, Color(0xFFFACC15), Color(0xFFB45309), 1.8f,
            kf(p, 0f, 0f, 0.15f, 0f, 0.32f, 1f, 0.82f, 1f, 0.95f, 0f, 1f, 0f))
    }
    withTransform({
        translate(32f, -6f)
        translate(kf(p, 0f, 0f, 0.45f, 0f, 0.62f, -2f, 0.85f, 12f, 1f, 0f), kf(p, 0f, 4f, 0.45f, 4f, 0.62f, -10f, 0.85f, -12f, 1f, 4f))
        val s = kf(p, 0f, 0.5f, 0.45f, 0.5f, 0.62f, 0.95f, 0.85f, 0.75f, 1f, 0.5f)
        scale(s, s, Offset(196f, 154f))
    }) {
        shape(Paths.bolt2, Color(0xFFFEF08A), Color(0xFFD97706), 1.5f,
            kf(p, 0f, 0f, 0.45f, 0f, 0.62f, 1f, 0.85f, 1f, 1f, 0f))
    }
}

// ───────────────────────── B. EV 모드 (3.2초 루프) ─────────────────────────

private val wonPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = 11f
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    color = Color(0xFFB45309).toArgb()
}

private fun DrawScope.drawEv(t: Float) {
    val p = phase(t, 3200f)

    drawPath(Paths.guideL, Color(0xFFCBD5E1), alpha = 0.48f,
        style = Stroke(2.2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))

    // 3.2초 숨쉬기
    val b = p * 2f * PI.toFloat()
    val br = -1.4f * (0.5f - 0.5f * cos(b))

    withTransform({ translate(-12f, 0f) }) {
        shadow()
        generator(247f, 178f, 0.35f)
        arm(114f, 160f + br, 96f, 190f + br * 0.5f)
        arm(196f, 160f + br, 214f, 190f + br * 0.5f)
        withTransform({ translate(0f, br) }) {
            rereureu(
                antenna = kf(p, 0f, 0f, 0.5f, -5f, 1f, 0f),
                swirl = lerp(Color(0xFF7FB4F5), Color(0xFF2DD4AA), kf(p, 0f, 0f, 0.5f, 1f, 1f, 0f)),
                cheeks = 1f,
                gen = false,
            )
        }
    }

    // 배터리 후광 (blur 대신 겹친 반투명 사각형)
    val ho = kf(p, 0f, 0.18f, 0.5f, 0.42f, 1f, 0.18f)
    val hs = kf(p, 0f, 1f, 0.5f, 1.06f, 1f, 1f)
    withTransform({ scale(hs, hs, Offset(280f, 179f)) }) {
        for (i in 0..2) {
            val g = i * 3f
            rrect(253f - g, 137f - g, 54f + g * 2, 84f + g * 2, 14f + g, Color(0xFF34D399), null, a = ho * (0.55f - i * 0.15f))
        }
    }
    // 떠오르는 에너지 입자
    fun spark(delayMs: Float, cx: Float, cy: Float, draw: DrawScope.(Float) -> Unit) {
        val q = phase(t + delayMs, 3200f)
        val e = 1f - (1f - q) * (1f - q)
        val o = kf(q, 0f, 0f, 0.25f, 0.9f, 1f, 0f)
        val s = 0.6f + 0.4f * e
        withTransform({ translate(0f, -26f * e); scale(s, s, Offset(cx, cy)) }) { draw(o) }
    }
    spark(0f, 272f, 136f) { circle(272f, 136f, 2.6f, Color(0xFF10B981), null, a = it) }
    spark(1100f, 284f, 134f) { circle(284f, 134f, 2f, Color(0xFF34D399), null, a = it) }
    spark(2200f, 290f, 136f) { drawPath(Paths.sparkBolt, Color(0xFF10B981), alpha = it) }

    batteryShell()
    rrect(267f, 193f, 26f, 13f, 3f, Color(0xFF059669), null)
    rrect(267f, 176f, 26f, 13f, 3f, Color(0xFF10B981), null)
    rrect(267f, 159f, 26f, 13f, 3f, Color(0xFF34D399), Color(0xFF059669), 1.5f, kf(p, 0f, 1f, 0.5f, 0.8f, 1f, 1f))

    fuelBadge(dim = true)

    // 동전 반짝 (한 루프에 한 번)
    val co = kf(p, 0f, 0f, 0.55f, 0f, 0.68f, 1f, 0.85f, 1f, 1f, 0f)
    if (co > 0.01f) {
        val ty = kf(p, 0f, 6f, 0.55f, 6f, 0.68f, 0f, 0.85f, -3f, 1f, -6f)
        val s = kf(p, 0f, 0.7f, 0.55f, 0.7f, 0.68f, 1f, 0.85f, 1f, 1f, 0.9f)
        withTransform({ translate(0f, ty); scale(s, s, Offset(228f, 110.5f)) }) {
            circle(222f, 112f, 9f, Color(0xFFFDE68A), WInk, 2.4f, co)
            wonPaint.alpha = (co * 255).toInt()
            drawContext.canvas.nativeCanvas.drawText("₩", 222f, 116.5f, wonPaint)
            drawPath(Paths.star, Color(0xFFFACC15), alpha = co)
        }
    }
}
