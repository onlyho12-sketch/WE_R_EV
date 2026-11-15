package com.example.rrrphone

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * 구글 캘린더 연동.
 *
 * 안드로이드에서 구글 계정 캘린더는 기기 캘린더 프로바이더(CalendarContract)로 자동 동기화되므로
 * READ_CALENDAR 권한만으로 구글 캘린더 일정을 그대로 읽는다 (OAuth·클라우드 프로젝트 불필요).
 * 일정 추가·수정은 구글 캘린더 앱(ACTION_INSERT / ACTION_VIEW)에서 하고, 돌아오면 다시 읽는다.
 */
object GoogleCalendar {
    const val PERMISSION = Manifest.permission.READ_CALENDAR
    private const val GOOGLE_TYPE = "com.google"
    const val DAYS = 21

    data class Result(
        val events: List<CalEvent>,
        /** 읽은 구글 계정 */
        val accounts: List<String>,
        val calendarCount: Int,
        /** 구글 계정 캘린더가 없어서 기기의 다른 캘린더를 읽었는지 */
        val fallbackToDevice: Boolean,
        /** 장소를 몰라서 전략에서 뺀 일정 제목 */
        val noPlace: List<String>,
    )

    val PERMISSIONS = arrayOf(PERMISSION, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * 앱에서 입력한 일정을 기기 캘린더(= 구글 계정 캘린더)에 실제로 써넣는다.
     * 구글 캘린더 앱을 띄우는 대신 앱 안에서 입력받을 때 쓰는 길 — 저장되는 곳은 똑같다.
     */
    suspend fun insert(ctx: Context, e: CalEvent): Boolean = withContext(Dispatchers.IO) {
        val calId = writableCalendarId(ctx) ?: return@withContext false
        val zone = ZoneId.systemDefault()
        val begin = e.date.atTime(e.start).atZone(zone).toInstant().toEpochMilli()
        val endDate = if (e.overnight) e.date.plusDays(1) else e.date
        val end = endDate.atTime(e.end).atZone(zone).toInstant().toEpochMilli()
        val v = ContentValues().apply {
            put(Events.CALENDAR_ID, calId)
            put(Events.TITLE, e.title)
            put(Events.EVENT_LOCATION, e.place.name)
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
            put(Events.EVENT_TIMEZONE, zone.id)
            put(Events.ALL_DAY, 0)
        }
        runCatching { ctx.contentResolver.insert(Events.CONTENT_URI, v) }.getOrNull() != null
    }

    /** 쓸 수 있는 캘린더 — 구글 계정 것 우선 */
    private fun writableCalendarId(ctx: Context): Long? {
        var google: Long? = null
        var any: Long? = null
        ctx.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_ACCESS_LEVEL),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) < Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                val id = c.getLong(0)
                if (any == null) any = id
                if (c.getString(1) == GOOGLE_TYPE && google == null) google = id
            }
        }
        return google ?: any
    }

    /** 구글 캘린더 앱에서 새 일정 입력 화면 열기 */
    fun insertIntent(date: LocalDate, startH: Int = 19): Intent {
        val begin = date.atTime(startH, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT).setData(Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 2 * 3_600_000L)
    }

    /** 구글 캘린더 앱에서 그 일정 열기 (수정·삭제는 거기서) */
    fun viewIntent(g: GoogleRef): Intent =
        Intent(Intent.ACTION_VIEW).setData(ContentUris.withAppendedId(Events.CONTENT_URI, g.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, g.beginMs)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, g.endMs)

    /** 오늘부터 [DAYS]일 일정 읽기 (IO 스레드). 권한이 없으면 SecurityException */
    suspend fun load(ctx: Context, today: LocalDate): Result = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        data class Cal(val id: Long, val account: String, val type: String)
        val cals = mutableListOf<Cal>()
        cr.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.VISIBLE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) == 1) cals += Cal(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        }
        val google = cals.filter { it.type == GOOGLE_TYPE }
        val use = google.ifEmpty { cals }
        if (use.isEmpty()) return@withContext Result(emptyList(), emptyList(), 0, google.isEmpty(), emptyList())

        val zone = ZoneId.systemDefault()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from); ContentUris.appendId(it, to)
        }.build()

        val events = mutableListOf<CalEvent>()
        val noPlace = mutableListOf<String>()
        cr.query(
            uri,
            arrayOf(Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE, Instances.EVENT_LOCATION,
                Instances.ALL_DAY, Instances.STATUS),
            "${Instances.CALENDAR_ID} IN (${use.joinToString { it.id.toString() }})",
            null, "${Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(5) == 1) continue                                   // 종일 일정은 이동 없음으로 봄
                if (!c.isNull(6) && c.getInt(6) == Events.STATUS_CANCELED) continue
                val eventId = c.getLong(0)
                val beginMs = c.getLong(1)
                val endMs = c.getLong(2)
                val title = c.getString(3)?.trim().orEmpty().ifEmpty { "(제목 없음)" }
                val location = c.getString(4)?.trim().orEmpty()
                val b = Instant.ofEpochMilli(beginMs).atZone(zone)
                val e = Instant.ofEpochMilli(endMs).atZone(zone)
                if (b.toLocalDate() < today) continue                            // 어제 시작한 일정은 건너뜀
                val place = resolvePlace(ctx, location, title)
                if (place == null) { noPlace += title; continue }
                // 다음 날 넘어가서 끝나면 1박 (자정 정각 종료는 당일로)
                val overnight = e.toLocalDate() > b.toLocalDate() && e.toLocalTime() != LocalTime.MIDNIGHT
                val end = when {
                    overnight -> e.toLocalTime()
                    e.toLocalDate() > b.toLocalDate() -> LocalTime.of(23, 59)
                    else -> e.toLocalTime()
                }
                events += CalEvent(
                    id = 0, date = b.toLocalDate(), title = title, place = place,
                    start = b.toLocalTime().withSecond(0).withNano(0), end = end.withSecond(0).withNano(0),
                    overnight = overnight,
                    google = GoogleRef("$eventId@${b.toLocalDate()}", eventId, beginMs, endMs, location),
                )
            }
        }
        Result(events, google.map { it.account }.distinct(), use.size, google.isEmpty(), noPlace.distinct())
    }

    // ───────────── 장소 → 충전 인프라 ─────────────

    private val geoCache = mutableMapOf<String, Place?>()

    /** "서울 마포구 연남동" → "연남동" */
    private fun shortOf(location: String) =
        location.split(' ', ',', '·').lastOrNull { it.isNotBlank() }?.take(8) ?: location.take(8)

    /** 캘린더 장소/제목 → 데모 장소 DB(충전 인프라 있음) → 없으면 지오코딩(인프라 정보 없음) */
    fun resolvePlace(ctx: Context?, location: String, title: String): Place? {
        matchKnown(location)?.let { return it }
        if (location.isNotBlank()) {
            geocode(ctx, location)?.let { return it }
        }
        return matchKnown(title)
    }

    private fun matchKnown(text: String): Place? {
        if (text.isBlank()) return null
        return DemoPlaces.all.mapNotNull { p ->
            p.aliases.mapNotNull { a -> text.indexOf(a, ignoreCase = true).takeIf { it >= 0 } }.minOrNull()?.let { p to it }
        }.minByOrNull { it.second }?.first
    }

    private fun geocode(ctx: Context?, location: String): Place? {
        if (ctx == null) return null
        synchronized(geoCache) { if (location in geoCache) return geoCache[location] }
        val found = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.KOREA).getFromLocationName(location, 1)?.firstOrNull()
        }.getOrNull()?.let { a ->
            Place(location, shortOf(location), emptyList(), LatLng(a.latitude, a.longitude), parkHours = 2)
        }
        if (found != null) synchronized(geoCache) { geoCache[location] = found }  // 실패(오프라인 등)는 다음에 다시 시도
        return found
    }

    // ───────────── 지난 동기화 기억 (앱을 껐다 켜도 "바뀐 일정"을 알 수 있게) ─────────────

    private const val KEY_EVENTS = "google_events"
    private const val KEY_SYNCED_AT = "google_synced_at"
    private const val KEY_ACCOUNT = "google_account"
    private const val FS = '\u001F'

    private fun clean(s: String) = s.replace(FS, ' ').replace('\n', ' ')

    fun save(prefs: SharedPreferences, events: List<CalEvent>, account: String?, syncedAt: Long) {
        val lines = events.mapNotNull { e ->
            val g = e.google ?: return@mapNotNull null
            listOf(g.key, g.eventId, g.beginMs, g.endMs, clean(g.location), clean(e.title), clean(e.place.name),
                e.place.at.lat, e.place.at.lon, e.date, e.start, e.end, e.overnight).joinToString(FS.toString())
        }
        prefs.edit().putStringSet(KEY_EVENTS, lines.toSet()).putLong(KEY_SYNCED_AT, syncedAt)
            .putString(KEY_ACCOUNT, account).apply()
    }

    fun restore(prefs: SharedPreferences, today: LocalDate): Triple<List<CalEvent>, String?, Long>? {
        val at = prefs.getLong(KEY_SYNCED_AT, 0L).takeIf { it > 0 } ?: return null
        val events = prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty().mapNotNull { line ->
            runCatching {
                val f = line.split(FS)
                val date = LocalDate.parse(f[9])
                if (date < today) return@runCatching null
                val known = DemoPlaces.all.firstOrNull { it.name == f[6] }
                val place = known ?: Place(f[6], shortOf(f[6]), emptyList(), LatLng(f[7].toDouble(), f[8].toDouble()), parkHours = 2)
                CalEvent(
                    0, date, f[5], place, LocalTime.parse(f[10]), LocalTime.parse(f[11]), overnight = f[12].toBoolean(),
                    google = GoogleRef(f[0], f[1].toLong(), f[2].toLong(), f[3].toLong(), f[4]),
                )
            }.getOrNull()
        }.sortedWith(compareBy({ it.date }, { it.start }))
        return Triple(events, prefs.getString(KEY_ACCOUNT, null), at)
    }

    fun forget(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_EVENTS).remove(KEY_SYNCED_AT).remove(KEY_ACCOUNT).apply()
    }
}

