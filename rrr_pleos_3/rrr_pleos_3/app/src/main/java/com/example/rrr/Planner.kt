package com.example.rrr

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * We'r ev 연료 공급 전략
 *
 * 차량 상수 (VehicleSpec) — 싼타페 EREV 공식 제원이 공개되지 않아 모두 "가정값".
 *  - 배터리 20kWh, 4km/kWh → 전기 주행 최대 80km
 *  - 발전(엔진) 주행 14km/L, 연료탱크 50L
 * 요금·유가는 Region.kt 의 RegionConfig (예시 요금) 에서 온다.
 * 계산은 km · kWh · L 로 하고, 문자열을 만들 때만 지역 단위(mi · gal · $)로 바꾼다.
 *
 * We'r ev 의 범위는 "언제·어디서·무엇을(전기/기름) 채울지" — 주행 중 길 안내는 내비게이션 몫.
 */
const val BATTERY_KWH = 20.0
const val KM_PER_KWH = 4.0
const val EV_FULL_KM = BATTERY_KWH * KM_PER_KWH
const val RESERVE_PCT = 10
const val GAS_KM_PER_L = 14.0
const val FUEL_TANK_L = 50.0
const val SLOW_KW = 7.0
const val FAST_DETOUR_MIN = 25
/** 집 충전 목표 (Vehicle SDK setEvChargePercentLimit 로 보내는 값) */
const val CHARGE_TARGET_PCT = 90
/** 최근 2주 패턴의 하루 평균 발전 주행 거리 (데모) */
const val DAILY_GAS_KM = 20.0
/** 연료가 이 아래로 떨어지기 전에 넣도록 추천 */
const val FUEL_REFILL_PCT = 25
/** 기본 모드 가정: 목적지가 집이 아니면 그날 저녁 이 시각에 귀가해서 바로 충전기를 꽂는다 */
const val BASELINE_EVENING_PLUG_H = 18f

enum class Priority(val label: String) { Economy("경제 우선"), Balance("균형"), Time("시간 우선") }

enum class Energy(val label: String) { Electric("배터리 주행"), Gas("발전 주행") }

/** 스토리라인의 판단 표 그대로 */
enum class Rule(val condition: String, val answer: String) {
    HomeCharge("오늘 밤 집 충전 가능", "배터리 다 쓰고 들어가기"),
    DestSlow("목적지에 완속충전", "주차하는 동안 충전"),
    FastOnly("급속충전밖에 없음", "충전 건너뛰고 기름으로"),
    NoCharger("충전소가 없음", "배터리 아끼고 발전 주행"),
    TimeFirst("급한 날 (시간 우선)", "기름으로 바로 출발"),
}

/** ③ 결론 → '채우기' 행동 (주행 모드 조작이 아님) */
enum class ActionKind { ScheduleHomeCharge, SlowChargeAlert, SaveStation }

// ───────────── ① 에너지 공급 타임라인 ─────────────

/** hour: 오늘 0시 기준 시간(24 이상이면 다음날) */
data class Pt(val hour: Float, val pct: Float)
data class SupplyWindow(val start: Float, val end: Float, val label: String)
data class SupplyEvent(val hour: Float, val label: String, val isFuel: Boolean = false)

data class SupplyTimeline(
    val start: Float,
    val end: Float,
    val now: Float,
    val battery: List<Pt>,
    val fuel: List<Pt>,
    val windows: List<SupplyWindow>,
    val events: List<SupplyEvent>,
)

// ───────────── ② 기름 전략 ─────────────

data class GasPlan(
    val fuelPct: Int,
    val rangeKm: Int,
    /** 0 = 오늘 넣기, n = n일 뒤 */
    val fillInDays: Int,
    val fillDayLabel: String,
    val station: GasStation,
    val liters: Int,
    val cost: Double,
    val savingVsAvg: Double,
    val region: Region,
)

// ───────────── ③ 기본 모드 대비 비용 (T6) ─────────────

data class CostBreakdown(val elec: Double, val gas: Double, val note: String) {
    val total: Double get() = elec + gas
}

/**
 * 이번 트립에 쓴 에너지를 다시 채우는 비용.
 *
 * baseline = "차 기본 모드" (차가 알아서 하는 에너지 관리):
 *   1) 배터리 먼저 다 쓰고(예비 10% 남김) 나머지는 엔진 발전
 *   2) 쓴 전기는 집에 도착하자마자 플러그인 → 그 시각의 요금 (피크일 수 있음)
 *      목적지가 집이 아니면 그날 저녁(18시)에 귀가해서 꽂는 것으로 가정
 *   3) 쓴 연료는 지역 평균 가격으로 다시 채움
 * planned = We'r ev 제안대로 (싼 시간대 집충전 / 목적지 완속 / 급속 생략 / 배터리 보존, 주유는 고른 주유소 가격)
 */
