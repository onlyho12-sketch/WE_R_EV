package com.example.rrr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * 화면 상황별 르르르 애니메이션 (위젯 애니메이션과 같은 캐릭터 그림체, 장면은 새로 만듦).
 *  - Talk    : 오늘의 제안 — 차 옆에서 말풍선 글자에 맞춰 입을 뻥끗, 차 쪽으로 손 흔들기
 *  - Cheer   : 에너지 리포트 '점수 올랐어요' — 만세 점프 + 꽃가루 + "+3" 배지
 *  - Collect : 에코 리워드 '이번 달 챌린지' — 하늘에서 떨어지는 포인트 동전을 가방으로 받기
 * 좌표는 RrrWidgetAnimation 과 같은 캐릭터 좌표계(몸 중심 x≈155, 발 y≈246)를 쓴다.
 */
enum class RrrMoment(val x: Float, val y: Float, val w: Float, val h: Float) {
    Talk(60f, 50f, 200f, 206f),
    Cheer(40f, 24f, 232f, 234f),
    Collect(62f, 14f, 190f, 242f);

    val aspect: Float get() = w / h
}

/**
 * @param energy Talk 에서 옆에 떠 있는 아이콘 (전기 ⚡ / 발전 ⛽)
 * @param key    바뀔 때마다 장면을 처음부터 (Talk: 말 시작 + 손 인사)
 * @param talkMs Talk 에서 입을 뻥끗거릴 시간 (말풍선 타이핑 길이)
 */
@Composable
fun RrrMomentAnimation(
    moment: RrrMoment,
    modifier: Modifier = Modifier,
    energy: Energy = Energy.Electric,
    key: Any? = null,
    talkMs: Int = 0,
) {
    val ctx = LocalContext.current
    val motion = remember { WidgetLink.load(ctx).motion }
    val rate = when (motion) { WidgetMotion.Normal -> 1f; WidgetMotion.Calm -> 0.5f; WidgetMotion.Off -> 0f }

    var clock by remember { mutableFloatStateOf(0f) }
    var origin by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rate) {
        if (rate == 0f) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last >= 0) clock += (now - last) * rate
                last = now
            }
        }
    }
    LaunchedEffect(key) { origin = clock }

    Canvas(modifier) {
        val frozen = rate == 0f
        val t = if (frozen) FROZEN[moment.ordinal] else clock
        val since = if (frozen) 99_999f else clock - origin
        fitBox(moment) {
            when (moment) {
                RrrMoment.Talk -> drawTalk(t, since, talkMs.toFloat(), energy)
                RrrMoment.Cheer -> drawCheer(t)
                RrrMoment.Collect -> drawCollect(t)
            }
        }
    }
}

/** '멈춤' 설정일 때 보여줄 정지 프레임 */
private val FROZEN = floatArrayOf(400f, 900f, 1350f)

@Preview(widthDp = 600, heightDp = 220, showBackground = true)
@Composable
private fun RrrMomentsPreview() {
    Row {
        RrrMomentAnimation(RrrMoment.Talk, Modifier.size(200.dp))
        RrrMomentAnimation(RrrMoment.Cheer, Modifier.size(200.dp))
        RrrMomentAnimation(RrrMoment.Collect, Modifier.size(200.dp))
    }
}

// ───────────────────────── 공통 그림 ─────────────────────────

private val Ink = Color(0xFF1E1E1E)
private val Skin = Color(0xFFFDE4BE)
private val Shell = Color(0xFF9BC9FF)
private val ShellLine = Color(0xFF7FB4F5)
private val Cheek = Color(0xFFFF8A80)
private val Coin = Color(0xFFFDE68A)
private val Gold = Color(0xFFFACC15)
private val Mint = Color(0xFF34D399)
private val EvGreenC = Color(0xFF10B981)
private val FuelOrange = Color(0xFFF5A623)
private val NavyC = Color(0xFF2A4372)

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