/** 지난 동기화 대비 바뀐 점 */
data class CalendarDiff(
    val added: List<CalEvent>,
    /** (예전, 지금) */
    val changed: List<Pair<CalEvent, CalEvent>>,
    val removed: List<CalEvent>,
) {
    val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
    val dates: List<LocalDate>
        get() = (added.map { it.date } + changed.flatMap { listOf(it.first.date, it.second.date) } + removed.map { it.date })
            .distinct().sorted()

    companion object {
        private fun same(a: CalEvent, b: CalEvent) =
            a.date == b.date && a.start == b.start && a.end == b.end && a.title == b.title &&
                a.place.name == b.place.name && a.overnight == b.overnight

        fun of(old: List<CalEvent>, new: List<CalEvent>): CalendarDiff {
            val oldBy = old.associateBy { it.google!!.key }
            val newBy = new.associateBy { it.google!!.key }
            val changed = mutableListOf<Pair<CalEvent, CalEvent>>()
            newBy.forEach { (k, n) -> oldBy[k]?.let { o -> if (!same(o, n)) changed += o to n } }
            val added = newBy.filterKeys { it !in oldBy }.values.toMutableList()
            val removed = oldBy.filterKeys { it !in newBy }.values.toMutableList()
            // 날짜를 옮긴 일정: 같은 eventId 가 한쪽에서 빠지고 한쪽에 생김 → "변경"
            removed.toList().forEach { o ->
                val n = added.firstOrNull { it.google!!.eventId == o.google!!.eventId }
                if (n != null) { changed += o to n; added.remove(n); removed.remove(o) }
            }
            return CalendarDiff(added.sortedBy { it.date }, changed.sortedBy { it.second.date }, removed.sortedBy { it.date })
        }
    }
}
