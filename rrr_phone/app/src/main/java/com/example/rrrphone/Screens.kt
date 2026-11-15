package com.example.rrrphone

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

// ═════════════════════════ 오늘 ═════════════════════════

@Composable
fun HomeScreen(state: AppState) {
    val today = state.today
    val s = state.strategy(today)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // 차량 앱 홈과 같은 머리: WE'R EV 로고 → 큰 제목 → 한 줄 설명
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { WerEvLogo(28.dp) }
            PleosPill(state.isSynced(today))
        }
        Column {
            Text("SANTA FE EREV", color = Ink, fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
            Text("나의 일정에 맞춘 에너지 코치 · ${fullDate(today)} ${hm(DEMO_NOW_H)}", color = Sub, fontSize = 14.sp)
        }
        RCard(padding = 12.dp) { LrrrHero(LrrrAgent.brief(s), s.heroEnergy) }
        CarStrip(state.car)
        GoogleSyncCard(state, compact = true)
        SectionTitle("오늘 일정", "캘린더") { state.selectedDate = today; state.tab = 1 }
        RCard { DayItems(s) }
        SectionTitle("르르르의 전략")
        StrategyCard(state, s)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CarStrip(car: VehicleState) {
    RCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("배터리 · 연료", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${car.at.name} 주차 중", color = Faint, fontSize = 12.sp)
            }
            Stat("${car.soc}%", "배터리", EvGreen)
            Spacer(Modifier.width(16.dp))
            Stat("${car.fuel}%", "연료", GasOrange)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Field)) {
            if (car.soc > 0) Box(Modifier.weight(car.soc.toFloat()).fillMaxHeight().background(EvGreen))
            if (car.soc < 100) Spacer(Modifier.weight((100 - car.soc).toFloat()))
        }
        Text("전기로 ${((car.soc - RESERVE_PCT).coerceAtLeast(0) / 100.0 * EV_FULL_KM).roundToInt()}km 더 갈 수 있어요 (예비 ${RESERVE_PCT}% 제외)",
            color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Faint, fontSize = 11.sp)
    }
}

// ═════════════════════════ 캘린더 ═════════════════════════

/** 구글 캘린더 연동 카드 — 연동 버튼을 누르면 르르르가 바뀐 일정·장소로 전략을 다시 세움 */
@Composable
fun GoogleSyncCard(state: AppState, compact: Boolean = false) {
    val connected = state.googleConnected
    RCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(NavySoft),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Ic.Calendar, Navy, size = 20.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (connected) "구글 캘린더 연동됨" else "구글 캘린더 연동", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        state.syncing -> "일정 읽는 중…"
                        connected -> listOfNotNull(state.googleAccount, "${syncedAgo(state.googleSyncedAt)} 동기화").joinToString(" · ")
                        else -> "구글 캘린더 일정으로 충전 전략을 세워요"
                    },
                    color = Faint, fontSize = 12.sp, maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { state.requestGoogleSync() },
                enabled = !state.syncing,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, disabledContainerColor = Blue.copy(alpha = 0.5f),
                    disabledContentColor = Surface),
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Surface, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (connected) "동기화" else "연동", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!compact && !connected) {
            Spacer(Modifier.height(8.dp))
            Text("구글 캘린더에 일정을 넣고 연동을 누르면, 르르르가 바뀐 일정과 장소의 충전 인프라를 보고 전략을 다시 세워요.",
                color = Sub, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (!compact && connected) {
            Text("연동 해제", color = Faint, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(4.dp))
                    .clickable { state.disconnectGoogle() }.padding(2.dp))
        }
    }
}

private fun syncedAgo(ms: Long): String {
    val min = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        else -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) }
    }
}

