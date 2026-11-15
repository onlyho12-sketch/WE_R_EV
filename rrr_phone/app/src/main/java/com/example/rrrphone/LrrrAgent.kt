package com.example.rrrphone

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 르르르 에이전트 (목업).
 *
 * 최종 구조에서는 Gleo AI 같은 LLM 어시스턴트가
 *   입력: 새 일정(캘린더) + DayStrategy(규칙 엔진 결과) + 지역 인프라 + 차량 상태
 *   출력: 사용자에게 하는 말 + 실행할 행동
 * 을 만든다. 데모에서는 같은 입력으로 템플릿 문장을 만든다.
 *
 * TODO: compose*() 를 Gleo AI / LLM 호출로 교체 (DayStrategy 를 JSON 으로 넘기고 tool call 로 행동 실행)
 */
object LrrrAgent {

    data class Action(val key: String, val label: String, val done: String)

    data class Template(
        val label: String, val title: String, val place: Place,
        val dayOffset: Long, val startMin: Int, val durMin: Int, val overnight: Boolean = false,
    )

    fun templates(today: LocalDate): List<Template> {
        val toSat = ((DayOfWeek.SATURDAY.value - today.dayOfWeek.value) + 7) % 7
        return listOf(
            Template("🛍 퇴근 후 쇼핑", "스타필드 쇼핑", DemoPlaces.Hanam, 0, 19 * 60, 120),
            Template("👪 부모님 댁 저녁", "부모님 댁 저녁", DemoPlaces.Hwado, 0, 19 * 60, 180),
            Template("💼 판교 외근", "판교 외근 미팅", DemoPlaces.Pangyo, 2, 13 * 60, 180),
            Template("⛺ 주말 캠핑 1박", "퇴촌 캠핑", DemoPlaces.Toechon, (if (toSat == 0) 7 else toSat).toLong(), 14 * 60, 0, overnight = true),
        )
    }

    val suggestions = listOf(
        "오늘 저녁 7시 스타필드 하남 쇼핑 2시간",
        "오늘 7시 부모님 댁 저녁 3시간",
        "내일 오후 1시 판교 외근 3시간",
        "토요일 2시 퇴촌 캠핑 1박",
    )

    const val GREETING = "안녕하세요, 르르르예요! 구글 캘린더에 평소랑 다른 일정이 생기면 그 지역 충전 인프라랑 차 상태를 보고 충전 전략을 다시 세워드릴게요.\n\n캘린더 탭에서 ‘구글 캘린더 연동’을 눌러주세요. 저한테 말로 알려주셔도 돼요."

    fun headline(s: DayStrategy): String {
        val c = s.charges.firstOrNull()
        return when {
            s.legs.isEmpty() -> "${if (s.isToday) "오늘은" else "이날은"} 이동 계획이 없어요"
            c != null && s.homeTonight -> "${c.place.short}에서 ${c.durText}만 충전하세요"
            c != null -> "${c.place.short}에서 주차하는 동안 충전하세요"
            s.skipped.isNotEmpty() && s.gasKm >= 1 -> "급속은 건너뛰고 기름으로"
            !s.homeTonight && s.gasKm >= 1 -> "배터리 아끼고 발전으로 가세요"
            s.gasKm >= 1 -> "전기 먼저, 모자란 ${s.gasKm.roundToInt()}km만 발전"
            else -> "배터리 다 쓰고 들어가세요"
        }
    }

    fun benefit(s: DayStrategy): String =
        if (s.saving > 0) "기름만 쓸 때보다 −${"%,d".format(s.saving)}원" else "충전 정차 0회 · 최단 동선"

