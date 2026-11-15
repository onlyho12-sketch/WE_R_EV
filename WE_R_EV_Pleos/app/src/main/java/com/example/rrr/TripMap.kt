package com.example.rrr

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class LatLng(val lat: Double, val lon: Double)

data class RouteResult(
    val points: List<LatLng>,
    val distanceKm: Double,
    val durationMin: Int,
    /** false면 API 실패 → 직선 거리로 추정한 값 */
    val fromApi: Boolean,
)

/** OSRM 공개 라우팅 API (키 불필요, 데모 용도). 실서비스는 상용 API로 교체. */
suspend fun fetchRoute(start: LatLng, end: LatLng): RouteResult = withContext(Dispatchers.IO) {
    try {
        val url = URL(
            "https://router.project-osrm.org/route/v1/driving/" +
                "${start.lon},${start.lat};${end.lon},${end.lat}?overview=full&geometries=geojson"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "LRRing-demo")
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val route = JSONObject(body).getJSONArray("routes").getJSONObject(0)
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = List(coords.length()) {
                val c = coords.getJSONArray(it)
                LatLng(c.getDouble(1), c.getDouble(0))
            }
            RouteResult(
                points = points,
                distanceKm = route.getDouble("distance") / 1000.0,
                durationMin = (route.getDouble("duration") / 60.0).roundToInt(),
                fromApi = true,
            )
        } finally {
            conn.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val km = haversineKm(start, end) * 1.3
        RouteResult(listOf(start, end), km, (km / 50.0 * 60).roundToInt(), fromApi = false)
    }
}

fun haversineKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/**
 * 목업용 정적 지도(res/drawable-nodpi/map_seoul.png, 1842×1347) 좌표 변환.
 * 기준점: 서울시청(37.5665, 126.9780) = (868, 722). 동작대교·과천·성남 위치로 배율 보정.
 * 표시 범위: 경도 약 126.59~127.41, 위도 약 37.34~37.82
 */
private object SeoulMap {
    const val REF_W = 1842f
    const val REF_H = 1347f
    const val REF_X = 868.0
    const val REF_Y = 722.0
    const val REF_LON = 126.978
    const val REF_LAT = 37.5665
    const val PX_PER_LON = 2230.0
    const val PX_PER_LAT = 2812.0

    /** 원본 이미지(1842×1347) 픽셀 좌표. 실제 디코딩 크기와 무관. */
    fun toPx(p: LatLng): Offset = Offset(
        (REF_X + (p.lon - REF_LON) * PX_PER_LON).toFloat(),
        (REF_Y - (p.lat - REF_LAT) * PX_PER_LAT).toFloat(),
    )
}

/**
 * 정적 지도 이미지 한 장의 좌표 변환 정보.
 * (refX, refY) 픽셀이 (refLat, refLon) 이고, 경도·위도 1도가 각각 pxPerLon · pxPerLat 픽셀.
 */
class MapSpec(
    val drawable: Int,
    val w: Float,
    val h: Float,
    val refX: Double,
    val refY: Double,
    val refLon: Double,
    val refLat: Double,
    val pxPerLon: Double,
    val pxPerLat: Double,
    /** 확대 단계별 조각(assets/maptiles)을 같이 쓰는지 — 서울 지도만 */
    val tiles: Boolean,
) {
    fun toPx(p: LatLng): Offset = Offset(
        (refX + (p.lon - refLon) * pxPerLon).toFloat(),
        (refY - (p.lat - refLat) * pxPerLat).toFloat(),
    )
}

/** 한국: 서울·경기 (map_seoul.png, 1842×1347) */
val SeoulMapSpec = MapSpec(
    R.drawable.map_seoul, SeoulMap.REF_W, SeoulMap.REF_H,
    SeoulMap.REF_X, SeoulMap.REF_Y, SeoulMap.REF_LON, SeoulMap.REF_LAT,
    SeoulMap.PX_PER_LON, SeoulMap.PX_PER_LAT, tiles = true,
)

/**
 * 미국: 조지아 서배너 — 현대차그룹 메타플랜트 주변 (map_savannah.png, 1799×1106).
 * 기준점: I-16 / I-95 분기점. 배율은 경로가 지도 위 I-16 에 겹치도록 맞춘 값 (위도 = 경도 × 1.18, 메르카토르).
 */
