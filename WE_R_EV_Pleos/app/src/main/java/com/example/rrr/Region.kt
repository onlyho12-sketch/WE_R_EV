package com.example.rrr

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * 지역 분리 (T5)
 *  - 계산은 지역과 무관하게 km · kWh · L 로 하고, 돈은 그 지역 통화(Double)로 둔다.
 *  - 화면에 보여줄 때만 mi · gal · $ 로 바꾼다 (아래 표시 함수들).
 *  - 요금은 모두 "예시 요금(가정)" — 실제 유틸리티 요금표·유가 API 로 교체 예정.
 */

enum class Region { KR, US }

const val KM_PER_MI = 1.609344
const val L_PER_GAL = 3.78541

/** 시간대별 전기요금(TOU). startH 는 0~24, endH 는 자정을 넘기면 24 보다 크게 (예: 23 → 31) */
data class Tariff(val bands: List<Band>) {
    data class Band(val startH: Float, val endH: Float, val perKwh: Double, val label: String)

    private fun inBand(b: Band, t: Float) = (t >= b.startH && t < b.endH) || (t + 24f >= b.startH && t + 24f < b.endH)

    fun bandAt(hour: Float): Band {
        val t = ((hour % 24f) + 24f) % 24f
        return bands.firstOrNull { inBand(it, t) } ?: bands.maxBy { it.perKwh }
    }

    fun priceAt(hour: Float): Double = bandAt(hour).perKwh

    /** 가장 싼 구간 */
    val cheapest: Band get() = bands.minBy { it.perKwh }

    /** 가장 비싼 구간 */
    val peak: Band get() = bands.maxBy { it.perKwh }

    /** h(오늘 0시 기준 시간) 시점에 진행 중이거나 다음에 오는 '가장 싼 구간' — (시작, 끝) */
    fun cheapWindowFor(h: Float): Pair<Float, Float> {
        val c = cheapest
        val day = floor(h / 24f) * 24f
        return listOf(day - 24f, day, day + 24f)
            .map { (it + c.startH) to (it + c.endH) }
            .first { it.second > h }
    }
}

data class RegionConfig(
    val region: Region,
    /** 집 충전 요금 (시간대별) */
    val homeTariff: Tariff,
    /** 공용 완속(L2) · 급속(DC) 평균 요금 */
    val publicL2PerKwh: Double,
    val dcFastPerKwh: Double,
    /** 지역 평균 휘발유 가격 (통화/L) */
    val gasAvgPerL: Double,
    /** 싼 시간대 이름 — 화면 문구용 */
    val cheapLabel: String,
    /** 포인트 1P 가 되는 절약액 (T8) */
    val savingsPerPoint: Double,
    val ttsLocale: Locale,
    val tariffNote: String,
) {
    /** 평균가로 발전(엔진) 주행할 때 km당 비용 */
    val gasPerKm: Double get() = gasAvgPerL / GAS_KM_PER_L
}

object Regions {
    /** 한국 — 심야(경부하) 23~07시 120원, 그 외 180원 (예시 요금) */
    val KR = RegionConfig(
        region = Region.KR,
        homeTariff = Tariff(
            listOf(
                Tariff.Band(23f, 31f, 120.0, "심야"),
                Tariff.Band(7f, 23f, 180.0, "일반"),
            )
        ),
        publicL2PerKwh = 190.0,
        dcFastPerKwh = 347.0,
        gasAvgPerL = 1650.0,
        cheapLabel = "심야",
        savingsPerPoint = 100.0,
        ttsLocale = Locale.KOREA,
        tariffNote = "예시 요금(가정)",
    )

    /**
     * 미국(조지아 서배너) — 미국 유틸리티의 시간대별(TOU) 요금 '형태'를 본뜬 예시값 (가정).
     * 16–21시 피크 $0.55 / 06–16시·21–24시 $0.33 / 00–06시 오프피크 $0.25
     * 공용 L2 $0.30, DC 급속 $0.56 /kWh, 휘발유 평균 $3.29/gal
     */
    val US = RegionConfig(
        region = Region.US,
        homeTariff = Tariff(
            listOf(
                Tariff.Band(0f, 6f, 0.25, "오프피크"),
                Tariff.Band(6f, 16f, 0.33, "미드피크"),
                Tariff.Band(16f, 21f, 0.55, "피크"),
                Tariff.Band(21f, 24f, 0.33, "미드피크"),
            )
        ),
        publicL2PerKwh = 0.30,
        dcFastPerKwh = 0.56,
        gasAvgPerL = 3.29 / L_PER_GAL,
        cheapLabel = "오프피크",
        savingsPerPoint = 0.10,
        // 심사위원용 기본 음성은 한국어 (D1) — 미국 현지 시연 시 Locale.US 로 교체
        ttsLocale = Locale.KOREA,
        tariffNote = "예시 요금(가정) · 미국 TOU 형태",
    )

    fun of(region: Region) = if (region == Region.US) US else KR
}

// ───────────── 표시 함수 (계산값 → 지역 단위 문자열) ─────────────

fun money(v: Double, r: Region): String =
    if (r == Region.US) "$%.2f".format(Locale.US, v) else "%,d원".format(v.roundToInt())

fun perKwhText(v: Double, r: Region): String =
    if (r == Region.US) "$%.2f/kWh".format(Locale.US, v) else "${v.roundToInt()}원/kWh"

fun distUnit(r: Region) = if (r == Region.US) "mi" else "km"

fun distValue(km: Double, r: Region): Int = (if (r == Region.US) km / KM_PER_MI else km).roundToInt()

fun distText(km: Double, r: Region): String = "${distValue(km, r)}${distUnit(r)}"

/** 연료 단가: 통화/L 로 들고 있다가 미국은 $/gal 로 표시 */
fun fuelPriceText(perL: Double, r: Region): String =
    if (r == Region.US) "$%.2f/gal".format(Locale.US, perL * L_PER_GAL) else "%,d원/L".format(perL.roundToInt())

fun fuelVolText(liters: Double, r: Region): String =
    if (r == Region.US) "%.1fgal".format(Locale.US, liters / L_PER_GAL) else "${liters.roundToInt()}L"

/** km당 비용 → 미국은 mi당 */
fun perDistText(perKm: Double, r: Region): String =
    if (r == Region.US) "$%.2f/mi".format(Locale.US, perKm * KM_PER_MI) else "${perKm.roundToInt()}원/km"
