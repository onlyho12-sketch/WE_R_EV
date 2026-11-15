package com.example.pleosconnect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Pleos Connect 주행 화면 — WereEV_PleosConnect.html (1920×1080 목업)의 레이아웃·배치·아이콘을 그대로 옮긴 것.
 * 좌표는 HTML 의 px 를 dp 로 그대로 쓰고, 화면 크기에 맞춰 통째로 확대/축소한다.
 */

// ───────────────────────── 스타일 ─────────────────────────

private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)
private val Ink = Color(0xFF1F2128)
private val Ink2 = Color(0xFF2A2D35)
private val Sub = Color(0xFF545866)
private val Muted = Color(0xFF5E626C)
private val Red = Color(0xFFE5484D)
private val Blue = Color(0xFF2F7BF6)
private val LogoNavy = Color(0xFF0B2350)
private val LogoGray = Color(0xFFA9B3C6)
private val EvBar = Color(0xFF10B981)
private val FuelBar = Color(0xFFF5A623)

@Composable
private fun T(
    text: String, size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Ink,
    modifier: Modifier = Modifier, spacing: TextUnit = TextUnit.Unspecified,
) = Text(text, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Pretendard,
    letterSpacing = spacing, lineHeight = size * 1.15f, maxLines = 1, softWrap = false)

// ───────────────────────── SVG 그리기 도우미 ─────────────────────────

private val pathCache = HashMap<String, Path>()
private fun sp(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun DrawScope.fillP(d: String, c: Color, a: Float = 1f) = drawPath(sp(d), c, alpha = a)
private fun DrawScope.strokeP(d: String, c: Color, w: Float, a: Float = 1f, round: Boolean = true) =
    drawPath(sp(d), c, alpha = a, style = Stroke(w, cap = if (round) StrokeCap.Round else StrokeCap.Butt, join = StrokeJoin.Round))
private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, a: Float = 1f) =
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r), alpha = a)

/** viewBox 를 w×h(dp) 상자에 맞춰 그리는 SVG 아이콘 */
@Composable
private fun Svg(
    w: Int, h: Int, vbW: Float = w.toFloat(), vbH: Float = h.toFloat(), vbX: Float = 0f, vbY: Float = 0f,
    modifier: Modifier = Modifier, draw: DrawScope.() -> Unit,
) {
    Canvas(modifier.size(w.dp, h.dp)) {
        val k = min(size.width / vbW, size.height / vbH)
        withTransform({
            translate((size.width - vbW * k) / 2f, (size.height - vbH * k) / 2f)
            scale(k, k, Offset.Zero)
            translate(-vbX, -vbY)
        }) { draw() }
    }
}

private fun textPaint(size: Float, color: Color, bold: Boolean = true, spacing: Float = 0f) =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER; textSize = size
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        this.color = color.toArgb(); letterSpacing = spacing
    }

/** 절대 배치 (HTML position:absolute) */
private fun Modifier.at(x: Dp, y: Dp) = this.offset(x, y)

// ───────────────────────── 주행 상태 ─────────────────────────

/** 앱(WE'R EV 홈·전략 화면)과 같은 시작값 */
private const val START_BATT = 62f
private const val START_FUEL = 48f
private const val START_RATIO = 78f
private const val LOOP_S = 36f          // HTML LOOP 36초
private const val HALF_S = LOOP_S / 2f  // 반 루프 동안 EV -4%, 발전 +3%

/** 앱과 같은 주행 가능 거리 계산 (배터리 100% = 200km, 연료 100% = 765km) */
private fun rangeKm(batt: Float, fuel: Float) = (batt / 100f * 200f + fuel / 100f * 765f).roundToInt()

private class EnergySim {
    var batt = START_BATT; var fuel = START_FUEL; var ratio = START_RATIO
    var lastT = 0f; var lastU = 0f
    fun step(t: Float, gen: Boolean) {
        val u = (t % LOOP_S) / LOOP_S
        if (u < lastU) { batt = START_BATT; fuel = START_FUEL; ratio = START_RATIO } // 루프 재시작
        val dt = (t - lastT).coerceIn(0f, 0.1f)
        lastT = t; lastU = u
        if (gen) { batt += 3f * dt / HALF_S; fuel -= 3f * dt / HALF_S; ratio -= 6f * dt / HALF_S }
        else batt -= 4f * dt / HALF_S
        batt = batt.coerceIn(5f, 100f); fuel = fuel.coerceIn(0f, 100f); ratio = ratio.coerceIn(0f, 100f)
    }
}

private data class Hud(
    val gen: Boolean, val batt: Float, val fuel: Float, val ratio: Int,
    val speed: Int, val rpm: Int, val range: Int, val odo: String,
    val clock: String, val eta: String, val remain: String,
    val dir: Char, val turnDist: String, val street: String, val nextIc: String, val nextDist: String, val road: String,
)

private val ClockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private fun fmtDist(m: Float) = if (m >= 1000) "%.1fkm".format(m / 1000f) else "${maxOf(0, (m / 10f).roundToInt() * 10)}m"

private fun hudAt(t: Float, gen: Boolean, e: EnergySim): Hud {
    val u = (t % LOOP_S) / LOOP_S
    val s = t * Scenario.SIM_MPS
    val turns = DriveRoute.upcoming(s)
    val (i1, d1) = turns[0]; val (i2, d2) = turns[1]
    val dir = if (d1 > 700) 'S' else if (DriveRoute.turnDir(i1) > 0) 'R' else 'L'
    val now = LocalTime.now()
    val ms = t * 1000f
    return Hud(
        gen = gen, batt = e.batt, fuel = e.fuel, ratio = e.ratio.roundToInt(),
        speed = (50 + sin(ms / 1700f) * 3 + sin(ms / 530f)).roundToInt(),
        rpm = if (gen) (1800 + sin(ms / 420f) * 35).roundToInt() else 0,
        range = rangeKm(e.batt, e.fuel),
        odo = "${(1576 + u * 4.2f).roundToInt()}km",
        clock = now.format(ClockFmt), eta = now.plusMinutes(27).format(ClockFmt),
        remain = "${(27 - u * 4).roundToInt()}분 · ${"%.1f".format(31.3f - u * 4.2f)}km",
        dir = dir, turnDist = fmtDist(d1), street = DriveRoute.SEG[i1].first,
        nextIc = if (DriveRoute.turnDir(i2) > 0) "↱" else "↰", nextDist = fmtDist(d2 - d1),
        road = DriveRoute.SEG[DriveRoute.segmentAt(s)].second,
    )
}

