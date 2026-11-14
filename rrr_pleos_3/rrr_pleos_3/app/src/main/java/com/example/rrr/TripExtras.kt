package com.example.rrr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrr.pleos.Source
import com.example.rrr.ui.theme.Hd
import kotlin.math.abs

// 기존 비용 설명의 문구만 번역합니다.
// 금액·단가·계산값은 변경하지 않습니다.
private fun englishTripCostNote(note: String): String {
    return note
        .replace(
            "저녁에 귀가해 바로 충전",
            "charge on returning home in the evening"
        )
        .replace(
            "도착하자마자 충전",
            "charge on arrival"
        )
        .replace(
            "주차하는 동안 완속",
            "Level 2 charging while parked"
        )
        .replace(
            "급속 생략 · 남은 충전은 ",
            "skip DC fast charging; recharge "
        )
        .replace(
            "충전 정차 없이 발전 주행",
            "generator driving without charging stops"
        )
        .replace(
            "배터리 보존 · ",
            "preserve battery; "
        )
        .replace(
            " 시간에 집 충전",
            " home charging"
        )
        .replace(
            " 시간에 집에서",
            " at home"
        )
        .replace(
            " 주유",
            " refueling"
        )
        .replace("오프피크", "off-peak")
        .replace("미드피크", "mid-peak")
        .replace("피크", "peak")
        .replace("심야", "off-peak")
        .replace("일반", "standard")
}

// ───────────────────── 트립 비용 카드 ─────────────────────

@Composable
fun TripCostCard(
    plan: Plan,
    cfg: RegionConfig,
    modifier: Modifier = Modifier
) {
    val r = cfg.region
    val isUs = r == Region.US
    val cost = plan.cost

    // 표시 단위보다 작은 차이는 '비슷함'
    val unit = if (isUs) 0.01 else 1.0

    val badge = when {
        plan.rule == Rule.TimeFirst -> {
            if (isUs) {
                "About $FAST_DETOUR_MIN min faster"
            } else {
                "약 ${FAST_DETOUR_MIN}분 단축"
            }
        }

        cost.savings >= unit -> {
            if (isUs) {
                "Save ${money(cost.savings, r)}"
            } else {
                "${money(cost.savings, r)} 절약"
            }
        }

        else -> null
    }

    val explanation = if (isUs) {
        when {
            plan.rule == Rule.TimeFirst ->
                "No charging stops · Save time today"

            cost.savings >= unit ->
                "Plan around electricity rates, fuel prices and parking time"

            abs(cost.savings) < unit ->
                "Similar cost to default mode"

            else ->
                "Higher estimated cost than default mode"
        }
    } else {
        when {
            plan.rule == Rule.TimeFirst ->
                "충전 정차 없음 · 오늘은 돈보다 시간을 아껴요"

            cost.savings >= unit ->
                "차가 모르는 것: 시간대별 전기요금 · 주유소 가격 · 주차 시간"

            abs(cost.savings) < unit ->
                "이번 트립은 기본 모드와 비슷해요"

            else ->
                "이번엔 조금 더 들지만 ${plan.benefit}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Hd.SurfaceAlt)
            .padding(14.dp)
    ) {
        // 제목과 요금 출처
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isUs) {
                    "Trip cost"
                } else {
                    "이번 트립 비용"
                },
                color = Hd.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = if (isUs) {
                    "Sample rates"
                } else {
                    cfg.tariffNote.substringBefore(" ·")
                },
                color = Hd.Faint,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // 기본 모드와 제안 비용 비교
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = if (isUs) {
                        "Default mode"
                    } else {
                        "기본 모드"
                    },
                    color = Hd.Faint,
                    fontSize = 11.sp
                )

                Text(
                    text = money(cost.baseline.total, r),
                    color = Hd.Faint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.LineThrough
                )
            }

            Text(
                text = "→",
                color = Hd.Faint,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 2.dp)
            )

            Column {
                Text(
                    text = "We'r ev",
                    color = Hd.Navy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = money(cost.planned.total, r),
                    color = Hd.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.weight(1f))

            badge?.let { label ->
                Text(
                    text = label,
                    color = Hd.Navy,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Hd.NavySoft)
                        .padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = explanation,
            color = Hd.Sub,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (isUs) {
                "Default = battery first, then ${
                    englishTripCostNote(cost.baseline.note)
                }"
            } else {
                "기본 모드 = 배터리 먼저 쓰고 ${cost.baseline.note}"
            },
            color = Hd.Faint,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )

        Text(
            text = if (isUs) {
                "We'r ev = ${
                    englishTripCostNote(cost.planned.note)
                }"
            } else {
                "We'r ev = ${cost.planned.note}"
            },
            color = Hd.Faint,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

