package com.example.rrrphone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrrphone.ui.theme.AppText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ───────────── 팔레트 (차량 앱 ui/theme/Tokens.kt 의 Hd 와 동일) ─────────────
//  거의 흰색에 가까운 웜 그레이 배경 · 검정 타이포 · 얇은 라인, 포인트 컨러는 네이비 하나.
//  전기 = 네이비, 연료 = 웜 그레이 (초록·주황·파랑 강조색은 쓰지 않음)

val Bg = Color(0xFFF5F4F2)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF111111)
val Sub = Color(0xFF555452)
val Faint = Color(0xFF9A9895)
val Line = Color(0xFFE3E1DE)
val Field = Color(0xFFF0EEEB)
val Navy = Color(0xFF002C5F)
val NavySoft = Color(0xFFE9EDF2)
val Muted = Color(0xFFBDB8B2)
/** 예전 이름 유지 — 모두 네이비 / 웜 그레이로 통일 */
val Blue = Navy
val EvGreen = Navy
val GasOrange = Color(0xFF8A8178)

val Tone.color: Color get() = when (this) { Tone.Ev -> EvGreen; Tone.Gas -> GasOrange; Tone.Neutral -> Sub }

// ───────────── 공통 ─────────────

@Composable
fun RCard(modifier: Modifier = Modifier, padding: Dp = 13.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text("$action ›", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(4.dp))
        }
    }
}

@Composable
fun Tag(text: String, color: Color, filled: Boolean = false) {
    Text(
        text, color = if (filled) Surface else color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (filled) color else color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun InfraTags(p: Place) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (p.home != null) Tag("집충전", EvGreen)
        if (p.slow != null) Tag("완속", EvGreen)
        if (p.fast != null) Tag("급속", GasOrange)
        if (p.home == null && p.slow == null && p.fast == null) Tag(if (p.aliases.isEmpty()) "인프라 정보 없음" else "충전소 없음", Faint)
    }
}

@Composable
fun Pill(text: String, active: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (active) Surface else Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (active) Navy else Surface)
            .border(1.dp, if (active) Navy else Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun PleosPill(synced: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (synced) NavySoft else Field)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (synced) Navy else Muted))
        Spacer(Modifier.width(6.dp))
        Text(if (synced) "Pleos 반영됨" else "Pleos 연결됨", color = if (synced) Navy else Sub,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 전기(초록)/발전(주황) 비율 막대 */
@Composable
fun EnergyBar(ev: Double, gas: Double, modifier: Modifier = Modifier) {
    Row(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Field)) {
        if (ev > 0.05) Box(Modifier.weight(ev.toFloat()).fillMaxHeight().background(EvGreen))
        if (gas > 0.05) Box(Modifier.weight(gas.toFloat()).fillMaxHeight().background(GasOrange))
    }
}

// ───────────── 일정 + 이동 타임라인 ─────────────