    /** 홈 화면 말풍선 / 대화 본문 */
    fun brief(s: DayStrategy): String {
        val c = s.charges.firstOrNull()
        val last = s.legs.lastOrNull()?.to
        val main = when {
            s.legs.isEmpty() -> "${if (s.isToday) "오늘은" else "이날은"} 차 탈 일이 없네요. 푹 쉬어요!"
            c != null && s.homeTonight ->
                "${c.place.short}에 완속충전기(${c.charger.wonPerKwh}원)가 있어요. ${c.durText}만 꽂아두면 집까지 전기로 가고, 나머진 밤에 집에서 싸게 채워요."
            c != null -> "${c.place.short}에서 주차하는 동안 완속으로 ${c.socAfter.roundToInt()}%까지 채워요."
            s.skipped.isNotEmpty() && s.gasKm >= 1 -> {
                val p = s.skipped.first()
                "${p.short}엔 급속충전기(${p.fast!!.wonPerKwh}원)뿐이에요. 비싸고 오래 걸리니 건너뛰고 엔진 발전으로 가요."
            }
            !s.homeTonight && s.gasKm >= 1 ->
                "${last?.short ?: "목적지"} 근처엔 충전소가 없어요. 고속 구간은 발전으로 가고, 배터리는 캠핑장 V2L이랑 귀가길 도심용으로 남겨요."
            s.gasKm >= 1 -> "${if (s.isToday) "오늘은" else "이날은"} 이동이 길어요. 전기 먼저 쓰고 모자란 ${s.gasKm.roundToInt()}km만 발전으로 가요."
            else -> "${if (s.isToday) "오늘" else "그날"} 밤 집에서 싸게 충전할 수 있으니 배터리 아끼지 말고 쓰세요."
        }
        val fill = if (s.gas.fillInDays == 0 && s.legs.isNotEmpty())
            " 연료도 부족해서 ${s.station.short}(%,d원/L)에서 넣고 가요.".format(s.station.wonPerL) else ""
        return main + fill
    }

    /** "일정이 바뀌었네요!" */
    fun changeMessage(e: CalEvent, before: DayStrategy, after: DayStrategy, dateLabel: String): String {
        val b = before.totalKm.roundToInt()
        val a = after.totalKm.roundToInt()
        val what = if (a > b) "주행 거리가 ${b}km → ${a}km로 늘었어요." else "동선이 바뀌었어요."
        return "일정이 바뀌었네요! $dateLabel ${hm(e.startH)} ‘${e.title}’ 일정이 생겨서 $what\n\n${brief(after)}"
    }

    // ───────────── 구글 캘린더 동기화 ─────────────

    private fun whenText(e: CalEvent, today: LocalDate) =
        "${dateLabel(e.date, today)} ${hm(e.startH)}${if (e.overnight) " (1박)" else ""}"

    /** 바뀐 점 한 줄 (시간 13:00→14:00, 장소 A→B …) */
    fun changeWhat(o: CalEvent, n: CalEvent, today: LocalDate): String {
        val parts = mutableListOf<String>()
        if (o.date != n.date || o.start != n.start || o.end != n.end || o.overnight != n.overnight)
            parts += "시간 ${whenText(o, today)} → ${whenText(n, today)}"
        if (o.place.name != n.place.name) parts += "장소 ${o.place.short} → ${n.place.short}"
        if (o.title != n.title) parts += "제목 ‘${o.title}’ → ‘${n.title}’"
        return parts.joinToString(" · ").ifEmpty { "내용 변경" }
    }

    /** 동기화 결과 요약 (첫 연동이면 가져온 일정, 아니면 바뀐 일정) */
    fun syncSummary(diff: CalendarDiff, first: Boolean, total: Int, account: String?, noPlace: List<String>, today: LocalDate): String {
        val head = when {
            first -> "구글 캘린더 연동 완료!${account?.let { " ($it)" } ?: ""}\n앞으로 ${GoogleCalendar.DAYS}일 일정 ${total}개를 가져왔어요."
            diff.isEmpty -> "구글 캘린더 확인했어요. 지난번 이후로 바뀐 일정은 없어요."
            else -> "구글 캘린더에서 바뀐 일정을 찾았어요."
        }
        val lines = mutableListOf<String>()
        if (!first) {
            diff.added.forEach { lines += "＋ ${whenText(it, today)} ‘${it.title}’ · ${it.place.short}" }
            diff.changed.forEach { (o, n) -> lines += "↻ ‘${n.title}’ ${changeWhat(o, n, today)}" }
            diff.removed.forEach { lines += "－ ${whenText(it, today)} ‘${it.title}’ 삭제" }
        }
        val shown = lines.take(6) + (if (lines.size > 6) listOf("… 외 ${lines.size - 6}건") else emptyList())
        val tail = if (noPlace.isEmpty()) "" else
            "\n\n장소를 몰라서 뺀 일정: ${noPlace.take(3).joinToString { "‘$it’" }}${if (noPlace.size > 3) " 외 ${noPlace.size - 3}개" else ""}" +
                "\n구글 캘린더에서 장소를 넣어주시면 전략에 반영할게요."
        val body = if (shown.isEmpty()) "" else "\n\n" + shown.joinToString("\n")
        val next = if (!diff.isEmpty) "\n\n바뀐 날짜별로 충전 전략 다시 세울게요." else ""
        return head + body + next + tail
    }