// ───────────────────── 데이터 출처 배지 ─────────────────────

@Composable
fun SourceBadge(
    source: Source,
    modifier: Modifier = Modifier,
    region: Region = Region.KR
) {
    val live = source == Source.PLEOS

    val label = when {
        !live -> "DEMO"
        region == Region.US -> "PLEOS live"
        else -> "PLEOS 실시간"
    }

    Text(
        text = label,
        color = if (live) Hd.Navy else Hd.Faint,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (live) Hd.Navy else Hd.Line,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(
                horizontal = 5.dp,
                vertical = 1.dp
            )
    )
}

// ───────────────────── 시연 도구 상태 ─────────────────────

data class DemoToolsState(
    val tripActive: Boolean,
    val chargePending: Boolean,
    val vehicle: Source,
    val navi: Source,
    val voice: Source
)

// ───────────────────── 시연 도구 ─────────────────────

@Composable
fun DemoTools(
    state: DemoToolsState,
    onStartTrip: () -> Unit,
    onMoment: () -> Unit,
    onEndTrip: () -> Unit,
    onSkipToOffPeak: () -> Unit,
    modifier: Modifier = Modifier,
    region: Region = Region.KR
) {
    val isUs = region == Region.US

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Hd.Line,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = if (isUs) {
                "Demo controls"
            } else {
                "시연 도구"
            },
            color = Hd.Sub,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = if (isUs) {
                "Vehicle ${state.vehicle} · " +
                        "Nav ${state.navi} · " +
                        "Voice ${state.voice}" +
                        if (state.tripActive) {
                            " · Trip in progress"
                        } else {
                            ""
                        }
            } else {
                "차량 ${state.vehicle} · " +
                        "내비 ${state.navi} · " +
                        "음성 ${state.voice}" +
                        if (state.tripActive) {
                            " · 트립 진행 중"
                        } else {
                            ""
                        }
            },
            color = Hd.Faint,
            fontSize = 10.sp
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolButton(
                label = if (isUs) {
                    if (state.tripActive) {
                        "In progress"
                    } else {
                        "Start trip"
                    }
                } else {
                    if (state.tripActive) {
                        "트립 진행 중"
                    } else {
                        "트립 시작"
                    }
                },
                enabled = !state.tripActive,
                onClick = onStartTrip,
                modifier = Modifier.weight(1f)
            )

            ToolButton(
                label = if (isUs) {
                    "Voice cue"
                } else {
                    "결정적 순간 음성"
                },
                enabled = true,
                onClick = onMoment,
                modifier = Modifier.weight(1.2f)
            )

            ToolButton(
                label = if (isUs) {
                    "End trip"
                } else {
                    "트립 종료"
                },
                enabled = state.tripActive,
                onClick = onEndTrip,
                modifier = Modifier.weight(1f)
            )
        }

        if (state.chargePending) {
            Spacer(Modifier.height(6.dp))

            ToolButton(
                label = if (isUs) {
                    "Skip to off-peak → Start charging"
                } else {
                    "싼 시간대로 건너뛰기 → 지금 충전 시작"
                },
                enabled = true,
                onClick = onSkipToOffPeak,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        color = if (enabled) Hd.Navy else Hd.Faint,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (enabled) {
                    Hd.NavySoft
                } else {
                    Hd.SurfaceAlt
                }
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                horizontal = 6.dp,
                vertical = 9.dp
            )
    )
}

// ───────────────────── 시연용 배터리 조절 ─────────────────────

@Composable
fun DemoStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            label = "−",
            onClick = {
                onChange(value - 5)
            }
        )

        Text(
            text = "$value%",
            color = Hd.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(50.dp)
        )

        StepperButton(
            label = "+",
            onClick = {
                onChange(value + 5)
            }
        )
    }
}

@Composable
private fun StepperButton(
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = Hd.Ink,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .border(
                width = 1.dp,
                color = Hd.Line,
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(
                onClick = onClick
            )
            .padding(vertical = 3.dp)
    )
}