package com.example.rrr

/**
 * 데모용 장소·충전소 DB. 요금은 모두 예시 값(가정).
 *  - 한국: 좌표는 지도 이미지(map_seoul) 범위 안, 요금은 원
 *  - 미국: 조지아 서배너 — 현대차그룹 메타플랜트 주변(map_savannah 범위 안), 요금은 달러.
 *    충전기·주유소 이름은 가상(실존 상호 아님)
 */

enum class ChargerType(val label: String) { Home("집 완속"), Slow("완속"), Fast("급속") }

/** perKwh: 그 지역 통화 기준 kWh당 요금 (집 충전은 가장 싼 시간대 요금) */
data class Charger(val name: String, val type: ChargerType, val perKwh: Double, val at: LatLng)

data class Place(
    val name: String,
    val short: String,
    val aliases: List<String>,
    val at: LatLng,
    val chargers: List<Charger> = emptyList(),
    /** 목적지에서 예상 주차 시간(완속 충전량 계산용) */
    val parkHours: Int = 0,
    val region: Region = Region.KR,
) {
    val home: Charger? get() = chargers.firstOrNull { it.type == ChargerType.Home }
    val slow: Charger? get() = chargers.firstOrNull { it.type == ChargerType.Slow }
    val fast: Charger? get() = chargers.firstOrNull { it.type == ChargerType.Fast }
}

object DemoPlaces {
    val Gangnam = Place("회사 · 강남역", "강남", listOf("강남", "강남역", "회사", "사무실"), LatLng(37.4979, 127.0276))
    val Jeongja = Place(
        "집 · 분당 정자동", "집", listOf("집", "정자", "정자동", "분당", "우리집"), LatLng(37.3670, 127.1080),
        listOf(Charger("아파트 지하주차장 완속", ChargerType.Home, 120.0, LatLng(37.3670, 127.1080))), parkHours = 9,
    )
    val Hanam = Place(
        "스타필드 하남", "하남", listOf("하남", "스타필드", "쇼핑몰"), LatLng(37.5455, 127.2240),
        listOf(Charger("스타필드 하남 B2 완속", ChargerType.Slow, 180.0, LatLng(37.5470, 127.2215))), parkHours = 3,
    )
    val Hwado = Place(
        "부모님 댁 · 남양주 화도", "남양주", listOf("남양주", "화도", "부모님", "본가"), LatLng(37.6520, 127.3070),
        listOf(Charger("화도 공영 급속충전소", ChargerType.Fast, 347.0, LatLng(37.6560, 127.2990))), parkHours = 4,
    )
    val Toechon = Place(
        "퇴촌 캠핑장 · 경기 광주", "퇴촌", listOf("퇴촌", "캠핑", "캠핑장", "광주"), LatLng(37.4700, 127.3100), parkHours = 20,
    )
    val Pangyo = Place(
        "판교 테크노밸리", "판교", listOf("판교", "테크노밸리"), LatLng(37.4020, 127.1080),
        listOf(
            Charger("판교 공영주차장 완속", ChargerType.Slow, 190.0, LatLng(37.4035, 127.1060)),
            Charger("판교 급속충전소", ChargerType.Fast, 324.0, LatLng(37.4000, 127.1110)),
        ),
        parkHours = 8,
    )
    val Yeouido = Place(
        "여의도 IFC몰", "여의도", listOf("여의도", "IFC", "ifc"), LatLng(37.5251, 126.9255),
        listOf(Charger("IFC몰 B3 완속", ChargerType.Slow, 200.0, LatLng(37.5260, 126.9240))), parkHours = 2,
    )
    val Jamsil = Place(
        "잠실 롯데월드몰", "잠실", listOf("잠실", "롯데월드", "롯데월드몰"), LatLng(37.5126, 127.1025),
        listOf(
            Charger("롯데월드몰 B2 완속", ChargerType.Slow, 195.0, LatLng(37.5135, 127.1010)),
            Charger("잠실 급속충전소", ChargerType.Fast, 330.0, LatLng(37.5110, 127.1045)),
        ),
        parkHours = 3,
    )
    val Gimpo = Place(
        "김포공항", "김포공항", listOf("김포공항", "김포", "공항"), LatLng(37.5583, 126.8010),
        listOf(Charger("김포공항 주차장 급속", ChargerType.Fast, 347.0, LatLng(37.5600, 126.8040))), parkHours = 48,
    )
    val Uijeongbu = Place("의정부역", "의정부", listOf("의정부", "의정부역"), LatLng(37.7381, 127.0337), parkHours = 2)

    /** 한국 장소 */
    val all = listOf(Gangnam, Jeongja, Hanam, Hwado, Toechon, Pangyo, Yeouido, Jamsil, Gimpo, Uijeongbu)