    /** 일정이 지워지기만 한 날 */
    fun removedMessage(date: LocalDate, removed: List<CalEvent>, after: DayStrategy, today: LocalDate): String =
        "${dateLabel(date, today)} ${removed.joinToString { "‘${it.title}’" }} 일정이 빠졌어요. 동선이 줄어서 전략을 다시 세웠어요.\n\n${brief(after)}"

    /** 구글 캘린더 일정 변경 → "일정이 바뀌었네요!" */
    fun googleChangeMessage(e: CalEvent, old: CalEvent?, before: DayStrategy, after: DayStrategy, today: LocalDate): String {
        if (old == null) return changeMessage(e, before, after, dateLabel(e.date, today))
        val b = before.totalKm.roundToInt()
        val a = after.totalKm.roundToInt()
        val km = when {
            a > b -> " 주행 거리가 ${b}km → ${a}km로 늘었어요."
            a < b -> " 주행 거리가 ${b}km → ${a}km로 줄었어요."
            else -> ""
        }
        return "일정이 바뀌었네요! ‘${e.title}’ ${changeWhat(old, e, today)}.$km\n\n${brief(after)}"
    }

    /** 에이전트가 확인하는 단계 (화면에서 하나씩 체크) */
    fun steps(e: CalEvent, s: DayStrategy, car: VehicleState, old: CalEvent? = null): List<String> = listOf(
        when {
            old != null -> "구글 캘린더 · ‘${e.title}’ 일정 변경 감지"
            e.google != null -> "구글 캘린더 · ‘${e.title}’ 새 일정 감지 (평소 루틴과 달라요)"
            else -> "캘린더 · ‘${e.title}’ 새 일정 감지 (평소 루틴과 달라요)"
        },
        "충전 인프라 · ${e.place.short}: ${infraText(e.place)}",
        "유가 · 동선 근처 최저가 ${s.station.short} %,d원/L".format(s.station.wonPerL),
        if (s.isToday) "차량 상태 · 배터리 ${car.soc}% · 연료 ${car.fuel}% (Pleos 실시간)"
        else "차량 상태 · 밤사이 집 충전 → 출발 100% 가정 · 연료 ${car.fuel}%",
        "전략 생성 · ${s.totalKm.roundToInt()}km 중 전기 ${s.evKm.roundToInt()}km / 발전 ${s.gasKm.roundToInt()}km",
    )

    fun actions(s: DayStrategy): List<Action> = buildList {
        val d = s.date
        s.homeCharge?.let { add(Action("$d:home", "${hm(it.first)} 집 충전 예약", "✓ ${hm(it.first)} 심야 충전 예약됨")) }
        s.charges.forEach { add(Action("$d:slow:${it.place.short}", "${it.place.short} 완속 빈자리 알림", "✓ 도착 10분 전에 빈자리 알려드릴게요")) }
        if (s.gas.fillInDays == 0 || s.skipped.isNotEmpty()) {
            add(Action("$d:gas", "${s.station.short} 경유지 등록", "✓ ${s.station.short} 내비 경유지로 보냄"))
        }
    }

    /**
     * 주행 중 내비 위젯에 뜨는 한 줄. 평소엔 조용하다가 '결정적인 순간'에만 말하는 톤이라
     * 그날 전략에서 운전자가 꼭 알아야 할 한 가지만 고른다.
     */
    fun naviWidgetLine(s: DayStrategy): String {
        val c = s.charges.firstOrNull()
        return when {
            c != null -> "${c.place.short} 주차장 ${c.charger.type.label}에 대세요 · ${c.durText}"
            s.skipped.isNotEmpty() && s.gasKm >= 1 -> "곧 ${s.skipped.first().short} 급속충전소 — 그냥 지나가요"
            s.gas.fillInDays == 0 && s.legs.isNotEmpty() -> "${s.station.short} 들러서 주유하고 가요"
            s.homeCharge != null -> "배터리 다 써도 돼요 · 밤에 집에서 채워요"
            !s.homeTonight && s.gasKm >= 1 -> "배터리는 돌아올 때 쓸게요"
            s.legs.isEmpty() -> "오늘은 이동 계획이 없어요"
            else -> "충전 생각 말고 그대로 가요"
        }
    }

