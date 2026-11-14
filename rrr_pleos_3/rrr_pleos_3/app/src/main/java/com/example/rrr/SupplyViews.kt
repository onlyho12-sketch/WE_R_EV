package com.example.rrr

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrr.ui.theme.AppText
import com.example.rrr.ui.theme.Hd
import kotlin.math.ceil
import kotlin.math.roundToInt

private val CInk = Hd.Ink
private val CSub = Hd.Sub
private val CFaint = Hd.Faint
private val CLine = Hd.Line
private val CField = Hd.SurfaceAlt
private val CEv = Hd.Ev
private val CGas = Hd.Gas
private val CWindow = Hd.Navy.copy(alpha = 0.06f)

// ───────────────────── 시간 표시 ─────────────────────

private fun hourLabel(
    h: Float,
    region: Region
): String {
    val hour = ((h.roundToInt() % 24) + 24) % 24

    return if (region == Region.US) {
        "%02d:00".format(java.util.Locale.US, hour)
    } else {
        "${hour}시"
    }
}

// ───────────────────── 그래프 문구 번역 ─────────────────────

private fun supplyLabel(
    text: String,
    region: Region
): String {
    if (region != Region.US) return text

    return text
        .replace("주차 중 완속", "Parked L2")
        .replace("오프피크 요금", "Off-peak rate")
        .replace("심야 요금", "Off-peak rate")
        .replace("미드피크 요금", "Mid-peak rate")
        .replace("피크 요금", "Peak rate")
        .replace("급속 지나침", "Skip DC fast")
        .replace("회사 도착", "Work arrival")
        .replace("집 도착", "Home arrival")
        .replace(" 도착", " arrival")
        .replace(" 주유", " refuel")
}

// ───────────────────── 에너지 공급 타임라인 ─────────────────────

