package com.example.rrr

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 앱 공통 팔레트(네이비)에 맞춤 — WE'R Score · Rewards 와 같은 색
private val SInk = com.example.rrr.ui.theme.Hd.Ink
private val SSub = com.example.rrr.ui.theme.Hd.Sub
private val SFaint = com.example.rrr.ui.theme.Hd.Faint
private val SLine = com.example.rrr.ui.theme.Hd.Line
private val SField = com.example.rrr.ui.theme.Hd.SurfaceAlt
private val SBlue = com.example.rrr.ui.theme.Hd.Navy
private val SNavySoft = com.example.rrr.ui.theme.Hd.NavySoft
private val SEv = Color(0xFF12A37A)
private val SGas = Color(0xFFE58A00)

// ───────────────────────── 설정 모델 ─────────────────────────

/** 차량 화면 WE'R EV 위젯에 띄울 애니메이션 (칩 문구·아이콘은 WereEV_PleosConnect.html 최신 위젯 기준) */
enum class WidgetAnim(val key: String, val title: String, val desc: String, val chip: String, val icon: Ic) {
    Auto("auto", "자동 전환", "주행 상태에 맞춰 EV ↔ 발전 애니메이션이 알아서 바뀌어요", "EV ↔ 발전", Ic.Swap),
    Gen("gen", "발전 애니메이션", "발전기를 힘차게 돌려요 · 연료로 배터리 충전", "발전 중", Ic.Bolt),
    Ev("ev", "EV 애니메이션", "배터리 전기로 조용히 달려요", "EV 주행", Ic.Battery),
}

/** 움직임 강도 (운전 중 시선 분산 고려) */
enum class WidgetMotion(val key: String, val title: String, val desc: String) {
    Normal("normal", "기본", "원래 속도로 움직여요"),
    Calm("calm", "차분하게", "절반 속도로 천천히 움직여요"),
    Off("off", "멈춤", "움직이지 않는 그림으로 보여줘요"),
}

data class WidgetConfig(
    val anim: WidgetAnim = WidgetAnim.Auto,
    val motion: WidgetMotion = WidgetMotion.Normal,
    val toast: Boolean = true,
)

/** 르르링 → Pleos Connect(차량 화면) 위젯 설정 연동 */
object WidgetLink {
    const val PLEOS_PACKAGE = "com.example.pleosconnect"
    private const val ACTION = "com.example.pleosconnect.action.WIDGET_SETTINGS"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): WidgetConfig {
        val p = prefs(ctx)
        return WidgetConfig(
            anim = WidgetAnim.entries.firstOrNull { it.key == p.getString("anim", null) } ?: WidgetAnim.Auto,
            motion = WidgetMotion.entries.firstOrNull { it.key == p.getString("motion", null) } ?: WidgetMotion.Normal,
            toast = p.getBoolean("toast", true),
        )
    }

    /** 저장 + 차량 화면 앱에 명시적 broadcast */
    fun apply(ctx: Context, c: WidgetConfig) {
        prefs(ctx).edit()
            .putString("anim", c.anim.key).putString("motion", c.motion.key).putBoolean("toast", c.toast)
            .apply()
        ctx.sendBroadcast(
            Intent(ACTION).setPackage(PLEOS_PACKAGE)
                .putExtra("anim", c.anim.key)
                .putExtra("motion", c.motion.key)
                .putExtra("toast", c.toast)
        )
    }

    fun launchIntent(ctx: Context): Intent? = ctx.packageManager.getLaunchIntentForPackage(PLEOS_PACKAGE)
}

// ───────────────────────── 화면 ─────────────────────────

