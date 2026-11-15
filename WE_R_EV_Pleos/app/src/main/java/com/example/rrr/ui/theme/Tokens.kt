package com.example.rrr.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 현대 웹/마이현대 톤 디자인 토큰.
 *  - 거의 흰색에 가까운 웜 그레이 배경, 검정 타이포, 얇은 라인
 *  - 포인트 컬러는 네이비 하나. 그 외 데이터 구분은 무채색 계열로만.
 */
object Hd {
    val Bg = Color(0xFFF5F4F2)          // 페이지 배경 (웜 그레이)
    val Surface = Color(0xFFFFFFFF)     // 카드
    val SurfaceAlt = Color(0xFFF0EEEB)  // 카드 안 보조 영역 / 트랙
    val Ink = Color(0xFF111111)
    val Sub = Color(0xFF555452)
    val Faint = Color(0xFF9A9895)
    val Line = Color(0xFFE3E1DE)

    val Navy = Color(0xFF002C5F)        // 유일한 포인트 컬러
    val NavySoft = Color(0xFFE9EDF2)    // 네이비 선택 상태 배경

    val Ev = Navy                       // 전기 = 네이비
    val Gas = Color(0xFF8A8178)         // 연료 = 웜 그레이 (포인트 컬러 아님)
    val Muted = Color(0xFFBDB8B2)       // 3번째 데이터 계열
}

val HdColorScheme = lightColorScheme(
    primary = Hd.Navy,
    onPrimary = Color.White,
    secondary = Hd.Navy,
    background = Hd.Bg,
    surface = Hd.Surface,
    onSurface = Hd.Ink,
    surfaceContainerHigh = Hd.Surface,
    outline = Hd.Line,
)
