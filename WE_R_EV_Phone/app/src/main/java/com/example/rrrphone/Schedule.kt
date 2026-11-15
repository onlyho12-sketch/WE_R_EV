package com.example.rrrphone

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 폰 앱의 하루 단위 전략.
 *  캘린더 일정(여러 곳 들르는 하루) + 그 지역 충전 인프라 + 차량 상태 → 언제·어디서 무엇을 채울지.
 *  차량(Pleos) 앱의 planTrip 이 "한 번의 이동"이라면, 여기는 "하루 전체 동선".
 */

/** 시연용 고정 시각: 오늘 15:00, 차는 회사(강남) 주차장 */
const val DEMO_NOW_H = 15f

/** Pleos 주행 데이터에서 받아오는 최근 2주 실주행 전비 (겨울 · 히터 사용) — 데모 값 */
const val PLEOS_KM_PER_KWH = 3.6

val HOME: Place = DemoPlaces.Jeongja
val WORK: Place = DemoPlaces.Gangnam

data class CalEvent(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val place: Place,
    val start: LocalTime,
    val end: LocalTime,
    /** 평소 루틴(출퇴근) — 새 일정과 구분 */
    val routine: Boolean = false,
    /** 1박 (오늘 밤 집에 안 들어감 → 집 충전 불가) */
    val overnight: Boolean = false,
    /** 구글 캘린더에서 가져온 일정이면 원본 참조 (없으면 앱/루틴 일정) */
    val google: GoogleRef? = null,
) {
    val startH: Float get() = start.hour + start.minute / 60f
    val endH: Float get() = (if (overnight || end <= start) 24f else 0f) + end.hour + end.minute / 60f
}

/** 구글 캘린더 원본 일정 (CalendarContract) */
data class GoogleRef(
    /** 반복 일정까지 구분하는 키: eventId@시작날짜 */
    val key: String,
    val eventId: Long,
    val beginMs: Long,
    val endMs: Long,
    /** 캘린더에 적힌 장소 그대로 */
    val location: String,
)

private fun hToTime(h: Float): LocalTime {
    val m = (h * 60).roundToInt().coerceIn(0, 23 * 60 + 59)
    return LocalTime.of(m / 60, m % 60)
}

/**
 * 출근 같은 루틴 시간에 다른 일정(외근 등)이 끼면 루틴을 앞뒤로 쪼갬.
 * 예) 출근 09–18 + 판교 외근 13–16 → 출근 09–13 · 판교 13–16 · 출근 16–18
 */
private fun splitRoutine(r: CalEvent, fixed: List<CalEvent>): List<CalEvent> {
    var pieces = listOf(r.startH to r.endH)
    fixed.forEach { f ->
        pieces = pieces.flatMap { (a, b) ->
            if (f.endH <= a || f.startH >= b) listOf(a to b)
            else listOf(a to f.startH, f.endH to b).filter { (x, y) -> y - x >= 1f }
        }
    }
    if (pieces.size == 1 && pieces[0] == (r.startH to r.endH)) return listOf(r)
    return pieces.filter { it.second <= 24f }.map { (a, b) -> r.copy(start = hToTime(a), end = hToTime(b)) }
}

/** Pleos Vehicle SDK 에서 오는 차량 상태 (데모 값) */
data class VehicleState(val soc: Int = 62, val fuel: Int = 48, val at: Place = WORK)

object DemoCalendar {
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    fun seed(today: LocalDate): List<CalEvent> = (0 until 21).mapNotNull { i ->
        val d = today.plusDays(i.toLong())
        if (d != today && d.dayOfWeek in weekend) null
        else CalEvent(i + 1L, d, "출근", WORK, LocalTime.of(9, 0), LocalTime.of(18, 0), routine = true)
    }
}

// ───────────── 결과 ─────────────

data class Leg(
    val from: Place, val to: Place,
    val depart: Float, val arrive: Float, val parkEnd: Float,
    val distKm: Double, val evKm: Double, val gasKm: Double,
    val socStart: Double, val socEnd: Double,
    val fuelStart: Double, val fuelEnd: Double,
)

data class SlowCharge(
    val place: Place, val charger: Charger, val start: Float, val hours: Float,
    val kwh: Double, val socBefore: Double, val socAfter: Double,
) {
    /** 5분 단위로 올림 */
    val minutes: Int get() = (ceil(hours * 60 / 5.0) * 5).toInt().coerceAtLeast(10)
    val durText: String get() = minText(minutes)
}

enum class Tone { Neutral, Ev, Gas }

sealed interface PlanItem {
    data class Stop(
        val time: String, val title: String, val place: Place, val note: String?,
        val isNew: Boolean, val tone: Tone,
    ) : PlanItem