    // ── 미국 시나리오 (출근길 1개): 서배너 집 → 현대차그룹 메타플랜트(싼타페 EREV 북미 생산 거점) ──
    val SavannahHome = Place(
        "Home · Savannah", "Home", listOf("집", "savannah", "서배너", "home"), LatLng(32.0514, -81.1138),
        listOf(Charger("Home Garage L2", ChargerType.Home, 0.25, LatLng(32.0514, -81.1138))),
        parkHours = 10, region = Region.US,
    )
    val Metaplant = Place(
        "Work · Hyundai Metaplant", "Work", listOf("회사", "사무실", "metaplant", "메타플랜트", "공장", "office"),
        LatLng(32.1594, -81.4394),
        // 직장 완속 요금 $0.18/kWh — 예시값(가정)
        listOf(Charger("Plant Parking L2", ChargerType.Slow, 0.18, LatLng(32.1640, -81.4330))),
        parkHours = 8, region = Region.US,
    )

    /** 미국 장소 */
    val us = listOf(SavannahHome, Metaplant)

    fun of(region: Region) = if (region == Region.US) us else all

    /** 내비 목적지 좌표와 가장 가까운 데모 장소 (maxKm 이내) */
    fun nearest(at: LatLng, maxKm: Double = 3.0): Place? =
        (all + us).minByOrNull { haversineKm(it.at, at) }?.takeIf { haversineKm(it.at, at) <= maxKm }
}

// ───────────── ② 기름 전략용 주유소 (데모 가격 — 실서비스는 유가 API) ─────────────

/** perL: 그 지역 통화 기준 L당 가격 (미국은 화면에 $/gal 로 표시) */
data class GasStation(
    val name: String,
    val short: String,
    val perL: Double,
    val at: LatLng,
    val region: Region = Region.KR,
)

object DemoStations {
    val all = listOf(
        GasStation("SK 양재 셀프주유소", "양재 SK", 1612.0, LatLng(37.4725, 127.0390)),
        GasStation("GS칼텍스 정자주유소", "정자 GS", 1629.0, LatLng(37.3720, 127.1100)),
        GasStation("현대오일뱅크 구리토평", "토평 오일뱅크", 1598.0, LatLng(37.5930, 127.1380)),
        GasStation("S-OIL 하남미사", "미사 S-OIL", 1605.0, LatLng(37.5610, 127.1880)),
        GasStation("알뜰주유소 퇴촌", "퇴촌 알뜰", 1585.0, LatLng(37.4760, 127.2980)),
        GasStation("SK 판교 셀프", "판교 SK", 1621.0, LatLng(37.4050, 127.1040)),
    )

    /** 미국 — 가상 상호. 가격은 $/gal 예시값을 L당으로 환산 */
    val us = listOf(
        GasStation("Pooler Fuel", "Pooler Fuel", 3.09 / L_PER_GAL, LatLng(32.0805, -81.2459), Region.US),
        GasStation("Midtown Gas", "Midtown Gas", 3.19 / L_PER_GAL, LatLng(32.0560, -81.1265), Region.US),
    )

    fun of(region: Region) = if (region == Region.US) us else all
}

/** 경로 위(2.5km 이내) 최저가 → 없으면 목적지 근처 최저가 → 없으면 전체 최저가 */
fun pickStation(route: List<LatLng>, dest: LatLng, region: Region = Region.KR): GasStation {
    val stations = DemoStations.of(region)
    val samples = if (route.size > 60) route.filterIndexed { i, _ -> i % (route.size / 60) == 0 } else route
    val onRoute = stations.filter { s -> samples.any { haversineKm(it, s.at) < 2.5 } }
    val nearDest = stations.filter { haversineKm(it.at, dest) < 6.0 }
    return (onRoute.ifEmpty { nearDest }.ifEmpty { stations }).minBy { it.perL }
}

fun searchPlaces(query: String, region: Region = Region.KR): List<Place> {
    val places = DemoPlaces.of(region)
    val q = query.trim()
    if (q.isEmpty()) return places
    return places.filter { p ->
        p.name.contains(q, ignoreCase = true) || p.aliases.any { it.contains(q, ignoreCase = true) }
    }
}

/** 자연어 문장에서 출발지/도착지 추출. 예) "강남에서 하남 스타필드 가자", "집으로 가자" */
data class TripCommand(val origin: Place?, val destination: Place?)

fun parseTripCommand(text: String, region: Region = Region.KR): TripCommand {
    data class Mention(val place: Place, val index: Int, val alias: String)

    val mentions = DemoPlaces.of(region).mapNotNull { p ->
        p.aliases.mapNotNull { a -> text.indexOf(a, ignoreCase = true).takeIf { it >= 0 }?.let { Mention(p, it, a) } }
            .minByOrNull { it.index }
    }.sortedBy { it.index }

    if (mentions.isEmpty()) return TripCommand(null, null)
    val fromMarked = mentions.firstOrNull { m ->
        val after = text.substring(m.index + m.alias.length).trimStart()
        after.startsWith("에서") || after.startsWith("부터") || after.startsWith("출발")
    }
    return when {
        mentions.size >= 2 -> {
            val origin = fromMarked?.place ?: mentions[0].place
            TripCommand(origin, mentions.first { it.place != origin }.place)
        }
        fromMarked != null -> TripCommand(fromMarked.place, null)
        else -> TripCommand(null, mentions[0].place)
    }
}
