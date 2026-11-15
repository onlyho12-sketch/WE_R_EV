package com.example.rrrphone

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/*
 * 구글 캘린더 일정 입력 화면.
 *
 * 에뮬레이터에는 구글 계정을 붙일 수 없어 구글 캘린더 앱이 로그인 벽에서 막힌다. 시연에서
 * "구글 캘린더에 일정을 넣는다"를 보여주려고 같은 자리에 쓰는 입력 화면을 앱 안에 뒀다.
 * 저장은 흉내가 아니라 진짜다 — GoogleCalendar.insert() 가 기기 캘린더(CalendarContract) 의
 * 구글 계정 캘린더에 일정을 만들고, 그 뒤 [연동] 이 그걸 읽어서 변경을 감지한다.
 *
 * 글자 입력 없이도 끝까지 갈 수 있게 제목을 칩으로 고르면 장소·날짜·시간까지 함께 채워진다.
 */

private val GBlue = Color(0xFF1A73E8)
private val GText = Color(0xFF202124)
private val GSub = Color(0xFF5F6368)
private val GLine = Color(0xFFDADCE0)

@Composable
fun GoogleCalendarEditor(
    today: LocalDate,
    initial: LocalDate,
    account: String?,
    onClose: () -> Unit,
    onSave: (CalEvent) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var place by remember { mutableStateOf<Place?>(null) }
    var date by remember { mutableStateOf(initial) }
    var startMin by remember { mutableIntStateOf(13 * 60) }
    var durMin by remember { mutableIntStateOf(180) }
    var allDay by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), Alignment.Center) {
                Glyph(GText) { u -> close(u) }
            }
            Spacer(Modifier.width(4.dp))
            Text("일정", color = GText, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            Text(
                "저장", color = if (saving) GSub else GBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = !saving) {
                    saving = true
                    val end = (startMin + durMin).coerceAtMost(23 * 60 + 59)
                    val p = place ?: DemoPlaces.Pangyo
                    onSave(
                        CalEvent(
                            0L, date, title.ifBlank { "${p.short} 일정" }, p,
                            LocalTime.of(startMin / 60, startMin % 60),
                            LocalTime.of(end / 60, end % 60),
                        )
                    )
                }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Text(
            title.ifBlank { "제목 추가" },
            color = if (title.isBlank()) GSub else GText,
            fontSize = 24.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().padding(start = 68.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
        )
        if (title.isBlank()) {
            LazyRow(
                Modifier.padding(bottom = 14.dp),
                contentPadding = PaddingValues(start = 68.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LrrrAgent.templates(today)) { t ->
                    Text(
                        t.title, color = GBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                            .border(1.dp, GLine, RoundedCornerShape(18.dp))
                            .clickable {
                                title = t.title
                                place = t.place
                                date = today.plusDays(t.dayOffset)
                                startMin = t.startMin
                                if (t.durMin > 0) durMin = t.durMin
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = GLine)

        Field({ Glyph(GSub) { u -> clock(u) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("하루 종일", color = GText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(allDay, { allDay = it }, colors = SwitchDefaults.colors(checkedTrackColor = GBlue))
            }
            Spacer(Modifier.height(6.dp))
            TimeRow(dayText(date), timeText(startMin), !allDay,
                onDate = { date = date.plusDays(1) }, onTime = { startMin = nextStart(startMin) })
            Spacer(Modifier.height(4.dp))
            TimeRow(dayText(date), timeText(startMin + durMin), !allDay,
                onDate = { date = date.plusDays(1) }, onTime = { durMin = if (durMin >= 300) 60 else durMin + 60 })
        }
        HorizontalDivider(color = GLine)

        Field({ Glyph(GSub) { u -> pin(u) } }) {
            Text(place?.name ?: "위치 추가", color = if (place == null) GSub else GText, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DemoPlaces.all) { p ->
                    val on = p == place
                    Text(
                        p.short, color = if (on) Color.White else GSub, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) GBlue else Color.White)
                            .border(1.dp, if (on) GBlue else GLine, RoundedCornerShape(16.dp))
                            .clickable { place = p }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = GLine)

        Field({ Glyph(GSub) { u -> bell(u) } }) { Text("30분 전", color = GText, fontSize = 16.sp) }
        HorizontalDivider(color = GLine)

        Field({ Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(GBlue)) }) {
            Text(account ?: "내 캘린더", color = GText, fontSize = 16.sp)
            Text("구글 캘린더", color = GSub, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Field(icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)) {
        Box(Modifier.width(48.dp).padding(top = 2.dp)) { icon() }
        Column { content() }
    }
}

@Composable
private fun TimeRow(day: String, time: String, enabled: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            day, color = if (enabled) GText else GSub, fontSize = 16.sp,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onDate).padding(vertical = 6.dp),
        )
        Text(
            time, color = if (enabled) GText else GSub, fontSize = 16.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onTime).padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

private fun nextStart(min: Int) = if (min >= 21 * 60) 7 * 60 else min + 30

private fun dayText(d: LocalDate): String =
    "%d년 %d월 %d일 (%s)".format(
        d.year, d.monthValue, d.dayOfMonth, d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
    )

private fun timeText(min: Int): String {
    val m = min.coerceAtMost(23 * 60 + 59)
    val h = m / 60
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$ampm $h12:%02d".format(m % 60)
}

// ───────── 이 화면에서만 쓰는 선 아이콘 ─────────

@Composable
private fun Glyph(color: Color, draw: DrawScope.(Float) -> Unit) {
    Canvas(Modifier.size(24.dp)) { draw(size.minDimension / 24f) }
}

private fun DrawScope.close(u: Float) {
    drawLine(Color(0xFF202124), Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), 1.8f * u, StrokeCap.Round)
    drawLine(Color(0xFF202124), Offset(18f * u, 6f * u), Offset(6f * u, 18f * u), 1.8f * u, StrokeCap.Round)
}

private fun DrawScope.clock(u: Float) {
    val c = GSub
    drawCircle(c, 8.5f * u, Offset(12f * u, 12f * u), style = Stroke(1.7f * u))
    drawLine(c, Offset(12f * u, 12f * u), Offset(12f * u, 7.5f * u), 1.7f * u, StrokeCap.Round)
    drawLine(c, Offset(12f * u, 12f * u), Offset(15.5f * u, 13.5f * u), 1.7f * u, StrokeCap.Round)
}

private fun DrawScope.pin(u: Float) {
    val path = Path().apply {
        moveTo(12f * u, 21f * u)
        cubicTo(12f * u, 21f * u, 4.5f * u, 14f * u, 4.5f * u, 9.5f * u)
        cubicTo(4.5f * u, 5.4f * u, 7.9f * u, 2.5f * u, 12f * u, 2.5f * u)
        cubicTo(16.1f * u, 2.5f * u, 19.5f * u, 5.4f * u, 19.5f * u, 9.5f * u)
        cubicTo(19.5f * u, 14f * u, 12f * u, 21f * u, 12f * u, 21f * u)
        close()
    }
    drawPath(path, GSub, style = Stroke(1.7f * u, join = StrokeJoin.Round))
    drawCircle(GSub, 2.6f * u, Offset(12f * u, 9.3f * u), style = Stroke(1.7f * u))
}

private fun DrawScope.bell(u: Float) {
    val path = Path().apply {
        moveTo(5.5f * u, 17f * u)
        lineTo(18.5f * u, 17f * u)
        lineTo(17f * u, 14.5f * u)
        lineTo(17f * u, 10.5f * u)
        cubicTo(17f * u, 7f * u, 14.8f * u, 5f * u, 12f * u, 5f * u)
        cubicTo(9.2f * u, 5f * u, 7f * u, 7f * u, 7f * u, 10.5f * u)
        lineTo(7f * u, 14.5f * u)
        close()
    }
    drawPath(path, GSub, style = Stroke(1.7f * u, join = StrokeJoin.Round))
    drawLine(GSub, Offset(10f * u, 19.5f * u), Offset(14f * u, 19.5f * u), 1.7f * u, StrokeCap.Round)
}
