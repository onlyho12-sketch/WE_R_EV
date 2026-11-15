package com.example.rrrphone

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrrphone.ui.theme.AppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(
                    primary = Navy, onPrimary = Color.White, secondary = Navy, background = Bg,
                    surface = Surface, onSurface = Ink, surfaceContainerHigh = Surface, outline = Line,
                ),
            ) { PhoneApp() }
        }
    }
}

sealed interface ChatItem {
    val id: Long
    data class User(override val id: Long, val text: String) : ChatItem
    data class Lrrr(override val id: Long, val text: String) : ChatItem
    /** 새 일정 → 에이전트가 단계별로 확인하고 전략을 내놓는 한 묶음 */
    data class Run(override val id: Long, val steps: List<String>, val message: String, val strategy: DayStrategy) : ChatItem
    /** Pleos 전송 후 — 주행 중 내비 위젯에 뜰 모습 */
    data class Widget(override val id: Long, val strategy: DayStrategy) : ChatItem
}

/**
 * 앱 상태. 흐름:
 *   구글 캘린더 입력 → [연동] → (폰) 르르르가 바뀐 일정·장소의 충전 인프라로 1차 전략 → (Pleos) 주행 데이터 반영 최종 전략
 */
class AppState(val today: LocalDate, private val prefs: SharedPreferences? = null) {
    val events = mutableStateListOf<CalEvent>().apply { addAll(DemoCalendar.seed(today)) }
    var car by mutableStateOf(VehicleState())
    var tab by mutableIntStateOf(0)
    var selectedDate by mutableStateOf(today)
    val chat = mutableStateListOf<ChatItem>()
    /** 실행한 행동 키 (충전 예약, 빈자리 알림, 경유지 …) */
    val done = mutableStateListOf<String>()
    /** Pleos 로 보낸 날짜 → 보낼 때의 일정/차량 지문 (그 뒤 바뀌면 다시 보내야 함) */
    private val synced = mutableStateMapOf<LocalDate, Int>()
    /** 단계 애니메이션을 이미 보여준 대화 */
    val animated = mutableStateListOf<Long>()
    var unread by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
        private set
    /** 같은 문구를 연달아 띄워도 타이머가 다시 돌도록 */
    var toastSeq by mutableIntStateOf(0)
        private set
    private var seq = 1000L
    fun nextId() = ++seq

    // ── 구글 캘린더 ──
    var googleConnected by mutableStateOf(false)
        private set
    var googleAccount by mutableStateOf<String?>(null)
        private set
    var googleSyncedAt by mutableLongStateOf(0L)
        private set
    var syncing by mutableStateOf(false)
    /** 연동 버튼 → 권한 확인 후 동기화 (PhoneApp 에서 연결) */
    var requestGoogleSync: () -> Unit = {}
    /** 구글 캘린더 앱 열기 (돌아오면 자동 동기화). 열 앱이 없으면 false */
    var openGoogleCalendar: (Intent) -> Boolean = { false }
    /** 앱에서 입력한 일정을 구글 캘린더에 써넣기 (PhoneApp 에서 연결) */
    var addEventToGoogle: (CalEvent) -> Unit = {}
    /** 구글 캘린더 일정 입력 화면 (null 이면 닫힘) */
    var googleEditorDate by mutableStateOf<LocalDate?>(null)

    init {
        chat += ChatItem.Lrrr(nextId(), LrrrAgent.GREETING)
        prefs?.let { GoogleCalendar.restore(it, today) }?.let { (evs, account, at) ->
            events += evs.map { it.copy(id = nextId()) }
            googleConnected = true; googleAccount = account; googleSyncedAt = at
        }
    }

    fun showToast(msg: String) { toast = msg; toastSeq++ }
    fun clearToast() { toast = null }

    private fun fingerprint(date: LocalDate) =
        events.filter { it.date == date }.hashCode() * 31 + car.hashCode()

    fun isSynced(date: LocalDate) = synced[date] == fingerprint(date)
    fun phoneStrategy(date: LocalDate) = planDay(date, events, car, today)
    fun finalStrategy(date: LocalDate) = planDay(date, events, car, today, PLEOS_KM_PER_KWH)
    fun strategy(date: LocalDate) = if (isSynced(date)) finalStrategy(date) else phoneStrategy(date)

