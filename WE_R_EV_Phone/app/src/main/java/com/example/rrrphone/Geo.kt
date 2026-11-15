package com.example.rrrphone

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** rrr_pleos/TripMap.kt 와 같은 좌표 타입 (폰 앱은 지도 없이 거리 계산에만 사용) */
data class LatLng(val lat: Double, val lon: Double)

fun haversineKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** 도로 거리 근사 (직선 × 1.3). 실서비스는 지도 경로 API */
fun roadKm(a: Place, b: Place): Double = haversineKm(a.at, b.at) * 1.3
