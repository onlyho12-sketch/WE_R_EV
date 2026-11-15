package com.example.rrrphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrrphone.ui.theme.AppTypography
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * 캘린더 앱 화면 (시연용 독립 앱).
 *
 * 시연 시나리오의 첫 단계가 "구글 캘린더에 일정 입력"인데, 에뮬레이터에는 구글 계정을 붙일 수
 * 없어서 구글 캘린더 앱이 로그인 벽에서 막힌다. 그래서 같은 자리를 대신할 캘린더 앱을 따로 띄운다.
 *
 * 읽고 쓰는 곳은 진짜다 — 기기 캘린더(CalendarContract) 의 구글 계정 캘린더를 그대로 읽고,
 * 여기서 저장한 일정이 르르르 앱의 [구글 캘린더 연동] 에 잡힌다. 두 앱은 별개 액티비티라
 * 시연 영상에서도 실제 앱 전환으로 보인다.
 */

private val GBlue = Color(0xFF1A73E8)
private val GText = Color(0xFF202124)
private val GSub = Color(0xFF5F6368)
private val GLine = Color(0xFFDADCE0)
private val GChip = Color(0xFF039BE5)

class CalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(typography = AppTypography) { CalendarApp() } }
    }
}

@Composable
fun CalendarApp() {
    val ctx = LocalContext.current
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var account by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(reload) {
        runCatching { GoogleCalendar.load(ctx, today) }.onSuccess { r ->
            events = r.events.sortedWith(compareBy({ it.date }, { it.start }))
            account = r.accounts.firstOrNull()
        }
    }

    val open = editing
    if (open != null) {
        GoogleCalendarEditor(
            today = today, initial = open, account = account,
            onClose = { editing = null },
            onSave = { e ->
                editing = null
                scope.launch { GoogleCalendar.insert(ctx, e); reload++ }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(today, account)
            Text(
                "%d년 %d월".format(today.year, today.monthValue),
                color = GText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(events, key = { it.google?.key ?: "${it.date}${it.start}" }) { e -> DayRow(e) }
            }
        }
        // 일정 추가
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 32.dp)
                .navigationBarsPadding()
                .size(62.dp).clip(CircleShape).background(Color.White)
                .border(1.dp, GLine, CircleShape)
                .clickable { editing = today },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(26.dp)) {
                val c = size.minDimension / 2f
                drawLine(GBlue, Offset(c, 2f), Offset(c, size.height - 2f), 5f, StrokeCap.Round)
                drawLine(GBlue, Offset(2f, c), Offset(size.width - 2f, c), 5f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TopBar(today: LocalDate, account: String?) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape), Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                val u = size.minDimension / 24f
                listOf(7f, 12f, 17f).forEach { y ->
                    drawLine(GText, Offset(3.5f * u, y * u), Offset(20.5f * u, y * u), 1.9f * u, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("${today.monthValue}월", color = GText, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Text("▾", color = GSub, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        // 오늘로 가기
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).border(1.6.dp, GSub, RoundedCornerShape(7.dp)),
            Alignment.Center,
        ) { Text("${today.dayOfMonth}", color = GText, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF7B9BD1)), Alignment.Center) {
            Text(account?.firstOrNull()?.uppercase() ?: "D", color = Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DayRow(e: CalEvent) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Column(Modifier.width(62.dp)) {
            Text(e.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                color = GSub, fontSize = 12.sp)
            Text("${e.date.dayOfMonth}", color = GText, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(GChip)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${hm(e.startH)} – ${hm(e.endH)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            e.google?.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}