    /** 새 일정 → 르르르가 루틴과 비교해서 전략을 다시 세움 */
    fun addEvent(e: CalEvent) {
        val before = phoneStrategy(e.date)
        // 시간이 겹치는 앱 일정(루틴·구글 일정 아님)은 새 일정으로 바꿈
        val clash = events.filter {
            it.date == e.date && !it.routine && it.google == null && it.startH < e.endH && e.startH < it.endH
        }
        events.removeAll(clash)
        events += e
        val after = phoneStrategy(e.date)
        val replaced = if (clash.isEmpty()) "" else
            "${clash.joinToString { "‘${it.title}’" }} 일정과 시간이 겹쳐서 새 일정으로 바꿨어요.\n"
        chat += ChatItem.Run(
            nextId(), LrrrAgent.steps(e, after, car),
            replaced + LrrrAgent.changeMessage(e, before, after, dateLabel(e.date, today)), after,
        )
        selectedDate = e.date
        if (tab != 2) unread = true
    }

    fun removeEvent(e: CalEvent) { events.remove(e) }

    fun ask(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        chat += ChatItem.User(nextId(), t)
        val e = LrrrAgent.parse(t, today, nextId())
        if (e == null) {
            chat += ChatItem.Lrrr(nextId(), "어디로, 언제 가는지 알려주시면 전략 세워드릴게요.\n예) “내일 저녁 7시 스타필드 하남 쇼핑”")
        } else {
            chat += ChatItem.Lrrr(nextId(), "${dateLabel(e.date, today)} ${hm(e.startH)} ‘${e.title}’(${e.place.name}) 일정으로 전략 세워볼게요.")
            addEvent(e)
        }
    }

    /**
     * 구글 캘린더에서 읽은 일정 반영.
     * 지난 동기화와 비교해서 새로 생기거나 바뀐(시간·장소) 일정이 있는 날마다 르르르가 전략을 다시 세운다.
     * @param fromButton 연동 버튼을 직접 눌렀으면 true (바뀐 게 없어도 르르르가 답함)
     */
    fun applyGoogle(r: GoogleCalendar.Result, fromButton: Boolean) {
        val first = !googleConnected
        val old = events.filter { it.google != null }
        val oldByKey = old.associateBy { it.google!!.key }
        val fresh = r.events.map { e -> e.copy(id = oldByKey[e.google!!.key]?.id ?: nextId()) }
        val diff = if (first) CalendarDiff(fresh, emptyList(), emptyList()) else CalendarDiff.of(old, fresh)
        val before = events.toList()
        events.removeAll { it.google != null }
        events += fresh
        googleConnected = true
        googleAccount = r.accounts.firstOrNull() ?: googleAccount
        googleSyncedAt = System.currentTimeMillis()
        prefs?.let { GoogleCalendar.save(it, fresh, googleAccount, googleSyncedAt) }

        if (!fromButton && diff.isEmpty) { showToast("구글 캘린더 확인 · 바뀐 일정 없음"); return }

        if (r.calendarCount == 0) {
            chat += ChatItem.Lrrr(nextId(), "이 폰에 구글 계정 캘린더가 없어요. 설정 › 계정에서 구글 계정을 추가하고 캘린더 동기화를 켜주세요.")
        } else {
            val note = if (r.fallbackToDevice) "\n(구글 계정 캘린더가 없어서 폰의 다른 캘린더를 읽었어요)" else ""
            chat += ChatItem.Lrrr(nextId(), LrrrAgent.syncSummary(diff, first, fresh.size, googleAccount, r.noPlace, today) + note)
        }

        val dates = diff.dates.filter { d -> !first || fresh.any { it.date == d } }.take(if (first) 3 else 4)
        dates.forEach { date ->
            val b = planDay(date, before, car, today)
            val a = phoneStrategy(date)
            val change = diff.changed.firstOrNull { it.second.date == date }
            val e = change?.second ?: diff.added.firstOrNull { it.date == date }
            if (e != null) {
                chat += ChatItem.Run(nextId(), LrrrAgent.steps(e, a, car, change?.first),
                    LrrrAgent.googleChangeMessage(e, change?.first, b, a, today), a)
            } else {
                val gone = diff.removed.filter { it.date == date } +
                    diff.changed.filter { it.first.date == date }.map { it.first }
                chat += ChatItem.Lrrr(nextId(), LrrrAgent.removedMessage(date, gone, a, today))
            }
        }
        dates.firstOrNull()?.let { selectedDate = it }
        tab = 2
    }

    fun disconnectGoogle() {
        events.removeAll { it.google != null }
        googleConnected = false; googleAccount = null; googleSyncedAt = 0L
        prefs?.let { GoogleCalendar.forget(it) }
        showToast("구글 캘린더 연동을 해제했어요")
    }

    fun sendToPleos(date: LocalDate) {
        val phone = phoneStrategy(date)
        val fin = finalStrategy(date)
        synced[date] = fingerprint(date)
        chat += ChatItem.Lrrr(nextId(), LrrrAgent.finalizeMessage(phone, fin))
        chat += ChatItem.Widget(nextId(), fin)
        if (tab != 2) unread = true
        showToast("Pleos로 전송 · 주행 데이터 반영 최종 전략 확정")
    }
}

