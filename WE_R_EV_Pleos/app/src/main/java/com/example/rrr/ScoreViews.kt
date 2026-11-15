package com.example.rrr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrr.ui.theme.AppText
import com.example.rrr.ui.theme.Hd
import kotlin.math.roundToInt

/*
 * WE'R Score 대시보드 · WE'R Rewards (T7 · T8)
 *  - 숫자는 모두 트립 기록(Ledger)에서 계산한다.
 *  - 화면 한 장에 스크롤 없이 들어오도록, 주어진 영역(가로·세로)에 맞춰 통째로 배율을 정하고
 *    행은 weight 로 나눠 빈 공간 없이 채운다.
 */

private val Ink = Hd.Ink
private val Sub = Hd.Sub
private val Faint = Hd.Faint
private val Line = Hd.Line
private val Field = Hd.SurfaceAlt
private val Navy = Hd.Navy

private val FollowColor = Hd.Navy
private val SavingColor = Color(0xFF3979D6)
private val OffPeakColor = Color(0xFFA8B1BD)

/** 디자인 기준 크기(designW × minDesignH)를 주어진 영역에 맞춰 확대/축소. 남는 방향은 내용이 늘어나 채운다 */
@Composable
internal fun FitCanvas(
    designW: Float,
    minDesignH: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / designW, maxHeight.value / minDesignH).coerceAtLeast(0.1f)
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, density.fontScale)
        ) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Hd.Surface)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

// ───────────────────── WE'R Score ─────────────────────

