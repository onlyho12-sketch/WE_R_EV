package com.example.rrr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.example.rrr.R

/** Pretendard (SIL Open Font License 1.1) — res/font */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

/** 한글은 기본 자간(+0.5sp)이면 성글어 보여서 Pretendard 권장대로 살짝 좁힘 */
private val Tracking = (-0.02).em

private fun TextStyle.pretendard() = copy(fontFamily = Pretendard, letterSpacing = Tracking)

/** 앱 전체 기본 글꼴 (MaterialTheme → LocalTextStyle 로 모든 Text 에 적용) */
val AppTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.pretendard(), displayMedium = displayMedium.pretendard(), displaySmall = displaySmall.pretendard(),
        headlineLarge = headlineLarge.pretendard(), headlineMedium = headlineMedium.pretendard(), headlineSmall = headlineSmall.pretendard(),
        titleLarge = titleLarge.pretendard(), titleMedium = titleMedium.pretendard(), titleSmall = titleSmall.pretendard(),
        bodyLarge = bodyLarge.pretendard(), bodyMedium = bodyMedium.pretendard(), bodySmall = bodySmall.pretendard(),
        labelLarge = labelLarge.pretendard(), labelMedium = labelMedium.pretendard(), labelSmall = labelSmall.pretendard(),
    )
}

/** 직접 TextStyle 을 만드는 곳(입력창·지도 라벨·말풍선)에서 쓰는 기본 스타일 */
val AppText = TextStyle(fontFamily = Pretendard, letterSpacing = Tracking)

// 기존 템플릿 이름 유지
val Typography = AppTypography