data class TripCost(val baseline: CostBreakdown, val planned: CostBreakdown) {
    val savings: Double get() = baseline.total - planned.total
}

data class Plan(
    val rule: Rule,
    val energy: Energy,
    val headline: String,
    val benefit: String,
    val action: String,
    val actionDone: String,
    val actionKind: ActionKind,
    val reason: String,
    val lrrr: String,
    /** 내비 위젯에서 '결정적인 순간'에만 하는 한마디 (TTS 로도 같은 문장을 1회 말함) */
    val widgetMoment: String,
    val evKm: Int,
    val gasKm: Int,
    val timeline: SupplyTimeline,
    val gas: GasPlan,
    val cost: TripCost,
    /** 이 제안대로 하면 충전하게 되는 양 (기록용) */
    val chargeKwh: Double,
    /** 집 충전을 시작할 시각 (오늘 0시 기준 시간) — 집 충전 제안일 때만 */
    val chargeStartHour: Float?,
    val arriveHour: Float,
    val region: Region,
)

private fun clock(h: Float): String {
    val m = ((h % 24f + 24f) % 24f * 60).roundToInt()
    return "%02d:%02d".format(m / 60 % 24, m % 60)
}

fun planGas(
    fuelPct: Int,
    tripGasKm: Double,
    station: GasStation,
    cfg: RegionConfig,
    today: LocalDate = LocalDate.now(),
): GasPlan {
    val afterTrip = fuelPct - tripGasKm / GAS_KM_PER_L / FUEL_TANK_L * 100
    val rangeKm = (fuelPct / 100.0 * FUEL_TANK_L * GAS_KM_PER_L).roundToInt()
    val spareKm = (afterTrip - FUEL_REFILL_PCT) / 100.0 * FUEL_TANK_L * GAS_KM_PER_L
    val days = if (spareKm <= 0) 0 else (spareKm / DAILY_GAS_KM).toInt()
    val day = today.plusDays(days.toLong())
    val dayLabel = when (days) {
        0 -> "오늘 가는 길에"
        1 -> "내일"
        in 2..6 -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        else -> "다음 주 ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}쯤"
    }
    val liters = ((80 - max(afterTrip, 0.0)) / 100 * FUEL_TANK_L).roundToInt().coerceAtLeast(10)
    return GasPlan(
        fuelPct = fuelPct, rangeKm = rangeKm, fillInDays = days, fillDayLabel = dayLabel, station = station,
        liters = liters, cost = liters * station.perL, savingVsAvg = liters * (cfg.gasAvgPerL - station.perL),
        region = cfg.region,
    )
}