private val AnimText = mapOf("auto" to "자동 전환", "gen" to "발전 애니메이션 고정", "ev" to "EV 애니메이션 고정")
private val MotionText = mapOf("normal" to "기본 움직임", "calm" to "차분하게", "off" to "멈춤")
fun settingsToastText(s: WidgetSettings) = "WE'R EV 위젯 설정 · ${AnimText[s.anim]} · ${MotionText[s.motion]}"

// ───────────────────────── 화면 ─────────────────────────

/**
 * @param appToast WE'R EV 앱에서 설정이 바뀌었을 때 띄울 문구 (key 가 바뀔 때마다 표시)
 */
@Composable
fun PleosConnectScreen(settings: WidgetSettings, appToastKey: Int = 0, appToast: String? = null) {
    // 모드: 위젯 하단 「배터리 사용 / 발전 모드」를 눌러 전환 (HTML 과 동일). 자동이면 배터리 20% 이하에서 발전
    var manualMode by remember { mutableStateOf<String?>(null) }
    var autoGen by remember { mutableStateOf(false) }
    val gen = when (manualMode) { "gen" -> true; "ev" -> false; else -> autoGen }
    val energy = remember { EnergySim() }

    // 주행 시계 (초) + 배터리·연료 적분
    var simT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) withFrameNanos { n ->
            if (last >= 0) simT += ((n - last) / 1e9f).coerceAtMost(0.1f)
            last = n
            val g = when (manualMode) { "gen" -> true; "ev" -> false; else -> autoGen }
            energy.step(simT, g)
            if (manualMode == null && !autoGen && energy.batt <= 20f) autoGen = true
        }
    }

    val tick by remember { derivedStateOf { (simT * 10).toInt() } }
    val hud = remember(tick, gen) { hudAt(tick / 10f, gen, energy) }

    // 토스트
    var toast by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    fun showToast(msg: String) { toast = msg; toastKey++ }
    LaunchedEffect(autoGen) {
        if (autoGen && manualMode == null && settings.toast) showToast("⚡ 배터리 20% · 발전 모드로 전환했어요")
    }
    LaunchedEffect(appToastKey) { if (appToastKey > 0 && appToast != null) showToast(appToast) }
    LaunchedEffect(toastKey) { if (toastKey > 0) { delay(3200); toast = null } }

    val onMode: (Boolean) -> Unit = { toGen ->
        manualMode = if (toGen) "gen" else "ev"
        if (settings.toast) showToast(if (toGen) "⚡ 발전 모드 · 연료로 배터리를 충전해요" else "🔋 배터리 사용 모드 · 전기로만 주행해요")
    }

    val widgetMode = when (settings.anim) { "gen" -> "gen"; "ev" -> "ev"; else -> if (gen) "gen" else "ev" }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth / 1920.dp, maxHeight / 1080.dp)
        Box(Modifier.requiredSize(1920.dp, 1080.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(Color.White)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(968.dp)) {
                    DrivePanel(hud, { simT }, Modifier.width(632.dp).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, bottom = 12.dp)) {
                        StatusBar(hud.clock, Modifier.fillMaxWidth().height(70.dp))
                        Row(Modifier.weight(1f).padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MapCard(hud, { simT }, toast, Modifier.weight(1f).fillMaxHeight())
                            WidgetColumn(hud, widgetMode, settings.motion, onMode, Modifier.width(400.dp).fillMaxHeight())
                        }
                    }
                }
                Dock(Modifier.fillMaxWidth().height(112.dp))
            }
        }
    }
}

// ───────────────────────── 좌측 주행 정보 ─────────────────────────

private val sfPaint = textPaint(7.5f, Color(0xFFBFC3CC), spacing = 0.66f)
private val plateSfPaint = textPaint(8f, Color(0xFF2A2C31))

@Composable
private fun DrivePanel(hud: Hud, simT: () -> Float, modifier: Modifier) {
    Box(modifier.background(Color.White)) {
        // rpm
        Row(Modifier.at(13.dp, 19.dp), verticalAlignment = Alignment.Bottom) {
            T("${hud.rpm}", 27.sp, FontWeight.Medium, spacing = (-0.3).sp)
            T("rpm", 24.sp, FontWeight.Medium, Muted)
        }
        T("D", 47.sp, FontWeight.ExtraBold, modifier = Modifier.at(293.dp, 9.dp))
        // 주행 가능 거리 + 충전 아이콘 + 배터리
        Row(Modifier.at(433.dp, 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            T("${hud.range}km", 26.sp, FontWeight.Medium)
            Svg(34, 30) {
                strokeP("M6 11 L1.5 15 L6 19", Ink, 2f)
                drawRoundRect(Ink, Offset(9f, 3f), Size(13f, 24f), CornerRadius(2f), style = Stroke(2.4f))
                rr(12f, 6.5f, 7f, 6f, 0f, Ink)
                strokeP("M22 10 h3 a2 2 0 0 1 2 2 v10 a2 2 0 0 0 4 0 V9 l-3 -4", Ink, 2.4f)
            }
            Box(Modifier.padding(start = 2.dp).size(40.dp, 24.dp)) {
                Box(Modifier.fillMaxSize().border(2.5.dp, Ink, RoundedCornerShape(5.dp)).padding(4.5.dp)) {
                    Box(Modifier.fillMaxWidth(hud.batt / 100f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Ink))
                }
                Box(Modifier.align(Alignment.CenterEnd).offset(x = 6.dp).size(3.5.dp, 8.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(Ink))
            }
        }
        // 속도
        Column(Modifier.at(13.dp, 58.dp)) {
            T("${hud.speed}", 84.sp, FontWeight.SemiBold, spacing = (-2).sp)
            T("km/h", 30.sp, FontWeight.Medium, Muted, Modifier.padding(top = 6.dp))
        }
        // ADAS
        Svg(44, 30, modifier = Modifier.at(192.dp, 78.dp)) {
            strokeP("M4 28 L11 6", Ink, 2.4f); strokeP("M40 28 L33 6", Ink, 2.4f)
            fillP("M14 22 v-5 l3 -6 h10 l3 6 v5 z", Ink); strokeP("M14 22 v-5 l3 -6 h10 l3 6 v5 z", Ink, 2.4f)
            strokeP("M18 15 l1.5 -2.5 h5 l1.5 2.5", Color.White, 1.6f)
        }
        // 제한속도
        Box(Modifier.at(24.dp, 206.dp).size(56.dp).clip(CircleShape).background(Color.White).border(5.dp, Red, CircleShape),
            contentAlignment = Alignment.Center) { T("60", 22.sp, FontWeight.Bold) }
        // 미니 길안내
        Row(Modifier.at(104.dp, 208.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(when (hud.dir) { 'R' -> "↱"; 'L' -> "↰"; else -> "↑" }, 30.sp, FontWeight.Bold)
                    T(hud.turnDist, 30.sp, FontWeight.Bold)
                }
                T(hud.street.substringBefore(","), 13.sp, color = Color(0xFF6B6F7A), modifier = Modifier.padding(top = 2.dp))
            }
            Row(
                Modifier.height(24.dp).drawBehind { drawLine(Color(0xFFD9DCE3), Offset.Zero, Offset(0f, size.height), 1.5.dp.toPx()) }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { T("${hud.nextIc} ${hud.nextDist}", 16.sp, FontWeight.SemiBold, Color(0xFF3A3D46)) }
        }
        // 원근 도로 + 산타페 후면
        LaneScene(simT, Modifier.at(0.dp, 292.dp).size(632.dp, 543.dp))
        T(hud.odo, 24.sp, FontWeight.Medium, modifier = Modifier.at(11.dp, 928.dp))
        Svg(48, 22, modifier = Modifier.at(286.dp, 920.dp)) { strokeP("M3 18 L24 5 L45 18", Color(0xFF8D929C), 3.2f) }
        T("ECO", 25.sp, FontWeight.ExtraBold, Color(0xFF3FA9A2),
            Modifier.align(Alignment.TopEnd).at((-20).dp, 885.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE6F5F4)).padding(horizontal = 5.dp))
        T("27°C", 24.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.TopEnd).at((-20).dp, 928.dp))
        // 패널 경계 손잡이
        Box(Modifier.align(Alignment.TopEnd).at(5.dp, 458.dp).size(3.dp, 120.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFA9AEB8)))
    }
}