val SavannahMapSpec = MapSpec(
    R.drawable.map_savannah, 1799f, 1106f,
    refX = 855.0, refY = 658.0, refLon = -81.2549, refLat = 32.0750,
    pxPerLon = 2764.0, pxPerLat = 3260.0, tiles = false,
)

/**
 * 확대 단계별 지도 조각 (tools/maptiles/build_map_tiles.py 가 지도 녹화 영상에서 생성).
 * x, y, w, h: 기본 지도(1842×1347) 픽셀 좌표의 위치·크기, s: 조각 1px 이 기본 지도 몇 px 인지 (작을수록 정밀)
 */
private data class MapTile(val file: String, val x: Float, val y: Float, val w: Float, val h: Float, val s: Float)

private fun loadTileIndex(ctx: android.content.Context): List<MapTile> = runCatching {
    val json = JSONObject(ctx.assets.open("maptiles/tiles.json").bufferedReader().use { it.readText() })
    val arr = json.getJSONArray("tiles")
    List(arr.length()) {
        val t = arr.getJSONObject(it)
        MapTile(
            "maptiles/" + t.getString("f"), t.getDouble("x").toFloat(), t.getDouble("y").toFloat(),
            t.getDouble("w").toFloat(), t.getDouble("h").toFloat(), t.getDouble("s").toFloat(),
        )
    }
}.getOrDefault(emptyList())

private fun decodeTile(ctx: android.content.Context, file: String): ImageBitmap? = runCatching {
    val opts = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
    ctx.assets.open(file).use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
}.getOrNull()

private const val MAX_LOADED_TILES = 14
private const val BASE_ZOOM_LIMIT = 1.8f
/** 단계 조각 1px 이 화면에서 차지할 수 있는 범위 (너무 줄이면 글자가 깨알, 너무 키우면 흐림) */
private const val LEVEL_MIN = 0.55f
private const val LEVEL_MAX = 1.7f
/** 조각 가장자리를 투명하게 겹치는 폭 (조각 짧은 변 대비) */
private const val FEATHER = 0.12f
/** 화면 중 이 비율 이상을 조각이 덮어야 그 단계를 쓴다 (나머지는 페더로 부드럽게 기본 지도와 이어짐) */
private const val COVER_MIN = 0.6f

/** 같은 배율(반 옥타브 구간) 조각 묶음 */
private class TileLevel(val scale: Float, val tiles: List<MapTile>)

private fun buildLevels(tiles: List<MapTile>): Map<Int, TileLevel> =
    tiles.groupBy { round(log2(1f / it.s) * 2f).toInt() }
        .mapValues { (_, ts) -> TileLevel(ts.map { it.s }.sorted()[ts.size / 2], ts) }

/** 뷰포트 중 조각들이 (페더 구간 제외) 덮는 비율 — 7×5 격자 점 검사 */
private fun coverage(ts: List<MapTile>, x: Float, y: Float, w: Float, h: Float): Float {
    var hit = 0
    for (i in 0..6) for (j in 0..4) {
        val px = x + w * i / 6f
        val py = y + h * j / 4f
        if (ts.any { t ->
                val m = min(t.w, t.h) * FEATHER
                px >= t.x + m && px <= t.x + t.w - m && py >= t.y + m && py <= t.y + t.h - m
            }) hit++
    }
    return hit / 35f
}

private fun covers(ts: List<MapTile>, x: Float, y: Float, w: Float, h: Float) = coverage(ts, x, y, w, h) >= COVER_MIN