@Composable
fun SupplyTimelineChart(
    tl: SupplyTimeline,
    modifier: Modifier = Modifier,
    region: Region = Region.KR
) {
    val tm = rememberTextMeasurer()
    val isUs = region == Region.US

    Column(modifier = modifier) {
        // 범례
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(CEv)

            Text(
                text = if (isUs) {
                    " Battery"
                } else {
                    " 배터리"
                },
                color = CSub,
                fontSize = 11.sp
            )

            Spacer(Modifier.width(10.dp))

            LegendDot(CGas)

            Text(
                text = if (isUs) {
                    " Fuel"
                } else {
                    " 연료"
                },
                color = CSub,
                fontSize = 11.sp
            )

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(
                        width = 14.dp,
                        height = 8.dp
                    )
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Hd.Navy.copy(alpha = 0.12f)
                    )
            )

            Text(
                text = if (isUs) {
                    " Low-rate window"
                } else {
                    " 싼 충전 시간"
                },
                color = CSub,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (tl.end <= tl.start) return@Canvas

            val left = 30.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 20.dp.toPx()

            if (right <= left || bottom <= top) {
                return@Canvas
            }

            val plotWidth = right - left

            fun x(hour: Float): Float {
                return left +
                        (hour - tl.start) /
                        (tl.end - tl.start) * plotWidth
            }

            fun y(percent: Float): Float {
                return bottom -
                        percent / 100f * (bottom - top)
            }

            val smallStyle = AppText.copy(
                fontSize = 9.sp,
                color = CFaint
            )

            // 세로축: 0%, 50%, 100%
            listOf(0f, 50f, 100f).forEach { percent ->
                drawLine(
                    color = CLine,
                    start = Offset(left, y(percent)),
                    end = Offset(right, y(percent)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (percent == 0f) {
                        null
                    } else {
                        PathEffect.dashPathEffect(
                            floatArrayOf(6f, 6f)
                        )
                    }
                )

                val label = tm.measure(
                    text = "${percent.roundToInt()}%",
                    style = smallStyle
                )

                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        x = left -
                                label.size.width -
                                4.dp.toPx(),
                        y = y(percent) -
                                label.size.height / 2f
                    )
                )
            }

            // 시간 눈금
            val step = when {
                tl.end - tl.start <= 6f -> 1f
                tl.end - tl.start <= 12f -> 2f
                else -> 3f
            }

            var hour = ceil(tl.start / step) * step

            while (hour <= tl.end) {
                val label = tm.measure(
                    text = hourLabel(hour, region),
                    style = smallStyle
                )

                val labelX = (
                        x(hour) - label.size.width / 2f
                        ).coerceIn(
                        0f,
                        (size.width - label.size.width)
                            .coerceAtLeast(0f)
                    )

                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        x = labelX,
                        y = bottom + 4.dp.toPx()
                    )
                )

                hour += step
            }

            // 저렴한 충전 시간대
            tl.windows.forEach { window ->
                val start = window.start
                    .coerceAtLeast(tl.start)

                val end = window.end
                    .coerceAtMost(tl.end)

                if (end > start) {
                    val x0 = x(start)
                    val x1 = x(end)

                    drawRect(
                        color = CWindow,
                        topLeft = Offset(x0, top),
                        size = Size(
                            width = x1 - x0,
                            height = bottom - top
                        )
                    )

                    val label = tm.measure(
                        text = supplyLabel(
                            window.label,
                            region
                        ),
                        style = AppText.copy(
                            fontSize = 9.sp,
                            color = CEv,
                            fontWeight = FontWeight.SemiBold
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(
                            maxWidth = plotWidth
                                .roundToInt()
                                .coerceAtLeast(1)
                        )
                    )

                    val labelX = (
                            x0 + 3.dp.toPx()
                            ).coerceIn(
                            left,
                            (right - label.size.width)
                                .coerceAtLeast(left)
                        )

                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            x = labelX,
                            y = top -
                                    label.size.height -
                                    1.dp.toPx()
                        )
                    )
                }
            }

            // 배터리·연료 선
            fun drawSupplyLine(
                points: List<Pt>,
                color: Color,
                width: Float,
                dashed: Boolean
            ) {
                if (points.isEmpty()) return

                val path = Path()

                points.forEachIndexed { index, point ->
                    if (index == 0) {
                        path.moveTo(
                            x(point.hour),
                            y(point.pct)
                        )
                    } else {
                        path.lineTo(
                            x(point.hour),
                            y(point.pct)
                        )
                    }
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (dashed) {
                            PathEffect.dashPathEffect(
                                floatArrayOf(10f, 7f)
                            )
                        } else {
                            null
                        }
                    )
                )
            }

            drawSupplyLine(
                points = tl.fuel,
                color = CGas,
                width = 2.dp.toPx(),
                dashed = true
            )

            drawSupplyLine(
                points = tl.battery,
                color = CEv,
                width = 3.dp.toPx(),
                dashed = false
            )

            // 현재 시각
            drawLine(
                color = CInk,
                start = Offset(x(tl.now), top),
                end = Offset(x(tl.now), bottom),
                strokeWidth = 1.dp.toPx()
            )

            val nowLabel = tm.measure(
                text = if (isUs) "Now" else "지금",
                style = AppText.copy(
                    fontSize = 9.sp,
                    color = CInk,
                    fontWeight = FontWeight.Bold
                )
            )

            drawText(
                textLayoutResult = nowLabel,
                topLeft = Offset(
                    x = (x(tl.now) + 3.dp.toPx())
                        .coerceIn(
                            left,
                            (right - nowLabel.size.width)
                                .coerceAtLeast(left)
                        ),
                    y = bottom -
                            nowLabel.size.height -
                            2.dp.toPx()
                )
            )

            // 도착·충전·주유 이벤트
            tl.events
                .sortedBy { it.hour }
                .forEachIndexed { index, event ->
                    val eventX = x(event.hour)

                    val points = if (event.isFuel) {
                        tl.fuel
                    } else {
                        tl.battery
                    }

                    val percent = interp(
                        points,
                        event.hour
                    )

                    val color = if (event.isFuel) {
                        CGas
                    } else {
                        CInk
                    }

                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = Offset(
                            eventX,
                            y(percent)
                        )
                    )

                    drawCircle(
                        color = color,
                        radius = 3.5.dp.toPx(),
                        center = Offset(
                            eventX,
                            y(percent)
                        )
                    )

                    val label = tm.measure(
                        text = supplyLabel(
                            event.label,
                            region
                        ),
                        style = AppText.copy(
                            fontSize = 9.sp,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(
                            maxWidth = plotWidth
                                .roundToInt()
                                .coerceAtLeast(1)
                        )
                    )

                    val above = index % 2 == 0

                    val labelY = if (above) {
                        (
                                y(percent) -
                                        label.size.height -
                                        6.dp.toPx()
                                ).coerceAtLeast(0f)
                    } else {
                        (
                                y(percent) + 6.dp.toPx()
                                ).coerceAtMost(
                                bottom - label.size.height
                            )
                    }

                    val labelX = (
                            eventX - label.size.width / 2f
                            ).coerceIn(
                            left,
                            (right - label.size.width)
                                .coerceAtLeast(left)
                        )

                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            labelX,
                            labelY
                        )
                    )
                }
        }
    }
}