    data class Drive(val leg: Leg) : PlanItem
}

data class DayStrategy(
    val date: LocalDate,
    val isToday: Boolean,
    val homeTonight: Boolean,
    val legs: List<Leg>,
    val charges: List<SlowCharge>,
    /** 급속만 있어서 충전을 건너뛰는 곳 */
    val skipped: List<Place>,
    /** 집 심야 충전 (시작, 끝) */
    val homeCharge: Pair<Float, Float>?,
    val soc0: Int,
    val endSoc: Double,
    val totalKm: Double,
    val evKm: Double,
    val gasKm: Double,
    val saving: Int,
    val station: GasStation,
    val gas: GasPlan,
    val timeline: SupplyTimeline,
    val items: List<PlanItem>,
    val kmPerKwh: Double,
)

// ───────────── 계산 ─────────────

private fun travelH(km: Double) = (km / (if (km < 15) 28.0 else 40.0) + 5.0 / 60).toFloat()

fun hm(h: Float): String {
    val m = (h * 60).roundToInt()
    return "%02d:%02d".format((m / 60) % 24, m % 60)
}

fun dateLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    today.plusDays(2) -> "모레"
    else -> "${d.monthValue}월 ${d.dayOfMonth}일 (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
}

fun fullDate(d: LocalDate): String =
    "${d.monthValue}월 ${d.dayOfMonth}일 ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}"

fun infraText(p: Place): String = when {
    p.home != null -> "집 완속 ${p.home!!.wonPerKwh}원/kWh"
    p.slow != null && p.fast != null -> "완속 ${p.slow!!.wonPerKwh}원 · 급속 ${p.fast!!.wonPerKwh}원/kWh"
    p.slow != null -> "완속 ${p.slow!!.wonPerKwh}원/kWh"
    p.fast != null -> "급속 ${p.fast!!.wonPerKwh}원/kWh만 있음"
    p.aliases.isEmpty() -> "충전소 정보 없음 (지도 검색 장소)"
    else -> "충전소 없음"
}

