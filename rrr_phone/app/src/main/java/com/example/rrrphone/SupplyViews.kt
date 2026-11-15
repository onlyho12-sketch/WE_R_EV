package com.example.rrrphone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrrphone.ui.theme.AppText
import kotlin.math.ceil
import kotlin.math.roundToInt

// 차량 앱과 같은 팔레트 (Common.kt)
private val CInk = Ink
private val CSub = Sub
private val CFaint = Faint
private val CLine = Line
private val CField = Field
private val CEv = Navy
private val CGas = GasOrange
private val CWindow = Navy.copy(alpha = 0.06f)

private fun hourLabel(h: Float) = "${((h.roundToInt() % 24) + 24) % 24}시"

/**
 * ① 에너지 공급 타임라인 — 지금부터 다음날 아침까지 배터리(초록)·연료(주황)가 어떻게 줄고 언제 채워지는지.
 * 싼 충전 시간대(심야 요금 / 주차 중 완속)는 연한 초록 띠로 표시.
 */
@Composable
fun SupplyTimelineChart(tl: SupplyTimeline, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(CEv); Text(" 배터리", color = CSub, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            LegendDot(CGas); Text(" 연료", color = CSub, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(width = 14.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).background(CWindow.copy(alpha = 0.35f)))
            Text(" 싼 충전 시간", color = CSub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val left = 30.dp.toPx(); val right = size.width - 6.dp.toPx()
            val top = 18.dp.toPx(); val bottom = size.height - 20.dp.toPx()
            fun x(h: Float) = left + (h - tl.start) / (tl.end - tl.start) * (right - left)
            fun y(p: Float) = bottom - p / 100f * (bottom - top)
            val small = AppText.copy(fontSize = 10.sp, color = CFaint)

            // 축 · 눈금 (0/50/100%)
            listOf(0f, 50f, 100f).forEach { p ->
                drawLine(CLine, Offset(left, y(p)), Offset(right, y(p)), 1.dp.toPx(),
                    pathEffect = if (p == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val l = tm.measure("${p.roundToInt()}%", small)
                drawText(l, topLeft = Offset(left - l.size.width - 4.dp.toPx(), y(p) - l.size.height / 2f))
            }
            // 시간 눈금 (기간에 따라 1/2/3시간 간격)
            val step = when { tl.end - tl.start <= 6f -> 1f; tl.end - tl.start <= 12f -> 2f; else -> 3f }
            var h = ceil(tl.start / step) * step
            while (h <= tl.end) {
                val l = tm.measure(hourLabel(h), small)
                drawText(l, topLeft = Offset(x(h) - l.size.width / 2f, bottom + 4.dp.toPx()))
                h += step
            }
            // 싼 충전 시간대
            tl.windows.forEach { w ->
                val x0 = x(w.start.coerceAtLeast(tl.start)); val x1 = x(w.end.coerceAtMost(tl.end))
                drawRect(CWindow, Offset(x0, top), Size(x1 - x0, bottom - top))
                val l = tm.measure(w.label, AppText.copy(fontSize = 10.sp, color = CEv, fontWeight = FontWeight.SemiBold))
                drawText(l, topLeft = Offset((x0 + 3.dp.toPx()).coerceAtMost(right - l.size.width), top - l.size.height - 1.dp.toPx()))
            }
            // 선 그리기
            fun line(pts: List<Pt>, color: Color, width: Float, dash: Boolean) {
                val path = Path()
                pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.hour), y(p.pct)) else path.lineTo(x(p.hour), y(p.pct)) }
                drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null))
            }
            line(tl.fuel, CGas, 2.dp.toPx(), dash = true)
            line(tl.battery, CEv, 3.dp.toPx(), dash = false)
            // 지금
            drawLine(CInk, Offset(x(tl.now), top), Offset(x(tl.now), bottom), 1.dp.toPx())
            val nowL = tm.measure("지금", AppText.copy(fontSize = 10.sp, color = CInk, fontWeight = FontWeight.Bold))
            drawText(nowL, topLeft = Offset(x(tl.now) + 3.dp.toPx(), bottom - nowL.size.height - 2.dp.toPx()))
            // 이벤트 (도착 / 급속 지나침 / 주유)
            // 라벨은 시간순으로 위·아래 번갈아 배치해서 겹치지 않게
            tl.events.sortedBy { it.hour }.forEachIndexed { i, e ->
                val ex = x(e.hour)
                val pct = (if (e.isFuel) tl.fuel else tl.battery).let { pts -> interp(pts, e.hour) }
                val c = if (e.isFuel) CGas else CInk
                drawCircle(Color.White, 5.dp.toPx(), Offset(ex, y(pct)))
                drawCircle(c, 3.5.dp.toPx(), Offset(ex, y(pct)))
                val l = tm.measure(e.label, AppText.copy(fontSize = 10.sp, color = c, fontWeight = FontWeight.SemiBold))
                val above = i % 2 == 0
                val ly = if (above) (y(pct) - l.size.height - 6.dp.toPx()).coerceAtLeast(0f)
                else (y(pct) + 6.dp.toPx()).coerceAtMost(bottom - l.size.height)
                drawText(l, topLeft = Offset((ex - l.size.width / 2f).coerceIn(left, right - l.size.width), ly))
            }
        }
    }
}

private fun interp(pts: List<Pt>, h: Float): Float {
    val a = pts.lastOrNull { it.hour <= h } ?: return pts.first().pct
    val b = pts.firstOrNull { it.hour >= h } ?: return pts.last().pct
    if (b.hour == a.hour) return a.pct
    return a.pct + (b.pct - a.pct) * (h - a.hour) / (b.hour - a.hour)
}

@Composable
private fun LegendDot(c: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(c))

/** ② 기름 전략 카드 — 언제 넣을지 / 어디서 넣을지 / 얼마나 */
@Composable
fun GasStrategyCard(g: GasPlan, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(CField).padding(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("연료 ${g.fuelPct}%", color = CInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("발전 주행 약 ${g.rangeKm}km", color = CSub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 1.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (g.fillInDays == 0) "오늘 가는 길에 넣으세요" else "${g.fillDayLabel} 넣으면 돼요",
            color = if (g.fillInDays == 0) CGas else CInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (g.fillInDays == 0) "이번 이동 후 연료가 ${FUEL_REFILL_PCT}% 아래로 내려가요"
            else "최근 2주 패턴(하루 발전 ${DAILY_GAS_KM.roundToInt()}km)이면 그때까지 충분해요",
            color = CFaint, fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(CGas))
            Spacer(Modifier.width(6.dp))
            Text(g.station.name, color = CInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("%,d원/L".format(g.station.wonPerL), color = CGas, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "${g.liters}L ≈ %,d원 · 평균가 대비 −%,d원".format(g.cost, g.savingVsAvg),
            color = CSub, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp),
        )
    }
}
