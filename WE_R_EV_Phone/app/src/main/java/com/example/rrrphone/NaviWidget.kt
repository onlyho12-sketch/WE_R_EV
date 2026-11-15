package com.example.rrrphone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 주행 중 차량 내비에 뜨는 르르르 위젯 미리보기.
 *
 * 폰에서 세운 전략을 Pleos 로 보내면 "주행 중엔 내비 위젯이 한 줄로 알려준다"가 마지막 단계인데,
 * 말로만 하면 와닿지 않아서 실제 위젯 그림(RrrWidgetAnimation — 차량 앱과 같은 애니메이션)을 그대로 보여준다.
 */

private val NaviBg = Color(0xFF12223D)
private val NaviRoad = Color(0xFF2B3E60)
private val NaviRoute = Color(0xFF4F8DF7)
private val WidgetBg = Color(0xFFF7F5F2)

@Composable
fun NaviWidgetCard(s: DayStrategy, modifier: Modifier = Modifier) {
    RCard(modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pleos Connect · 주행 중 내비 위젯", color = Faint, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Tag("연동됨", EvGreen, filled = true)
        }
        Spacer(Modifier.height(10.dp))
        NaviPreview(s)
        Spacer(Modifier.height(8.dp))
        Text("결정적인 순간에만 한마디 해요. 그 외엔 조용히 주행 화면만 보여드릴게요.",
            color = Sub, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** 내비 화면(목업) 위에 얹힌 르르르 위젯 */
@Composable
private fun NaviPreview(s: DayStrategy) {
    Box(
        Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(12.dp)).background(NaviBg),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 교차하는 도로 몇 개 + 파란 경로선 — 내비처럼 보이기만 하면 되는 배경
            val w = size.width; val h = size.height
            listOf(0.22f to 0.78f, 0.55f to 0.18f).forEach { (y0, y1) ->
                drawLine(NaviRoad, Offset(0f, h * y0), Offset(w, h * y1), 9f)
            }
            listOf(0.18f, 0.62f, 0.88f).forEach { x ->
                drawLine(NaviRoad, Offset(w * x, 0f), Offset(w * (x - 0.06f), h), 7f)
            }
            val route = Path().apply {
                moveTo(w * 0.10f, h * 1.02f)
                cubicTo(w * 0.26f, h * 0.74f, w * 0.30f, h * 0.60f, w * 0.52f, h * 0.44f)
                cubicTo(w * 0.70f, h * 0.31f, w * 0.74f, h * 0.22f, w * 0.92f, h * 0.06f)
            }
            drawPath(route, NaviRoute, style = Stroke(11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // 다음 안내 (좌상단)
        Row(
            Modifier.padding(10.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↱", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Column {
                Text("400m 후 우회전", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(s.legs.firstOrNull()?.to?.name ?: "목적지", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }

        // 르르르 위젯 (우하단) — 차량 앱 위젯과 같은 애니메이션
        Row(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(14.dp))
                .background(WidgetBg).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RrrWidgetAnimation(
                mode = if (s.heroEnergy == Energy.Electric) "ev" else "gen",
                modifier = Modifier.size(width = 104.dp, height = 91.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("르르르", color = Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(LrrrAgent.naviWidgetLine(s), color = Ink, fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