@Composable
fun WerScoreDashboard(region: Region, modifier: Modifier = Modifier) {
    val cfg = Regions.of(region)
    val weeks = weeklyScores(region)
    val now = weeks.last()
    val diff = now.total.roundToInt() - weeks[weeks.size - 2].total.roundToInt()
    // 가장 많이 잃은 항목 → 코칭 한 줄
    val lostFollow = SCORE_FOLLOW_MAX - now.followScore
    val lostSaving = SCORE_SAVING_MAX - now.savingScore
    val lostOffPeak = SCORE_OFFPEAK_MAX - now.offPeakScore
    val missed = now.judged - now.followed
    val tip = when {
        now.trips == 0 -> "이번 주 첫 주행을 시작해 보세요."
        lostFollow >= lostSaving && lostFollow >= lostOffPeak && missed > 0 ->
            "놓친 제안 ${missed}회만 실천해도 최대 +${lostFollow.roundToInt()}점이에요."
        lostOffPeak >= lostSaving && lostOffPeak > 0.5 ->
            "${cfg.cheapLabel} 시간에 충전하면 최대 +${lostOffPeak.roundToInt()}점이에요."
        lostSaving > 0.5 -> "싼 주유소·싼 시간대를 고르면 최대 +${lostSaving.roundToInt()}점이에요."
        else -> "이번 주는 완벽해요. 이대로만 가요!"
    }

    FitCanvas(designW = 1180f, minDesignH = 610f, modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 제목
            Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = menuBrandTitle("WE'R Score"),
                        color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("나의 EREV 에너지 활용 리포트 · 최근 7일", color = Sub, fontSize = 16.sp)
                }
                // 점수 올랐을 때: 만세 점프 + 꽃가루로 기뻐하는 르르르
                RrrMomentAnimation(
                    moment = RrrMoment.Cheer,
                    modifier = Modifier.size(width = 90.dp, height = 92.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Hd.Surface)
                        .border(1.dp, Line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        when {
                            diff > 0 -> "지난주보다 ${diff}점 올랐어요"
                            diff < 0 -> "지난주보다 ${-diff}점 내려갔어요"
                            else -> "지난주와 같은 점수예요"
                        },
                        color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "이번 주 기본 모드보다 ${money(now.savings.coerceAtLeast(0.0), region)} 아꼈어요",
                        color = Sub, fontSize = 14.sp,
                    )
                }
            }

            // 종합 점수 + 항목별 점수
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Panel(Modifier.weight(0.85f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Navy))
                        Spacer(Modifier.width(8.dp))
                        Text("종합 에너지 점수", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                    ScoreGauge(now.total, Modifier.fillMaxWidth().weight(1f))
                    Text(
                        "제안 실천 · 절약 · ${cfg.cheapLabel} 충전 종합",
                        color = Sub, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Panel(Modifier.weight(2.15f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PanelTitle("항목별 점수")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "100점 만점", color = Navy, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Hd.NavySoft)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 제안 실천률 — 원형 달성률
                        KpiCard(
                            title = "제안 실천", max = SCORE_FOLLOW_MAX, score = now.followScore, strong = true,
                            detail = "제안 ${now.judged}회 중 ${now.followed}회 실천",
                            modifier = Modifier.weight(1f),
                        ) {
                            // 항상 정원: 주어진 높이·너비 중 작은 쪽에 맞추고, 글자도 원 크기에 비례해 줄인다
                            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                val d = minOf(maxWidth, maxHeight, 84.dp)
                                Canvas(Modifier.size(d)) {
                                    val stroke = size.minDimension * 0.095f
                                    val arc = Size(size.width - stroke, size.height - stroke)
                                    val o = Offset(stroke / 2, stroke / 2)
                                    drawArc(Line, -90f, 360f, false, o, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                                    drawArc(
                                        FollowColor, -90f, 360f * now.followRate.toFloat().coerceIn(0f, 1f), false, o, arc,
                                        style = Stroke(stroke, cap = StrokeCap.Round),
                                    )
                                }
                                Text(
                                    "${(now.followRate * 100).roundToInt()}%",
                                    color = Ink, fontSize = (d.value * 0.25f).sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        // 절약률 — 기본 모드 비용 → 실제 비용
                        KpiCard(
                            title = "절약", max = SCORE_SAVING_MAX, score = now.savingScore, strong = false,
                            detail = "기본 모드보다 ${money(now.savings.coerceAtLeast(0.0), region)} 아꼈어요",
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                Text(
                                    "기본 모드 ${money(now.baseline, region)}",
                                    color = Faint, fontSize = 15.sp, textDecoration = TextDecoration.LineThrough,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        money(now.actual, region),
                                        color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "▼${(now.savingRate * 100).roundToInt()}%",
                                        color = Navy, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Hd.NavySoft)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        // 싼 시간대 충전 비율 — 10칸 미터
                        KpiCard(
                            title = "${cfg.cheapLabel} 충전", max = SCORE_OFFPEAK_MAX, score = now.offPeakScore, strong = false,
                            detail = "${now.chargedKwh.roundToInt()}kWh 중 ${now.offPeakKwh.roundToInt()}kWh 를 싼 시간에",
                            modifier = Modifier.weight(1f),
                        ) {
                            val ratio = now.offPeakRate.toFloat()
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${(ratio * 100).roundToInt()}",
                                        color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                                    )
                                    Text("%", color = Sub, fontSize = 17.sp, modifier = Modifier.padding(start = 2.dp, bottom = 5.dp))
                                    Spacer(Modifier.weight(1f))
                                    Text("싼 시간 충전 비율", color = Faint, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(10) { i ->
                                        val fill = (ratio * 10 - i).coerceIn(0f, 1f)
                                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Line)) {
                                            if (fill > 0f) Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Navy))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 하단: 맞춤 코칭 · 점수 변화 · 점수 구성
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Panel(Modifier.weight(1.05f).fillMaxHeight()) {
                    PanelTitle("맞춤 코칭")
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoachLine("✓", "이번 주 제안 ${now.followed}회를 실천했어요.", OffPeakColor, Modifier.weight(1f))
                        CoachLine(
                            "✓", "에너지 비용 약 ${(now.savingRate * 100).roundToInt()}% 절감 (${money(now.savings.coerceAtLeast(0.0), region)})",
                            OffPeakColor, Modifier.weight(1f),
                        )
                        CoachLine("↑", tip, Navy, Modifier.weight(1f))
                    }
                }

                Panel(Modifier.weight(1.4f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PanelTitle("점수 변화")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "최근 4주", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Navy)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    WeeklyScoreChart(
                        values = weeks.map { it.total },
                        labels = listOf("3주 전", "2주 전", "지난주", "이번 주"),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }

                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    PanelTitle("점수 구성")
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(Modifier.weight(SCORE_FOLLOW_MAX.toFloat()).fillMaxHeight().background(FollowColor))
                        Box(Modifier.weight(SCORE_SAVING_MAX.toFloat()).fillMaxHeight().background(SavingColor))
                        Box(Modifier.weight(SCORE_OFFPEAK_MAX.toFloat()).fillMaxHeight().background(OffPeakColor))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                        LegendRow("제안 실천", now.followScore, SCORE_FOLLOW_MAX, FollowColor)
                        LegendRow("절약", now.savingScore, SCORE_SAVING_MAX, SavingColor)
                        LegendRow("${cfg.cheapLabel} 충전", now.offPeakScore, SCORE_OFFPEAK_MAX, OffPeakColor)
                    }
                    Text("배점 50 · 30 · 20 · ${cfg.tariffNote.substringBefore(" ·")}", color = Faint, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    max: Double,
    score: Double,
    strong: Boolean,
    detail: String,
    modifier: Modifier = Modifier,
    visual: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Field).padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${max.toInt()}점", color = Faint, fontSize = 14.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp)) { visual() }
        Text(
            "${"%.1f".format(score)} / ${max.toInt()}점",
            color = if (strong) Navy else Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(detail, color = Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScoreGauge(score: Double, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val thickness = 15.dp.toPx()
            val diameter = (minOf(size.width, size.height * 1.25f) - thickness * 2).coerceAtLeast(1f)
            val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2 + 16.dp.toPx())
            val arc = Size(diameter, diameter)
            drawArc(Field, 150f, 240f, false, origin, arc, style = Stroke(thickness, cap = StrokeCap.Round))
            drawArc(
                Navy, 150f, 240f * (score / 100).toFloat().coerceIn(0f, 1f), false, origin, arc,
                style = Stroke(thickness, cap = StrokeCap.Round),
            )
        }
        Column(Modifier.padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${score.roundToInt()}", color = Ink, fontSize = 68.sp, fontWeight = FontWeight.SemiBold)
            Text("WE'R Score", color = Sub, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CoachLine(icon: String, line: String, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Field).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(line, color = Ink, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LegendRow(name: String, score: Double, max: Double, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(tint))
        Spacer(Modifier.width(10.dp))
        Text(name, color = Sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("${"%.1f".format(score)} / ${max.toInt()}", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 주별 점수 추이 — 점 위에 점수를 같이 적어 한눈에 읽히게 */
@Composable
private fun WeeklyScoreChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800))
    }
    val tm = rememberTextMeasurer()
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 100.0
    val axisMin = (kotlin.math.floor((minValue - 8.0) / 10.0) * 10.0).coerceIn(0.0, 90.0)
    val axisMax = maxOf(kotlin.math.ceil((maxValue + 6.0) / 10.0) * 10.0, axisMin + 10.0).coerceAtMost(110.0)

    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val padX = 34.dp.toPx()
        val padTop = 26.dp.toPx()
        val padBottom = 26.dp.toPx()
        val chartW = (size.width - padX * 2).coerceAtLeast(1f)
        val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
        val bottom = padTop + chartH

        for (i in 0..3) {
            val y = padTop + chartH * i / 3f
            drawLine(Line, Offset(padX / 2, y), Offset(size.width - padX / 2, y), 1.dp.toPx())
        }
        val points = values.mapIndexed { i, v ->
            val ratio = ((v - axisMin) / (axisMax - axisMin)).toFloat().coerceIn(0f, 1f)
            Offset(padX + chartW * i / (values.size - 1), bottom - chartH * ratio * progress.value)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            moveTo(points.first().x, bottom)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Navy.copy(alpha = 0.22f), Navy.copy(alpha = 0.02f)), padTop, bottom))
        drawPath(line, Navy, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        points.forEachIndexed { i, p ->
            val last = i == points.lastIndex
            if (last) drawCircle(Navy.copy(alpha = 0.12f), 12.dp.toPx(), p)
            drawCircle(Navy, (if (last) 6 else 5).dp.toPx(), p)
            drawCircle(Color.White, 2.2.dp.toPx(), p)
            // 점수
            val v = tm.measure(
                "${values[i].roundToInt()}",
                AppText.copy(fontSize = if (last) 17.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (last) Navy else Ink),
            )
            drawText(v, topLeft = Offset(p.x - v.size.width / 2f, p.y - v.size.height - 9.dp.toPx()))
            // 주 라벨
            val l = tm.measure(labels.getOrElse(i) { "" }, AppText.copy(fontSize = 13.sp, color = Sub))
            drawText(l, topLeft = Offset(p.x - l.size.width / 2f, bottom + 7.dp.toPx()))
        }
    }
}

// ───────────────────── WE'R Rewards ─────────────────────

@Composable
fun WerRewardsPage(region: Region, modifier: Modifier = Modifier) {
    val cfg = Regions.of(region)
    val now = scoreOf(Ledger.week(region, 0))
    val points = pointsOf(region)
    val monthSavings = Ledger.month(region).sumOf { it.savings.coerceAtLeast(0.0) }

    FitCanvas(designW = 1180f, minDesignH = 610f, modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    text = menuBrandTitle("WE'R Rewards"),
                    color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("아낀 만큼 쌓이는 포인트", color = Sub, fontSize = 16.sp)
            }

            // 보유 포인트
            Panel(Modifier.fillMaxWidth().weight(0.8f)) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    // 이번 달 챌린지: 떨어지는 포인트 동전을 가방으로 받는 르르르
                    RrrMomentAnimation(
                        moment = RrrMoment.Collect,
                        modifier = Modifier.size(width = 110.dp, height = 140.dp),
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("이번 달 챌린지", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "제안을 실천해서 최근 30일 동안 ${money(monthSavings, region)} 아꼈어요",
                            color = Sub, fontSize = 18.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("보유 포인트", color = Sub, fontSize = 17.sp)
                        Text(
                            "%,d P".format(points),
                            color = Navy, fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
                        )
                        Text(
                            "절약액 ${money(cfg.savingsPerPoint, region)}당 1P · 주간 80점 이상 +50P",
                            color = Faint, fontSize = 14.sp,
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().weight(1.25f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MissionPanel(
                    title = "제안 실천 미션",
                    body = "이번 주 WE'R Score 80점 달성",
                    value = "${now.total.roundToInt()}점",
                    goal = "목표 80점",
                    progress = (now.total / 100.0).toFloat(),
                    status = "제안 ${now.judged}회 중 ${now.followed}회 실천",
                    reward = if (now.total >= 80.0) "달성! +50P 적립" else "달성 시 +50P",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MissionPanel(
                    title = "${cfg.cheapLabel} 충전 미션",
                    body = "충전의 80% 이상을 싼 시간대에",
                    value = "${(now.offPeakRate * 100).roundToInt()}%",
                    goal = "목표 80%",
                    progress = now.offPeakRate.toFloat(),
                    status = "${now.chargedKwh.roundToInt()}kWh 중 ${now.offPeakKwh.roundToInt()}kWh를 싼 시간에",
                    reward = "WE'R Score 20점에 반영",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                // 포인트는 어디서 오고 어디에 쓰나요
                Panel(Modifier.weight(1.5f).fillMaxHeight()) {
                    PanelTitle("포인트는 어디서 오고 어디에 쓰나요")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "내 절약액이 아니라 제휴사 예산으로 지급돼요",
                        color = Sub, fontSize = 15.sp, lineHeight = 20.sp,
                    )
                    Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UseRow(Ic.Reward, "현대차 멤버십 포인트 전환", "OEM 멤버십", Modifier.weight(1f))
                        UseRow(Ic.Bolt, "충전 크레딧", "충전 사업자 제휴", Modifier.weight(1f))
                        UseRow(Ic.Home, "수요반응 리베이트 연계", "유틸리티 관리형 충전 프로그램", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionPanel(
    title: String,
    body: String,
    value: String,
    goal: String,
    progress: Float,
    status: String,
    reward: String,
    modifier: Modifier = Modifier,
    target: Float = 0.8f,
) {
    Panel(modifier) {
        PanelTitle(title)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Sub, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        // 가운데: 지금 값을 크게
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Navy, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.width(8.dp))
            Text(goal, color = Faint, fontSize = 15.sp, modifier = Modifier.padding(bottom = 7.dp))
        }
        Spacer(Modifier.height(10.dp))
        // 100 기준 진행 바 + 목표 눈금 (두 미션의 차이가 보이도록)
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.Center).clip(RoundedCornerShape(5.dp)).background(Field)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Navy))
            }
            Box(
                Modifier.offset(x = maxWidth * target - 1.dp).width(2.dp).fillMaxHeight()
                    .background(Ink.copy(alpha = 0.55f))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(status, color = Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(reward, color = Navy, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UseRow(icon: Ic, title: String, sub: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 네이비 원 안에 선 아이콘
        Box(Modifier.size(44.dp).clip(CircleShape).background(Hd.NavySoft), contentAlignment = Alignment.Center) {
            LineIcon(icon, Navy, size = 24.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = Faint, fontSize = 22.sp)
    }
}