@Composable
private fun LaneScene(simT: () -> Float, modifier: Modifier) {
    Svg(632, 543, 500f, 430f, modifier = modifier) {
        drawPath(sp("M215 0 L285 0 L470 430 L30 430 Z"), Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EBF0)), 0f, 430f))
        val dash = PathEffect.dashPathEffect(floatArrayOf(26f, 30f), ((simT() * 1000f / 9f) % 56f))
        drawLine(Color(0xFFC9CDD6), Offset(238f, 0f), Offset(170f, 430f), 5f, pathEffect = dash)
        drawLine(Color(0xFFC9CDD6), Offset(262f, 0f), Offset(330f, 430f), 5f, pathEffect = dash)
        // 앞차
        fun frontCar(x: Float, y: Float, s: Float, a: Float) = withTransform({ translate(x, y); scale(s, s, Offset.Zero) }) {
            rr(-40f, 0f, 80f, 54f, 14f, Color(0xFFD7DAE1), a); rr(-30f, 8f, 60f, 18f, 5f, Color(0xFFBFC3CC), a)
        }
        frontCar(272f, 120f, 0.42f, 0.55f); frontCar(330f, 215f, 0.62f, 0.7f)
        // 내 차: 산타페 후면 (박스형 테일게이트 + H 리어램프)
        withTransform({ translate(250f, 286f) }) {
            drawOval(Color.Black, Offset(-96f, 117f), Size(192f, 22f), alpha = 0.16f)
            rr(-82f, 96f, 26f, 30f, 5f, Color(0xFF16171A)); rr(56f, 96f, 26f, 30f, 5f, Color(0xFF16171A))
            fillP("M-84 24 h-12 a5 5 0 0 0 -5 5 v8 a4 4 0 0 0 4 4 h13 z", Color(0xFF1E2024))
            fillP("M84 24 h12 a5 5 0 0 1 5 5 v8 a4 4 0 0 1 -4 4 h-13 z", Color(0xFF1E2024))
            rr(-70f, -14f, 10f, 10f, 3f, Color(0xFF2A2C31)); rr(60f, -14f, 10f, 10f, 3f, Color(0xFF2A2C31))
            fillP("M-74 -6 Q-74 -10 -68 -10 H68 Q74 -10 74 -6 L82 56 V108 Q82 114 76 114 H-76 Q-82 114 -82 108 V56 Z", Color(0xFF1E2024))
            rr(-70f, -10f, 140f, 8f, 3f, Color(0xFF141518)); rr(-20f, -8f, 40f, 3f, 1.5f, Red)
            fillP("M-62 2 H62 L66 36 H-66 Z", Color(0xFF5B616D))
            fillP("M-56 6 H56 L58 18 H-58 Z", Color(0xFF7A808B), 0.55f)
            fillP("M-76 42 H76 L79 58 H-79 Z", Color(0xFF26282D))
            drawOval(Color(0xFFD5D8DE), Offset(-9f, 43f), Size(18f, 10f), style = Stroke(1.6f))
            drawContext.canvas.nativeCanvas.drawText("SANTA FE", 0f, 68f, sfPaint)
            rr(-80f, 72f, 44f, 16f, 2f, Color(0xFF4A1518)); rr(36f, 72f, 44f, 16f, 2f, Color(0xFF4A1518))
            strokeP("M-76 75 v10 M-76 80 h14 M-62 75 v10 M-58 80 h18", Color(0xFFFF4D4D), 2.6f)
            strokeP("M40 80 h18 M62 75 v10 M62 80 h14 M76 75 v10", Color(0xFFFF4D4D), 2.6f)
            rr(-12f, 78f, 24f, 5f, 2f, Color(0xFF0F1012))
            rr(-82f, 92f, 164f, 4f, 0f, Color(0xFF141518))
            rr(-74f, 98f, 38f, 3f, 1.5f, Color(0xFFB4262C)); rr(36f, 98f, 38f, 3f, 1.5f, Color(0xFFB4262C))
            rr(-74f, 103f, 38f, 4f, 1f, Color(0xFF9DA2AB)); rr(36f, 103f, 38f, 4f, 1f, Color(0xFF9DA2AB))
            rr(-26f, 96f, 52f, 14f, 2f, Color(0xFFF4F5F7))
            drawContext.canvas.nativeCanvas.drawText("SANTA FE", 0f, 106.5f, plateSfPaint)
            drawOval(Color(0xFFC8CCD3), Offset(51f, 111f), Size(14f, 6f))
            drawOval(Color(0xFF6B6F7A), Offset(51f, 111f), Size(14f, 6f), style = Stroke(1f))
        }
    }
}