@Composable
fun CalendarScreen(state: AppState) {
    var adding by remember { mutableStateOf(false) }
    var showStrategy by remember { mutableStateOf(false) }
    val sel = state.selectedDate
    val day = state.events.filter { it.date == sel }.sortedBy { it.start }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("캘린더", color = Ink, fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
            Text("${sel.year}년 ${sel.monthValue}월 · 출근은 Pleos 주행 패턴에서 찾은 루틴", color = Faint, fontSize = 13.sp)
            GoogleSyncCard(state)
            WeekStrip(state)
            Text(fullDate(sel), color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            if (day.isEmpty()) RCard { Text("일정이 없어요. 아래 버튼으로 구글 캘린더에 추가해 보세요.", color = Faint, fontSize = 14.sp) }
            day.forEach { e ->
                EventRow(
                    e,
                    onOpen = e.google?.let { g -> { if (!state.openGoogleCalendar(GoogleCalendar.viewIntent(g))) state.showToast("구글 캘린더 앱을 열 수 없어요") } },
                    onDelete = { state.removeEvent(e) },
                )
            }

            val s = state.strategy(sel)
            RCard(Modifier.clickable { showStrategy = !showStrategy }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LrrrAvatar(40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("이날 르르르 전략", color = Faint, fontSize = 12.sp)
                        Text(LrrrAgent.headline(s), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${s.totalKm.roundToInt()}km · 전기 ${s.evKm.roundToInt()}km / 발전 ${s.gasKm.roundToInt()}km",
                            color = Sub, fontSize = 13.sp)
                    }
                    Text(if (showStrategy) "접기 ▲" else "자세히 ▼", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (showStrategy) StrategyCard(state, s)
            Spacer(Modifier.height(52.dp))
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("빠른 입력", color = Sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(20.dp))
                    .clickable { adding = true }.padding(horizontal = 14.dp, vertical = 8.dp))
            Spacer(Modifier.width(8.dp))
            ExtendedFloatingActionButton(
                onClick = { state.googleEditorDate = sel },
                containerColor = Blue, contentColor = Surface,
                shape = RoundedCornerShape(28.dp),
            ) { Text("+  구글 캘린더에 추가", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (adding) {
        AddEventSheet(state, sel, toGoogle = false, onDismiss = { adding = false }) { e ->
            adding = false
            state.addEvent(e)
            state.tab = 2
        }
    }
}

@Composable
private fun WeekStrip(state: AppState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items((0 until 21).map { state.today.plusDays(it.toLong()) }) { d ->
            val active = d == state.selectedDate
            val evs = state.events.filter { it.date == d }
            Column(
                Modifier.width(46.dp).clip(RoundedCornerShape(12.dp)).background(if (active) Navy else Surface)
                    .border(1.dp, if (active) Navy else Line, RoundedCornerShape(12.dp))
                    .clickable { state.selectedDate = d }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                Text(if (d == state.today) "오늘" else dow, fontSize = 12.sp,
                    color = if (active) Surface.copy(alpha = 0.7f) else Faint)
                Text("${d.dayOfMonth}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (active) Surface else Ink)
                Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    evs.take(3).forEach {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (it.routine) Faint else Blue))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, onOpen: (() -> Unit)?, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(Surface)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (e.routine) Line else Blue))
        Column(Modifier.weight(1f).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.overnight) "${hm(e.startH)} ~ 1박" else "${hm(e.startH)} – ${hm(e.endH)}",
                    color = Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                when {
                    e.routine -> Tag("루틴", Faint)
                    e.google != null -> Tag("구글 캘린더", Blue, filled = true)
                    else -> Tag("앱 일정", Blue)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(e.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.place.name, color = Faint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                InfraTags(e.place)
            }
            if (e.google != null) {
                Text("탭하면 구글 캘린더에서 수정 › 돌아오면 자동 반영", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            } else if (!e.routine) {
                Text("삭제", color = Faint, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onDelete).padding(2.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventSheet(
    state: AppState,
    initial: LocalDate,
    toGoogle: Boolean,
    onDismiss: () -> Unit,
    onSave: (CalEvent) -> Unit,
) {
    val today = state.today
    var title by remember { mutableStateOf("") }
    var place by remember { mutableStateOf(DemoPlaces.Hanam) }
    var date by remember { mutableStateOf(initial) }
    var startMin by remember { mutableIntStateOf(19 * 60) }
    var durMin by remember { mutableIntStateOf(120) }
    var overnight by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 버그 수정: 시트를 닫는 애니메이션 없이 바로 탭을 바꾸면 시트(와 버튼)가 르르르 화면 위에 남아 있었음
    //  → 시트를 먼저 내리고, 다 내려간 뒤에 저장·화면 전환. 연타로 두 번 저장되는 것도 막음
    var saving by remember { mutableStateOf(false) }
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(if (toGoogle) "구글 캘린더에 새 일정" else "새 일정", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (toGoogle) "${state.googleAccount ?: "구글 계정"} 캘린더에 저장돼요"
                else "이 앱에서만 쓰는 일정이에요",
                color = Faint, fontSize = 13.sp,
            )
            Text("빠른 입력", color = Faint, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LrrrAgent.templates(today)) { t ->
                    Pill(t.label) {
                        title = t.title; place = t.place; date = today.plusDays(t.dayOffset)
                        startMin = t.startMin; durMin = if (t.durMin > 0) t.durMin else durMin; overnight = t.overnight
                    }
                }
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text("제목") }, placeholder = { Text("${place.short} 일정") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("장소", color = Faint, fontSize = 13.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DemoPlaces.all) { p -> Pill(p.short, active = p == place) { place = p } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(place.name, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                InfraTags(place)
            }
            Text("충전 인프라 · ${infraText(place)}", color = Sub, fontSize = 13.sp)
            HorizontalDivider(color = Line)
            Stepper("날짜", dateLabel(date, today), { if (date > today) date = date.minusDays(1) }, { date = date.plusDays(1) })
            Stepper("시작", "%02d:%02d".format(startMin / 60, startMin % 60),
                { startMin = (startMin - 30).coerceAtLeast(6 * 60) }, { startMin = (startMin + 30).coerceAtMost(22 * 60) })
            if (!overnight) {
                Stepper("머무는 시간", if (durMin % 60 == 0) "${durMin / 60}시간" else "${durMin / 60}시간 ${durMin % 60}분",
                    { durMin = (durMin - 30).coerceAtLeast(30) }, { durMin = (durMin + 30).coerceAtMost(10 * 60) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("1박", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("오늘 밤 집에 안 들어가요 (집 충전 불가)", color = Faint, fontSize = 12.sp)
                }
                Switch(overnight, { overnight = it }, colors = SwitchDefaults.colors(checkedTrackColor = Blue))
            }
            Button(
                enabled = !saving,
                onClick = {
                    if (saving) return@Button
                    saving = true
                    val endMin = (startMin + durMin).coerceAtMost(23 * 60 + 59)
                    val e = CalEvent(
                        state.nextId(), date, title.ifBlank { "${place.short} 일정" }, place,
                        LocalTime.of(startMin / 60, startMin % 60),
                        if (overnight) LocalTime.of(11, 0) else LocalTime.of(endMin / 60, endMin % 60),
                        overnight = overnight,
                    )
                    closeThen { onSave(e) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text(if (toGoogle) "구글 캘린더에 저장" else "저장하고 르르르에게 전략 받기",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        RoundBtn("−", onMinus)
        Text(value, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp))
        RoundBtn("+", onPlus)
    }
}

@Composable
private fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.dp, Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ink, fontSize = 19.sp) }
}

// ═════════════════════════ 르르르 (에이전트 대화) ═════════════════════════

@Composable
fun AgentScreen(state: AppState) {
    // 탭에 들어올 때는 맨 아래에서 바로 시작 (예전엔 매번 맨 위에서부터 끝까지 스크롤 애니메이션)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.chat.lastIndex.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val toBottom: () -> Unit = {
        scope.launch { if (state.chat.isNotEmpty()) list.animateScrollToItem(state.chat.lastIndex, 100_000) }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(state.chat.size) {
        if (!entered) { entered = true; if (state.chat.isNotEmpty()) list.scrollToItem(state.chat.lastIndex, 100_000) }
        else toBottom()
    }
    val send = { if (input.isNotBlank()) { state.ask(input); input = "" } }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            LrrrAvatar(40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("르르르", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Gleo AI 어시스턴트 · 데모 응답", color = Faint, fontSize = 12.sp)
            }
            PleosPill(state.isSynced(state.today))
        }
        HorizontalDivider(color = Line)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(state.chat, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(item.text)
                    is ChatItem.Lrrr -> LrrrBubble(item.text)
                    is ChatItem.Run -> RunBlock(state, item, toBottom)
                    is ChatItem.Widget -> Row(verticalAlignment = Alignment.Top) {
                        LrrrAvatar(32.dp)
                        Spacer(Modifier.width(8.dp))
                        NaviWidgetCard(item.strategy, Modifier.weight(1f))
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Pill(if (state.syncing) "구글 캘린더 읽는 중…" else if (state.googleConnected) "구글 캘린더 변경 확인" else "구글 캘린더 연동",
                    active = true) { state.requestGoogleSync() }
            }
            items(LrrrAgent.suggestions) { Pill(it) { state.ask(it) } }
        }
        Row(
            Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, singleLine = true,
                placeholder = { Text("르르르에게 일정 말하기", fontSize = 15.sp) },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (input.isBlank()) Blue.copy(alpha = 0.4f) else Blue)
                    .clickable(enabled = input.isNotBlank()) { send() },
                contentAlignment = Alignment.Center,
            ) { Text("↑", color = Surface, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(text, color = Surface, fontSize = 15.sp, lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(Blue).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun LrrrBubble(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        LrrrAvatar(32.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ink, fontSize = 15.sp, lineHeight = 23.sp,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(Surface).border(1.dp, Line, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/** 새 일정 → 확인 단계가 하나씩 체크되고 → "일정이 바뀌었네요!" + 전략 카드 */
@Composable
private fun RunBlock(state: AppState, run: ChatItem.Run, onGrow: () -> Unit) {
    val already = remember(run.id) { run.id in state.animated }
    var shown by remember(run.id) { mutableIntStateOf(if (already) run.steps.size + 1 else 0) }
    LaunchedEffect(run.id) {
        if (!already) {
            // 시작할 때 표시해 둬야 탭을 옮겼다 돌아와도(또는 스크롤로 사라졌다 나타나도) 애니메이션이 처음부터 다시 돌지 않음
            state.animated += run.id
            while (shown <= run.steps.size) { delay(450); shown++; onGrow() }
            delay(100); onGrow()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            LrrrAvatar(32.dp)
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).background(Field)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(if (shown > run.steps.size) "확인 완료" else "르르르가 확인하고 있어요…",
                    color = Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                run.steps.forEachIndexed { i, step ->
                    if (i < shown) Row {
                        Text("✓ ", color = EvGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(step, color = Sub, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        if (shown > run.steps.size) {
            Box(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SpeechBubble(run.message, Modifier.weight(1f).padding(bottom = 24.dp, end = 2.dp), animate = !already)
                        BouncyLrrr(
                            greetKey = run.id, width = 92.dp,
                            energy = run.strategy.heroEnergy,
                            talkMs = if (already) 0 else talkMsFor(run.message),
                        )
                    }
                    StrategyCard(state, run.strategy, showChart = false, showItems = true)
                }
            }
        }
    }
}

// ═════════════════════════ 차량 (Pleos 연동) ═════════════════════════

@Composable
fun CarScreen(state: AppState) {
    val car = state.car
    val today = state.today
    val synced = state.isSynced(today)
    val s = state.strategy(today)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SANTA FE EREV", color = Ink, fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                Text("Pleos Connect · 차량 상태 실시간", color = Sub, fontSize = 15.sp)
            }
            PleosPill(synced)
        }

        RCard {
            Gauge("배터리", car.soc, EvGreen,
                "전기 ${((car.soc - RESERVE_PCT).coerceAtLeast(0) / 100.0 * BATTERY_KWH * PLEOS_KM_PER_KWH).roundToInt()}km (실전비 기준)")
            Spacer(Modifier.height(9.dp))
            Gauge("연료", car.fuel, GasOrange,
                "발전 ${(car.fuel / 100.0 * FUEL_TANK_L * GAS_KM_PER_L).roundToInt()}km")
            Spacer(Modifier.height(8.dp))
            Text("${car.at.name} · 주차 중", color = Faint, fontSize = 14.sp)
        }

        SectionTitle("Pleos 주행 데이터 · 최근 2주")
        RCard {
            Row {
                DataCell("실주행 전비", "$PLEOS_KM_PER_KWH", "km/kWh · 공인 $KM_PER_KWH", Modifier.weight(1f))
                DataCell("하루 평균", "38", "km 주행", Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            Row {
                DataCell("전기 주행 비율", "72", "%", Modifier.weight(1f))
                DataCell("하루 발전", "${DAILY_GAS_KM.roundToInt()}", "km 평균", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("겨울철 히터 사용으로 공인 전비보다 10% 낮아요. 최종 전략은 이 값으로 다시 계산해요.",
                color = Sub, fontSize = 13.sp, lineHeight = 19.sp)
        }

        SectionTitle("원격 제어")
        RCard(padding = 4.dp) {
            val homeKey = "$today:home"
            Remote(Ic.Plug, "집 충전 예약", if (homeKey in state.done) "${s.homeCharge?.let { hm(it.first) } ?: "23:00"} 예약됨" else "꺼짐",
                homeKey in state.done) { state.toggle(homeKey) }
            HorizontalDivider(color = Line)
            val next = s.legs.firstOrNull()?.to
            Remote(Ic.Pin, "목적지 차로 보내기", next?.let { "다음: ${it.name}" } ?: "다음 목적지 없음",
                "remote:dest" in state.done) { state.toggle("remote:dest") }
            HorizontalDivider(color = Line)
            Remote(Ic.Fuel, "주유소 경유지 등록", "${s.station.short} %,d원/L".format(s.station.wonPerL),
                "$today:gas" in state.done) { state.toggle("$today:gas") }
            HorizontalDivider(color = Line)
            Remote(Ic.Fan, "출발 전 공조", "${s.legs.firstOrNull()?.let { hm(it.depart - 1 / 6f) } ?: "--:--"} · 22℃ (충전 중이면 전력망 사용)",
                "remote:hvac" in state.done) { state.toggle("remote:hvac") }
        }

        SectionTitle("오늘 최종 전략")
        if (synced) {
            RCard {
                Text("✓ Pleos에 반영됨", color = EvGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(LrrrAgent.headline(s), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(LrrrAgent.finalizeMessage(state.phoneStrategy(today), s), color = Sub, fontSize = 14.sp, lineHeight = 21.sp)
            }
            NaviWidgetCard(s)
        } else {
            RCard {
                Text("폰에서 세운 1차 전략을 차로 보내면, Pleos가 실제 주행 데이터(전비·주행 패턴)로 다시 계산해서 최종 확정해요.",
                    color = Sub, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(9.dp))
                PleosButton(state, s)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun AppState.toggle(key: String) {
    if (key in done) done.remove(key) else done += key
}

@Composable
private fun Gauge(label: String, pct: Int, color: Color, sub: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = Sub, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text("$pct%", color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(sub, color = Faint, fontSize = 14.sp)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Field)) {
        if (pct > 0) Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(color))
        if (pct < 100) Spacer(Modifier.weight((100 - pct).toFloat()))
    }
}

@Composable
private fun DataCell(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Faint, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = Sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun Remote(icon: Ic, title: String, sub: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp)) { LineIcon(icon, if (on) Navy else Sub, size = 22.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Faint, fontSize = 12.sp)
        }
        Switch(on, { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = Blue))
    }
}