/** 조각을 가장자리만 부드럽게 투명 처리해서 그리기 (같은 단계 조각끼리 이음새 숨김) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFeathered(
    bmp: ImageBitmap, t: MapTile, ox: Float, oy: Float, sc: Float, alpha: Float, filter: ColorFilter,
) {
    if (alpha <= 0f) return
    val l = ox + t.x * sc; val tp = oy + t.y * sc
    val r = l + t.w * sc; val b = tp + t.h * sc
    val f = min(r - l, b - tp) * FEATHER
    val canvas = drawContext.canvas
    canvas.saveLayer(androidx.compose.ui.geometry.Rect(l, tp, r, b), androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
    drawImage(
        image = bmp,
        dstOffset = IntOffset(l.roundToInt(), tp.roundToInt()),
        dstSize = IntSize((r - l).roundToInt(), (b - tp).roundToInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.High,
    )
    val dstIn = androidx.compose.ui.graphics.BlendMode.DstIn
    val clear = Color.Transparent; val solid = Color.Black
    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(clear, solid), l, l + f), Offset(l, tp), Size(f, b - tp), blendMode = dstIn)
    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(solid, clear), r - f, r), Offset(r - f, tp), Size(f, b - tp), blendMode = dstIn)
    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(clear, solid), tp, tp + f), Offset(l, tp), Size(r - l, f), blendMode = dstIn)
    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(solid, clear), b - f, b), Offset(l, b - f), Size(r - l, f), blendMode = dstIn)
    canvas.restore()
}

enum class MarkerStyle { Origin, Destination, Charger }

data class MapMarker(val at: LatLng, val label: String, val style: MarkerStyle, val color: Color)

private val MapInk = Color(0xFF171A20)

/**
 * 정적 지도 위에 경로·마커를 그리고 핀치 줌 / 드래그 / 더블탭 줌 / +− 버튼 / 경로 맞춤을 지원.
 * 출발·도착이 바뀌면 경로가 카드 오른쪽 영역 가운데 오도록 자동으로 이동한다.
 */