fun planTrip(
    origin: Place,
    dest: Place,
    soc: Int,
    fuel: Int,
    priority: Priority,
    distKm: Double,
    durMin: Int,
    nowHour: Float,
    night: Boolean,
    station: GasStation,
    cfg: RegionConfig,
): Plan {
    val r = cfg.region
    val tariff = cfg.homeTariff
    val cheap = tariff.cheapest
    val evRange = (soc - RESERVE_PCT).coerceAtLeast(0) / 100.0 * EV_FULL_KM
    val time = priority == Priority.Time
    val home = dest.home
    val slow = dest.slow
    val fast = dest.fast
    val rule = when {
        time -> Rule.TimeFirst
        home != null -> Rule.HomeCharge
        slow != null -> Rule.DestSlow
        fast != null -> Rule.FastOnly
        else -> Rule.NoCharger
    }
    // 이번 이동에서 전기/발전 분담
    val evKm = when (rule) {
        Rule.TimeFirst, Rule.NoCharger -> 0.0
        else -> min(distKm, evRange)
    }
    val gasKm = distKm - evKm
    val mixed = gasKm > 0.5
    val arriveSoc = (soc - evKm / EV_FULL_KM * 100).roundToInt().coerceAtLeast(0)
    val split = if (evKm < 0.5) "발전 ${distText(gasKm, r)}"
    else if (mixed) "배터리 ${distText(evKm, r)} + 발전 ${distText(gasKm, r)}"
    else "배터리 ${distText(evKm, r)}"
    val gas = planGas(fuel, gasKm, station, cfg)

    // ── 타임라인 ──
    val h0 = nowHour
    val h1 = h0 + durMin / 60f
    val fuelArr = (fuel - gasKm / GAS_KM_PER_L / FUEL_TANK_L * 100).toFloat()
    // 그래프 기간: 충전 구간이 없으면 이동이 잘 보이게 짧게 (이동이 그래프의 약 40%)
    var end = h1 + max(1.5f, (h1 - h0) * 1.5f)
    val battery = mutableListOf(Pt(h0, soc.toFloat()), Pt(h1, arriveSoc.toFloat()))
    val windows = mutableListOf<SupplyWindow>()
    val events = mutableListOf(SupplyEvent(h1, "${dest.short} 도착"))
    // 집 충전: 가장 싼 요금 구간에서 시작
    val (wStart, wEnd) = tariff.cheapWindowFor(h1)
    val homeStart = max(h1, wStart)
    var chargeKwh = 0.0
    when (rule) {
        Rule.HomeCharge -> {
            val target = max(CHARGE_TARGET_PCT, arriveSoc)
            chargeKwh = (target - arriveSoc) / 100.0 * BATTERY_KWH
            val hours = (chargeKwh / SLOW_KW).toFloat()
            battery += Pt(homeStart, arriveSoc.toFloat()); battery += Pt(homeStart + hours, target.toFloat())
            windows += SupplyWindow(wStart, wEnd, "${cfg.cheapLabel} 요금 ${perKwhText(cheap.perKwh, r)}")
            end = max(end, wEnd + 1f)
        }
        Rule.DestSlow -> {
            chargeKwh = min(SLOW_KW * dest.parkHours, (100 - arriveSoc) / 100.0 * BATTERY_KWH)
            val after = (arriveSoc + chargeKwh / BATTERY_KWH * 100).toFloat().coerceAtMost(100f)
            battery += Pt(h1 + (chargeKwh / SLOW_KW).toFloat(), after)
            windows += SupplyWindow(h1, h1 + dest.parkHours, "주차 중 완속 ${perKwhText(slow!!.perKwh, r)}")
            end = max(end, h1 + dest.parkHours + 1f)
        }
        Rule.FastOnly -> events += SupplyEvent(h0 + (h1 - h0) * 0.6f, "급속 지나침")
        else -> Unit
    }
    if (gas.fillInDays == 0 && rule != Rule.HomeCharge && rule != Rule.DestSlow) {
        events += SupplyEvent(h0 + (h1 - h0) * 0.35f, "${gas.station.short} 주유", isFuel = true)
    }
    battery += Pt(end, battery.last().pct)
    val timeline = SupplyTimeline(
        start = h0, end = end, now = h0,
        battery = battery.sortedBy { it.hour },
        fuel = listOf(Pt(h0, fuel.toFloat()), Pt(h1, fuelArr), Pt(end, fuelArr)),
        windows = windows, events = events,
    )

    // ── 기본 모드 대비 비용 ──
    val baseEvKm = min(distKm, evRange)
    val baseGasKm = distKm - baseEvKm
    val plugHour = if (home != null) h1 else BASELINE_EVENING_PLUG_H
    val plugBand = tariff.bandAt(plugHour)
    val baseline = CostBreakdown(
        elec = baseEvKm / KM_PER_KWH * plugBand.perKwh,
        gas = baseGasKm / GAS_KM_PER_L * cfg.gasAvgPerL,
        note = if (home != null) "도착하자마자 충전 (${plugBand.label} ${perKwhText(plugBand.perKwh, r)})"
        else "저녁에 귀가해 바로 충전 (${plugBand.label} ${perKwhText(plugBand.perKwh, r)})",
    )
    val planElecPrice = when (rule) {
        Rule.DestSlow -> slow!!.perKwh
        else -> cheap.perKwh
    }
    val planned = CostBreakdown(
        elec = evKm / KM_PER_KWH * planElecPrice,
        gas = gasKm / GAS_KM_PER_L * station.perL,
        note = when (rule) {
            Rule.HomeCharge -> "${cfg.cheapLabel} 시간에 집 충전 (${perKwhText(cheap.perKwh, r)})"
            Rule.DestSlow -> "주차하는 동안 완속 (${perKwhText(slow!!.perKwh, r)})"
            Rule.FastOnly -> "급속 생략 · 남은 충전은 ${cfg.cheapLabel} 시간에 집에서"
            Rule.NoCharger -> "배터리 보존 · ${station.short} 주유"
            Rule.TimeFirst -> "충전 정차 없이 발전 주행"
        },
    )
    val cost = TripCost(baseline, planned)

    fun plan(
        energy: Energy, headline: String, benefit: String, action: String, actionDone: String, kind: ActionKind,
        reason: String, lrrr: String, moment: String, ev: Int, gasOnly: Int,
    ) = Plan(
        rule, energy, headline, benefit, action, actionDone, kind, reason, lrrr, moment, ev, gasOnly,
        timeline, gas, cost, chargeKwh,
        chargeStartHour = if (rule == Rule.HomeCharge) homeStart else null,
        arriveHour = h1, region = r,
    )

    val stationAction = "가는 길 ${gas.station.short} 경유지로 등록"
    val stationDone = "✓ ${gas.station.short} 경유지 등록 요청됨"
    return when (rule) {
        Rule.TimeFirst -> plan(
            Energy.Gas,
            headline = "충전 없이 바로 출발",
            benefit = "충전 정차 없음 · 최단 시간",
            action = stationAction, actionDone = stationDone, kind = ActionKind.SaveStation,
            reason = "시간 우선 선택 · 충전 없이 $split",
            lrrr = "오늘은 시간이 먼저예요. 충전 생각 말고 바로 가요.",
            moment = "충전 생각 말고 그대로 가요",
            ev = 0, gasOnly = distKm.roundToInt(),
        )
        Rule.HomeCharge -> plan(
            Energy.Electric,
            headline = if (mixed) "배터리 우선, 부족분은 발전" else "배터리로 귀가 후\n${cfg.cheapLabel} 충전",
            benefit = "휘발유 대비 ${money((cfg.gasPerKm - cheap.perKwh / KM_PER_KWH) * evKm, r)} 절약",
            action = if (h1 >= wStart) "도착하면 바로 ${cfg.cheapLabel} 충전" else "${clock(wStart)} ${cfg.cheapLabel} 충전 맡기기",
            actionDone = "✓ ${clock(homeStart)} ${cfg.cheapLabel} 요금에 충전 시작 · 목표 $CHARGE_TARGET_PCT%",
            kind = ActionKind.ScheduleHomeCharge,
            reason = "${home!!.name} · ${cfg.cheapLabel} ${perKwhText(cheap.perKwh, r)} · $split",
            lrrr = if (night) "야근하셔서 늦었네요. 밤이고 주택가라 조용하게 배터리로 들어가요."
            else "오늘 밤 집에서 싸게 충전할 수 있으니 배터리 아끼지 말고 쓰세요.",
            moment = "배터리 다 써도 돼요 · 밤에 집에서 채워요",
            ev = evKm.roundToInt(), gasOnly = gasKm.roundToInt(),
        )
        Rule.DestSlow -> plan(
            Energy.Electric,
            headline = if (r == Region.US) {
                "Level 2 charging\nwhile parked"
            } else {
                "주차 중 완속 충전"
            },
            benefit = "다음 주행 ${money((cfg.gasPerKm - slow!!.perKwh / KM_PER_KWH) * chargeKwh * KM_PER_KWH, r)} 절약",action = if (r == Region.US) {
                "Notify me of available Level 2 chargers"
            } else {
                "도착 전에 완속 빈자리 알려주기"
            },
            actionDone = "✓ 도착 10분 전에 ${slow.name} 빈자리 알려드릴게요",
            kind = ActionKind.SlowChargeAlert,
            reason = "${slow.name} · ${perKwhText(slow.perKwh, r)} · 주차 ${dest.parkHours}시간 동안 ${chargeKwh.roundToInt()}kWh",
            lrrr = if (r == Region.US) {
                "There's a low-cost Level 2 charger at ${dest.short}. Charge while you're there."
            } else {
                "${dest.short}에 싼 완속 충전기가 있어요. 볼일 보는 동안 채워요."
            },
            moment = "${dest.short} 도착하면 ${slow.name}에 대세요",
            ev = evKm.roundToInt(), gasOnly = gasKm.roundToInt(),
        )
        Rule.FastOnly -> plan(
            if (mixed) Energy.Gas else Energy.Electric,
            headline = if (mixed) "급속 충전 생략,\n발전 주행" else "충전 없이 배터리로 도착",
            benefit = "급속 충전 대기 약 ${FAST_DETOUR_MIN}분 절약",
            action = stationAction, actionDone = stationDone, kind = ActionKind.SaveStation,
            reason = "목적지엔 ${fast!!.name}(${perKwhText(fast.perKwh, r)})뿐 · $split · 남은 충전은 집에서 ${cfg.cheapLabel} 요금으로",
            lrrr = "급속은 비싸고 오래 걸려요. 이번엔 그냥 지나치고 엔진 발전으로 가요.",
            moment = "곧 급속충전소 — 그냥 지나가요",
            ev = evKm.roundToInt(), gasOnly = gasKm.roundToInt(),
        )
        Rule.NoCharger -> plan(
            Energy.Gas,
            headline = "배터리 보존, 발전 주행",
            benefit = "돌아오는 도심 구간 배터리 ${distText(evRange, r)} 확보",
            action = stationAction, actionDone = stationDone, kind = ActionKind.SaveStation,
            reason = "${dest.short} 주변에 완속·급속 모두 없음 · 고속 구간은 발전, 배터리는 귀가길 도심·주택가용",
            lrrr = "${dest.short} 근처엔 충전소가 없어요. 배터리는 돌아오는 길에 쓰게 남겨둘게요.",
            moment = "배터리는 돌아올 때 쓸게요",
            ev = 0, gasOnly = distKm.roundToInt(),
        )
    }
}
