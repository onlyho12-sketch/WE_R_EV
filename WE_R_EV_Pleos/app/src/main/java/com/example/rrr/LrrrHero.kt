package com.example.rrr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BubbleBg = Color(0xFFF0EEEB)
private val BubbleText = Color(0xFF333231)

// 한국어 어절 단위 줄바꿈 ("그 / 냥"처럼 단어 중간에서 끊기지 않게)
private val bubbleText = com.example.rrr.ui.theme.AppText.copy(
    fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, lineBreak = LineBreak.Heading,
)

/**
 * 차량 히어로: 3/4 정면으로 서 있는 싸타페 + 옆에서 말하는 르르르 + 만화 말풍선.
 * message 가 바뀔 때마다 말풍선이 "뿅" 나오며 글자가 타자 치듯 나오고,
 * 르르르는 그 길이만큼 입을 뻐끗거리며 차 쪽으로 손을 흔든다 (RrrMoment.Talk).
 */
@Composable
fun CarHeroScene(message: String, energy: Energy, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(240.dp)) {
        CarHero(Modifier.fillMaxWidth().height(155.dp).align(Alignment.BottomStart).padding(end = 128.dp, bottom = 4.dp))
        SpeechBubble(message, Modifier.align(Alignment.TopStart).padding(end = 84.dp))
        RrrMomentAnimation(
            moment = RrrMoment.Talk,
            energy = energy,
            key = message,
            talkMs = 300 + message.length * 28, // 말풍선 타이핑과 같은 길이
            modifier = Modifier.align(Alignment.BottomEnd).size(width = 140.dp, height = 144.dp),
        )
    }
}

@Composable
private fun SpeechBubble(message: String, modifier: Modifier) {
    val pop = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(message) {
        shown = 0
        pop.snapTo(0.92f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        while (shown < message.length) { delay(28); shown++ }
    }

    Column(
        modifier.graphicsLayer {
            scaleX = pop.value; scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(1f, 1f) // 꼬리(르르르 쪽)에서 튀어나오게
        }
    ) {
        Box(
            Modifier.background(BubbleBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            // 다 나왔을 때 크기로 자리를 먼저 잡아서 타이핑 중에 말풍선이 출렁이지 않게
            Text(message, style = bubbleText, color = Color.Transparent)
            Text(message.take(shown), style = bubbleText, color = BubbleText)
        }
        // 만화 말풍선 꼬리 (오른쪽 아래 → 르르르 머리)
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val baseL = size.width - 40.dp.toPx()
            val baseR = size.width - 20.dp.toPx()
            val tip = Offset(size.width + 22.dp.toPx(), size.height)
            val tail = Path().apply {
                moveTo(baseL, -2.dp.toPx()); lineTo(tip.x, tip.y); lineTo(baseR, -2.dp.toPx()); close()
            }
            drawPath(tail, BubbleBg)
        }
    }
}