fun planDay(
    date: LocalDate,
    events: List<CalEvent>,
    car: VehicleState,
    today: LocalDate,
    kmPerKwh: Double = KM_PER_KWH,
): DayStrategy {
    val isToday = date == today
    val evFull = BATTERY_KWH * kmPerKwh
    val raw = events.filter { it.date == date }
    val fixed = raw.filter { !it.routine }
    val day = raw.flatMap { if (it.routine) splitRoutine(it, fixed) else listOf(it) }.sortedBy { it.startH }
    val upcoming = if (isToday) day.filter { it.endH > DEMO_NOW_H } else day
    val homeTonight = upcoming.lastOrNull()?.let { it.overnight && it.place != HOME } != true
    // 오늘은 실시간 차량 상태, 다른 날은 밤사이 집에서 완충했다고 가정
    val soc0 = if (isToday) car.soc else 100

    // 들를 곳 (마지막은 귀가)
    data class Stop(val event: CalEvent?, val place: Place, val startH: Float, val endH: Float)
    val stops = upcoming.map { Stop(it, it.place, it.startH, it.endH) }.toMutableList()
    if (homeTonight) stops += Stop(null, HOME, Float.MAX_VALUE, Float.MAX_VALUE)

    class Raw(val from: Place, val to: Place, val dist: Double, val depart: Float, val arrive: Float, var parkEnd: Float, val stopIdx: Int)
    val raws = mutableListOf<Raw>()
    var cur = if (isToday) car.at else HOME
    var prevEnd: Float? = if (isToday) DEMO_NOW_H else null
    // 직전 머문 곳이 루틴(회사)이면 다음 일정 시간에 맞춰 일찍 나설 수 있음 → 그곳에 도착한 시각
    var flexFrom: Float? = null
    stops.forEachIndexed { i, s ->
        if (s.place == cur) {
            if (s.event != null) {
                val pe = prevEnd ?: s.startH
                flexFrom = if (s.event.routine) pe else null
                prevEnd = max(pe, s.endH)
            }
            return@forEachIndexed
        }
        val d = roadKm(cur, s.place)
        val dur = travelH(d)
        val pe = prevEnd
        val target = s.startH - dur - 1 / 6f
        val flex = flexFrom
        val depart = when {
            pe == null -> target
            s.event == null -> pe
            flex != null && target < pe -> max(flex, target)
            else -> max(pe, target)
        }
        if (pe != null && depart < pe) raws.lastOrNull()?.let { it.parkEnd = max(it.arrive, depart) }
        val arrive = depart + dur
        val parkEnd = if (s.event == null) arrive else max(arrive, s.endH)
        raws += Raw(cur, s.place, d, depart, arrive, parkEnd, i)
        prevEnd = parkEnd
        flexFrom = if (s.event?.routine == true) arrive else null
        cur = s.place
    }

    // 어느 구간을 발전(기름)으로 갈지: 전기가 모자라면 가장 긴(고속) 구간부터 발전
    val n = raws.size
    val planned = DoubleArray(n)
    val total = raws.sumOf { it.dist }
    val evAvail0 = max(0, soc0 - RESERVE_PCT) / 100.0 * evFull
    val lastNoCharger = !homeTonight && raws.lastOrNull()?.to?.let { it.slow == null && it.home == null } == true
    if (lastNoCharger) {
        // 집에도 못 가고 충전소도 없음 → 고속 구간은 발전, 배터리는 현장(V2L)·귀가 도심용
        raws.forEachIndexed { i, r -> if (r.dist >= 15) planned[i] = r.dist }
    } else {
        val slowPotential = raws.sumOf { r -> if (r.to.slow != null) SLOW_KW * (r.parkEnd - r.arrive) * kmPerKwh else 0.0 }
        var deficit = total - evAvail0 - slowPotential
        raws.indices.sortedByDescending { raws[it].dist }.forEach { i ->
            if (deficit > 0) { val g = min(raws[i].dist, deficit); planned[i] = g; deficit -= g }
        }
    }

    // 순서대로 주행 시뮬레이션
    var soc = soc0.toDouble()
    var fuel = car.fuel.toDouble()
    val legs = mutableListOf<Leg>()
    val charges = mutableListOf<SlowCharge>()
    val skipped = mutableListOf<Place>()
    raws.forEachIndexed { i, r ->
        val evAvail = max(0.0, (soc - RESERVE_PCT) / 100.0 * evFull)
        val ev = min(r.dist - planned[i], evAvail).coerceAtLeast(0.0)
        val gas = r.dist - ev
        val socEnd = soc - ev / evFull * 100
        val fuelEnd = fuel - gas / GAS_KM_PER_L / FUEL_TANK_L * 100
        legs += Leg(r.from, r.to, r.depart, r.arrive, r.parkEnd, r.dist, ev, gas, soc, socEnd, fuel, fuelEnd)
        soc = socEnd; fuel = fuelEnd
        val park = r.parkEnd - r.arrive
        val slow = r.to.slow
        if (slow != null && park > 0.2f) {
            // 오늘 밤 집 충전(더 쌈)이 가능하면 남은 동선에 "필요한 만큼만", 아니면 주차하는 동안 가득
            val restKm = (i + 1 until n).sumOf { raws[it].dist - planned[it] }
            val needKwh = if (homeTonight) max(0.0, restKm - max(0.0, (soc - RESERVE_PCT) / 100.0 * evFull)) / kmPerKwh * 1.1
            else (100 - soc) / 100 * BATTERY_KWH
            val kwh = minOf(needKwh, SLOW_KW * park, (100 - soc) / 100 * BATTERY_KWH)
            if (kwh > 0.3) {
                val after = soc + kwh / BATTERY_KWH * 100
                charges += SlowCharge(r.to, slow, r.arrive, (kwh / SLOW_KW).toFloat(), kwh, soc, after)
                soc = after
            }
        }
        if (r.to.fast != null && r.to.slow == null && r.to.home == null) skipped += r.to
    }
    val endSoc = soc
    val lastArrive = legs.lastOrNull()?.arrive ?: (if (isToday) DEMO_NOW_H else 9f)
    val homeCharge = if (homeTonight && endSoc < 95) {
        val st = max(lastArrive, NIGHT_START_H)
        st to (st + ((100 - endSoc) / 100 * BATTERY_KWH / SLOW_KW).toFloat())
    } else null

    // 비용
    val evKm = legs.sumOf { it.evKm }
    val gasKm = legs.sumOf { it.gasKm }
    val slowKwh = charges.sumOf { it.kwh }
    val evCost = max(0.0, evKm / kmPerKwh - slowKwh) * HOME.home!!.wonPerKwh + charges.sumOf { it.kwh * it.charger.wonPerKwh }
    val saving = (evKm * GAS_WON_PER_KM - evCost).roundToInt()

    val lastPlace = raws.lastOrNull()?.to ?: HOME
    val station = pickStation(raws.flatMap { listOf(it.from.at, it.to.at) }.ifEmpty { listOf(HOME.at) }, lastPlace.at)
    val gas = planGas(car.fuel, gasKm, station, date)

    // 공급 타임라인 (차량 앱과 같은 차트)
    val start = if (isToday) DEMO_NOW_H else (legs.firstOrNull()?.depart ?: 9f) - 0.3f
    val battery = mutableListOf(Pt(start, soc0.toFloat()))
    val fuelPts = mutableListOf(Pt(start, car.fuel.toFloat()))
    legs.forEach { l ->
        battery += Pt(l.depart, l.socStart.toFloat()); battery += Pt(l.arrive, l.socEnd.toFloat())
        fuelPts += Pt(l.depart, l.fuelStart.toFloat()); fuelPts += Pt(l.arrive, l.fuelEnd.toFloat())
        charges.firstOrNull { it.place == l.to && it.start == l.arrive }?.let { c ->
            battery += Pt(c.start + c.hours, c.socAfter.toFloat())
        }
    }
    homeCharge?.let { (s, e) -> battery += Pt(s, endSoc.toFloat()); battery += Pt(e, 100f) }
    val end = max(start + 3f, (homeCharge?.second ?: lastArrive) + 1f)
    battery += Pt(end, battery.last().pct)
    fuelPts += Pt(end, fuelPts.last().pct)
    val windows = mutableListOf<SupplyWindow>()
    charges.forEach { c ->
        val l = legs.first { it.to == c.place && it.arrive == c.start }
        windows += SupplyWindow(c.start, l.parkEnd, "완속 ${c.charger.wonPerKwh}원")
    }
    if (homeTonight && end > NIGHT_START_H) windows += SupplyWindow(NIGHT_START_H, NIGHT_END_H, "심야 ${HOME.home!!.wonPerKwh}원/kWh")
    val tlEvents = legs.map { SupplyEvent(it.arrive, "${it.to.short} 도착") }.toMutableList()
    if (gas.fillInDays == 0 && legs.isNotEmpty()) {
        val l = legs.maxBy { it.gasKm }
        tlEvents += SupplyEvent((l.depart + l.arrive) / 2, "${station.short} 주유", isFuel = true)
    }
    val timeline = SupplyTimeline(
        start = start, end = end, now = start,
        battery = battery.sortedBy { it.hour }, fuel = fuelPts.sortedBy { it.hour },
        windows = windows, events = tlEvents,
    )

    // 화면용 일정 + 이동 목록
    val items = mutableListOf<PlanItem>()
    if (isToday && upcoming.firstOrNull()?.place != car.at) {
        items += PlanItem.Stop("지금", "주차 중", car.at, "배터리 ${car.soc}% · 연료 ${car.fuel}%", false, Tone.Neutral)
    }
    stops.forEachIndexed { i, s ->
        val li = raws.indexOfFirst { it.stopIdx == i }
        val leg = if (li >= 0) legs[li] else null
        leg?.let { items += PlanItem.Drive(it) }
        val charge = leg?.let { l -> charges.firstOrNull { it.place == s.place && it.start == l.arrive } }
        val note = when {
            charge != null -> "완속 ${charge.durText} 충전 · ${charge.charger.wonPerKwh}원/kWh"
            s.event == null && homeCharge != null -> "${hm(homeCharge.first)} 심야 충전 → 07:00 전 완충"
            s.place in skipped -> "급속만 있음 → 충전 건너뛰기"
            s.event?.overnight == true && s.place.slow == null -> "충전소 없음 · 배터리는 V2L·귀가용"
            else -> null
        }
        val time = when {
            s.event == null -> "${hm(leg?.arrive ?: DEMO_NOW_H)} 귀가"
            s.event.overnight -> "${hm(s.startH)}~ 1박"
            else -> "${hm(s.startH)}–${hm(s.endH)}"
        }
        val tone = when {
            charge != null || (s.event == null && homeCharge != null) -> Tone.Ev
            s.place in skipped -> Tone.Gas
            else -> Tone.Neutral
        }
        items += PlanItem.Stop(time, s.event?.title ?: "집", s.place, note, s.event?.routine == false, tone)
    }

    return DayStrategy(
        date = date, isToday = isToday, homeTonight = homeTonight, legs = legs, charges = charges,
        skipped = skipped, homeCharge = homeCharge, soc0 = soc0, endSoc = endSoc, totalKm = total,
        evKm = evKm, gasKm = gasKm, saving = saving, station = station, gas = gas,
        timeline = timeline, items = items, kmPerKwh = kmPerKwh,
    )
}

fun minText(m: Int): String = when {
    m < 60 -> "${m}분"
    m % 60 == 0 -> "${m / 60}시간"
    else -> "${m / 60}시간 ${m % 60}분"
}
