package com.example.rrr

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/*
 * 트립 기록 (T7) → WE'R Score · 절약액 · 포인트의 근거.
 * SharedPreferences + org.json 으로 저장 (새 의존성 없음).
 */

data class TripRecord(
    val id: String,
    val startedAt: Long,
    val region: Region,
    val origin: String,
    val dest: String,
    val distanceKm: Double,
    val evKm: Double,
    val rule: Rule,
    val priority: Priority,
    val action: ActionKind?,
    /** null = 판정 제외 (시간 우선 등) */
    val followed: Boolean?,
    /** 어떻게 판정했는지 (예: "waypoint_arrived", "action_accepted") */
    val followSignal: String,
    /** 기본 모드였다면 든 비용 */
    val baseline: Double,
    /** 제안대로 하면 드는 비용 */
    val planned: Double,
    /** 실제로 든 비용 (따랐으면 planned, 안 따랐으면 baseline) */
    val actual: Double,
    val offPeakKwh: Double,
    val totalChargedKwh: Double,
    /** 시드(가상) 데이터 */
    val simulated: Boolean,
) {
    val savings: Double get() = baseline - actual
}

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val WEEK_MS = 7 * DAY_MS

object Ledger {
    /** Compose 가 관찰할 수 있는 목록 — 기록이 추가되면 점수·절약액 화면이 바로 바뀐다 */
    val records = mutableStateListOf<TripRecord>()

    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("werev_ledger_v1", Context.MODE_PRIVATE)
        records.clear()
        records += load()
        seedIfEmpty(Regions.KR)
        seedIfEmpty(Regions.US)
    }

    fun add(r: TripRecord) {
        records += r
        save()
    }

    /** n 주 전 기록 (0 = 최근 7일, 1 = 그 전 7일 …) */
    fun week(region: Region, n: Int, now: Long = System.currentTimeMillis()): List<TripRecord> =
        records.filter { it.region == region && it.startedAt > now - (n + 1) * WEEK_MS && it.startedAt <= now - n * WEEK_MS }

    /** 최근 30일 */
    fun month(region: Region, now: Long = System.currentTimeMillis()): List<TripRecord> =
        records.filter { it.region == region && it.startedAt > now - 30 * DAY_MS }

    fun hasSimulated(region: Region) = records.any { it.region == region && it.simulated }

    /**
     * 최근 4주치 가상 트립 16개를 만든다 (simulated = true → 화면에 "시뮬레이션 데이터 포함").
     * 주가 지날수록 제안을 더 잘 따르는 흐름. 가상 트립은 저장하지 않고 앱을 켤 때마다
     * '오늘' 기준으로 다시 만든다 (시연 날짜가 달라져도 최근 4주가 비지 않게). 실제 트립은 그 위에 쌓인다.
     */
    fun seedIfEmpty(cfg: RegionConfig) {
        if (records.any { it.region == cfg.region && it.simulated }) return
        val now = System.currentTimeMillis()
        val us = cfg.region == Region.US
        // 금액 단위: 한국 100원, 미국 $0.10
        val u = if (us) 0.10 else 100.0
        val places = if (us) listOf("집" to "회사", "회사" to "집") else listOf("강남" to "집", "집" to "하남", "강남" to "판교", "집" to "강남")

        // (몇 주 전, 그 주의 며칠째, 따랐는지, 기본 비용, 제안 비용, 싼 시간 충전 kWh, 전체 충전 kWh)
        data class Seed(val week: Int, val day: Int, val followed: Boolean, val base: Double, val plan: Double, val off: Double, val all: Double)

        val seeds = listOf(
            Seed(3, 1, true, 24.0, 17.0, 4.0, 4.0), Seed(3, 2, false, 22.0, 15.0, 0.0, 4.5),
            Seed(3, 4, false, 30.0, 21.0, 0.0, 5.0), Seed(3, 6, true, 26.0, 19.0, 4.5, 4.5),
            Seed(2, 1, true, 24.0, 20.0, 4.0, 4.0), Seed(2, 3, true, 28.0, 24.0, 5.0, 5.0),
            Seed(2, 4, false, 22.0, 16.0, 1.0, 4.0), Seed(2, 6, true, 31.0, 26.0, 5.5, 5.5),
            Seed(1, 1, true, 25.0, 19.0, 4.5, 4.5), Seed(1, 2, true, 23.0, 17.0, 4.0, 4.0),
            Seed(1, 4, true, 29.0, 22.0, 5.0, 5.0), Seed(1, 5, false, 24.0, 17.0, 1.5, 4.5),
            Seed(0, 1, true, 26.0, 17.0, 4.5, 4.5), Seed(0, 2, true, 24.0, 16.0, 4.0, 4.0),
            Seed(0, 3, false, 27.0, 19.0, 2.0, 5.0), Seed(0, 5, true, 30.0, 20.0, 5.5, 5.5),
        )
        seeds.forEachIndexed { i, s ->
            val (o, d) = places[i % places.size]
            // 주 안에서 날짜를 고르게: 0주 전의 1일째 = 6일 전 … 5일째 = 2일 전
            val at = now - s.week * WEEK_MS - (7 - s.day) * DAY_MS
            records += TripRecord(
                id = "seed-${cfg.region}-$i", startedAt = at, region = cfg.region, origin = o, dest = d,
                distanceKm = 16.0 + i % 5 * 3, evKm = 14.0 + i % 4 * 2,
                rule = if (i % 3 == 0) Rule.DestSlow else Rule.HomeCharge, priority = Priority.Economy,
                action = if (i % 3 == 0) ActionKind.SlowChargeAlert else ActionKind.ScheduleHomeCharge,
                followed = s.followed, followSignal = "simulated",
                baseline = s.base * u, planned = s.plan * u, actual = (if (s.followed) s.plan else s.base) * u,
                offPeakKwh = s.off, totalChargedKwh = s.all, simulated = true,
            )
        }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        records.filter { !it.simulated }.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("at", r.startedAt).put("region", r.region.name)
                    .put("origin", r.origin).put("dest", r.dest).put("dist", r.distanceKm).put("evKm", r.evKm)
                    .put("rule", r.rule.name).put("priority", r.priority.name)
                    .put("action", r.action?.name ?: "")
                    .put("followed", when (r.followed) { true -> 1; false -> 0; null -> -1 })
                    .put("signal", r.followSignal)
                    .put("baseline", r.baseline).put("planned", r.planned).put("actual", r.actual)
                    .put("off", r.offPeakKwh).put("all", r.totalChargedKwh).put("sim", r.simulated)
            )
        }
        prefs?.edit()?.putString("records", arr.toString())?.apply()
    }

    private fun load(): List<TripRecord> = runCatching {
        val arr = JSONArray(prefs?.getString("records", "[]") ?: "[]")
        List(arr.length()) {
            val o = arr.getJSONObject(it)
            TripRecord(
                id = o.getString("id"), startedAt = o.getLong("at"), region = Region.valueOf(o.getString("region")),
                origin = o.getString("origin"), dest = o.getString("dest"),
                distanceKm = o.getDouble("dist"), evKm = o.getDouble("evKm"),
                rule = Rule.valueOf(o.getString("rule")), priority = Priority.valueOf(o.getString("priority")),
                action = o.getString("action").takeIf { a -> a.isNotEmpty() }?.let { a -> ActionKind.valueOf(a) },
                followed = when (o.getInt("followed")) { 1 -> true; 0 -> false; else -> null },
                followSignal = o.getString("signal"),
                baseline = o.getDouble("baseline"), planned = o.getDouble("planned"), actual = o.getDouble("actual"),
                offPeakKwh = o.getDouble("off"), totalChargedKwh = o.getDouble("all"), simulated = o.getBoolean("sim"),
            )
        }
    }.getOrDefault(emptyList())
}