private object M {
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
    val smile = p("M 145 128 Q 150 134 155 129 Q 160 134 165 128")
    val happyL = p("M 130 120 Q 136 113 142 120")
    val happyR = p("M 168 120 Q 174 113 180 120")
    val star = p("M 0 -7 L 2 -2 L 7 0 L 2 2 L 0 7 L -2 2 L -7 0 L -2 -2 Z")
    val bolt = p("M 2 -9 L -5 1 L 0 1 L -2 9 L 5 -1 L 0 -1 Z")
    val drop = p("M 0 -8 C 0 -8 6 -1 6 2.5 A 6 6 0 1 1 -6 2.5 C -6 -1 0 -8 0 -8 Z")
}

private enum class Eyes { Dot, Happy }

/** CSS 키프레임 보간: kf(p, t0, v0, t1, v1, …) — 구간마다 ease-in-out */
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
private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f

private fun DrawScope.fitBox(m: RrrMoment, block: DrawScope.() -> Unit) {
    val s = min(size.width / m.w, size.height / m.h)
    withTransform({
        translate((size.width - m.w * s) / 2f, (size.height - m.h * s) / 2f)
        scale(s, s, Offset.Zero)
        translate(-m.x, -m.y)
    }) { block() }
}

private fun DrawScope.outlined(path: Path, fill: Color?, stroke: Color? = Ink, sw: Float = 3f, a: Float = 1f) {
    fill?.let { drawPath(path, it, alpha = a) }
    stroke?.let { drawPath(path, it, alpha = a, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

/** (cx, cy) 를 원점으로 옮겨 path 를 그림 (아이콘·별처럼 0,0 중심 path 용) */
private fun DrawScope.at(cx: Float, cy: Float, s: Float = 1f, rot: Float = 0f, block: DrawScope.() -> Unit) =
    withTransform({ translate(cx, cy); rotate(rot, Offset.Zero); scale(s, s, Offset.Zero) }) { block() }

private fun DrawScope.arm(sx: Float, sy: Float, hx: Float, hy: Float) {
    drawLine(Ink, Offset(sx, sy), Offset(hx, hy), 17f, StrokeCap.Round)
    drawCircle(Skin, 7.5f, Offset(hx, hy))
    drawCircle(Ink, 7.5f, Offset(hx, hy), style = Stroke(3f))
    drawLine(Skin, Offset(sx, sy), Offset(hx, hy), 11f, StrokeCap.Round)
}

private fun DrawScope.groundShadow(k: Float = 1f) {
    val w = 116f * k
    drawOval(Color(0xFFCBD5E1), Offset(155f - w / 2f, 241f), Size(w, 10f), alpha = 0.5f * k)
}

private fun DrawScope.sparkle(cx: Float, cy: Float, s: Float, color: Color = Gold, a: Float = 1f) {
    if (a <= 0.01f || s <= 0.01f) return
    at(cx, cy, s) { drawPath(M.star, color, alpha = a) }
}

/** 몸통 (더듬이·몸·가방·얼굴) */
private fun DrawScope.rereureu(
    antenna: Float,
    eyes: Eyes,
    blink: Boolean = false,
    look: Float = 0f,          // 눈동자 위(-)/아래(+) 시선
    mouthOpen: Float = 0f,     // 0 = 웃는 입, 1 = 크게 벌림
    cheeks: Float = 0.7f,
    shellPulse: Float = 1f,
) {
    withTransform({ rotate(antenna, Offset(127f, 92f)) }) { outlined(M.antL, Skin, Ink, 3.2f) }
    withTransform({ rotate(-antenna, Offset(183f, 92f)) }) { outlined(M.antR, Skin, Ink, 3.2f) }
    outlined(M.body, Skin, Ink, 3.2f)
    M.straps.forEach { outlined(it, Shell, Ink, 3f) }
    withTransform({ scale(shellPulse, shellPulse, Offset(155f, 186f)) }) {
        drawOval(Shell, Offset(115f, 149f), Size(80f, 74f))
        drawOval(Ink, Offset(115f, 149f), Size(80f, 74f), style = Stroke(3.2f))
        outlined(M.swirl, null, ShellLine, 2.3f)
        outlined(M.flap, null, Ink, 3f)
        outlined(M.buckle, Color.White, Ink, 3f)
    }
    drawOval(Cheek, Offset(120.5f, 123.2f), Size(13f, 7.6f), alpha = cheeks)
    drawOval(Cheek, Offset(176.5f, 123.2f), Size(13f, 7.6f), alpha = cheeks)

    when {
        blink -> listOf(136f, 174f).forEach {
            drawLine(Ink, Offset(it - 5f, 119f), Offset(it + 5f, 119f), 3.2f, StrokeCap.Round)
        }
        eyes == Eyes.Happy -> {
            outlined(M.happyL, null, Ink, 3.2f); outlined(M.happyR, null, Ink, 3.2f)
        }
        else -> listOf(136f, 174f).forEach {
            drawOval(Ink, Offset(it - 4.2f, 114f + look), Size(8.4f, 10f))
            drawCircle(Color.White, 1.7f, Offset(it + 1.3f, 116.6f + look))
        }
    }

    if (mouthOpen > 0.05f) {
        val h = 3f + 8f * mouthOpen
        drawOval(Color(0xFF8A3B3B), Offset(149f, 127.5f), Size(12f, h))
        drawOval(Ink, Offset(149f, 127.5f), Size(12f, h), style = Stroke(2.6f))
    } else {
        outlined(M.smile, null, Ink, 3f)
    }
}

private fun textPaint(size: Float, color: Color) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = size
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    this.color = color.toArgb()
}

private val pillPaint = textPaint(13f, Color.White)
private val coinPaint = textPaint(10f, Color(0xFFB45309))
private val pointPaint = textPaint(12f, Color(0xFFB45309))

private fun DrawScope.text(s: String, x: Float, y: Float, paint: android.graphics.Paint, a: Float) {
    paint.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
    drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
}

// ───────────────────────── Talk: 오늘의 제안 ─────────────────────────

private fun DrawScope.drawTalk(t: Float, since: Float, talkMs: Float, energy: Energy) {
    val breathe = -1.6f * (0.5f - 0.5f * cos(2f * PI.toFloat() * phase(t, 2800f)))
    val talking = since < talkMs
    val mouth = if (talking) (0.5f + 0.5f * sin(since / 65f)) * 0.85f else 0f
    val antenna = if (talking) sin(since / 150f) * 6f else sin(2f * PI.toFloat() * phase(t, 2800f)) * 3f
    val blink = (t % 3300f) < 130f && !talking

    // 말 시작할 때 + 이후 7초마다 차 쪽으로 손 흔들기
    val q = since % 7000f
    val wave = if (q < 1800f) kf(q / 1800f, 0f, 0f, 0.12f, 1f, 0.82f, 1f, 1f, 0f) else 0f
    val swing = sin(since / 110f) * 0.32f
    val wa = (-2.35f + swing) // 왼쪽 위(차 방향)
    val leftHand = Offset(mix(96f, 114f + cos(wa) * 50f, wave), mix(192f, 156f + sin(wa) * 50f, wave))

    // 오른손: 허리에 얹고 살짝 까딱
    val rightHand = Offset(214f, 190f + breathe * 0.5f)

    groundShadow()
    arm(114f, 160f + breathe, leftHand.x, leftHand.y + breathe)
    arm(196f, 160f + breathe, rightHand.x, rightHand.y)
    withTransform({ translate(0f, breathe) }) {
        rereureu(antenna = antenna, eyes = if (wave > 0.5f) Eyes.Happy else Eyes.Dot, blink = blink, mouthOpen = mouth)
    }

    // 오늘 에너지 아이콘 (말풍선 보조) — 둥실둥실
    val bob = sin(2f * PI.toFloat() * phase(t, 2200f)) * 3f
    val pop = kf((since / 600f).coerceIn(0f, 1f), 0f, 0f, 0.6f, 1.15f, 1f, 1f)
    val cx = 234f; val cy = 92f + bob
    val accent = if (energy == Energy.Electric) EvGreenC else FuelOrange
    at(cx, cy, pop) {
        drawCircle(accent.copy(alpha = 0.18f), 18f, Offset.Zero)
        drawCircle(Color.White, 13f, Offset.Zero)
        drawCircle(accent, 13f, Offset.Zero, style = Stroke(2.4f))
        if (energy == Energy.Electric) outlined(M.bolt, accent, Color(0xFF047857), 1.4f)
        else outlined(M.drop, accent, Color(0xFFB45309), 1.4f)
    }
    sparkle(cx + 16f, cy - 14f, 0.5f + 0.25f * sin(t / 300f), accent, 0.8f)
}

// ───────────────────────── Cheer: 점수 올랐어요 ─────────────────────────

private val confettiX = floatArrayOf(52f, 78f, 104f, 128f, 182f, 206f, 232f, 258f, 66f, 244f, 92f, 220f)
private val confettiC = listOf(NavyC, Gold, Mint, Cheek, Shell)

private fun DrawScope.drawCheer(t: Float) {
    val p = phase(t, 1900f)

    // 꽃가루 (뒤쪽)
    confettiX.forEachIndexed { i, x ->
        val speed = 0.045f + (i % 4) * 0.012f
        val y = 24f + ((t * speed + i * 57f) % 230f)
        val sway = sin(t / 380f + i) * 6f
        val a = kf((y - 24f) / 230f, 0f, 0f, 0.1f, 1f, 0.8f, 1f, 1f, 0f)
        at(x + sway, y, 1f, t * 0.25f + i * 40f) {
            drawRoundRect(confettiC[i % confettiC.size], Offset(-4f, -2f), Size(8f, 4f), CornerRadius(1.5f), alpha = a)
        }
    }

    // 만세 점프: 웅크림 → 점프 → 착지
    val jump = kf(p, 0f, 0f, 0.14f, 4f, 0.42f, -24f, 0.68f, 0f, 0.76f, 3f, 0.88f, 0f, 1f, 0f)
    val squash = kf(p, 0f, 0f, 0.14f, 0.07f, 0.28f, -0.05f, 0.42f, 0f, 0.68f, 0f, 0.76f, 0.07f, 0.88f, 0f, 1f, 0f)
    val up = kf(p, 0f, 0.35f, 0.14f, 0.15f, 0.36f, 1f, 0.74f, 1f, 0.9f, 0.35f, 1f, 0.35f)
    val shake = sin(t / 70f) * 3f * up

    groundShadow(1f + jump / 60f)
    withTransform({
        translate(0f, jump)
        scale(1f + squash, 1f - squash, Offset(155f, 246f))
    }) {
        arm(114f, 160f, mix(98f, 84f, up) + shake, mix(192f, 98f, up))
        arm(196f, 160f, mix(212f, 226f, up) - shake, mix(192f, 98f, up))
        rereureu(
            antenna = kf(p, 0f, 0f, 0.42f, -12f, 0.76f, 6f, 1f, 0f),
            eyes = Eyes.Happy,
            mouthOpen = 0.35f + 0.6f * up,
            cheeks = 1f,
        )
    }

    // 점프 꼭대기에서 손 끝 반짝
    val sa = kf(p, 0f, 0f, 0.36f, 0f, 0.46f, 1f, 0.7f, 0f, 1f, 0f)
    val ss = kf(p, 0f, 0.4f, 0.36f, 0.4f, 0.46f, 1.3f, 0.7f, 0.9f, 1f, 0.4f)
    sparkle(70f, 80f + jump, ss, Gold, sa)
    sparkle(240f, 80f + jump, ss, Gold, sa)
    sparkle(58f, 112f + jump, ss * 0.6f, Mint, sa)
    sparkle(252f, 112f + jump, ss * 0.6f, Mint, sa)

    // "+3" 배지가 올라가며 사라짐
    val ba = kf(p, 0f, 0f, 0.38f, 0f, 0.5f, 1f, 0.85f, 1f, 1f, 0f)
    if (ba > 0.01f) {
        val by = kf(p, 0f, 66f, 0.38f, 66f, 1f, 40f)
        val bs = kf(p, 0f, 0.6f, 0.38f, 0.6f, 0.5f, 1.1f, 0.6f, 1f, 1f, 1f)
        at(236f, by, bs) {
            drawRoundRect(EvGreenC, Offset(-20f, -11f), Size(40f, 22f), CornerRadius(11f), alpha = ba)
            text("▲+3", 0f, 4.5f, pillPaint, ba)
        }
    }
}

// ───────────────────────── Collect: 이번 달 챌린지 ─────────────────────────

private fun DrawScope.drawCollect(t: Float) {
    val p = phase(t, 2400f)

    // 동전: 위에서 떨어져(0~0.5) 가방에 쏙(0.5~0.58)
    val fall = (p / 0.5f).coerceIn(0f, 1f)
    val coinY = 22f + 162f * fall * fall
    val coinA = kf(p, 0f, 0f, 0.05f, 1f, 0.5f, 1f, 0.58f, 0f, 1f, 0f)
    val coinS = kf(p, 0f, 1f, 0.5f, 1f, 0.58f, 0.3f, 1f, 0.3f)
    val spin = 0.25f + 0.75f * abs(cos(t / 140f))

    // 받는 순간 반응
    val hop = kf(p, 0f, 0f, 0.52f, 0f, 0.6f, -9f, 0.72f, 0f, 1f, 0f)
    val pulse = kf(p, 0f, 1f, 0.5f, 1f, 0.56f, 1.08f, 0.68f, 1f, 1f, 1f)
    val happy = p in 0.52f..0.92f
    val look = kf(p, 0f, -1f, 0.3f, -3f, 0.5f, 1.5f, 1f, -1f)
    val armUp = kf(p, 0f, 0.4f, 0.45f, 0.8f, 0.55f, 0.3f, 0.65f, 1f, 0.85f, 0.4f, 1f, 0.4f)

    groundShadow(1f + hop / 50f)
    withTransform({ translate(0f, hop) }) {
        rereureu(
            antenna = kf(p, 0f, 0f, 0.4f, -6f, 0.58f, 8f, 0.8f, 0f, 1f, 0f),
            eyes = if (happy) Eyes.Happy else Eyes.Dot,
            look = look,
            mouthOpen = if (happy) 0.6f else 0.3f,
            cheeks = if (happy) 1f else 0.6f,
            shellPulse = pulse,
        )
        // 가방을 앞으로 받쳐 드는 팔 (몸 앞)
        arm(112f, 162f, mix(114f, 108f, armUp), mix(206f, 186f, armUp))
        arm(198f, 162f, mix(196f, 202f, armUp), mix(206f, 186f, armUp))
    }

    // 떨어지는 동전
    if (coinA > 0.01f) {
        withTransform({
            translate(155f, coinY + if (p > 0.5f) hop else 0f)
            scale(spin * coinS, coinS, Offset.Zero)
        }) {
            drawCircle(Coin, 11f, Offset.Zero, alpha = coinA)
            drawCircle(Color(0xFFD97706), 11f, Offset.Zero, alpha = coinA, style = Stroke(2.4f))
            drawCircle(Color(0xFFD97706), 7f, Offset.Zero, alpha = coinA * 0.5f, style = Stroke(1.2f))
            text("P", 0f, 3.6f, coinPaint, coinA)
        }
    }
    // 낙하 꼬리 반짝이
    val trailA = kf(p, 0f, 0f, 0.15f, 0.7f, 0.48f, 0.7f, 0.52f, 0f, 1f, 0f)
    sparkle(146f, coinY - 18f, 0.45f, Gold, trailA)
    sparkle(165f, coinY - 30f, 0.35f, Gold, trailA * 0.7f)

    // 쏙 들어간 순간 가방 주변 반짝 + "+10P"
    val sa = kf(p, 0f, 0f, 0.54f, 0f, 0.62f, 1f, 0.82f, 0f, 1f, 0f)
    val ss = kf(p, 0f, 0.3f, 0.54f, 0.3f, 0.64f, 1.2f, 0.82f, 0.7f, 1f, 0.3f)
    sparkle(108f, 160f + hop, ss, Gold, sa)
    sparkle(204f, 158f + hop, ss, Gold, sa)
    sparkle(118f, 222f + hop, ss * 0.7f, Mint, sa)
    sparkle(194f, 224f + hop, ss * 0.7f, Mint, sa)

    val pa = kf(p, 0f, 0f, 0.56f, 0f, 0.64f, 1f, 0.9f, 1f, 1f, 0f)
    val py = kf(p, 0f, 150f, 0.56f, 150f, 1f, 122f)
    text("+10P", 226f, py, pointPaint, pa)
}