/** 하단 탭 — 차량 앱 사이드바처럼 라인 아이콘 (르르르 탭만 캐릭터) */
private val Tabs = listOf(Ic.Home to "오늘", Ic.Calendar to "캘린더", null to "르르르", Ic.Car to "차량")

@Composable
fun PhoneApp() {
    val ctx = LocalContext.current
    val state = remember { AppState(LocalDate.now(), ctx.getSharedPreferences("rrr_phone", Context.MODE_PRIVATE)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.toast, state.toastSeq) { if (state.toast != null) { delay(2600); state.clearToast() } }
    LaunchedEffect(state.tab) { if (state.tab == 2) state.unread = false }

    // ── 구글 캘린더 연동 ──
    val runSync: (Boolean) -> Unit = { fromButton ->
        if (!state.syncing) {
            state.syncing = true
            scope.launch {
                val t0 = System.currentTimeMillis()
                val r = runCatching { GoogleCalendar.load(ctx, state.today) }
                // 너무 빨리 끝나면 눌렀는지 모르니 잠깐은 "동기화 중" 표시
                delay((600 - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
                state.syncing = false
                r.onSuccess { state.applyGoogle(it, fromButton) }
                    .onFailure { state.showToast("캘린더를 읽지 못했어요 (${it.javaClass.simpleName})") }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[GoogleCalendar.PERMISSION] == true) runSync(true)
        else {
            val canAskAgain = (ctx as? Activity)?.shouldShowRequestPermissionRationale(GoogleCalendar.PERMISSION) == true
            if (canAskAgain) state.showToast("캘린더 권한이 있어야 구글 캘린더 일정을 읽을 수 있어요")
            else {
                state.showToast("설정 › 권한에서 ‘캘린더’를 허용해 주세요")
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }
            }
        }
    }
    // 구글 캘린더 앱에서 일정 추가·수정하고 돌아오면 자동으로 다시 읽음
    val calendarApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (GoogleCalendar.hasPermission(ctx)) runSync(false)
    }
    SideEffect {
        state.requestGoogleSync = {
            if (GoogleCalendar.hasPermission(ctx)) runSync(true) else permission.launch(GoogleCalendar.PERMISSIONS)
        }
        state.addEventToGoogle = { e ->
            if (!GoogleCalendar.hasWritePermission(ctx)) {
                state.showToast("캘린더 쓰기 권한을 허용하면 구글 캘린더에 바로 넣어드려요")
                permission.launch(GoogleCalendar.PERMISSIONS)
            } else scope.launch {
                state.syncing = true
                val ok = GoogleCalendar.insert(ctx, e)
                delay(400)                       // 저장되는 걸 보여줄 만큼은 머무르게
                state.syncing = false
                state.showToast(
                    if (ok) "구글 캘린더에 ‘${e.title}’ 저장됨 · 연동을 누르면 전략을 다시 세워요"
                    else "구글 캘린더에 저장하지 못했어요",
                )
            }
        }
        state.openGoogleCalendar = { intent ->
            try { calendarApp.launch(intent); true } catch (_: ActivityNotFoundException) { false }
        }
    }

    // 구글 캘린더 입력 화면은 하단 탭까지 덮는 전체 화면
    val editing = state.googleEditorDate
    if (editing != null) {
        GoogleCalendarEditor(
            today = state.today, initial = editing, account = state.googleAccount,
            onClose = { state.googleEditorDate = null },
            onSave = { e -> state.googleEditorDate = null; state.addEventToGoogle(e) },
        )
        return
    }

    Scaffold(containerColor = Bg, bottomBar = { BottomNav(state) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (state.tab) {
                0 -> HomeScreen(state)
                1 -> CalendarScreen(state)
                2 -> AgentScreen(state)
                else -> CarScreen(state)
            }
            state.toast?.let {
                Text(
                    it, color = Surface, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Ink.copy(alpha = 0.88f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomNav(state: AppState) {
    Column(Modifier.fillMaxWidth().background(Surface)) {
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp)) {
            Tabs.forEachIndexed { i, (icon, label) ->
                val active = state.tab == i
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { state.tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (active) NavySoft else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon == null) LrrrAvatar(26.dp) else LineIcon(icon, if (active) Navy else Faint, size = 24.dp)
                        if (i == 2 && state.unread) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(RoundedCornerShape(4.dp)).background(Navy))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = 12.sp, color = if (active) Navy else Faint,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}
