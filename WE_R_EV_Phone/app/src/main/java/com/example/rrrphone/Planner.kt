package com.example.rrrphone

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 르르링 연료 공급 전략 (데모 상수 — 실제 차량 데이터/요금 API로 교체 예정)
 *  - 배터리 20kWh, 4km/kWh → 전기 주행 최대 80km
 *  - 발전(엔진) 주행: 휘발유 1,650원/L ÷ 14km/L ≈ 118원/km, 연료탱크 50L
 *
 * 르르링의 범위는 "언제·어디서·무엇을(전기/기름) 채울지" — 주행 중 길 안내는 내비게이션 몫.
 */
const val BATTERY_KWH = 20.0
const val KM_PER_KWH = 4.0
const val EV_FULL_KM = BATTERY_KWH * KM_PER_KWH
const val RESERVE_PCT = 10
const val GAS_WON_PER_KM = 118.0
const val GAS_KM_PER_L = 14.0
const val FUEL_TANK_L = 50.0
const val GAS_AVG_WON = 1650
const val SLOW_KW = 7.0
const val FAST_DETOUR_MIN = 25
/** 최근 2주 패턴의 하루 평균 발전 주행 거리 (데모) */
const val DAILY_GAS_KM = 20.0
/** 연료가 이 아래로 떨어지기 전에 넣도록 추천 */
const val FUEL_REFILL_PCT = 25
/** 심야 요금 시간 (23:00 ~ 다음날 07:00) */
const val NIGHT_START_H = 23f
const val NIGHT_END_H = 31f

enum class Priority(val label: String) { Economy("경제 우선"), Balance("균형"), Time("시간 우선") }

enum class Energy(val label: String) { Electric("전기로 주행"), Gas("기름(발전)으로 주행") }

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
    val cost: Int,
    val savingVsAvg: Int,
)

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
    /** 내비 위젯에서 '결정적인 순간'에만 하는 한마디 */
    val widgetMoment: String,
    val evKm: Int,
    val gasKm: Int,
    val timeline: SupplyTimeline,
    val gas: GasPlan,
)

fun won(v: Double): String = "%,d".format(v.roundToInt())

/** 도착 시각 기준 다음(또는 진행 중인) 심야 요금 구간 — 새벽(07시 전)이면 이미 진행 중인 구간 */
private fun nightWindowFor(h: Float): Pair<Float, Float> {
    val day = kotlin.math.floor(h / 24f) * 24f
    val t = h - day
    return if (t < NIGHT_END_H - 24f) (day + NIGHT_START_H - 24f) to (day + NIGHT_END_H - 24f)
    else (day + NIGHT_START_H) to (day + NIGHT_END_H)
}

private fun clock(h: Float): String {
    val m = ((h % 24f + 24f) % 24f * 60).roundToInt()
    return "%02d:%02d".format(m / 60 % 24, m % 60)
}

