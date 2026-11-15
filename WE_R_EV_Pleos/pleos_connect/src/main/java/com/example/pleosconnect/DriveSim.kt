package com.example.pleosconnect

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * 100m 격자 도로 위 순환 경로 (모서리는 둥글게) + 영상 37.5초 주행 시나리오.
 * 좌표는 m 단위, y 가 작을수록 북쪽.
 */
object DriveRoute {
    private val V = listOf(0f to 0f, 0f to -600f, 500f to -600f, 500f to -1100f, 1100f to -1100f, 1100f to 0f)

    /** V[i] → V[i+1] 구간의 도로 이름 / 지역 */
    val SEG = listOf(
        "양재대로" to "서초구 양재대로", "남부순환로" to "강남구 남부순환로", "수서IC, 일원IC" to "강남구 수서동",
        "광평로" to "강남구 일원동", "밤고개로" to "강남구 자곡동", "헌릉로" to "서초구 내곡동",
    )

    private const val R = 26f
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val cum = ArrayList<Float>()
    private val turnS = FloatArray(V.size)
    var length = 0f
        private set

    init {
        val n = V.size
        val cs = ArrayList<Pair<Float, Float>>()
        val ce = ArrayList<Pair<Float, Float>>()
        for (i in 0 until n) {
            val prev = V[(i - 1 + n) % n]; val cur = V[i]; val next = V[(i + 1) % n]
            val din = norm(cur.first - prev.first, cur.second - prev.second)
            val dout = norm(next.first - cur.first, next.second - cur.second)
            cs += (cur.first - din.first * R) to (cur.second - din.second * R)
            ce += (cur.first + dout.first * R) to (cur.second + dout.second * R)
        }
        for (k in 0 until n) {
            val i1 = (k + 1) % n
            val a = ce[k]; val b = cs[i1]
            val steps = ceil(hypot(b.first - a.first, b.second - a.second) / 4f).toInt()
            for (s in 0 until steps) push(a.first + (b.first - a.first) * s / steps, a.second + (b.second - a.second) * s / steps)
            val c = V[i1]; val e = ce[i1]
            for (j in 0 until 12) {
                val t = j / 12f; val u = 1 - t
                push(u * u * b.first + 2 * u * t * c.first + t * t * e.first, u * u * b.second + 2 * u * t * c.second + t * t * e.second)
                if (j == 6) turnS[i1] = cum.last()
            }
        }
        push(ce[0].first, ce[0].second)
    }

    private fun norm(x: Float, y: Float): Pair<Float, Float> { val l = hypot(x, y); return (x / l) to (y / l) }

    private fun push(x: Float, y: Float) {
        if (xs.isNotEmpty()) length += hypot(x - xs.last(), y - ys.last())
        xs += x; ys += y; cum += length
    }

    data class Loc(val x: Float, val y: Float, val heading: Float)

    /** heading: 북쪽 0, 시계방향 (라디안) */
    fun locate(sIn: Float): Loc {
        val s = ((sIn % length) + length) % length
        var lo = 0; var hi = cum.size - 1
        while (lo < hi - 1) { val mid = (lo + hi) ushr 1; if (cum[mid] <= s) lo = mid else hi = mid }
        val f = (s - cum[lo]) / maxOf(1e-6f, cum[hi] - cum[lo])
        return Loc(
            xs[lo] + (xs[hi] - xs[lo]) * f, ys[lo] + (ys[hi] - ys[lo]) * f,
            atan2(xs[hi] - xs[lo], -(ys[hi] - ys[lo])),
        )
    }

    /** 1 = 우회전, -1 = 좌회전 */
    fun turnDir(i: Int): Int {
        val n = V.size
        val p = V[(i - 1 + n) % n]; val c = V[i]; val q = V[(i + 1) % n]
        val cross = (c.first - p.first) * (q.second - c.second) - (c.second - p.second) * (q.first - c.first)
        return if (cross > 0) 1 else -1
    }

    /** 다음 회전 두 개: (꼭짓점 index, 남은 거리 m) */
    fun upcoming(sIn: Float): List<Pair<Int, Float>> {
        val s = ((sIn % length) + length) % length
        val list = ArrayList<Pair<Int, Float>>()
        for (lap in 0..1) for (i in V.indices) {
            val ts = turnS[i] + lap * length
            if (ts > s + 1) list += i to (ts - s)
        }
        return list.sortedBy { it.second }.take(2)
    }

    fun segmentAt(s: Float): Int = (upcoming(s)[0].first - 1 + V.size) % V.size
}

/** 영상 37.5초 루프 */
object Scenario {
    const val LOOP = 37.5f
    const val SIM_MPS = 34f

    data class State(val gen: Boolean, val battery: Int, val ratio: Int)

    //  0 ~ 16s   EV 주행 · 배터리 24 → 20%
    //  16s       배터리 20% → 발전 모드 전환
    //  16 ~ 34s  발전 중 · 배터리 20 → 24%, 전기 주행 비율 78 → 75%
    //  34s       EV 주행 복귀
    fun at(t: Float): State = when {
        t < 16f -> State(false, 24 - (t / 4f).toInt(), 78)
        t < 34f -> State(true, minOf(24, 20 + ((t - 16f) / 4.5f).toInt()), Math.round(78f - 3f * (t - 16f) / 18f))
        else -> State(false, 24, Math.round(75f + 3f * (t - 34f) / 3.5f))
    }
}