// ───────────────────────── 상태바 ─────────────────────────

private val ltePaint = textPaint(10f, Ink).apply { textAlign = android.graphics.Paint.Align.LEFT }

@Composable
private fun StatusBar(clock: String, modifier: Modifier) {
    Row(modifier.padding(start = 4.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Svg(44, 44) {
            drawCircle(Ink, 13f, Offset(19f, 19f), style = Stroke(3.6f))
            strokeP("M29 29 L39 39", Ink, 3.6f)
        }
        Spacer(Modifier.width(34.dp))
        Svg(40, 44) {
            fillP("M20 2 a3 3 0 0 1 3 3 v1.4 A12 12 0 0 1 32 18 v9 l4 5 v2 H4 v-2 l4 -5 v-9 A12 12 0 0 1 17 6.4 V5 a3 3 0 0 1 3 -3 z", Ink)
            fillP("M15 37 h10 a5 5 0 0 1 -10 0 z", Ink)
        }
        Spacer(Modifier.weight(1f))
        Svg(36, 36) {
            drawContext.canvas.nativeCanvas.drawText("LTE", 1f, 9f, ltePaint)
            rr(4f, 25f, 4f, 9f, 1f, Ink); rr(11f, 20f, 4f, 14f, 1f, Ink); rr(18f, 15f, 4f, 19f, 1f, Ink); rr(25f, 10f, 4f, 24f, 1f, Ink)
        }
        Spacer(Modifier.width(36.dp))
        Svg(42, 36) {
            fillP("M3 13 h7 l9 -8 v26 l-9 -8 H3 z", Ink); strokeP("M3 13 h7 l9 -8 v26 l-9 -8 H3 z", Ink, 3f)
            strokeP("M25 12 q4 6 0 12", Ink, 3f); strokeP("M30 7 q8 11 0 22", Ink, 3f)
        }
        Spacer(Modifier.width(36.dp))
        Svg(26, 38) { strokeP("M3 11 L22 27 L13 35 V3 L22 11 L3 27", Ink, 3.2f) }
        Spacer(Modifier.width(36.dp))
        Svg(50, 50) {
            drawCircle(Color(0xFFE3E5EA), 24f, Offset(25f, 25f)); drawCircle(Color(0xFF8E939C), 7f, Offset(25f, 19f))
            fillP("M12 37 a13 10 0 0 1 26 0 z", Color(0xFF8E939C))
        }
        Spacer(Modifier.width(32.dp))
        T(clock, 31.sp, FontWeight.Medium, spacing = (-0.2).sp)
    }
}

// ───────────────────────── 지도 ─────────────────────────

private fun rand(bx: Int, by: Int, n: Int): Float {
    var h = bx * 374761393 + by * 668265263 + n * 2147483647
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)).toUInt().toDouble() / 4294967296.0).toFloat()
}

private val Poi = listOf(Color(0xFFE5484D), Color(0xFF2F7BF6), Color(0xFFF5A623), Color(0xFF22B07D), Color(0xFF8B5CF6))
private const val PPM = 1.15f // dp per meter

private class Camera { var heading = Float.NaN; var lastT = 0f }