    /** Pleos 가 주행 데이터로 확정한 최종 전략 설명 */
    fun finalizeMessage(phone: DayStrategy, final: DayStrategy): String {
        val parts = mutableListOf("Pleos 연동 완료! 최근 2주 실주행 전비 ${PLEOS_KM_PER_KWH}km/kWh(히터 사용)를 반영해서 최종 전략을 확정했어요.")
        val pc = phone.charges.firstOrNull()
        val fc = final.charges.firstOrNull()
        if (fc != null && pc?.minutes != fc.minutes) parts += "${fc.place.short} 충전 ${minText(pc?.minutes ?: 0)} → ${fc.durText}으로 늘렸어요."
        if (abs(final.gasKm - phone.gasKm) >= 1) parts += "발전 주행 ${phone.gasKm.roundToInt()}km → ${final.gasKm.roundToInt()}km."
        if (parts.size == 1) parts += "1차 전략 그대로 가도 충분해요."
        parts += "내비 위젯까지 연동했어요 — 주행 중엔 아래처럼 한 줄로만 알려드릴게요."
        return parts.joinToString(" ")
    }

    // ───────────── 말로 일정 추가 (LLM 대신 간단한 규칙 파서) ─────────────

    private val timeRx = Regex("(오전|오후|저녁|밤|아침|점심|새벽)?\\s*(\\d{1,2})\\s*시(?!간)\\s*(반|(\\d{1,2})\\s*분)?")
    private val durRx = Regex("(\\d{1,2})\\s*시간")
    private val days = mapOf("월요일" to 1, "화요일" to 2, "수요일" to 3, "목요일" to 4, "금요일" to 5, "토요일" to 6, "일요일" to 7)

    fun parse(text: String, today: LocalDate, id: Long): CalEvent? {
        val cmd = parseTripCommand(text)
        val place = cmd.destination ?: cmd.origin ?: return null

        val date = when {
            "모레" in text -> today.plusDays(2)
            "내일" in text -> today.plusDays(1)
            else -> days.entries.firstOrNull { it.key in text }?.let { (_, v) ->
                val diff = ((v - today.dayOfWeek.value) + 7) % 7
                today.plusDays((if (diff == 0) 7 else diff).toLong())
            } ?: today
        }

        val m = timeRx.find(text)
        var startMin = if ("퇴근" in text) 19 * 60 else 14 * 60
        if (m != null) {
            var h = m.groupValues[2].toInt()
            val ampm = m.groupValues[1]
            if ((ampm in setOf("오후", "저녁", "밤") || (ampm.isEmpty() && h in 1..7)) && h < 12) h += 12
            val min = when { m.groupValues[3] == "반" -> 30; m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt(); else -> 0 }
            startMin = (h.coerceIn(0, 23)) * 60 + min.coerceIn(0, 59)
        }
        val overnight = listOf("1박", "캠핑", "자고").any { it in text }
        val dur = durRx.find(text)?.groupValues?.get(1)?.toIntOrNull()?.times(60)
            ?: (place.parkHours.coerceIn(1, 4) * 60)
        val endMin = (startMin + dur).coerceAtMost(23 * 60 + 59)

        val kw = listOf("쇼핑" to "쇼핑", "캠핑" to "캠핑", "외근" to "외근", "미팅" to "미팅", "저녁" to "저녁", "식사" to "식사",
            "생신" to "생신", "출장" to "출장").firstOrNull { it.first in text }?.second ?: "일정"
        val who = if ("부모님" in text) "부모님 댁" else place.short

        return CalEvent(
            id, date, "$who $kw", place,
            LocalTime.of(startMin / 60, startMin % 60),
            if (overnight) LocalTime.of(11, 0) else LocalTime.of(endMin / 60, endMin % 60),
            overnight = overnight,
        )
    }
}