// ───────────── WE'R Score (주간, 100점) ─────────────

const val SCORE_FOLLOW_MAX = 50.0
const val SCORE_SAVING_MAX = 30.0
const val SCORE_OFFPEAK_MAX = 20.0
/** 절약률이 이 이상이면 절약 항목 만점 */
const val SAVING_RATE_FULL = 0.25

data class WeekScore(
    val trips: Int,
    /** 제안을 따른 횟수 / 판정 대상 횟수 */
    val followed: Int,
    val judged: Int,
    val baseline: Double,
    val actual: Double,
    val offPeakKwh: Double,
    val chargedKwh: Double,
) {
    val savings: Double get() = baseline - actual
    val followRate: Double get() = if (judged > 0) followed.toDouble() / judged else 0.0
    val savingRate: Double get() = if (baseline > 0) (savings / baseline).coerceAtLeast(0.0) else 0.0
    val offPeakRate: Double get() = if (chargedKwh > 0) (offPeakKwh / chargedKwh).coerceIn(0.0, 1.0) else 0.0

    /** 제안 실천률 50점 */
    val followScore: Double get() = SCORE_FOLLOW_MAX * followRate
    /** 절약률 30점: 0% → 0점, 25% 이상 → 30점 (선형) */
    val savingScore: Double get() = SCORE_SAVING_MAX * (savingRate / SAVING_RATE_FULL).coerceIn(0.0, 1.0)
    /** 싼 시간대 충전 비율 20점 */
    val offPeakScore: Double get() = SCORE_OFFPEAK_MAX * offPeakRate
    val total: Double get() = followScore + savingScore + offPeakScore
}

fun scoreOf(trips: List<TripRecord>): WeekScore = WeekScore(
    trips = trips.size,
    followed = trips.count { it.followed == true },
    judged = trips.count { it.followed != null },
    baseline = trips.sumOf { it.baseline },
    actual = trips.sumOf { it.actual },
    offPeakKwh = trips.sumOf { it.offPeakKwh },
    chargedKwh = trips.sumOf { it.totalChargedKwh },
)

/** 최근 4주 점수 (오래된 주 → 이번 주) */
fun weeklyScores(region: Region): List<WeekScore> = (3 downTo 0).map { scoreOf(Ledger.week(region, it)) }

/**
 * 포인트 (T8): 절약액 기준 — 미국 $0.10당 1P, 한국 100원당 1P.
 * 주간 점수가 80점 이상인 주마다 +50P.
 */
fun pointsOf(region: Region): Int {
    val cfg = Regions.of(region)
    val fromSavings = Ledger.records.filter { it.region == region }.sumOf { it.savings.coerceAtLeast(0.0) } / cfg.savingsPerPoint
    val bonus = weeklyScores(region).count { it.trips > 0 && it.total >= 80.0 } * 50
    return fromSavings.roundToInt() + bonus
}