@Composable
fun DayItems(s: DayStrategy) {
    Column {
        s.items.forEachIndexed { i, item ->
            when (item) {
                is PlanItem.Stop -> StopRow(item)
                is PlanItem.Drive -> DriveRow(item.leg)
            }
            if (i < s.items.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun StopRow(st: PlanItem.Stop) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(st.time, color = Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp,
            modifier = Modifier.width(78.dp).padding(top = 2.dp))
        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape)
            .background(if (st.isNew) Blue else Ink))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (st.isNew) { Spacer(Modifier.width(6.dp)); Tag("새 일정", Blue, filled = true) }
            }
            Text(st.place.name, color = Faint, fontSize = 13.sp)
            st.note?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, color = st.tone.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DriveRow(l: Leg) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(hm(l.depart), color = Faint, fontSize = 12.sp, modifier = Modifier.width(78.dp).padding(top = 6.dp))
        Box(Modifier.width(10.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(Line))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Field)
            .padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("${l.distKm.roundToInt()}km · 약 ${((l.arrive - l.depart) * 60).roundToInt()}분",
                color = Sub, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            EnergyBar(l.evKm, l.gasKm, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Row {
                if (l.evKm >= 0.5) Text("전기 ${l.evKm.roundToInt()}km", color = EvGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (l.evKm >= 0.5 && l.gasKm >= 0.5) Text("  ·  ", color = Faint, fontSize = 12.sp)
                if (l.gasKm >= 0.5) Text("발전 ${l.gasKm.roundToInt()}km", color = GasOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("배터리 ${l.socStart.roundToInt()}→${l.socEnd.roundToInt()}%", color = Faint, fontSize = 12.sp)
            }
        }
    }
}

// ───────────── 전략 카드 ─────────────

@Composable
fun StrategyCard(state: AppState, s: DayStrategy, showChart: Boolean = true, showItems: Boolean = false) {
    val synced = state.isSynced(s.date) && s.kmPerKwh == PLEOS_KM_PER_KWH
    RCard {
        Text(
            if (synced) "최종 전략 · Pleos 주행 데이터 반영" else "1차 전략 · 캘린더 + 충전 인프라",
            color = if (synced) EvGreen else Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(LrrrAgent.headline(s), color = Ink, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(LrrrAgent.benefit(s), color = EvGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("${dateLabel(s.date, state.today)} ${s.totalKm.roundToInt()}km · 전기 ${s.evKm.roundToInt()}km / 발전 ${s.gasKm.roundToInt()}km",
            color = Faint, fontSize = 12.sp)
        if (showItems) {
            Spacer(Modifier.height(8.dp)); DayItems(s)
        }
        if (showChart && s.legs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("에너지 공급 계획", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            SupplyTimelineChart(s.timeline)
            Spacer(Modifier.height(9.dp))
            GasStrategyCard(s.gas)
        }
        val actions = LrrrAgent.actions(s)
        if (actions.isNotEmpty()) Spacer(Modifier.height(9.dp))
        actions.forEach { a ->
            val done = a.key in state.done
            Text(
                if (done) a.done else a.label,
                color = if (done) EvGreen else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (done) NavySoft else Surface)
                    .border(1.dp, if (done) Color.Transparent else Line, RoundedCornerShape(10.dp))
                    .clickable { if (!done) { state.done += a.key; state.showToast(a.done) } }
                    .padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        PleosButton(state, s)
    }
}

@Composable
fun PleosButton(state: AppState, s: DayStrategy) {
    val synced = state.isSynced(s.date)
    Button(
        onClick = { if (!synced) state.sendToPleos(s.date) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (synced) NavySoft else Navy,
            contentColor = if (synced) Navy else Surface,
        ),
    ) {
        Text(if (synced) "✓ 차량(Pleos)에 최종 전략 반영됨" else "Pleos로 보내기 · 주행 데이터로 확정",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────── 르르르 캐릭터 ─────────────
//  그림은 RrrMoments.kt — 차량 앱과 같은 Canvas 벡터 캐릭터.
//  (예전엔 lrrr_clean.png 한 장을 통통 튀게 하고 눈만 덮어 깜빡였는데, 이제 입·팔·더듬이가 다 움직인다)

/** 말풍선 타이핑 속도(24ms/글자)에 입 모양을 맞추기 위한 길이 */
fun talkMsFor(message: String) = 300 + message.length * 24

/** 히어로 아이콘(⚡/⛽) — 그날 주행 거리의 다수를 차지하는 에너지 */
val DayStrategy.heroEnergy: Energy get() = if (gasKm > evKm) Energy.Gas else Energy.Electric

@Composable
fun LrrrAvatar(size: Dp) = RrrFaceAvatar(size)

/** 홈 화면 히어로: 말풍선 + 말하는 르르르 (차량 앱 CarHeroScene 과 같은 동작) */
@Composable
fun LrrrHero(message: String, energy: Energy = Energy.Electric, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SpeechBubble(message, Modifier.weight(1f).padding(bottom = 26.dp, end = 2.dp))
        BouncyLrrr(greetKey = message, width = 120.dp, energy = energy, talkMs = talkMsFor(message))
    }
}

/**
 * 말풍선 옆에서 말하는 르르르. greetKey 가 바뀌면 처음부터 다시 — 말풍선 글자가 찍히는 동안
 * 입을 뻥끗거리고 손을 흔든다. (애니메이션은 Canvas 안에서만 돌아 재구성이 일어나지 않음)
 */
@Composable
fun BouncyLrrr(
    greetKey: Any,
    width: Dp,
    modifier: Modifier = Modifier,
    energy: Energy = Energy.Electric,
    talkMs: Int = 1200,
) {
    RrrMomentAnimation(
        moment = RrrMoment.Talk, energy = energy, key = greetKey, talkMs = talkMs,
        modifier = modifier.size(width = width, height = width / RrrMoment.Talk.aspect),
    )
}

/** 차량 앱 말풍선과 같은 톤: 연한 웜 그레이 배경 + 테두리 없음 */
private val BubbleBg = Field
private val BubbleText = Color(0xFF333231)
private val bubbleText = AppText.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, lineBreak = LineBreak.Heading)

@Composable
fun SpeechBubble(message: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    var shown by remember { mutableIntStateOf(if (animate) 0 else message.length) }
    LaunchedEffect(message) {
        if (!animate) { shown = message.length; return@LaunchedEffect }
        shown = 0
        pop.snapTo(0.4f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        while (shown < message.length) { delay(24); shown++ }
    }
    Column(
        modifier.graphicsLayer {
            scaleX = pop.value; scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(1f, 1f)
        }
    ) {
        Box(
            Modifier.background(BubbleBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(message, style = bubbleText, color = Color.Transparent)
            Text(message.take(shown), style = bubbleText, color = BubbleText)
        }
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val baseL = size.width - 40.dp.toPx()
            val baseR = size.width - 20.dp.toPx()
            val tip = Offset(size.width + 16.dp.toPx(), size.height)
            val tail = Path().apply { moveTo(baseL, -2.dp.toPx()); lineTo(tip.x, tip.y); lineTo(baseR, -2.dp.toPx()); close() }
            drawPath(tail, BubbleBg)
        }
    }
}