fun planGas(fuelPct: Int, tripGasKm: Double, station: GasStation, today: LocalDate = LocalDate.now()): GasPlan {
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
        liters = liters, cost = liters * station.wonPerL, savingVsAvg = liters * (GAS_AVG_WON - station.wonPerL),
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
): Plan {
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
    val split = if (evKm < 0.5) "발전 ${gasKm.roundToInt()}km" else if (mixed) "전기 ${evKm.roundToInt()}km + 발전 ${gasKm.roundToInt()}km" else "전기 ${evKm.roundToInt()}km"
    val gas = planGas(fuel, gasKm, station)

    // ── 타임라인 ──
    val h0 = nowHour
    val h1 = h0 + durMin / 60f
    val fuelArr = (fuel - gasKm / GAS_KM_PER_L / FUEL_TANK_L * 100).toFloat()
    // 그래프 기간: 충전 구간이 없으면 이동이 잘 보이게 짧게 (이동이 그래프의 약 40%)
    var end = h1 + max(1.5f, (h1 - h0) * 1.5f)
    val battery = mutableListOf(Pt(h0, soc.toFloat()), Pt(h1, arriveSoc.toFloat()))
    val windows = mutableListOf<SupplyWindow>()
    val events = mutableListOf(SupplyEvent(h1, "${dest.short} 도착"))
    when (rule) {
        Rule.HomeCharge -> {
            val (wStart, wEnd) = nightWindowFor(h1)
            val start = max(h1, wStart)
            val hours = ((100 - arriveSoc) / 100f * BATTERY_KWH.toFloat() / SLOW_KW.toFloat())
            battery += Pt(start, arriveSoc.toFloat()); battery += Pt(start + hours, 100f)
            windows += SupplyWindow(wStart, wEnd, "심야 요금 ${home!!.wonPerKwh}원/kWh")
            end = max(end, wEnd + 1f)
        }
        Rule.DestSlow -> {
            val kwh = min(SLOW_KW * dest.parkHours, (100 - arriveSoc) / 100.0 * BATTERY_KWH)
            val after = (arriveSoc + kwh / BATTERY_KWH * 100).toFloat().coerceAtMost(100f)
            battery += Pt(h1 + (kwh / SLOW_KW).toFloat(), after)
            windows += SupplyWindow(h1, h1 + dest.parkHours, "주차 중 완속 ${slow!!.wonPerKwh}원/kWh")
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

    val stationAction = "가는 길 ${gas.station.short} 저장"
    val stationDone = "✓ ${gas.station.short} 저장됨 · 내비 경유지로 보낼 수 있어요"
    return when (rule) {
        Rule.TimeFirst -> Plan(
            rule, Energy.Gas,
            headline = "기름으로 바로 출발하세요",
            benefit = "충전 정차 0회 · 최단 도착",
            action = stationAction, actionDone = stationDone, actionKind = ActionKind.SaveStation,
            reason = "시간 우선 선택 · 충전 없이 $split",
            lrrr = "오늘은 시간이 먼저예요. 충전 생각 말고 바로 가요.",
            widgetMoment = "충전 생각 말고 그대로 가요",
            evKm = 0, gasKm = distKm.roundToInt(), timeline = timeline, gas = gas,
        )
        Rule.HomeCharge -> Plan(
            rule, Energy.Electric,
            headline = if (mixed) "전기 먼저, 모자란 만큼만 발전" else "배터리 다 쓰고 들어가세요",
            benefit = "기름 대비 −${won((GAS_WON_PER_KM - home!!.wonPerKwh / KM_PER_KWH) * evKm)}원",
            action = nightWindowFor(h1).let { (s, _) -> if (h1 >= s) "도착하면 바로 심야 충전" else "오늘 밤 ${clock(s)} 충전 예약" },
            actionDone = nightWindowFor(h1).let { (s, _) -> "✓ ${clock(max(h1, s))} 심야 충전 예약됨 · 07:00 전 완료" },
            actionKind = ActionKind.ScheduleHomeCharge,
            reason = "${home.name} · 심야 kWh당 ${home.wonPerKwh}원 · $split",
            lrrr = if (night) "야근하셔서 늦었네요. 밤이고 주택가라 조용하게 전기로 들어가요."
            else "오늘 밤 집에서 싸게 충전할 수 있으니 배터리 아끼지 말고 쓰세요.",
            widgetMoment = "배터리 다 써도 돼요 · 밤에 집에서 채워요",
            evKm = evKm.roundToInt(), gasKm = gasKm.roundToInt(), timeline = timeline, gas = gas,
        )
        Rule.DestSlow -> {
            val kwh = min(SLOW_KW * dest.parkHours, (100 - arriveSoc) / 100.0 * BATTERY_KWH)
            Plan(
                rule, Energy.Electric,
                headline = "주차하는 동안 충전하세요",
                benefit = "다음 주행 기름 대비 −${won((GAS_WON_PER_KM - slow!!.wonPerKwh / KM_PER_KWH) * kwh * KM_PER_KWH)}원",
                action = "도착 전에 완속 빈자리 알려주기",
                actionDone = "✓ 도착 10분 전에 ${slow.name} 빈자리 알려드릴게요",
                actionKind = ActionKind.SlowChargeAlert,
                reason = "${slow.name} · kWh당 ${slow.wonPerKwh}원 · 주차 ${dest.parkHours}시간 동안 ${kwh.roundToInt()}kWh",
                lrrr = "${dest.short}에 싼 완속충전기가 있어요. 볼일 보는 동안 채워요.",
                widgetMoment = "${dest.short} 주차장 B2 완속에 대세요",
                evKm = evKm.roundToInt(), gasKm = gasKm.roundToInt(), timeline = timeline, gas = gas,
            )
        }
        Rule.FastOnly -> Plan(
            rule, if (mixed) Energy.Gas else Energy.Electric,
            headline = if (mixed) "급속은 건너뛰고 기름으로" else "충전 없이 전기로 도착해요",
            benefit = "급속 충전 대기 ${FAST_DETOUR_MIN}분 절약",
            action = stationAction, actionDone = stationDone, actionKind = ActionKind.SaveStation,
            reason = "목적지엔 ${fast!!.name}(kWh당 ${fast.wonPerKwh}원)뿐 · $split · 남은 충전은 집에서 심야 요금으로",
            lrrr = "급속은 비싸고 오래 걸려요. 이번엔 그냥 지나치고 엔진 발전으로 가요.",
            widgetMoment = "곧 급속충전소 — 그냥 지나가요",
            evKm = evKm.roundToInt(), gasKm = gasKm.roundToInt(), timeline = timeline, gas = gas,
        )
        Rule.NoCharger -> Plan(
            rule, Energy.Gas,
            headline = "배터리 아끼고 발전으로 가세요",
            benefit = "돌아오는 도심 구간 전기 ${evRange.roundToInt()}km 확보",
            action = stationAction, actionDone = stationDone, actionKind = ActionKind.SaveStation,
            reason = "${dest.short} 주변에 완속·급속 모두 없음 · 고속 구간은 발전, 배터리는 귀가길 도심·주택가용",
            lrrr = "${dest.short} 근처엔 충전소가 없어요. 배터리는 돌아오는 길에 쓰게 남겨둘게요.",
            widgetMoment = "배터리는 돌아올 때 쓸게요",
            evKm = 0, gasKm = distKm.roundToInt(), timeline = timeline, gas = gas,
        )
    }
}