@Composable
fun TripMap(
    start: LatLng,
    end: LatLng,
    route: RouteResult?,
    markers: List<MapMarker>,
    routeColor: Color,
    leftInset: Dp,
    modifier: Modifier = Modifier,
    spec: MapSpec = SeoulMapSpec,
) {
    val image: ImageBitmap = ImageBitmap.imageResource(spec.drawable)
    val softFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) }) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val tiles = remember(spec) { if (spec.tiles) loadTileIndex(ctx) else emptyList() }
    val loaded = remember { mutableStateMapOf<String, ImageBitmap>() }
    // 가장 정밀한 조각을 원래 크기의 1.5배까지 확대할 수 있게
    val maxZoom = remember(tiles) { max(3f, 1.5f / (tiles.minOfOrNull { it.s } ?: 1f)) }

    var sc by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }
    var recenterTick by remember { mutableIntStateOf(0) }
    var fitJob by remember { mutableStateOf<Job?>(null) }
    val levels = remember(tiles) { buildLevels(tiles) }
    var shownLevel by remember { mutableStateOf<Int?>(null) }
    var prevLevel by remember { mutableStateOf<Int?>(null) }
    val levelFade = remember { androidx.compose.animation.core.Animatable(1f) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val inset = with(density) { leftInset.toPx() }
        val imgW = spec.w
        val imgH = spec.h
        val cover = max(w / imgW, h / imgH)

        // 왼쪽은 카드가 덮고 있으므로, 지도 왼쪽 끝이 카드 바로 옆까지 오도록 이동을 허용
        // (지도 왼쪽 가장자리에 있는 목적지가 카드 뒤에 숨지 않게)
        val maxOx = (inset - with(density) { 20.dp.toPx() }).coerceAtLeast(0f)
        fun clampX(v: Float, s: Float) = v.coerceIn(min(0f, w - imgW * s), maxOx)
        fun clampY(v: Float, s: Float) = v.coerceIn(min(0f, h - imgH * s), 0f)
        // 이 배율·위치에서 지도를 '선명하고 이음새 없이' 그릴 수 있는지
        // (기본 지도로 충분하거나, 화면을 빈틈없이 덮는 조각 단계가 있어야 함)
        fun renderable(s: Float, x: Float, y: Float): Boolean {
            if (s <= BASE_ZOOM_LIMIT) return true
            val vx = (inset - x) / s; val vy = -y / s; val vw = (w - inset) / s; val vh = h / s
            return levels.values.any { lv -> lv.scale * s in LEVEL_MIN..LEVEL_MAX && covers(lv.tiles, vx, vy, vw, vh) }
        }
        fun zoomAround(c: Offset, factor: Float, pan: Offset = Offset.Zero) {
            fitJob?.cancel()
            val ns = (sc * factor).coerceIn(cover, maxZoom)
            val nx = clampX(c.x - (c.x - ox) * (ns / sc) + pan.x, ns)
            val ny = clampY(c.y - (c.y - oy) * (ns / sc) + pan.y, ns)
            when {
                renderable(ns, nx, ny) -> { sc = ns; ox = nx; oy = ny }
                // 확대가 안 되는 곳이면 배율은 그대로 두고 이동만
                else -> {
                    val px = clampX(ox + pan.x, sc); val py = clampY(oy + pan.y, sc)
                    if (renderable(sc, px, py)) { ox = px; oy = py }
                }
            }
        }

        val routePx = (route?.points ?: listOf(start, end)).map { spec.toPx(it) }
        val all = routePx + markers.map { spec.toPx(it.at) }

        // 자동 맞춤 목표 (경로가 카드 오른쪽 영역 가운데, 원본 기준 최대 1.5배)
        val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
        val pad = 90f
        val visW = (w - inset).coerceAtLeast(1f)
        val fit = min(visW / (maxX - minX + pad * 2), h / (maxY - minY + pad * 2)).coerceAtMost(1.5f)
        val targetS = max(fit, cover)
        val targetX = clampX(inset + visW / 2 - (minX + maxX) / 2 * targetS, targetS)
        val targetY = clampY(h / 2 - (minY + maxY) / 2 * targetS, targetS)

        LaunchedEffect(targetS, targetX, targetY, recenterTick) {
            if (!ready) {
                sc = targetS; ox = targetX; oy = targetY; ready = true
                return@LaunchedEffect
            }
            fitJob?.cancel()
            val s0 = sc; val x0 = ox; val y0 = oy
            fitJob = scope.launch {
                animate(0f, 1f, animationSpec = tween(700)) { t, _ ->
                    sc = s0 + (targetS - s0) * t
                    ox = x0 + (targetX - x0) * t
                    oy = y0 + (targetY - y0) * t
                }
            }
        }

        // ── 배율 단계 선택: 한 화면에는 한 단계만 그린다 (배율이 섞이면 글자 크기가 어긋나 '깨져' 보임) ──
        // 카드에 가려지지 않은 영역(뷰포트)을 기본 지도 좌표로
        val viewX = (inset - ox) / sc; val viewY = -oy / sc; val viewW = (w - inset) / sc; val viewH = h / sc
        fun levelOk(key: Int?): Boolean {
            if (key == null) return true
            val lv = levels[key] ?: return false
            return lv.scale * sc in LEVEL_MIN..LEVEL_MAX && covers(lv.tiles, viewX, viewY, viewW, viewH)
        }
        // 후보: 뷰포트를 빈틈없이 덮는 단계 + 기본 지도. 화면 1px ≈ 지도 1px 에 가장 가까운 것
        val best: Int? = (levels.keys.filter { levelOk(it) }.map<Int, Int?> { it } + listOf(null))
            .minByOrNull { k -> abs(ln((k?.let { levels.getValue(it).scale } ?: 1f) * sc)) }
        // 흔들림 방지: 지금 단계가 아직 유효하면 유지
        val level = if (shownLevel != best && levelOk(shownLevel) &&
            ((shownLevel?.let { levels.getValue(it).scale } ?: 1f) * sc) in 0.6f..1.6f) shownLevel else best
        LaunchedEffect(level) {
            if (level != shownLevel) {
                prevLevel = shownLevel
                shownLevel = level
                levelFade.snapTo(0f)
                levelFade.animateTo(1f, tween(250))
                prevLevel = null
            }
        }
        fun tilesOf(key: Int?) = key?.let { levels[it]?.tiles }.orEmpty().filter { t ->
            t.x < viewX + viewW && t.x + t.w > viewX && t.y < viewY + viewH && t.y + t.h > viewY
        }
        val currentTiles = tilesOf(shownLevel)
        val fadingTiles = tilesOf(prevLevel)
        val visibleFiles = (currentTiles + fadingTiles).map { it.file }
        LaunchedEffect(visibleFiles) {
            loaded.keys.filter { it !in visibleFiles }.drop((MAX_LOADED_TILES - visibleFiles.size).coerceAtLeast(0))
                .forEach { loaded.remove(it) }
            visibleFiles.filter { it !in loaded }.forEach { f ->
                withContext(Dispatchers.IO) { decodeTile(ctx, f) }?.let { loaded[f] = it }
            }
        }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(w, h) {
                    detectTransformGestures { centroid, pan, zoom, _ -> zoomAround(centroid, zoom, pan) }
                }
                .pointerInput(w, h) {
                    detectTapGestures(onDoubleTap = { zoomAround(it, 1.6f) })
                }
        ) {
            drawImage(
                image = image,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize((imgW * sc).roundToInt(), (imgH * sc).roundToInt()),
                colorFilter = softFilter,
                filterQuality = FilterQuality.High,
            )
            // 기본 지도만 크게 키운 상태면 살짝 흐리게 (저해상도 티 줄이기)
            if (shownLevel == null && sc > 1.6f) drawRect(Color.White.copy(alpha = ((sc - 1.6f) / 1.5f).coerceIn(0f, 0.5f)))
            val a = levelFade.value
            // 이전 단계: 새 단계가 조각이면 밑에 그대로 깔고, 기본 지도로 돌아가는 중이면 서서히 사라짐
            val prevAlpha = if (shownLevel == null) 1f - a else 1f
            fadingTiles.forEach { tile -> loaded[tile.file]?.let { drawFeathered(it, tile, ox, oy, sc, prevAlpha, softFilter) } }
            currentTiles.forEach { tile -> loaded[tile.file]?.let { drawFeathered(it, tile, ox, oy, sc, a, softFilter) } }
            fun t(p: Offset) = Offset(ox + p.x * sc, oy + p.y * sc)

            if (routePx.size >= 2) {
                val path = Path().apply {
                    val first = t(routePx.first())
                    moveTo(first.x, first.y)
                    routePx.drop(1).forEach { val q = t(it); lineTo(q.x, q.y) }
                }
                drawPath(path, Color.White, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, routeColor, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val labelStyle = com.example.rrr.ui.theme.AppText.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MapInk)
            markers.forEach { m ->
                val c = t(spec.toPx(m.at))
                when (m.style) {
                    MarkerStyle.Origin -> {
                        drawCircle(MapInk, 9.dp.toPx(), c); drawCircle(Color.White, 6.dp.toPx(), c)
                    }
                    MarkerStyle.Destination -> {
                        drawCircle(Color.White, 11.dp.toPx(), c); drawCircle(MapInk, 8.dp.toPx(), c)
                    }
                    MarkerStyle.Charger -> {
                        drawCircle(Color.White, 10.dp.toPx(), c); drawCircle(m.color, 7.dp.toPx(), c)
                    }
                }
                val layout = textMeasurer.measure(m.label, labelStyle)
                val padH = 6.dp.toPx(); val padV = 3.dp.toPx()
                // 충전소 이름표는 핀 왼쪽에 (목적지 이름표와 겹치지 않게)
                val boxW = layout.size.width + padH * 2
                val x = if (m.style == MarkerStyle.Charger) c.x - 14.dp.toPx() - boxW else c.x + 14.dp.toPx()
                val topLeft = Offset(x, c.y - layout.size.height / 2f - padV)
                drawRoundRect(
                    Color.White.copy(alpha = 0.92f), topLeft,
                    Size(layout.size.width + padH * 2, layout.size.height + padV * 2), CornerRadius(4.dp.toPx()),
                )
                drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV))
            }
        }

        // Tesla 스타일 지도 컨트롤
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color.White)
        ) {
            val center = Offset(inset + visW / 2, h / 2)
            MapButton("+") { zoomAround(center, 1.4f) }
            HorizontalDivider(Modifier.size(width = 44.dp, height = 1.dp), color = Color(0xFFE6E7EA))
            MapButton("−") { zoomAround(center, 1 / 1.4f) }
            HorizontalDivider(Modifier.size(width = 44.dp, height = 1.dp), color = Color(0xFFE6E7EA))
            MapButton("◎") { recenterTick++ }
        }
    }
}

@Composable
private fun MapButton(label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 20.sp, color = MapInk)
    }
}