/** ④ 위젯 설정: 차량 화면(Pleos Connect) 르르르 위젯에 어떤 애니메이션을 띄울지 */
@Composable
fun WidgetSettingsPage(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var config by remember { mutableStateOf(WidgetLink.load(ctx)) }
    val pleosLaunch = remember { WidgetLink.launchIntent(ctx) }
    fun update(c: WidgetConfig) { config = c; WidgetLink.apply(ctx, c) }

    // WE'R Score · Rewards 와 같은 방식: 화면 크기에 맞춰 통째로 키우고, 남는 높이는 카드가 나눠 채운다 (스크롤·공백 없음)
    FitCanvas(designW = 1180f, minDesignH = 610f, modifier = modifier) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    menuBrandTitle("WE'R Widget"), fontSize = 36.sp, fontWeight = FontWeight.Bold,
                    color = SInk, letterSpacing = (-0.8).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("차량 화면(Pleos Connect)의 WE'R EV 위젯이 어떻게 움직일지 골라요",
                    color = SSub, fontSize = 16.sp)
            }
            if (pleosLaunch != null) {
                Text(
                    "차량 화면에서 보기 ›", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SBlue)
                        .clickable { ctx.startActivity(pleosLaunch) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // 1. 애니메이션 선택
        SettingsCard(Modifier.fillMaxWidth().weight(2.2f)) {
            SectionTitle("애니메이션", "선택하면 차량 화면에 바로 적용돼요")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WidgetAnim.entries.forEach { a ->
                    AnimOption(
                        anim = a, motion = config.motion, selected = config.anim == a,
                        onClick = { update(config.copy(anim = a)) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 2. 움직임
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("움직임", "운전 중 시선이 덜 가도록 줄일 수 있어요 · ${config.motion.desc}")
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SField).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    WidgetMotion.entries.forEach { m ->
                        val on = m == config.motion
                        Text(
                            m.title, color = if (on) Color.White else SSub, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (on) SBlue else Color.Transparent)
                                .clickable { update(config.copy(motion = m)) }
                                .padding(vertical = 11.dp)
                        )
                    }
                }
            }
            // 3. 알림
            SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("모드 전환 알림", "배터리 20%에서 발전 모드로 바뀌거나 EV로 돌아올 때 지도 위에 알려줘요")
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = config.toast, onCheckedChange = { update(config.copy(toast = it)) },
                        colors = SwitchDefaults.colors(checkedTrackColor = SBlue),
                        modifier = Modifier.padding(end = 8.dp).scale(1.25f)
                    )
                }
            }
        }

        // 연결된 상태면 따로 안내하지 않음 (시연용 문구 제거)
        if (pleosLaunch == null) {
            Text(
                "차량 화면과 연결되어 있지 않아요 · 설정은 저장되고 연결되면 적용돼요",
                color = SFaint, fontSize = 13.sp
            )
        }
    }
    }
}

@Composable
private fun AnimOption(anim: WidgetAnim, motion: WidgetMotion, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = SBlue
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) SBlue else SLine, RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            // 미리보기 (르르르 위젯 애니메이션 그대로) — 남는 높이를 모두 쓴다
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(Color(0xFFF6F7FA), RoundedCornerShape(12.dp))
            ) {
                RrrWidgetAnimation(
                    mode = if (anim == WidgetAnim.Gen) "gen" else "ev",
                    cycle = anim == WidgetAnim.Auto, motion = motion,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .border(2.dp, if (selected) SBlue else SLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(SBlue)) }
                Spacer(Modifier.width(8.dp))
                Text(anim.title, color = SInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (anim == WidgetAnim.Auto) {
                    Text("추천", color = SBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.border(1.dp, SBlue.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(anim.desc, color = SSub, fontSize = 15.sp, lineHeight = 20.sp, minLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineIcon(anim.icon, accent, size = 15.dp)
                Spacer(Modifier.width(5.dp))
                Text(anim.chip, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.shadow(2.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .background(Color.White).border(1.dp, SLine, RoundedCornerShape(12.dp)).padding(20.dp)
    ) { content() }
}

@Composable
private fun SectionTitle(title: String, sub: String) {
    Text(title, color = SInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(3.dp))
    Text(sub, color = SFaint, fontSize = 15.sp, lineHeight = 20.sp)
}