@Composable
private fun MapCard(hud: Hud, simT: () -> Float, toast: String?, modifier: Modifier) {
    val cam = remember { Camera() }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.clip(shape).background(Color(0xFFEEF0F5)).border(1.5.dp, Color(0xFFE2E5EB), shape)) {
        Canvas(Modifier.fillMaxSize()) { drawMap(simT(), cam) }

        // 길안내 카드
        Column(Modifier.at(18.dp, 18.dp).width(300.dp).shadow(12.dp, shape, ambientColor = Color(0x1F141828), spotColor = Color(0x1F141828))
            .clip(shape).background(Color.White)) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TurnIcon(hud.dir)
                T(hud.turnDist, 34.sp, FontWeight.ExtraBold, spacing = (-1).sp)
            }
            T(hud.street, 14.sp, color = Sub, modifier = Modifier.padding(start = 70.dp, end = 18.dp, bottom = 12.dp).offset(y = (-10).dp))
            Row(
                Modifier.fillMaxWidth().drawBehind { drawLine(Color(0xFFEEF0F4), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("↰", "↑", "↑", "↑", "↱", "↱").forEachIndexed { i, a ->
                    val on = (hud.dir == 'R' && i >= 4) || (hud.dir == 'L' && i == 0) || (hud.dir == 'S' && i in 1..3)
                    T(a, 18.sp, color = if (on) Ink else Color(0xFFB9BDC7))
                }
            }
            Row(Modifier.fillMaxWidth().background(Color(0xFFF6F7FA)).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(hud.nextIc, 22.sp, FontWeight.Bold); T(hud.nextDist, 22.sp, FontWeight.Bold)
            }
        }

        // 2D / AUTO
        Column(Modifier.align(Alignment.TopEnd).at((-16).dp, 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapButton {
                T("2D", 20.sp, FontWeight.ExtraBold)
                Svg(16, 14, modifier = Modifier.padding(top = 3.dp)) { fillP("M8 0 L16 14 L8 10 L0 14 Z", Ink) }
            }
            MapButton {
                T("AUTO", 11.sp, FontWeight.ExtraBold)
                Svg(26, 22, modifier = Modifier.padding(top = 2.dp)) {
                    strokeP("M2 4 l7 -2 l8 3 l7 -2 v16 l-7 2 l-8 -3 l-7 2 z", Ink, 2.4f); strokeP("M9 2 v16 M17 5 v16", Ink, 2.4f)
                }
            }
        }
        // 설정
        MapButton(Modifier.align(Alignment.BottomEnd).at((-16).dp, (-16).dp)) {
            Svg(28, 28, 24f, 24f) {
                withTransform({ translate(-1f, 0f) }) {
                    fillP("M19.4 13a7.5 7.5 0 0 0 0-2l2.1-1.6-2-3.5-2.5 1a7.4 7.4 0 0 0-1.7-1L15 3.3h-4L10.6 6a7.4 7.4 0 0 0-1.7 1l-2.5-1-2 3.5L6.6 11a7.5 7.5 0 0 0 0 2l-2.1 1.6 2 3.5 2.5-1a7.4 7.4 0 0 0 1.7 1l.4 2.6h4l.4-2.6a7.4 7.4 0 0 0 1.7-1l2.5 1 2-3.5zM13 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7z", Ink)
                }
            }
        }

        // 도착 예정
        Column(Modifier.align(Alignment.BottomStart).at(18.dp, (-18).dp).width(300.dp)
            .shadow(12.dp, shape, ambientColor = Color(0x1F141828), spotColor = Color(0x1F141828)).clip(shape).background(Color.White)
            .padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row { T(hud.eta, 15.sp, FontWeight.SemiBold); Spacer(Modifier.weight(1f)); T(hud.remain, 15.sp, FontWeight.SemiBold, Color(0xFF3A3D46)) }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).border(2.5.dp, Blue, CircleShape)); Spacer(Modifier.width(6.dp)); T("회사", 13.sp, color = Sub)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("✕" to 2f, "↻" to 1f, "⌕" to 1f).forEach { (g, w) ->
                    Box(Modifier.weight(w).height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2F3F6)), contentAlignment = Alignment.Center) {
                        T(g, 16.sp, color = Color(0xFF3A3D46))
                    }
                }
            }
        }
        // 도로명
        T(hud.road, 20.sp, FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter).at(0.dp, (-22).dp)
            .shadow(6.dp, CircleShape, ambientColor = Color(0x24141828), spotColor = Color(0x24141828)).clip(CircleShape)
            .background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp))

        // 토스트
        val a by animateFloatAsState(if (toast != null) 1f else 0f, tween(450), label = "toast")
        var lastToast by remember { mutableStateOf("") }
        if (toast != null) lastToast = toast
        if (a > 0.01f) {
            T(lastToast, 15.sp, FontWeight.SemiBold, Color.White,
                Modifier.align(Alignment.TopCenter).at(150.dp, 18.dp).graphicsLayer { alpha = a; translationY = (1 - a) * -14f * density }
                    .clip(CircleShape).background(Color(0xE01F2128)).padding(horizontal = 18.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun TurnIcon(dir: Char) {
    Svg(40, 40) {
        when (dir) {
            'R' -> { strokeP("M10 36 V18 a6 6 0 0 1 6-6 H30", Ink, 5f); fillP("M26 4 L36 12 L26 20 Z", Ink) }
            'L' -> { strokeP("M30 36 V18 a6 6 0 0 0-6-6 H10", Ink, 5f); fillP("M14 4 L4 12 L14 20 Z", Ink) }
            else -> { strokeP("M20 36 V8", Ink, 5f); fillP("M11 12 L20 2 L29 12 Z", Ink) }
        }
    }
}

@Composable
private fun MapButton(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.size(56.dp).shadow(5.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x1F141828), spotColor = Color(0x1F141828))
            .clip(RoundedCornerShape(10.dp)).background(Color.White),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content
    )
}

private fun DrawScope.drawMap(simT: Float, cam: Camera) {
    val s = simT * Scenario.SIM_MPS
    val car = DriveRoute.locate(s)
    val dt = (simT - cam.lastT).coerceIn(0f, 0.1f); cam.lastT = simT
    if (cam.heading.isNaN()) cam.heading = car.heading
    var dh = car.heading - cam.heading
    while (dh > PI) dh -= (2 * PI).toFloat()
    while (dh < -PI) dh += (2 * PI).toFloat()
    cam.heading += dh * min(1f, dt * 5f)

    drawRect(Color.White)                // 도로(흰 면)
    val ppm = PPM * density
    val u = 1f / PPM
    val cx = size.width / 2f + 40f * density; val cy = size.height * 0.68f   // HTML: W/2+40, H*0.68

    withTransform({
        translate(cx, cy)
        rotate((-cam.heading * 180f / PI).toFloat(), Offset.Zero)
        scale(ppm, ppm, Offset.Zero)
        translate(-car.x, -car.y)
    }) {
        val rad = hypot(size.width, size.height) / ppm
        val bx0 = floor((car.x - rad) / 100f).toInt(); val bx1 = floor((car.x + rad) / 100f).toInt()
        val by0 = floor((car.y - rad) / 100f).toInt(); val by1 = floor((car.y + rad) / 100f).toInt()

        for (bx in bx0..bx1) for (by in by0..by1) {
            val x0 = bx * 100f + 8f; val y0 = by * 100f + 8f; val sz = 84f
            val park = rand(bx, by, 1) < 0.1f
            drawRoundRect(if (park) Color(0xFFDCF1E0) else Color(0xFFF6F7FA), Offset(x0, y0), Size(sz, sz), CornerRadius(3f))
            drawRoundRect(if (park) Color(0xFFCBE8D1) else Color(0xFFE4E7EE), Offset(x0, y0), Size(sz, sz), CornerRadius(3f), style = Stroke(1.4f * u))
            if (!park) {
                val nb = 1 + (rand(bx, by, 2) * 3).toInt()
                for (b in 0 until nb) {
                    val w = 18 + rand(bx, by, 10 + b) * 26; val h = 14 + rand(bx, by, 20 + b) * 24
                    val px = x0 + 6 + rand(bx, by, 30 + b) * (sz - 12 - w); val py = y0 + 6 + rand(bx, by, 40 + b) * (sz - 12 - h)
                    drawRoundRect(Color(0xFFE8EAF1), Offset(px, py), Size(w, h), CornerRadius(2f))
                    drawRoundRect(Color(0xFFDDE0E8), Offset(px, py), Size(w, h), CornerRadius(2f), style = Stroke(u))
                }
            }
            if (rand(bx, by, 3) < 0.35f) {
                val c = Offset(x0 + 12 + rand(bx, by, 5) * 60, y0 + 12 + rand(bx, by, 6) * 60)
                drawCircle(Color.White, 6.5f * u, c)
                drawCircle(Poi[(rand(bx, by, 4) * Poi.size).toInt()], 4.5f * u, c)
            }
        }

        // 간선도로 중앙 점선
        val dash = PathEffect.dashPathEffect(floatArrayOf(9 * u, 8 * u))
        val lineC = Color(0xFFF1D9A7)
        var gx = Math.floorDiv(bx0 + 2, 3) * 3
        while (gx <= bx1) { drawLine(lineC, Offset(gx * 100f, by0 * 100f), Offset(gx * 100f, (by1 + 1) * 100f), 1.4f * u, pathEffect = dash); gx += 3 }
        var gy = Math.floorDiv(by0 + 2, 3) * 3
        while (gy <= by1) { drawLine(lineC, Offset(bx0 * 100f, gy * 100f), Offset((bx1 + 1) * 100f, gy * 100f), 1.4f * u, pathEffect = dash); gy += 3 }

        // 안내 경로 (차 앞쪽만)
        val route = Path().apply {
            moveTo(car.x, car.y)
            var d = 4f
            while (d < 1300f) { val p = DriveRoute.locate(s + d); lineTo(p.x, p.y); d += 4f }
        }
        drawPath(route, Color(0xFF159A47), style = Stroke(16f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(route, Color(0xFF2BD467), style = Stroke(10f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 내 차 마커
    val d = density
    drawCircle(Color.White, 21f * d, Offset(cx, cy))
    drawCircle(Blue, 21f * d, Offset(cx, cy), style = Stroke(3f * d))
    drawCircle(Blue, 15f * d, Offset(cx, cy))
    drawPath(Path().apply {
        moveTo(cx, cy - 9f * d); lineTo(cx + 7f * d, cy + 6f * d); lineTo(cx, cy + 2.5f * d); lineTo(cx - 7f * d, cy + 6f * d); close()
    }, Color.White)
}

// ───────────────────────── WE'R EV 위젯 ─────────────────────────

@Composable
private fun WerEvLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Svg(26, 26) {
            strokeP("M22.4 10.58A10 10 0 0 1 20.07 21.07", Color(0xFFB9B9B5), 3.2f)
            strokeP("M5.93 21.07A10 10 0 1 1 22.4 10.58", LogoNavy, 3.2f)
            strokeP("M12.4 15.2L16.6 8.6", LogoNavy, 2.6f)
        }
        Spacer(Modifier.width(7.dp))
        T("W", 19.sp, FontWeight.ExtraBold, LogoGray, spacing = 0.2.sp)
        T("E'R EV", 19.sp, FontWeight.ExtraBold, LogoNavy, spacing = 0.2.sp)
    }
}

/** 배터리·연료 막대 (발전 중 배터리는 사선 줄무늬가 흐름) */
@Composable
private fun WidgetBar(pct: Float, color: Color, charging: Boolean, simT: () -> Float) {
    Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color(0xFFE6E8EE))) {
        Canvas(Modifier.fillMaxWidth(pct.coerceIn(0f, 100f) / 100f).fillMaxHeight().clip(CircleShape)) {
            drawRect(color)
            if (charging) {
                val step = 14.dp.toPx()
                val off = ((simT() / 0.9f) % 1f) * step
                clipRect {
                    var x = -size.height - step + off
                    while (x < size.width + step) {
                        drawPath(Path().apply {
                            moveTo(x, size.height); lineTo(x + step / 2f, size.height)
                            lineTo(x + step / 2f + size.height, 0f); lineTo(x + size.height, 0f); close()
                        }, Color.White.copy(alpha = 0.38f))
                        x += step
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        T(label, 15.sp, color = Sub); Spacer(Modifier.weight(1f)); T(value, 16.sp, FontWeight.Bold)
    }
}

@Composable
private fun WidgetColumn(hud: Hud, widgetMode: String, motion: String, onMode: (Boolean) -> Unit, modifier: Modifier) {
    val gen = hud.gen
    val battColor by animateColorAsState(if (gen) FuelBar else EvBar, tween(600), label = "bar")
    val shape = RoundedCornerShape(16.dp)
    val simT = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val t0 = System.nanoTime(); while (true) withFrameNanos { simT.floatValue = (it - t0) / 1e9f } }

    Column(
        modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFFFBFBFD), Color(0xFFF3F4F8))))
            .border(1.5.dp, Color(0xFFE2E5EB), shape).padding(top = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            WerEvLogo()
            Spacer(Modifier.weight(1f))
            T(if (gen) "발전 중" else "EV 주행", 13.sp, FontWeight.Bold, if (gen) Color(0xFFB76E00) else Color(0xFF058F6C),
                Modifier.clip(CircleShape).background(if (gen) Color(0xFFFFF1D9) else Color(0xFFE3F8F1)).padding(horizontal = 11.dp, vertical = 5.dp))
        }
        // 위젯 카드 360×360 (그림 320×280 + 하단 상태 라벨)
        Box(Modifier.padding(top = 40.dp).size(360.dp).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp)) {
            RrrWidgetAnimation(widgetMode, motion, Modifier.align(Alignment.TopCenter).size(320.dp, 280.dp), fuel = hud.fuel / 100f)
            Row(
                Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(Color.White.copy(alpha = 0.65f))
                    .border(1.dp, if (widgetMode == "gen") Color(0x47F5A623) else Color(0x5200C49A), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (widgetMode == "gen") { T("⚡", 14.sp, color = Color(0xFFF59E0B)); T("발전 중 · 연료로 배터리 충전", 14.sp, FontWeight.SemiBold, Color(0xFF2B2D36)) }
                else { T("🔋", 14.sp); T("EV 주행 · 배터리 전기 사용 중", 14.sp, FontWeight.SemiBold, Color(0xFF2B2D36)) }
            }
        }
        Column(Modifier.padding(top = 34.dp).width(330.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            WidgetStat(if (gen) "배터리 · 충전 중" else "배터리", "${hud.batt.roundToInt()}%")
            WidgetBar(hud.batt, battColor, gen) { simT.floatValue }
            Spacer(Modifier.height(4.dp))
            WidgetStat(if (gen) "연료 잔여량 · 사용 중" else "연료 잔여량", "${hud.fuel.roundToInt()}%")
            WidgetBar(hud.fuel, FuelBar, false) { 0f }
            Spacer(Modifier.height(4.dp))
            WidgetStat("오늘 전기 주행 비율", "${hud.ratio}%")
        }
        ModeSelector(gen, onMode, Modifier.padding(top = 30.dp))
    }
}

/** 배터리 사용 / 발전 모드 — 눌러서 전환 (시연 영상용 전환 장면) */
@Composable
private fun ModeSelector(gen: Boolean, onMode: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val thumbX by animateFloatAsState(if (gen) 1f else 0f, tween(450), label = "modeThumb")
    val evC by animateColorAsState(if (gen) Color(0xFF8A8F9A) else Color(0xFF058F6C), tween(400), label = "evC")
    val genC by animateColorAsState(if (gen) Color(0xFFB76E00) else Color(0xFF8A8F9A), tween(400), label = "genC")
    Box(modifier.size(330.dp, 58.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9EBF0)).padding(5.dp)) {
        Box(
            Modifier.offset(x = 160.dp * thumbX).size(160.dp, 48.dp)
                .shadow(5.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x1F141828), spotColor = Color(0x1F141828))
                .clip(RoundedCornerShape(12.dp)).background(Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            val noRipple = remember { MutableInteractionSource() }
            Row(Modifier.weight(1f).fillMaxHeight().clickable(noRipple, null) { if (gen) onMode(false) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Svg(22, 14, modifier = Modifier.graphicsLayer { alpha = if (gen) 0.45f else 1f }) {
                    drawRoundRect(EvBar, Offset(1f, 1f), Size(17f, 12f), CornerRadius(2.5f), style = Stroke(2f))
                    rr(18.5f, 4.5f, 2.5f, 5f, 1f, EvBar); rr(3.5f, 3.5f, 9f, 7f, 1f, EvBar)
                }
                Spacer(Modifier.width(7.dp))
                T("배터리 사용", 16.sp, FontWeight.Bold, evC)
            }
            val noRipple2 = remember { MutableInteractionSource() }
            Row(Modifier.weight(1f).fillMaxHeight().clickable(noRipple2, null) { if (!gen) onMode(true) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Svg(14, 20, modifier = Modifier.graphicsLayer { alpha = if (gen) 1f else 0.45f }) {
                    fillP("M9 1 L1 11.5 H6.5 L4.5 19 L13 8 H7.5 Z", FuelBar)
                    drawPath(sp("M9 1 L1 11.5 H6.5 L4.5 19 L13 8 H7.5 Z"), Color(0xFFB76E00), style = Stroke(1.2f, join = StrokeJoin.Round))
                }
                Spacer(Modifier.width(7.dp))
                T("발전 모드", 16.sp, FontWeight.Bold, genC)
            }
        }
    }
}

// ───────────────────────── 하단 독 (HTML 과 같은 x 좌표) ─────────────────────────

@Composable
private fun BoxScope.DockItem(x: Int, content: @Composable () -> Unit) {
    Box(Modifier.align(Alignment.CenterStart).offset(x = x.dp)) { content() }
}

@Composable
private fun Tile(bg: Brush, content: @Composable () -> Unit) {
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Chevron(left: Boolean, color: Color) {
    Svg(20, 30) { strokeP(if (left) "M15 3 L5 15 L15 27" else "M5 3 L15 15 L5 27", color, 3f) }
}

@Composable
private fun TempValue(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("23", 44.sp, FontWeight.Medium, spacing = (-1).sp)
            T(".5", 28.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
            T("°", 22.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Top).padding(start = 1.dp))
        }
        T(label, 19.sp, FontWeight.SemiBold, Color(0xFF1FAE4B), Modifier.padding(top = 5.dp), spacing = 0.3.sp)
    }
}

@Composable
private fun SeatHeat(mirror: Boolean) {
    Svg(70, 58) {
        val c = Ink2
        withTransform({ if (mirror) scale(-1f, 1f, Offset(35f, 29f)) }) {
            strokeP("M8 18 l9 -5 l-9 -5", c, 2.6f)
            strokeP("M30 6 q-7 0 -6 9 l4 26 h22 q5 0 4 6 h-26 q-5 0 -7 -6", c, 3f)
            strokeP("M50 41 l7 -12", c, 3f)
        }
        // 송풍 팬 (회색)
        withTransform({ translate(if (mirror) 36f else 46f, 13f) }) {
            val g = Color(0xFFC3C7CF)
            drawCircle(g, 3f, Offset.Zero)
            fillP("M0 -3 q-3 -9 5 -9 q2 6 -5 9z", g); fillP("M3 0 q9 -3 9 5 q-6 2 -9 -5z", g)
            fillP("M0 3 q3 9 -5 9 q-2 -6 5 -9z", g); fillP("M-3 0 q-9 3 -9 -5 q6 -2 9 5z", g)
        }
    }
}

@Composable
private fun Dock(modifier: Modifier) {
    Box(modifier.background(Color.White)) {
        DockItem(38) {
            Svg(54, 38) {
                fillP("M9 16 L14 5 Q15 3 18 3 H36 Q39 3 40 5 L45 16 H49 Q52 16 52 19 V30 Q52 32 50 32 H46 V36 H39 V32 H15 V36 H8 V32 H4 Q2 32 2 30 V19 Q2 16 5 16 Z", Ink)
                fillP("M14 15 L18 6.5 H36 L40 15 Z", Color.White)
                rr(7f, 21f, 8f, 4f, 2f, Color.White); rr(39f, 21f, 8f, 4f, 2f, Color.White)
            }
        }
        DockItem(124) { Chevron(true, Blue) }
        DockItem(163) { TempValue("A/C") }
        DockItem(272) { Chevron(false, Red) }
        // 앞유리 서리 제거
        DockItem(318) {
            Svg(66, 50) {
                val c = Ink2
                strokeP("M5 14 Q33 -2 61 14 L52 44 Q33 37 14 44 Z", c, 3f)
                for (x in listOf(24f, 33f, 42f)) {
                    strokeP("M$x 38 q-3.5 -5.5 0 -11.0 t0 -11.0", c, 3f)
                    strokeP("M${x - 3.5f} 20 L$x 16 L${x + 3.5f} 20", c, 3f)
                }
            }
        }
        // 뒷유리 열선
        DockItem(418) {
            Svg(58, 50) {
                val c = Ink2
                drawRoundRect(c, Offset(4f, 6f), Size(50f, 38f), CornerRadius(6f), style = Stroke(3f))
                for (x in listOf(18f, 29f, 40f)) {
                    strokeP("M$x 38 q-3.5 -6.0 0 -12.0 t0 -12.0", c, 3f)
                    strokeP("M${x - 3.5f} 18 L$x 14 L${x + 3.5f} 18", c, 3f)
                }
            }
        }
        DockItem(522) { SeatHeat(false) }
        DockItem(627) {
            Tile(Brush.linearGradient(listOf(Color(0xFF6B6F7A), Color(0xFF6B6F7A)))) {
                Svg(38, 38, 38f, 38f, -19f, -19f) {
                    drawCircle(Color.White, 4f, Offset.Zero)
                    fillP("M0 -4 q-5 -13 6 -14 q4 9 -6 14z", Color.White); fillP("M4 0 q13 -5 14 6 q-9 4 -14 -6z", Color.White)
                    fillP("M0 4 q5 13 -6 14 q-4 -9 6 -14z", Color.White); fillP("M-4 0 q-13 5 -14 -6 q9 -4 14 6z", Color.White)
                }
            }
        }
        DockItem(724) {
            Tile(Brush.linearGradient(listOf(Color(0xFF6D3CF5), Color(0xFFA64DF0)))) {
                Svg(40, 40) {
                    strokeP("M4 9 L13 17 L8.5 21 V5 L13 9 L4 17", Color.White, 2.4f)
                    strokeP("M21 30 V8 l13 -3 v20", Color.White, 2.4f)
                    drawOval(Color.White, Offset(12.5f, 26.4f), Size(9f, 7.2f)); drawOval(Color.White, Offset(25.5f, 22.4f), Size(9f, 7.2f))
                }
            }
        }
        DockItem(820) {
            Tile(Brush.linearGradient(listOf(Color(0xFF2FB384), Color(0xFF2FB384)))) {
                Svg(34, 34, 24f, 24f) {
                    fillP("M6.6 10.8a15.1 15.1 0 0 0 6.6 6.6l2.2-2.2a1 1 0 0 1 1-.25 11.4 11.4 0 0 0 3.6.57 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.45.57 3.57a1 1 0 0 1-.25 1z", Color.White)
                }
            }
        }
        DockItem(917) {
            Tile(Brush.linearGradient(listOf(Color(0xFF2F86F6), Color(0xFF2F86F6)))) {
                Svg(36, 36, 24f, 24f) { fillP("M21 3 L3 10.5 L10.5 13.5 L13.5 21 Z", Color.White) }
            }
        }
        DockItem(1018) {
            Svg(54, 54) { rr(3f, 3f, 21f, 21f, 10f, Ink); rr(30f, 3f, 21f, 21f, 10f, Ink); rr(3f, 30f, 21f, 21f, 10f, Ink); rr(30f, 30f, 21f, 21f, 10f, Ink) }
        }
        DockItem(1094) { Box(Modifier.size(2.dp, 44.dp).background(Color(0xFFD7DAE0))) }
        DockItem(1118) {
            Tile(Brush.linearGradient(listOf(Color(0xFF6B6F7A), Color(0xFF6B6F7A)))) {
                Svg(38, 34) {
                    strokeP("M19 7 Q12 3 3 4 V28 Q12 27 19 31 Q26 27 35 28 V4 Q26 3 19 7 Z", Color.White, 2.6f)
                    strokeP("M19 7 V31", Color.White, 2.6f)
                }
            }
        }
        DockItem(1215) {
            Tile(Brush.linearGradient(listOf(Color(0xFFF0479B), Color(0xFFE0398A)))) {
                Svg(38, 38) {
                    drawCircle(Color.White, 3.4f, Offset(19f, 17f))
                    strokeP("M19 21 V33", Color.White, 2.6f)
                    strokeP("M12.5 10.5 a9 9 0 0 0 0 13 M25.5 10.5 a9 9 0 0 1 0 13", Color.White, 2.6f)
                    strokeP("M7.5 5.5 a16 16 0 0 0 0 23 M30.5 5.5 a16 16 0 0 1 0 23", Color.White, 2.6f)
                }
            }
        }
        DockItem(1302) { Box(Modifier.size(2.dp, 44.dp).background(Color(0xFFD7DAE0))) }
        // 풍량 3 AUTO
        DockItem(1333) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Svg(26, 26, 26f, 26f, -13f, -13f) {
                        drawCircle(Ink, 2.6f, Offset.Zero)
                        fillP("M0 -2.6 q-3.5 -9 4 -10 q3 6 -4 10z", Ink); fillP("M2.6 0 q9 -3.5 10 4 q-6 3 -10 -4z", Ink)
                        fillP("M0 2.6 q3.5 9 -4 10 q-3 -6 4 -10z", Ink); fillP("M-2.6 0 q-9 3.5 -10 -4 q6 -3 10 4z", Ink)
                    }
                    T("3", 30.sp, FontWeight.Bold)
                }
                T("AUTO", 16.sp, FontWeight.SemiBold, Color(0xFF3A3D46), Modifier.padding(top = 4.dp))
            }
        }
        DockItem(1424) { SeatHeat(true) }
        // 탑승자 감지
        DockItem(1518) {
            Svg(62, 50) {
                val c = Ink2
                drawCircle(c, 4.5f, Offset(44f, 8f))
                strokeP("M44 16 l-6 12 h12 l-4 14", c, 3f); strokeP("M38 28 l-8 6", c, 3f)
                strokeP("M4 22 h18 M18 18 l4 4 l-4 4", c, 3f); strokeP("M4 34 h14 M14 30 l4 4 l-4 4", c, 3f)
            }
        }
        // 내기 순환
        DockItem(1618) {
            Svg(60, 50) {
                val c = Ink2
                strokeP("M6 36 V28 l5 -11 q1.5 -3 5 -3 h20 q3.5 0 5 3 l5 11 v8 z", c, 3f)
                drawCircle(c, 4f, Offset(16f, 38f), style = Stroke(3f)); drawCircle(c, 4f, Offset(40f, 38f), style = Stroke(3f))
                strokeP("M24 26 a7 7 0 1 1 6 5", c, 2.6f); strokeP("M26 33 l4 -2 l-2 -4", c, 2.6f)
            }
        }
        DockItem(1722) { Chevron(true, Blue) }
        DockItem(1762) { TempValue("SYNC") }
        DockItem(1874) { Chevron(false, Red) }
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun PleosConnectPreview() {
    PleosConnectScreen(WidgetSettings())
}