// ───────────────────── 중간 잔량 계산 ─────────────────────

private fun interp(
    points: List<Pt>,
    hour: Float
): Float {
    if (points.isEmpty()) return 0f

    val before = points.lastOrNull {
        it.hour <= hour
    } ?: return points.first().pct

    val after = points.firstOrNull {
        it.hour >= hour
    } ?: return points.last().pct

    if (after.hour == before.hour) {
        return before.pct
    }

    return before.pct +
            (after.pct - before.pct) *
            (hour - before.hour) /
            (after.hour - before.hour)
}

// ───────────────────── 범례 점 ─────────────────────

@Composable
private fun LegendDot(
    color: Color
) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ───────────────────── 주유 전략 카드 ─────────────────────

@Composable
fun GasStrategyCard(
    g: GasPlan,
    modifier: Modifier = Modifier
) {
    val isUs = g.region == Region.US

    val refillTitle = if (isUs) {
        when (g.fillInDays) {
            0 -> "Refuel on the way today"
            1 -> "Refuel tomorrow"
            else -> "Refuel in ${g.fillInDays} days"
        }
    } else {
        if (g.fillInDays == 0) {
            "오늘 가는 길에 넣으세요"
        } else {
            "${g.fillDayLabel} 넣으면 돼요"
        }
    }

    val refillDescription = if (isUs) {
        if (g.fillInDays == 0) {
            "Fuel will fall below $FUEL_REFILL_PCT% after this trip"
        } else {
            "Based on the past two weeks: " +
                    "${distText(DAILY_GAS_KM, g.region)} " +
                    "of generator driving per day"
        }
    } else {
        if (g.fillInDays == 0) {
            "이번 이동 후 연료가 ${FUEL_REFILL_PCT}% 아래로 내려가요"
        } else {
            "최근 2주 패턴(하루 발전 " +
                    "${distText(DAILY_GAS_KM, g.region)})이면 " +
                    "그때까지 충분해요"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CField)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = if (isUs) {
                    "Fuel ${g.fuelPct}%"
                } else {
                    "연료 ${g.fuelPct}%"
                },
                color = CInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = if (isUs) {
                    "Generator range: about ${
                        distText(
                            g.rangeKm.toDouble(),
                            g.region
                        )
                    }"
                } else {
                    "발전 주행 약 ${
                        distText(
                            g.rangeKm.toDouble(),
                            g.region
                        )
                    }"
                },
                color = CSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    bottom = 1.dp
                )
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = refillTitle,
            color = if (g.fillInDays == 0) {
                Hd.Navy
            } else {
                CInk
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = refillDescription,
            color = CFaint,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CGas)
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = g.station.name,
                color = CInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = fuelPriceText(
                    g.station.perL,
                    g.region
                ),
                color = CInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = if (isUs) {
                "${
                    fuelVolText(
                        g.liters.toDouble(),
                        g.region
                    )
                } ≈ ${
                    money(
                        g.cost,
                        g.region
                    )
                } · vs. average: −${
                    money(
                        g.savingVsAvg,
                        g.region
                    )
                }"
            } else {
                "${
                    fuelVolText(
                        g.liters.toDouble(),
                        g.region
                    )
                } ≈ ${
                    money(
                        g.cost,
                        g.region
                    )
                } · 평균가 대비 −${
                    money(
                        g.savingVsAvg,
                        g.region
                    )
                }"
            },
            color = CSub,
            fontSize = 11.sp,
            modifier = Modifier.padding(
                start = 14.dp,
                top = 2.dp
            )
        )
    }
}