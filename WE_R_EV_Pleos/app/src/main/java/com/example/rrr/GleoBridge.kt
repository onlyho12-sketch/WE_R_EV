package com.example.rrr

import android.content.Intent

/**
 * Gleo AI 연동 지점.
 *
 * Pleos의 Gleo AI는 서드파티 앱이 등록한 App Schema(assets/gleo_app_schema.json)를 보고
 * "Gleo Action"으로 앱 기능을 호출한다. 르르링은 출발지/도착지 설정 기능을 아래 인텐트로 노출한다.
 *
 *   action  : com.example.rrr.action.SET_TRIP
 *   extras  : origin / destination (장소 이름) 또는 utterance (사용자 발화 원문)
 *
 * 테스트(Gleo 없이 동일한 호출 재현):
 *   adb shell am start -n com.example.rrr/.MainActivity -a com.example.rrr.action.SET_TRIP \
 *       --es utterance "강남에서 스타필드 하남 가자"
 *
 * TODO: Pleos 개발자 콘솔에서 Gleo AI SDK를 받으면 App Schema 등록 + Callback 응답 연결.
 */
object GleoBridge {
    const val ACTION_SET_TRIP = "com.example.rrr.action.SET_TRIP"
    const val EXTRA_ORIGIN = "origin"
    const val EXTRA_DESTINATION = "destination"
    const val EXTRA_UTTERANCE = "utterance"

    fun fromIntent(intent: Intent?): TripCommand? {
        if (intent?.action != ACTION_SET_TRIP) return null
        intent.getStringExtra(EXTRA_UTTERANCE)?.takeIf { it.isNotBlank() }?.let { return parseTripCommand(it) }
        val origin = intent.getStringExtra(EXTRA_ORIGIN)?.let { searchPlaces(it).firstOrNull() }
        val dest = intent.getStringExtra(EXTRA_DESTINATION)?.let { searchPlaces(it).firstOrNull() }
        return TripCommand(origin, dest).takeIf { origin != null || dest != null }
    }
}
