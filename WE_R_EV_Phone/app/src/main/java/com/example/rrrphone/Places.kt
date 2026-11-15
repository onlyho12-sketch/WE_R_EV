package com.example.rrrphone

/** 데모용 장소·충전소 DB. 좌표는 모두 지도 이미지(map_seoul) 범위 안. 요금은 예시 값. */

enum class ChargerType(val label: String) { Home("집 완속"), Slow("완속"), Fast("급속") }

data class Charger(val name: String, val type: ChargerType, val wonPerKwh: Int, val at: LatLng)

data class Place(
    val name: String,
    val short: String,
    val aliases: List<String>,
    val at: LatLng,
    val chargers: List<Charger> = emptyList(),
    /** 목적지에서 예상 주차 시간(완속 충전량 계산용) */
    val parkHours: Int = 0,
) {
    val home: Charger? get() = chargers.firstOrNull { it.type == ChargerType.Home }
    val slow: Charger? get() = chargers.firstOrNull { it.type == ChargerType.Slow }
    val fast: Charger? get() = chargers.firstOrNull { it.type == ChargerType.Fast }
}

object DemoPlaces {
    val Gangnam = Place("회사 · 강남역", "강남", listOf("강남", "강남역", "회사", "사무실"), LatLng(37.4979, 127.0276))
    val Jeongja = Place(
        "집 · 분당 정자동", "집", listOf("집", "정자", "정자동", "분당", "우리집"), LatLng(37.3670, 127.1080),
        listOf(Charger("아파트 지하주차장 완속", ChargerType.Home, 120, LatLng(37.3670, 127.1080))), parkHours = 9,
    )
    val Hanam = Place(
        "스타필드 하남", "하남", listOf("하남", "스타필드", "쇼핑몰"), LatLng(37.5455, 127.2240),
        listOf(Charger("스타필드 하남 B2 완속", ChargerType.Slow, 180, LatLng(37.5470, 127.2215))), parkHours = 3,
    )
    val Hwado = Place(
        "부모님 댁 · 남양주 화도", "남양주", listOf("남양주", "화도", "부모님", "본가"), LatLng(37.6520, 127.3070),
        listOf(Charger("화도 공영 급속충전소", ChargerType.Fast, 347, LatLng(37.6560, 127.2990))), parkHours = 4,
    )
    val Toechon = Place(
        "퇴촌 캠핑장 · 경기 광주", "퇴촌", listOf("퇴촌", "캠핑", "캠핑장", "광주"), LatLng(37.4700, 127.3100), parkHours = 20,
    )
    val Pangyo = Place(
        "판교 테크노밸리", "판교", listOf("판교", "테크노밸리"), LatLng(37.4020, 127.1080),
        listOf(
            Charger("판교 공영주차장 완속", ChargerType.Slow, 190, LatLng(37.4035, 127.1060)),
            Charger("판교 급속충전소", ChargerType.Fast, 324, LatLng(37.4000, 127.1110)),
        ),
        parkHours = 8,
    )
    val Yeouido = Place(
        "여의도 IFC몰", "여의도", listOf("여의도", "IFC", "ifc"), LatLng(37.5251, 126.9255),
        listOf(Charger("IFC몰 B3 완속", ChargerType.Slow, 200, LatLng(37.5260, 126.9240))), parkHours = 2,
    )
    val Jamsil = Place(
        "잠실 롯데월드몰", "잠실", listOf("잠실", "롯데월드", "롯데월드몰"), LatLng(37.5126, 127.1025),
        listOf(
            Charger("롯데월드몰 B2 완속", ChargerType.Slow, 195, LatLng(37.5135, 127.1010)),
            Charger("잠실 급속충전소", ChargerType.Fast, 330, LatLng(37.5110, 127.1045)),
        ),
        parkHours = 3,
    )
    val Gimpo = Place(
        "김포공항", "김포공항", listOf("김포공항", "김포", "공항"), LatLng(37.5583, 126.8010),
        listOf(Charger("김포공항 주차장 급속", ChargerType.Fast, 347, LatLng(37.5600, 126.8040))), parkHours = 48,
    )
    val Uijeongbu = Place("의정부역", "의정부", listOf("의정부", "의정부역"), LatLng(37.7381, 127.0337), parkHours = 2)

    val all = listOf(Gangnam, Jeongja, Hanam, Hwado, Toechon, Pangyo, Yeouido, Jamsil, Gimpo, Uijeongbu)
}

// ───────────── ② 기름 전략용 주유소 (데모 가격 — 실서비스는 오피넷 유가 API) ─────────────

data class GasStation(val name: String, val short: String, val wonPerL: Int, val at: LatLng)

object DemoStations {
    val all = listOf(
        GasStation("SK 양재 셀프주유소", "양재 SK", 1612, LatLng(37.4725, 127.0390)),
        GasStation("GS칼텍스 정자주유소", "정자 GS", 1629, LatLng(37.3720, 127.1100)),
        GasStation("현대오일뱅크 구리토평", "토평 오일뱅크", 1598, LatLng(37.5930, 127.1380)),
        GasStation("S-OIL 하남미사", "미사 S-OIL", 1605, LatLng(37.5610, 127.1880)),
        GasStation("알뜰주유소 퇴촌", "퇴촌 알뜰", 1585, LatLng(37.4760, 127.2980)),
        GasStation("SK 판교 셀프", "판교 SK", 1621, LatLng(37.4050, 127.1040)),
    )
}

/** 경로 위(2.5km 이내) 최저가 → 없으면 목적지 근처 최저가 → 없으면 전체 최저가 */
fun pickStation(route: List<LatLng>, dest: LatLng): GasStation {
    val samples = if (route.size > 60) route.filterIndexed { i, _ -> i % (route.size / 60) == 0 } else route
    val onRoute = DemoStations.all.filter { s -> samples.any { haversineKm(it, s.at) < 2.5 } }
    val nearDest = DemoStations.all.filter { haversineKm(it.at, dest) < 6.0 }
    return (onRoute.ifEmpty { nearDest }.ifEmpty { DemoStations.all }).minBy { it.wonPerL }
}

fun searchPlaces(query: String): List<Place> {
    val q = query.trim()
    if (q.isEmpty()) return DemoPlaces.all
    return DemoPlaces.all.filter { p ->
        p.name.contains(q, ignoreCase = true) || p.aliases.any { it.contains(q, ignoreCase = true) }
    }
}

/** 자연어 문장에서 출발지/도착지 추출. 예) "강남에서 하남 스타필드 가자", "집으로 가자" */
data class TripCommand(val origin: Place?, val destination: Place?)

fun parseTripCommand(text: String): TripCommand {
    data class Mention(val place: Place, val index: Int, val alias: String)

    val mentions = DemoPlaces.all.mapNotNull { p ->
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
