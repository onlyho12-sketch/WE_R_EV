package com.example.rrr

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import com.example.rrr.pleos.NaviEvent
import com.example.rrr.pleos.PleosServices
import com.example.rrr.pleos.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.rrr.ui.theme.Hd
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath


// ───────────────────── 공통 색상 ─────────────────────

private val Bg = Hd.Bg
private val Surface = Hd.Surface
private val Ink = Hd.Ink
private val Sub = Hd.Sub
private val Faint = Hd.Faint
private val Line = Hd.Line
private val Field = Hd.SurfaceAlt
private val Blue = Hd.Navy
private val Navy = Hd.Navy
private val EvGreen = Hd.Ev
private val GasOrange = Hd.Gas

private val RingIce = Hd.Navy
private val RingMint = Hd.Navy
private val RingViolet = Hd.Gas

/** 흰 카드 공통: 그림자 대신 얇은 라인 */
private fun Modifier.card(radius: Int = 16) = this
    .clip(RoundedCornerShape(radius.dp))
    .background(Surface)
    .border(1.dp, Line, RoundedCornerShape(radius.dp))

private val Energy.color: Color
    get() = if (this == Energy.Electric) EvGreen else GasOrange

private fun Int.toHm(): String =
    if (this >= 60) "${this / 60}시간 ${this % 60}분" else "${this}분"

// ───────────────────── 데모 경로 ─────────────────────

private data class Preset(
    val label: String,
    val origin: Place,
    val dest: Place,
    val soc: Int,
    val fuel: Int
)

private val Presets = listOf(
    // 미국 시나리오 (싼타페 EREV 북미 선출시): 서배너 집 → 현대 메타플랜트, $ · mi · gal · TOU 요금
    Preset("🇺🇸 미국 출근길", DemoPlaces.SavannahHome, DemoPlaces.Metaplant, 62, 48),
    Preset("퇴근길", DemoPlaces.Gangnam, DemoPlaces.Jeongja, 62, 48),
    Preset("쇼핑", DemoPlaces.Jeongja, DemoPlaces.Hanam, 45, 55),
    Preset("부모님 댁", DemoPlaces.Gangnam, DemoPlaces.Hwado, 30, 22),
    Preset("캠핑", DemoPlaces.Gangnam, DemoPlaces.Toechon, 55, 35)
)

// ───────────────────── Activity ─────────────────────

class MainActivity : ComponentActivity() {
    private val gleoCommand = mutableStateOf<TripCommand?>(null)

    /** Pleos 권한 요청 결과가 오면 SDK 연결을 다시 시도한다 */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) PleosServices.start(this)
    }

    override fun onDestroy() {
        if (isFinishing) PleosServices.stop()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gleoCommand.value = GleoBridge.fromIntent(intent)

        Ledger.init(this)
        // Pleos SDK (Vehicle · NaviHelper · TextToSpeech) 연결 — 실패하면 각 게이트웨이가 DEMO 값으로 동작
        PleosServices.start(this)
        val missing = PleosServices.permissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())

        setContent {
            MaterialTheme(
                colorScheme = com.example.rrr.ui.theme.HdColorScheme,
                typography = com.example.rrr.ui.theme.AppTypography
            ) {
                LrringApp(gleoCommand)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        GleoBridge.fromIntent(intent)?.let {
            gleoCommand.value = it
        }
    }
}

// ───────────────────── 앱 상태 / 화면 전환 ─────────────────────

@Composable
private fun LrringApp(
    gleoCommand: MutableState<TripCommand?>
) {
    var selected by remember { mutableIntStateOf(4) }
    var origin by remember { mutableStateOf(DemoPlaces.Gangnam) }
    var dest by remember { mutableStateOf(DemoPlaces.Jeongja) }
    // 배터리·연료: Pleos Vehicle SDK 값 (연결이 없으면 DEMO 값)
    val energy by PleosServices.vehicle.state.collectAsState()
    val naviSource by PleosServices.navi.source.collectAsState()
    val voiceSource by PleosServices.voice.source.collectAsState()
    val naviDest by PleosServices.navi.destination.collectAsState()
    val soc = energy.socPct
    val fuel = energy.fuelPct
    // 지역은 출발지를 따른다 (미국 프리셋을 고르면 $ · mi · gal · TOU)
    val region = origin.region
    val cfg = Regions.of(region)
    val scope = rememberCoroutineScope()

    // 진행 중인 트립 (내비 길안내 시작 ~ 도착, 또는 시연 도구)
    var tripId by remember { mutableStateOf<String?>(null) }
    var waypointArrived by remember { mutableStateOf(false) }
    var chargeJob by remember { mutableStateOf<Job?>(null) }
    var chargePending by remember { mutableStateOf(false) }

    val doneActions = remember { mutableStateListOf<String>() }

    var savedStation by remember {
        mutableStateOf<GasStation?>(null)
    }
    var priority by remember {
        mutableStateOf(Priority.Economy)
    }
    var toast by remember {
        mutableStateOf<String?>(null)
    }


    val routes = remember {
        mutableStateMapOf<Pair<Place, Place>, RouteResult>()
    }

    fun applyCommand(cmd: TripCommand, source: String) {
        cmd.origin?.let { origin = it }
        cmd.destination?.let {
            if (it != origin) dest = it
        }

        selected = 0

        toast = if (cmd.origin == null && cmd.destination == null) {
            "$source: 장소를 찾지 못했어요"
        } else {
            "$source: ${origin.short} → ${dest.short}"
        }
    }

    LaunchedEffect(gleoCommand.value) {
        gleoCommand.value?.let {
            applyCommand(it, "Gleo")
            gleoCommand.value = null
        }
    }

    LaunchedEffect(origin, dest) {
        val key = origin to dest
        var tries = 0

        while (routes[key]?.fromApi != true && tries < 4) {
            if (tries > 0) {
                kotlinx.coroutines.delay(3000)
            }

            val result = fetchRoute(origin.at, dest.at)

            if (result.fromApi || routes[key] == null) {
                routes[key] = result
            }

            tries++
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(3000)
            toast = null
        }
    }

    val route = routes[origin to dest]
    val now = LocalTime.now()

    val distKm = route?.distanceKm
        ?: (haversineKm(origin.at, dest.at) * 1.3)

    val durMin = route?.durationMin
        ?: (distKm / 50 * 60).roundToInt()

    val station = pickStation(
        route?.points ?: listOf(origin.at, dest.at),
        dest.at,
        region
    )

    val plan = planTrip(
        origin = origin,
        dest = dest,
        soc = soc,
        fuel = fuel,
        priority = priority,
        distKm = distKm,
        durMin = durMin,
        nowHour = now.hour + now.minute / 60f,
        night = now.hour >= 21 || now.hour < 6,
        station = station,
        cfg = cfg
    )

    val actionKey =
        "${origin.short}>${dest.short}:${plan.actionKind}"

    val actionDone = actionKey in doneActions

    // ── 결정적 순간 음성 (T4): 위젯 한 줄과 같은 문장을 트립당 1회 ──
    fun speakMoment() {
        val key = "${tripId ?: "preview:${origin.short}>${dest.short}"}:${plan.rule}"
        toast = if (PleosServices.voice.speakOnce(key, plan.widgetMoment, cfg.ttsLocale)) {
            "🔊 ${plan.widgetMoment}"
        } else {
            "이번 트립에서는 이미 안내했어요 (트립당 1회)"
        }
    }

    // ── 트립 시작 / 종료 → 기록 (T7) ──
    fun startTrip() {
        if (tripId == null) {
            tripId = "trip-${System.currentTimeMillis()}"
            waypointArrived = false
            toast = "트립 시작 · ${origin.short} → ${dest.short}"
        }
    }

    fun endTrip() {
        val id = tripId ?: return
        // 제안을 따랐는지: 경유지(주유소) 도착 신호가 있거나, 제안 행동을 승인했으면 따른 것. 시간 우선은 판정 제외
        val judged = plan.rule != Rule.TimeFirst
        val followed: Boolean? = if (!judged) null else (actionDone || waypointArrived)
        val signal = when {
            !judged -> "not_judged"
            waypointArrived -> "waypoint_arrived"
            actionDone -> "action_accepted"
            else -> "not_accepted"
        }
        val usedKwh = plan.evKm / KM_PER_KWH
        // 제안을 안 따르면 기본 모드처럼 도착(귀가)하자마자 충전하는 것으로 본다
        val plugHour = if (dest.home != null) plan.arriveHour else BASELINE_EVENING_PLUG_H
        val plugIsCheap = cfg.homeTariff.bandAt(plugHour) == cfg.homeTariff.cheapest
        val record = TripRecord(
            id = id,
            startedAt = System.currentTimeMillis(),
            region = region,
            origin = origin.short,
            dest = dest.short,
            distanceKm = distKm,
            evKm = plan.evKm.toDouble(),
            rule = plan.rule,
            priority = priority,
            action = plan.actionKind,
            followed = followed,
            followSignal = signal,
            baseline = plan.cost.baseline.total,
            planned = plan.cost.planned.total,
            actual = if (followed == false) plan.cost.baseline.total else plan.cost.planned.total,
            offPeakKwh = if (followed == true || plugIsCheap) usedKwh else 0.0,
            totalChargedKwh = usedKwh,
            simulated = false
        )
        Ledger.add(record)
        tripId = null
        toast = if (record.savings > 0) {
            "트립 종료 · ${money(record.savings, region)} 절약이 WE'R Score에 반영됐어요"
        } else {
            "트립 종료 · WE'R Score에 기록됐어요"
        }
    }

    // ── 제안 행동 승인: 경유지 등록(NaviHelper) / 싼 시간대 충전(Vehicle) ──
    fun acceptAction() {
        if (actionDone) return
        doneActions += actionKey
        toast = plan.actionDone
        when (plan.actionKind) {
            ActionKind.SaveStation -> {
                savedStation = plan.gas.station
                val result = PleosServices.navi.addFuelStop(plan.gas.station)
                if (result.isFailure) toast = "내비 경유지 등록에 실패했어요"
            }
            ActionKind.ScheduleHomeCharge -> {
                // Vehicle SDK 에는 충전 '예약' API 가 없어, 목표 %를 먼저 보내고
                // 앱이 싼 시간대 시작 시각에 충전 스위치를 켠다.
                val startHour = plan.chargeStartHour ?: plan.arriveHour
                val nowH = LocalTime.now().let { it.hour + it.minute / 60f }
                val waitMs = ((startHour - nowH) * 3_600_000L).toLong().coerceAtLeast(0L)
                chargeJob?.cancel()
                chargePending = true
                chargeJob = scope.launch {
                    PleosServices.vehicle.setChargeLimit(CHARGE_TARGET_PCT)
                    delay(waitMs)
                    val on = PleosServices.vehicle.setCharging(true)
                    chargePending = false
                    toast = if (on.isSuccess) "${cfg.cheapLabel} 요금 시작 · 충전을 켰어요 (목표 $CHARGE_TARGET_PCT%)" else "충전 시작에 실패했어요"
                }
            }
            ActionKind.SlowChargeAlert -> Unit
        }
    }

    // 시연용: 싼 시간대가 시작된 것으로 치고 지금 충전 스위치를 켠다
    fun skipToOffPeak() {
        chargeJob?.cancel()
        chargeJob = scope.launch {
            PleosServices.vehicle.setChargeLimit(CHARGE_TARGET_PCT)
            val on = PleosServices.vehicle.setCharging(true)
            chargePending = false
            toast = if (on.isSuccess) "✓ 충전 스위치 ON · 목표 $CHARGE_TARGET_PCT% (${cfg.cheapLabel} 요금)" else "충전 시작에 실패했어요"
        }
    }

    // 오래 살아 있는 효과에서 '지금' 계획을 쓰기 위한 최신 참조
    val latestMoment by rememberUpdatedState<() -> Unit> { speakMoment() }
    val latestNaviEvent by rememberUpdatedState<(NaviEvent) -> Unit> { event ->
        when (event) {
            NaviEvent.RouteStarted -> startTrip()
            NaviEvent.WaypointChanged -> toast = "✓ 내비 경로에 경유지가 반영됐어요"
            is NaviEvent.WaypointArrived -> {
                waypointArrived = true
                toast = "경유지 도착 · ${event.name}"
            }
            is NaviEvent.DestinationArrived -> endTrip()
            is NaviEvent.Error -> toast = if (region == Region.US) {
                "에뮬레이터 내비 지역 제한 — 경유지 등록은 한국 시나리오에서 시연해요 (${event.code})"
            } else {
                "내비 오류: ${event.code}"
            }
        }
    }

    // 트립이 시작되고 30초 뒤 결정적 순간을 한 번 말한다 (같은 트립에서는 다시 말하지 않음)
    LaunchedEffect(tripId) {
        if (tripId != null) {
            delay(30_000)
            latestMoment()
        }
    }

    LaunchedEffect(Unit) {
        PleosServices.navi.events.collect { latestNaviEvent(it) }
    }

    // 내비에 목적지를 찍으면 We'r ev 가 알아서 다시 계산한다 (수동 입력은 보조)
    LaunchedEffect(naviDest) {
        val d = naviDest ?: return@LaunchedEffect
        val place = DemoPlaces.nearest(d.at) ?: Place(
            name = d.name,
            short = d.name.take(8),
            aliases = emptyList(),
            at = d.at,
            region = if (d.at.lon < -30.0) Region.US else Region.KR
        )
        if (place != origin) {
            if (place.region != origin.region) {
                origin = DemoPlaces.of(place.region).first { it != place }
            }
            dest = place
            toast = "내비 목적지 수신 · ${place.short}"
        }
    }

    val tools = DemoToolsState(
        tripActive = tripId != null,
        chargePending = chargePending,
        vehicle = energy.source,
        navi = naviSource,
        voice = voiceSource
    )

    if (selected == 4) {
        WerEvWelcome(
            soc = soc,
            fuel = fuel,
            region = region,
            onNavigate = { selected = it },
            modifier = Modifier.fillMaxSize()
        )
    }
else {
        Row(
            Modifier.fillMaxSize().background(Bg)
        ) {
            SideBar(
                selected = selected,
                onSelect = { selected = it }
            )

            if (selected == 0) {
                HomePage(
                    plan = plan,
                    route = route,
                    origin = origin,
                    dest = dest,
                    soc = soc,
                    cfg = cfg,
                    source = energy.source,
                    tools = tools,
                    onStartTrip = { startTrip() },
                    onMoment = { speakMoment() },
                    onEndTrip = { endTrip() },
                    onSkipToOffPeak = { skipToOffPeak() },
                    priority = priority,
                    toast = toast,
                    actionDone = actionDone,
                    savedStation = savedStation,
                    onAction = { acceptAction() },
                    onOrigin = {
                        if (it != dest) origin = it
                    },
                    onDest = {
                        if (it != origin) dest = it
                    },
                    onSwap = {
                        val previousOrigin = origin
                        origin = dest
                        dest = previousOrigin
                    },
                    onSoc = { PleosServices.vehicle.setDemo(soc = it) },
                    onPriority = { priority = it },
                    onCommand = { applyCommand(it, "르르르") },
                    onPreset = {
                        origin = it.origin
                        dest = it.dest
                        // 실차(PLEOS) 값이 있으면 프리셋 숫자는 무시된다
                        PleosServices.vehicle.setDemo(soc = it.soc, fuel = it.fuel)
                        priority = Priority.Economy
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            } else if (selected == 3) {
                // 르르르 위젯 설정 (rrr_pleos 최신 버전)
                WidgetSettingsPage(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 26.dp, vertical = 20.dp)
                )
            } else if (selected == 1) {
                // WE'R Score — 스크롤 없이 한 화면에 (ScoreViews.kt)
                WerScoreDashboard(
                    region = region,
                    modifier = Modifier.weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 26.dp, vertical = 20.dp)
                )
            } else {
                // WE'R Rewards (ScoreViews.kt)
                WerRewardsPage(
                    region = region,
                    modifier = Modifier.weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 26.dp, vertical = 20.dp)
                )
            }
        }
    }
}

// ───────────────────── 사이드바 ─────────────────────

@Composable
private fun SideBar(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(true)
    }

    val sidebarWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 210.dp else 72.dp,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 220
        ),
        label = "sidebarWidth"
    )

    val menus = listOf(
        Triple(4, Ic.Home, "홈"),
        Triple(0, Ic.Bolt, "WE'R Strategy"),
        Triple(1, Ic.Chart, "WE'R Report"),
        Triple(2, Ic.Reward, "WE'R Rewards"),
        Triple(3, Ic.Nav, "WE'R Widget")
    )

    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(Surface)
            .drawBehind {
                drawLine(
                    Line,
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                    1.dp.toPx()
                )
            }
            .padding(
                horizontal = 10.dp,
                vertical = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 접기 / 펼치기
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(48.dp)
            ) {
                LineIcon(
                    if (expanded) Ic.ChevronLeft else Ic.Menu,
                    Ink
                )
            }

            if (expanded) {
                Text(
                    text = werEvTitle(),
                    color = Navy,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 메뉴
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            menus.forEach { (destination, icon, title) ->
                val active = selected == destination

                val backgroundColor by
                androidx.compose.animation.animateColorAsState(
                    targetValue = if (active) {
                        Hd.NavySoft
                    } else {
                        Color.Transparent
                    },
                    label = "menuBackground"
                )

                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Ink
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(backgroundColor)
                            .clickable {
                                onSelect(destination)
                            }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            LineIcon(
                                icon,
                                if (active) Navy else Sub
                            )
                        }

                        if (expanded) {
                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = title,
                                color = if (active) Navy else Sub,
                                fontSize = 17.sp,
                                fontWeight = if (active) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (expanded) {
            Text(
                text = "EREV 에너지 파트너",
                color = Faint,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 8.dp
                )
            )
        }
    }
}
// ───────────────────── 지도 / 트립 화면 ─────────────────────

@Composable
private fun HomePage(
    plan: Plan,
    route: RouteResult?,
    origin: Place,
    dest: Place,
    soc: Int,
    cfg: RegionConfig,
    source: Source,
    tools: DemoToolsState,
    onStartTrip: () -> Unit,
    onMoment: () -> Unit,
    onEndTrip: () -> Unit,
    onSkipToOffPeak: () -> Unit,
    priority: Priority,
    toast: String?,
    actionDone: Boolean,
    savedStation: GasStation?,
    onAction: () -> Unit,
    onOrigin: (Place) -> Unit,
    onDest: (Place) -> Unit,
    onSwap: () -> Unit,
    onSoc: (Int) -> Unit,
    onPriority: (Priority) -> Unit,
    onCommand: (TripCommand) -> Unit,
    onPreset: (Preset) -> Unit,
    modifier: Modifier
) {
    val markers = buildList {
        add(
            MapMarker(
                origin.at,
                origin.short,
                MarkerStyle.Origin,
                Ink
            )
        )

        add(
            MapMarker(
                dest.at,
                dest.short,
                MarkerStyle.Destination,
                Ink
            )
        )

        dest.chargers
            .filter { it.type != ChargerType.Home }
            .forEach {
                add(
                    MapMarker(
                        it.at,
                        "${it.type.label} ${perKwhText(it.perKwh, cfg.region)}",
                        MarkerStyle.Charger,
                        if (it.type == ChargerType.Fast) {
                            GasOrange
                        } else {
                            EvGreen
                        }
                    )
                )
            }

        val showStation = savedStation
            ?: plan.gas.station.takeIf {
                plan.actionKind == ActionKind.SaveStation ||
                        plan.gas.fillInDays == 0
            }

        showStation?.let {
            add(
                MapMarker(
                    it.at,
                    "주유 ${it.short} ${fuelPriceText(it.perL, cfg.region)}",
                    MarkerStyle.Charger,
                    GasOrange
                )
            )
        }
    }

    var gleoOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        if (cfg.region == Region.US) {
            // 미국: 서배너(메타플랜트) 지도 이미지 위에 경로를 그린다
            TripMap(
                start = origin.at,
                end = dest.at,
                route = route,
                markers = markers,
                routeColor = plan.energy.color,
                leftInset = 404.dp,
                modifier = Modifier.fillMaxSize(),
                spec = SavannahMapSpec
            )
        } else {
            TripMap(
                start = origin.at,
                end = dest.at,
                route = route,
                markers = markers,
                routeColor = plan.energy.color,
                leftInset = 404.dp,
                modifier = Modifier.fillMaxSize()
            )
        }

        TripCard(
            plan,
            route,
            dest,
            soc,
            cfg,
            source,
            tools,
            onStartTrip,
            onMoment,
            onEndTrip,
            onSkipToOffPeak,
            onSoc,
            actionDone,
            onAction,
            Modifier.align(Alignment.TopStart)
                .width(384.dp)
                .fillMaxHeight()
        )

        TripInputBox(
            origin = origin,
            dest = dest,
            onOrigin = onOrigin,
            onDest = onDest,
            onSwap = onSwap,
            onGleo = { gleoOpen = true },
            priority = priority,
            onPriority = onPriority,
            onPreset = onPreset,
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 404.dp, top = 20.dp)
                .width(420.dp)
        )

        toast?.let {
            Text(
                it,
                color = Surface,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ink.copy(alpha = 0.9f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalAlignment = Alignment.End
        ) {
            SavingsPill(cfg.region)
            Spacer(Modifier.height(6.dp))

            Text(
                if (cfg.region == Region.US) "지도 이미지 © Google (목업) · 경로 OSRM · ${cfg.tariffNote}"
                else "지도 이미지 © NAVER · OpenStreetMap (목업) · 경로 OSRM",
                fontSize = 9.sp,
                color = Sub,
                modifier = Modifier
                    .background(Surface.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }

    if (gleoOpen) {
        GleoDialog(
            region = cfg.region,
            onDismiss = { gleoOpen = false },
            onCommand = {
                gleoOpen = false
                onCommand(it)
            }
        )
    }
}

// ───────────────────── 출발지 / 도착지 입력 ─────────────────────

private enum class TripField {
    Origin,
    Dest
}

@Composable
private fun TripInputBox(
    origin: Place,
    dest: Place,
    onOrigin: (Place) -> Unit,
    onDest: (Place) -> Unit,
    onSwap: () -> Unit,
    onGleo: () -> Unit,
    priority: Priority,
    onPriority: (Priority) -> Unit,
    onPreset: (Preset) -> Unit,
    modifier: Modifier
) {
    var active by remember {
        mutableStateOf<TripField?>(null)
    }

    var originText by remember(origin) {
        mutableStateOf(origin.name)
    }

    var destText by remember(dest) {
        mutableStateOf(dest.name)
    }

    val focus = LocalFocusManager.current

    fun pick(field: TripField, place: Place) {
        if (field == TripField.Origin) {
            originText = place.name
            onOrigin(place)
        } else {
            destText = place.name
            onDest(place)
        }

        active = null
        focus.clearFocus()
    }

    Column(modifier) {
        Column(
            Modifier
                .shadow(
                    2.dp,
                    RoundedCornerShape(14.dp)
                )
                .card(14)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 출발·도착 입력 영역
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 출발
                    PlaceField(
                        dot = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        Ink,
                                        CircleShape
                                    )
                            )
                        },
                        label = if (
                            origin.region == Region.US
                        ) {
                            "From"
                        } else {
                            "출발"
                        },
                        value = originText,
                        onValue = {
                            originText = it
                        },
                        onFocus = { focused ->
                            if (focused) {
                                active = TripField.Origin
                            } else if (
                                active == TripField.Origin
                            ) {
                                originText = origin.name
                            }
                        },
                        onDone = {
                            searchPlaces(
                                originText,
                                origin.region
                            )
                                .firstOrNull()
                                ?.let {
                                    pick(
                                        TripField.Origin,
                                        it
                                    )
                                }
                        }
                    )

                    HorizontalDivider(
                        color = Line,
                        modifier = Modifier.padding(
                            start = 22.dp
                        )
                    )

                    // 도착
                    PlaceField(
                        dot = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Ink)
                            )
                        },
                        label = if (
                            origin.region == Region.US
                        ) {
                            "To"
                        } else {
                            "도착"
                        },
                        value = destText,
                        onValue = {
                            destText = it
                        },
                        onFocus = { focused ->
                            if (focused) {
                                active = TripField.Dest
                            } else if (
                                active == TripField.Dest
                            ) {
                                destText = dest.name
                            }
                        },
                        onDone = {
                            searchPlaces(
                                destText,
                                origin.region
                            )
                                .firstOrNull()
                                ?.let {
                                    pick(
                                        TripField.Dest,
                                        it
                                    )
                                }
                        }
                    )
                } // 빠져 있던 Column 종료 중괄호

                Spacer(Modifier.width(8.dp))

                // 출발·도착 교환 / Gleo 버튼
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClick = onSwap
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        LineIcon(
                            Ic.Swap,
                            Sub,
                            size = 20.dp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                Line,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable(
                                onClick = onGleo
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LineIcon(
                            Ic.Mic,
                            Navy,
                            size = 14.dp
                        )

                        Spacer(Modifier.width(4.dp))

                        Text(
                            text = "Gleo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Navy
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Line
            )

            // 기존 우선순위 선택 기능 유지
            PrioritySelector(
                priority = priority,
                onPriority = onPriority,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                region = origin.region
            )
        }

        // 시나리오 선택 버튼
        if (active == null) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(
                    rememberScrollState()
                ),
                horizontalArrangement = Arrangement.spacedBy(
                    8.dp
                )
            ) {
                Presets.forEach { preset ->
                    RouteChip(
                        label = if (
                            origin.region == Region.US
                        ) {
                            when (preset.label) {
                                "🇺🇸 미국 출근길" ->
                                    "🇺🇸 Commute"

                                "퇴근길" ->
                                    "Home"

                                "쇼핑" ->
                                    "Shopping"

                                "부모님 댁" ->
                                    "Family visit"

                                "캠핑" ->
                                    "Camping"

                                else ->
                                    preset.label
                            }
                        } else {
                            preset.label
                        },
                        active = preset.origin == origin &&
                                preset.dest == dest
                    ) {
                        onPreset(preset)
                    }
                }
            }
        }

        // 장소 검색 결과
        val field = active

        if (field != null) {
            val query = if (
                field == TripField.Origin
            ) {
                originText
            } else {
                destText
            }

            val current = if (
                field == TripField.Origin
            ) {
                origin
            } else {
                dest
            }

            val suggestions = (
                    if (query == current.name) {
                        DemoPlaces.of(origin.region)
                    } else {
                        searchPlaces(
                            query,
                            origin.region
                        )
                    }
                    ).take(6)

            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        2.dp,
                        RoundedCornerShape(14.dp)
                    )
                    .card(14)
                    .padding(
                        vertical = 6.dp
                    )
            ) {
                if (suggestions.isEmpty()) {
                    Text(
                        text = if (
                            origin.region == Region.US
                        ) {
                            "No demo location for “$query”. " +
                                    "Try Home, Savannah or Metaplant."
                        } else {
                            "‘$query’ 데모 장소 없음 · " +
                                    "강남, 집, 하남, 판교, 잠실, 남양주, 퇴촌…"
                        },
                        color = Faint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        )
                    )
                }

                suggestions.forEach { place ->
                    SuggestionRow(place) {
                        pick(
                            field,
                            place
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceField(
    dot: @Composable () -> Unit,
    label: String,
    value: String,
    onValue: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    Row(
        Modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(22.dp)) {
            dot()
        }

        Text(
            label,
            color = Faint,
            fontSize = 12.sp,
            modifier = Modifier.width(34.dp)
        )

        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = com.example.rrr.ui.theme.AppText.copy(
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Blue),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            ),
            modifier = Modifier.weight(1f).onFocusChanged {
                onFocus(it.isFocused)
            }
        )
    }
}

@Composable
private fun SuggestionRow(
    p: Place,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            p.name,
            color = Ink,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        val tags = buildList {
            if (p.home != null) add("집충전" to EvGreen)
            if (p.slow != null) add("완속" to EvGreen)
            if (p.fast != null) add("급속" to GasOrange)
            if (isEmpty()) add("충전소 없음" to Faint)
        }

        tags.forEach { (title, color) ->
            Text(
                title,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp)
                    .border(
                        1.dp,
                        Line,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

// ───────────────────── Gleo 입력 ─────────────────────

@Composable
private fun GleoDialog(
    region: Region,
    onDismiss: () -> Unit,
    onCommand: (TripCommand) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            .putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "어디서 어디로 갈까요?"
            )
    }

    val canSpeak = remember {
        speechIntent.resolveActivity(context.packageManager) != null
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.let { text = it }
    }

    val parsed = parseTripCommand(text, region)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                "Gleo에게 말하기",
                color = Ink,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "예) “강남에서 스타필드 하남 가자”, “부모님 댁 가는데 충전해야 돼?”",
                    color = Sub,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text("말하거나 입력하세요") },
                        modifier = Modifier.weight(1f)
                    )

                    if (canSpeak) {
                        Spacer(Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { launcher.launch(speechIntent) }
                        ) {
                            LineIcon(Ic.Mic, Navy, size = 18.dp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "출발 ${parsed.origin?.short ?: "(그대로)"}  →  도착 ${parsed.destination?.short ?: "(그대로)"}",
                    color = if (
                        parsed.origin != null ||
                        parsed.destination != null
                    ) {
                        Ink
                    } else {
                        Faint
                    },
                    fontSize = 13.sp
                )

                if (!canSpeak) {
                    Text(
                        "이 기기에 음성인식이 없어 문장 입력으로 동작해요",
                        color = Faint,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCommand(parsed) },
                enabled = parsed.origin != null ||
                        parsed.destination != null
            ) {
                Text("경로 설정")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

// ───────────────────── 트립 카드 ─────────────────────

@Composable
private fun TripCard(
    plan: Plan,
    route: RouteResult?,
    dest: Place,
    soc: Int,
    cfg: RegionConfig,
    source: Source,
    tools: DemoToolsState,
    onStartTrip: () -> Unit,
    onMoment: () -> Unit,
    onEndTrip: () -> Unit,
    onSkipToOffPeak: () -> Unit,
    onSoc: (Int) -> Unit,
    actionDone: Boolean,
    onAction: () -> Unit,
    modifier: Modifier
) {
    val region = cfg.region
    Column(
        modifier.background(Surface)
            .drawBehind {
                drawLine(
                    Line,
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                    1.dp.toPx()
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier.offset(x = 12.dp)
        ) {
            CarHeroScene(plan.lrrr, plan.energy)
        }
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "SANTA FE EREV",
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (region == Region.US) {
                            "Battery ${
                                distText(
                                    (soc - RESERVE_PCT).coerceAtLeast(0) /
                                            100.0 * EV_FULL_KM,
                                    region
                                )
                            } · Fuel ${plan.gas.fuelPct}%"
                        } else {
                            "배터리 ${
                                distText(
                                    (soc - RESERVE_PCT).coerceAtLeast(0) /
                                            100.0 * EV_FULL_KM,
                                    region
                                )
                            } · 연료 ${plan.gas.fuelPct}%"
                        },
                        color = Sub,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    // 값의 출처: Pleos Vehicle SDK 실시간 값인지, 데모 값인지
                    SourceBadge(
                        source = source,
                        region = region
                    )
                }
            }

            // 시연용 −/+ 는 실차 값이 없을 때만 (실차에서는 Virtual Console 로 바꾼다)
            if (source == Source.DEMO) {
                DemoStepper(soc, onSoc)
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WE'R Strategy",
                color = Sub,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Text(
                if (region == Region.US) {
                    if (plan.energy == Energy.Electric) {
                        "Battery driving"
                    } else {
                        "Generator driving"
                    }
                } else {
                    plan.energy.label
                },
                color = if (plan.energy == Energy.Electric) Navy else Sub,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (plan.energy == Energy.Electric) Hd.NavySoft else Field
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            plan.headline,
            color = Ink,
            fontSize = 28.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            if (region == Region.US) {
                plan.benefit
                    .replace("다음 주행 ", "Next drive: ")
                    .replace("휘발유 대비 ", "Compared with gasoline: ")
                    .replace(" 절약", " saved")
            } else {
                plan.benefit
            },
            color = Sub,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(14.dp))

        // 차 기본 모드 대비 이번 트립 비용 (T6)
        TripCostCard(plan, cfg)

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (actionDone) {
                    Hd.NavySoft
                } else {
                    Navy
                },
                contentColor = if (actionDone) Navy else Surface
            )
        ) {
            Text(
                if (actionDone) plan.actionDone else plan.action,
                fontSize = if (actionDone) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(24.dp))

        Text(
            if (region == Region.US) "Energy plan" else "에너지 공급 계획",
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (region == Region.US) {
                (
                        route?.let {
                            "${distText(it.distanceKm, region)} · " +
                                    "About ${it.durationMin} min · "
                        } ?: ""
                        ) +
                        "Battery ${distText(plan.evKm.toDouble(), region)} / " +
                        "Generator ${distText(plan.gasKm.toDouble(), region)}"
            } else {
                (
                        route?.let {
                            "${distText(it.distanceKm, region)} · " +
                                    "약 ${it.durationMin.toHm()} · "
                        } ?: ""
                        ) +
                        "배터리 ${distText(plan.evKm.toDouble(), region)} / " +
                        "발전 ${distText(plan.gasKm.toDouble(), region)}"
            },
            color = Faint,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(8.dp))
        SupplyTimelineChart(
            tl = plan.timeline,
            region = region
        )

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(24.dp))
        InfraPanel(dest, plan.rule, region)

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(24.dp))

        Text(
            if (region == Region.US) "Refueling plan" else "주유 계획",
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))
        GasStrategyCard(plan.gas)

        Spacer(Modifier.height(16.dp))

        Text(
            plan.reason,
            color = Faint,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(20.dp))

        // 시연 도구: 에뮬레이터에서 주행 없이 트립 시작 → 음성 1회 → 종료(점수 반영)를 재현
        DemoTools(
            state = tools,
            onStartTrip = onStartTrip,
            onMoment = onMoment,
            onEndTrip = onEndTrip,
            onSkipToOffPeak = onSkipToOffPeak,
            region = region
        )
    }
}

@Composable
private fun InfraPanel(
    dest: Place,
    rule: Rule,
    region: Region
) {
    Text(
        if (region == Region.US) {
            "Charging near ${dest.short}"
        } else {
            "${dest.short} 충전 인프라"
        },
        color = Ink,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(8.dp))

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Field)
            .padding(14.dp)
    ) {
        InfraRow(
            if (region == Region.US) "Level 2" else "완속",
            dest.home ?: dest.slow,
            Navy,
            region
        )

        Spacer(Modifier.height(8.dp))

        InfraRow(
            if (region == Region.US) "DC fast" else "급속",
            dest.fast,
            Navy,
            region
        )
    }

    Spacer(Modifier.height(12.dp))
    Text("WE'R EV 판단", color = Faint, fontSize = 11.sp)
    Spacer(Modifier.height(4.dp))

    Rule.entries.forEach { currentRule ->
        val active = currentRule == rule

        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) Navy else Hd.Muted)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                currentRule.condition,
                color = if (active) Ink else Faint,
                fontSize = 12.sp,
                fontWeight = if (active) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                modifier = Modifier.weight(1f)
            )

            Text(
                "→ ${currentRule.answer}",
                color = if (active) Blue else Faint,
                fontSize = 12.sp,
                fontWeight = if (active) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                }
            )
        }
    }
}

@Composable
private fun InfraRow(
    label: String,
    charger: Charger?,
    color: Color,
    region: Region
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Sub,
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp)
        )

        if (charger != null) {
            Text(
                "✓",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(6.dp))

            Text(
                charger.name,
                color = Ink,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                perKwhText(charger.perKwh, region),
                color = Sub,
                fontSize = 12.sp
            )
        } else {
            Text(
                "✕",
                color = Faint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.width(6.dp))
            Text("없음", color = Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PrioritySelector(
    priority: Priority,
    onPriority: (Priority) -> Unit,
    modifier: Modifier,
    region: Region = Region.KR
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Field)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Priority.entries.forEach { item ->
            val active = item == priority

            val title = if (region == Region.US) {
                when (item) {
                    Priority.Economy -> "Economy"
                    Priority.Balance -> "Balanced"
                    Priority.Time -> "Fastest"
                }
            } else {
                item.label
            }

            Text(
                text = title,
                color = if (active) Surface else Sub,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (active) Blue else Color.Transparent
                    )
                    .clickable {
                        onPriority(item)
                    }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SavingsPill(
    region: Region
) {
    val month = Ledger.month(region)

    val saved = month.sumOf {
        it.savings.coerceAtLeast(0.0)
    }

    val totalKm = month.sumOf {
        it.distanceKm
    }

    val evShare = if (totalKm > 0) {
        (
                month.sumOf { it.evKm } /
                        totalKm * 100
                )
            .roundToInt()
            .coerceIn(0, 100)
    } else {
        0
    }

    val isUs = region == Region.US

    Row(
        modifier = Modifier
            .shadow(
                2.dp,
                RoundedCornerShape(12.dp)
            )
            .card(12)
            .padding(
                horizontal = 18.dp,
                vertical = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isUs) {
                "Saved this month"
            } else {
                "이번 달 절약"
            },
            color = Sub,
            fontSize = 12.sp
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = money(saved, region),
            color = Navy,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(12.dp))

        Box(
            Modifier
                .width(1.dp)
                .height(12.dp)
                .background(Line)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = if (isUs) {
                "Battery $evShare% · Gasoline ${100 - evShare}%"
            } else {
                "배터리 $evShare% · 휘발유 ${100 - evShare}%"
            },
            color = Sub,
            fontSize = 12.sp
        )
    }
}

// ───────────────────── 리워드 플레이 ─────────────────────

@Composable
private fun PlayPage(
    points: Int,
    onMeetDemo: () -> Unit
) {
    RingPanel(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이번 달 챌린지: 떨어지는 포인트 동전을 가방으로 받는 르르르
            RrrMomentAnimation(
                moment = RrrMoment.Collect,
                modifier = Modifier.size(width = 118.dp, height = 150.dp)
            )

            Spacer(Modifier.width(15.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "이번 달 챌린지",
                    color = Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "충전 계획과 운전 습관을 지키면 포인트가 쌓여요",
                    color = Sub,
                    fontSize = 16.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("보유 포인트", color = Sub, fontSize = 15.sp)

                Text(
                    "$points P",
                    color = Navy,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp
                )

                Text("시뮬레이션", color = Faint, fontSize = 12.sp)
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RingPanel(Modifier.weight(1f)) {
            Text(
                "충전 미션",
                color = Ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))
            Text("충전 계획 5회 중 4회 실천", color = Sub, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { .8f },
                modifier = Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Navy,
                trackColor = Field,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "달성 시 30P",
                color = Navy,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        RingPanel(Modifier.weight(1f)) {
            Text(
                "에코 드라이빙 미션",
                color = Ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))
            Text("급가속 감소 챌린지", color = Sub, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { .65f },
                modifier = Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Navy,
                trackColor = Field,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "달성 시 20P",
                color = Navy,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        RingPanel(Modifier.weight(1f)) {
            Text(
                "EREV 오너 만남",
                color = Ink,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(9.dp))
            Text("근처 EREV 오너와 만나면 포인트", color = Sub, fontSize = 16.sp)
            Spacer(Modifier.height(15.dp))

            ActionButton("만남 기록하기 · 10P", onMeetDemo)
        }
    }

    RingPanel(Modifier.fillMaxWidth()) {
        Text(
            "안전한 챌린지 운영",
            color = Ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(7.dp))

        Text(
            "보상 수치는 데모 데이터이며 실제 차량 탐지·포인트 지급은 구현되지 않았습니다. 안전운전을 방해하는 행동을 유도하지 않습니다.",
            color = Sub,
            fontSize = 14.sp
        )
    }
}

// ───────────────────── 공통 버튼 ─────────────────────

@Composable
private fun ActionButton(
    title: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp
        )
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ───────────────────── 홈 메뉴 ─────────────────────

private data class RingMenu(
    val title: String,
    val detail: String,
    val icon: Ic,
    val destination: Int
)

private val ringMenus = listOf(
    RingMenu(
        title = "WE'R Strategy",
        detail = "충전 · 주유 · 경로 추천",
        icon = Ic.Bolt,
        destination = 0
    ),
    RingMenu(
        title = "WE'R Report",
        detail = "WE'R Score · 운행 리포트",
        icon = Ic.Chart,
        destination = 1
    ),
    RingMenu(
        title = "WE'R Rewards",
        detail = "좋은 습관으로 포인트 획득",
        icon = Ic.Reward,
        destination = 2
    ),
    RingMenu(
        title = "WE'R Widget",
        detail = "르르르 위젯 애니메이션 설정",
        icon = Ic.Nav,
        destination = 3
    )
)

// ───────────────────── WE'R EV 로고 ─────────────────────

/** 제목 강조색: 검정 글자 옆에서도 확실히 구분되는 밝은 네이비 블루 (Hd.Navy 는 검정과 거의 구분이 안 됨) */
private val WerAccent = Color(0xFF1F5CD6)

/** 페이지 제목 "WE'R Score" / "WE'R Reward" / "WE'R Widget": WE'R 중 E·R 만 블루로 강조, 나머지는 기본 글자색 */
internal fun werTitle(suffix: String): AnnotatedString = buildAnnotatedString {
    val strong = SpanStyle(color = WerAccent)
    append("W")
    withStyle(strong) { append("E") }
    append("'")
    withStyle(strong) { append("R") }
    append(" $suffix")
}

/** W·' 는 연한 색으로 낮추고 E·R·E·V 를 네이비로 — 읽으면 'EREV' 가 도드라지게 */
private val WerEvMuted = Color(0xFFA7B3C6)

private fun werEvTitle(): AnnotatedString = buildAnnotatedString {
    val muted = SpanStyle(color = WerEvMuted)
    val strong = SpanStyle(color = Navy)
    withStyle(muted) { append("W") }
    withStyle(strong) { append("E") }
    withStyle(muted) { append("'") }
    withStyle(strong) { append("R EV") }
}

@Composable
private fun WerEvLogo(
    compact: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            Modifier.size(if (compact) 34.dp else 60.dp)
        ) {
            val stroke = size.minDimension * .105f
            val inset = stroke / 2
            val dialSize = Size(
                size.width - stroke,
                size.height - stroke
            )

            drawArc(
                color = Navy,
                startAngle = 145f,
                sweepAngle = 218f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = dialSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            drawArc(
                color = Hd.Muted,
                startAngle = -30f,
                sweepAngle = 65f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = dialSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            val center = Offset(
                size.width * .50f,
                size.height * .53f
            )

            drawLine(
                color = Navy,
                start = center,
                end = Offset(
                    size.width * .67f,
                    size.height * .26f
                ),
                strokeWidth = stroke * .62f,
                cap = StrokeCap.Round
            )

            drawCircle(
                color = Navy,
                radius = stroke * .67f,
                center = center
            )
        }

        Spacer(Modifier.width(if (compact) 8.dp else 16.dp))

        Text(
            werEvTitle(),
            color = Navy,
            fontSize = if (compact) 20.sp else 42.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ───────────────────── 홈 화면 ─────────────────────
@Composable
private fun WerEvWelcome(
    soc: Int,
    fuel: Int,
    region: Region,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(Surface, Bg))
        )
    ) {
        val scale = minOf(
            maxWidth.value / 1000f,
            maxHeight.value / 650f
        ).coerceAtLeast(0.1f)

        val density = LocalDensity.current
        val batteryPct = soc.coerceIn(0, 100)
        val fuelPct = fuel.coerceIn(0, 100)

        val evKm = (batteryPct / 100.0 * 200).roundToInt()
        val fuelKm = (fuelPct / 100.0 * 765).roundToInt()

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density * scale,
                fontScale = density.fontScale
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 40.dp,
                        vertical = 24.dp
                    )
            ) {
                // 상단 바
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WerEvLogo(compact = true)

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = "Hi, ",
                        color = Ink,
                        fontSize = 13.sp
                    )

                    Text(
                        text = "EREV",
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "SANTA FE EREV",
                    color = Ink,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.6).sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "나의 주행에 맞춘 에너지 코치",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                // 상태 카드와 차량
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.width(220.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VehicleEnergyCard(
                            title = "배터리",
                            percent = batteryPct,
                            rangeLabel = "배터리 주행 가능 거리",
                            rangeKm = distValue(evKm.toDouble(), region),
                            unit = distUnit(region),
                            accent = Navy
                        )

                        VehicleEnergyCard(
                            title = "연료",
                            percent = fuelPct,
                            rangeLabel = "연료 주행 가능 거리",
                            rangeKm = distValue(fuelKm.toDouble(), region),
                            unit = distUnit(region),
                            accent = Navy
                        )
                    }

                    // 오른쪽 영역 중앙에 차량 배치
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 차량 뒤에 그릴 바닥 그림자
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .padding(bottom = 30.dp)
                                    .fillMaxWidth(0.55f)
                                    .height(22.dp)
                            ) {
                                drawOval(
                                    color = Color.Black.copy(
                                        alpha = 0.05f
                                    )
                                )

                                drawOval(
                                    color = Color.Black.copy(
                                        alpha = 0.09f
                                    ),
                                    topLeft = Offset(
                                        size.width * 0.12f,
                                        size.height * 0.25f
                                    ),
                                    size = Size(
                                        size.width * 0.76f,
                                        size.height * 0.5f
                                    )
                                )
                            }
                        }

                        CarHero3D(
                            Modifier
                                .fillMaxSize()
                                .scale(1.25f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 하단 메뉴
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ringMenus.forEach { item ->
                        RingMenuCard(
                            menu = item,
                            onClick = {
                                onNavigate(item.destination)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(118.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "가상 차량 데이터를 이용한 시연 화면",
                    color = Faint,
                    fontSize = 9.sp
                )
            } // Column
        } // CompositionLocalProvider
    } // BoxWithConstraints
} // WerEvWelcome 종료

@Composable
private fun VehicleEnergyCard(
    title: String,
    percent: Int,
    rangeLabel: String,
    rangeKm: Int,
    accent: Color,
    unit: String = "km"
) {
    val safePercent = percent.coerceIn(0, 100)
    val isBattery = title == "배터리"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .card(16)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = Sub,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "$safePercent%",
                color = accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = rangeLabel,
                color = Sub,
                fontSize = 10.sp,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = rangeKm.coerceAtLeast(0).toString(),
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = unit,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        bottom = 5.dp
                    ),
                    color = Sub,
                    fontSize = 12.sp
                )
            }
        }

        EnergyLevelIcon(
            isBattery = isBattery,
            percent = safePercent,
            accent = accent,
            modifier = Modifier
                .width(64.dp)
                .height(116.dp)
        )
    }
}

@Composable
private fun EnergyLevelIcon(
    isBattery: Boolean,
    percent: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val outlineWidth = 3.dp.toPx()
        val level = percent.coerceIn(0, 100) / 100f

        val bodyTop = h * 0.14f
        val bodyBottom = h * 0.96f

        // 배터리: 직사각형 몸체
        // 연료통: 어깨가 비대칭인 몸체
        val body = Path().apply {
            if (isBattery) {
                moveTo(w * 0.26f, bodyTop)
                lineTo(w * 0.74f, bodyTop)
                quadraticBezierTo(
                    w * 0.94f, bodyTop,
                    w * 0.94f, h * 0.28f
                )
                lineTo(w * 0.94f, h * 0.82f)
                quadraticBezierTo(
                    w * 0.94f, bodyBottom,
                    w * 0.74f, bodyBottom
                )
                lineTo(w * 0.26f, bodyBottom)
                quadraticBezierTo(
                    w * 0.06f, bodyBottom,
                    w * 0.06f, h * 0.82f
                )
                lineTo(w * 0.06f, h * 0.28f)
                quadraticBezierTo(
                    w * 0.06f, bodyTop,
                    w * 0.26f, bodyTop
                )
            } else {
                moveTo(w * 0.08f, h * 0.36f)
                quadraticBezierTo(
                    w * 0.08f, h * 0.27f,
                    w * 0.26f, h * 0.24f
                )
                lineTo(w * 0.48f, h * 0.15f)
                quadraticBezierTo(
                    w * 0.54f, h * 0.12f,
                    w * 0.66f, h * 0.12f
                )
                lineTo(w * 0.74f, h * 0.12f)
                quadraticBezierTo(
                    w * 0.94f, h * 0.12f,
                    w * 0.94f, h * 0.28f
                )
                lineTo(w * 0.94f, h * 0.82f)
                quadraticBezierTo(
                    w * 0.94f, bodyBottom,
                    w * 0.74f, bodyBottom
                )
                lineTo(w * 0.28f, bodyBottom)
                quadraticBezierTo(
                    w * 0.08f, bodyBottom,
                    w * 0.08f, h * 0.82f
                )
            }

            close()
        }

        // 상단 단자 또는 연료 주입구
        if (isBattery) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.37f, h * 0.06f),
                size = Size(w * 0.26f, h * 0.10f),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        3.dp.toPx(),
                        3.dp.toPx()
                    )
            )
        } else {
            drawRect(
                color = accent,
                topLeft = Offset(w * 0.20f, h * 0.15f),
                size = Size(w * 0.14f, h * 0.12f)
            )

            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.12f, h * 0.10f),
                size = Size(w * 0.30f, h * 0.08f),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        2.dp.toPx(),
                        2.dp.toPx()
                    )
            )
        }

        // 빈 공간
        drawPath(
            path = body,
            color = Color(0xFFEAF2FA)
        )

        // 실제 잔량에 비례해 아래부터 채우기
        clipPath(body) {
            val fillHeight = (bodyBottom - bodyTop) * level

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.85f),
                        accent
                    ),
                    startY = bodyTop,
                    endY = bodyBottom
                ),
                topLeft = Offset(
                    0f,
                    bodyBottom - fillHeight
                ),
                size = Size(w, fillHeight)
            )
        }

        drawPath(
            path = body,
            color = accent,
            style = Stroke(width = outlineWidth)
        )

        // 연료통에만 손잡이 구멍 표시
        if (!isBattery) {
            val handle = Path().apply {
                moveTo(w * 0.56f, h * 0.20f)
                lineTo(w * 0.73f, h * 0.20f)
                quadraticBezierTo(
                    w * 0.82f, h * 0.20f,
                    w * 0.82f, h * 0.27f
                )
                lineTo(w * 0.82f, h * 0.32f)
                quadraticBezierTo(
                    w * 0.82f, h * 0.34f,
                    w * 0.77f, h * 0.33f
                )
                lineTo(w * 0.55f, h * 0.28f)
                quadraticBezierTo(
                    w * 0.47f, h * 0.26f,
                    w * 0.50f, h * 0.23f
                )
                quadraticBezierTo(
                    w * 0.51f, h * 0.20f,
                    w * 0.56f, h * 0.20f
                )
                close()
            }

            drawPath(handle, color = Surface)
            drawPath(
                path = handle,
                color = accent,
                style = Stroke(width = outlineWidth)
            )
        }
    }
}

@Composable
private fun RingMenuCard(
    menu: RingMenu,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .card(16)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 20.dp,
                vertical = 18.dp
            ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 아이콘과 화살표
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(
                menu.icon,
                Ink,
                size = 28.dp
            )

            Spacer(Modifier.weight(1f))

            LineIcon(
                Ic.ArrowRight,
                Faint,
                size = 18.dp
            )
        }

        // 제목과 설명
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = menuBrandTitle(menu.title),
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = menu.detail,
                color = Sub,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun menuBrandTitle(title: String): AnnotatedString =
    buildAnnotatedString {
        if (title.startsWith("WE'R")) {
            withStyle(
                SpanStyle(color = Color(0xFFA7B3C6))
            ) {
                append("W")
            }

            withStyle(SpanStyle(color = Navy)) {
                append("E")
            }

            withStyle(
                SpanStyle(color = Color(0xFFA7B3C6))
            ) {
                append("'")
            }

            withStyle(SpanStyle(color = Navy)) {
                append("R")
            }

            append(title.removePrefix("WE'R"))
        } else {
            append(title)
        }
    }
// ───────────────────── WE'R Score 계산 ─────────────────────

private fun norm01(value: Double): Double =
    value.coerceIn(0.0, 1.0)

private fun economyNormalized(
    baseline: Double,
    current: Double
): Double {
    if (baseline <= 0) return 0.0

    val improvement = (baseline - current) / baseline

    return if (improvement >= 0) {
        norm01(.8 + improvement)
    } else {
        norm01(.8 + 4 * improvement)
    }
}

private fun drivingNormalized(
    ratePer100Km: Double
): Double = norm01((10.0 - ratePer100Km) / 10.0)

// ───────────────────── 에너지 리포트 ─────────────────────

@Composable
private fun ESmartDashboard(
    chargingDays: Int,
    driveScore: Int,
    onChargingHabit: () -> Unit,
    onDriveHabit: () -> Unit
) {
    val completed = chargingDays.coerceIn(0, 5)
    val charge = 45.0 * completed / 5.0
    val economic = 35.0 * economyNormalized(172.0, 148.0)

    val accelerationRate =
        (3.4 - (driveScore - 82) * 0.05).coerceIn(0.0, 10.0)

    val drive = 20.0 * drivingNormalized(accelerationRate)
    val total = charge + economic + drive
    val savingsPercent = (172.0 - 148.0) / 172.0 * 100

    val chargeColor = Navy                  // 충전 습관: 네이비
    val economyColor = Color(0xFF3979D6)     // 에너지 경제성: 블루
    val drivingColor = Color(0xFFA8B1BD)     // 효율적 주행: 청록

    var period by remember { mutableIntStateOf(0) }

    val values = when (period) {
        1 -> listOf(68.0, 72.0, 76.0, total)
        2 -> listOf(61.0, 67.0, 72.0, 76.0, total)
        else -> listOf(
            76.0, 77.0, 79.0, 78.0, 80.0, 81.0, total
        )
    }

    val labels = when (period) {
        1 -> listOf("1주", "2주", "3주", "4주")
        2 -> listOf("1월", "3월", "6월", "9월", "현재")
        else -> listOf("월", "화", "수", "목", "금", "토", "일")
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val scale = (maxWidth.value / 1100f).coerceAtLeast(0.1f)

        CompositionLocalProvider(
            LocalDensity provides Density(
                density.density * scale,
                density.fontScale
            )
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 제목
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = menuBrandTitle("WE'R Score"),
                            color = Ink,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            "나의 EREV 에너지 활용 리포트",
                            color = Sub,
                            fontSize = 13.sp
                        )
                    }

                    // 점수 올랐을 때: 만세 점프 + 꽃가루로 기뻐하는 르르르
                    RrrMomentAnimation(
                        moment = RrrMoment.Cheer,
                        modifier = Modifier.size(width = 118.dp, height = 120.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(
                        Modifier
                            .card(14)
                            .padding(
                                horizontal = 18.dp,
                                vertical = 14.dp
                            )
                    ) {
                        Text(
                            "지난주보다 3점 올랐어요",
                            color = Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            "충전 계획을 꾸준히 지킨 덕분이에요",
                            color = Sub,
                            fontSize = 11.sp
                        )
                    }
                }

                // 종합 점수 + 항목별 점수
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RingPanel(
                        Modifier.weight(0.85f).height(265.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(chargeColor)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                "종합 에너지 점수",
                                color = Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        GaugeScore(
                            total,
                            Modifier.fillMaxWidth().weight(1f)
                        )

                        Text(
                            "충전 · 경제성 · 주행 습관 종합",
                            color = Sub,
                            fontSize = 11.sp,
                            modifier = Modifier.align(
                                Alignment.CenterHorizontally
                            )
                        )
                    }

                    RingPanel(
                        Modifier.weight(2.15f).height(265.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "항목별 점수",
                                color = Ink,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                "100점 만점",
                                color = chargeColor,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Hd.NavySoft)
                                    .padding(
                                        horizontal = 9.dp,
                                        vertical = 5.dp
                                    )
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiniKpiCard(
                                title = "충전 습관",
                                score = charge,
                                max = 45.0,
                                tint = chargeColor,
                                detail = "충전 계획 ${completed}회 / 5회 실천",
                                modifier = Modifier.weight(1f)
                            )

                            MiniKpiCard(
                                title = "에너지 경제성",
                                score = economic,
                                max = 35.0,
                                tint = economyColor,
                                detail = "기준보다 km당 24원 아꼈어요",
                                modifier = Modifier.weight(1f)
                            )

                            MiniKpiCard(
                                title = "효율적 주행",
                                score = drive,
                                max = 20.0,
                                tint = drivingColor,
                                detail = "급가속 ${
                                    "%.1f".format(accelerationRate)
                                }회 / 100km",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 하단 카드 영역
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 첫 번째: 맞춤 코칭
                    RingPanel(
                        Modifier.weight(1.05f).height(280.dp)
                    ) {
                        Text(
                            text = "맞춤 코칭",
                            color = Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(14.dp))

                        CoachingLine(
                            "✓",
                            "충전 계획 ${completed}회를 실천했어요.",
                            drivingColor
                        )

                        CoachingLine(
                            "✓",
                            "에너지 비용 약 ${
                                "%.0f".format(savingsPercent)
                            }% 절감",
                            drivingColor
                        )

                        CoachingLine(
                            "↑",
                            "급가속을 줄여 점수를 높여보세요.",
                            Navy
                        )

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "작은 습관이 더 나은 주행을 만들어요.",
                            color = Sub,
                            fontSize = 10.sp
                        )
                    }

                    // 두 번째: 점수 변화
                    RingPanel(
                        Modifier.weight(1.4f).height(280.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "점수 변화",
                                color = Ink,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // 기간 선택 버튼
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Field)
                                    .padding(3.dp)
                            ) {
                                listOf("주간", "월간", "연간")
                                    .forEachIndexed { index, label ->
                                        val active = period == index

                                        Text(
                                            text = label,
                                            color = if (active) {
                                                Color.White
                                            } else {
                                                Sub
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(
                                                    if (active) Navy
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    period = index
                                                }
                                                .padding(
                                                    horizontal = 10.dp,
                                                    vertical = 8.dp
                                                )
                                        )
                                    }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        ScoreLineChart(
                            values = values,
                            labels = labels,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    // 세 번째: 점수 구성
                    RingPanel(
                        Modifier.weight(1f).height(280.dp)
                    ) {
                        Text(
                            text = "점수 구성",
                            color = Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(22.dp))

                        // 항목별 배점 비율 막대
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp)),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                Modifier
                                    .weight(45f)
                                    .fillMaxHeight()
                                    .background(chargeColor)
                            )

                            Box(
                                Modifier
                                    .weight(35f)
                                    .fillMaxHeight()
                                    .background(economyColor)
                            )

                            Box(
                                Modifier
                                    .weight(20f)
                                    .fillMaxHeight()
                                    .background(drivingColor)
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        ScoreLegend(
                            "충전 습관",
                            charge,
                            45,
                            chargeColor
                        )

                        ScoreLegend(
                            "에너지 경제성",
                            economic,
                            35,
                            economyColor
                        )

                        ScoreLegend(
                            "효율적 주행",
                            drive,
                            20,
                            drivingColor
                        )

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = "배점 비율 45% · 35% · 20%",
                            color = Faint,
                            fontSize = 10.sp
                        )
                    }
                }
                // 하단 데모 버튼과 안내 문구 제거
            }
        }
    }
}

@Composable
private fun RingPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .card(16)
            .padding(20.dp),
        content = content
    )
}
// ───────────────────── 점수 게이지 ─────────────────────

@Composable
private fun GaugeScore(
    score: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier.fillMaxSize().padding(8.dp)
        ) {
            val thickness = 13.dp.toPx()

            val diameter = (
                    minOf(size.width, size.height * 1.25f) -
                            thickness * 2
                    ).coerceAtLeast(1f)

            val left = (size.width - diameter) / 2
            val top = (size.height - diameter) / 2 + 14.dp.toPx()

            val arcSize = Size(diameter, diameter)
            val origin = Offset(left, top)

            drawArc(
                color = Field,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = origin,
                size = arcSize,
                style = Stroke(thickness, cap = StrokeCap.Round)
            )

            drawArc(
                color = Navy,
                startAngle = 150f,
                sweepAngle = 240f *
                        (score / 100).toFloat().coerceIn(0f, 1f),
                useCenter = false,
                topLeft = origin,
                size = arcSize,
                style = Stroke(thickness, cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier.padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${score.toInt()}",
                color = Ink,
                fontSize = 54.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "WE'R Score",
                color = Sub,
                fontSize = 11.sp
            )
        }
    }
}

// ───────────────────── 항목별 점수 카드 ─────────────────────

@Composable
private fun MiniKpiCard(
    title: String,
    score: Double,
    max: Double,
    tint: Color,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(Field)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            Text(
                "${max.toInt()}점",
                color = Faint,
                fontSize = 12.sp
            )
        }

        when (title) {
            "충전 습관" -> {
                Box(
                    Modifier.fillMaxWidth().height(66.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(60.dp)) {
                        val stroke = 6.dp.toPx()
                        val arcSize = Size(
                            size.width - stroke,
                            size.height - stroke
                        )
                        val origin = Offset(stroke / 2, stroke / 2)

                        drawArc(
                            color = Line,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = origin,
                            size = arcSize,
                            style = Stroke(
                                stroke,
                                cap = StrokeCap.Round
                            )
                        )

                        drawArc(
                            color = tint,
                            startAngle = -90f,
                            sweepAngle = 360f *
                                    (score / max).toFloat().coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = origin,
                            size = arcSize,
                            style = Stroke(
                                stroke,
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    Text(
                        "${(score / max * 100).roundToInt()}%",
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            "에너지 경제성" -> {
                // 기준 → 현재 km당 비용 비교 (줄어든 만큼 배지로)
                val base = 172
                val now = 148
                val cut = ((base - now) * 100f / base).roundToInt()
                Row(
                    Modifier.fillMaxWidth().height(66.dp).padding(bottom = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.padding(bottom = 3.dp)) {
                        Text("기준", color = Faint, fontSize = 12.sp)
                        Text(
                            "$base",
                            color = Faint,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    Text(
                        "→",
                        color = Faint,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 10.dp).padding(top = 14.dp)
                    )
                    Column {
                        Text("현재", color = Sub, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$now",
                                color = Ink,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                "원/km",
                                color = Sub,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "▼$cut%",
                        color = Navy,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Hd.NavySoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            else -> {
                // 주행 습관 달성도: 10칸 세그먼트 미터
                val ratio = (score / max).toFloat().coerceIn(0f, 1f)
                Column(
                    Modifier.fillMaxWidth().height(66.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${(ratio * 100).roundToInt()}",
                            color = Ink,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "%",
                            color = Sub,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "주행 습관 달성도",
                            color = Faint,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().height(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(10) { i ->
                            val fill = (ratio * 10 - i).coerceIn(0f, 1f)
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Line)
                            ) {
                                if (fill > 0f) {
                                    Box(
                                        Modifier.fillMaxWidth(fill).fillMaxHeight()
                                            .background(Navy)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "${"%.1f".format(score)} / ${max.toInt()}점",
                color = if (tint == Navy) Navy else Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                detail,
                color = Sub,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ───────────────────── 점수 추이 차트 ─────────────────────

@Composable
private fun ScoreLineChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val progress = remember {
        androidx.compose.animation.core.Animatable(0f)
    }

    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 800
            )
        )
    }

    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 100.0

    // 데이터 주변으로 축 범위를 좁혀 변화 강조
    // 최소 10점 범위 유지
    val axisMin = (
            kotlin.math.floor((minValue - 2.0) / 2.0) * 2.0
            ).coerceIn(0.0, 90.0)

    val axisMax = maxOf(
        kotlin.math.ceil((maxValue + 2.0) / 2.0) * 2.0,
        axisMin + 10.0
    ).coerceAtMost(100.0)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (values.size < 2) return@Canvas

            val paddingX = 12.dp.toPx()
            val paddingY = 14.dp.toPx()

            val chartWidth =
                (size.width - paddingX * 2).coerceAtLeast(1f)
            val chartHeight =
                (size.height - paddingY * 2).coerceAtLeast(1f)

            val bottom = paddingY + chartHeight

            // 배경 눈금
            for (index in 0..3) {
                val y = paddingY + chartHeight * index / 3f

                drawLine(
                    color = Line,
                    start = Offset(paddingX, y),
                    end = Offset(paddingX + chartWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 표시할 점 위치
            val points = values.mapIndexed { index, value ->
                val ratio = (
                        (value - axisMin) / (axisMax - axisMin)
                        ).toFloat().coerceIn(0f, 1f)

                Offset(
                    x = paddingX +
                            chartWidth * index / (values.size - 1),
                    y = bottom -
                            chartHeight * ratio * progress.value
                )
            }

            val linePath =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, points.first().y)

                    points.drop(1).forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }

            val fillPath =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, bottom)

                    points.forEach { point ->
                        lineTo(point.x, point.y)
                    }

                    lineTo(points.last().x, bottom)
                    close()
                }

            // 선 아래 그라데이션
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Navy.copy(alpha = 0.24f),
                        Navy.copy(alpha = 0.02f)
                    ),
                    startY = paddingY,
                    endY = bottom
                )
            )

            // 점수 연결선
            drawPath(
                path = linePath,
                color = Navy,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // 각 날짜의 점
            points.forEach { point ->
                drawCircle(
                    color = Navy,
                    radius = 4.dp.toPx(),
                    center = point
                )

                drawCircle(
                    color = Color.White,
                    radius = 1.8.dp.toPx(),
                    center = point
                )
            }

            // 마지막 점 강조
            val lastPoint = points.last()

            drawCircle(
                color = Navy.copy(alpha = 0.12f),
                radius = 10.dp.toPx(),
                center = lastPoint
            )

            drawCircle(
                color = Navy,
                radius = 5.dp.toPx(),
                center = lastPoint
            )

            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = lastPoint
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    color = Sub,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "최근 ${
                values.lastOrNull()?.roundToInt() ?: 0
            }점 · 세로축 ${
                axisMin.toInt()
            }~${axisMax.toInt()}점 · 가상 데이터",
            color = Sub,
            fontSize = 10.sp
        )
    }
}

// ───────────────────── 점수 범례 ─────────────────────

@Composable
private fun ScoreLegend(
    name: String,
    score: Double,
    max: Int,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            name,
            color = Sub,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            "${"%.1f".format(score)} / $max",
            color = Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ───────────────────── 맞춤 코칭 ─────────────────────

@Composable
private fun CoachingLine(
    icon: String,
    line: String,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Field)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            icon,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.width(8.dp))

        Text(
            line,
            color = Sub,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun RouteChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (active) Surface else Ink,
        fontSize = 13.sp,
        fontWeight = if (active) {
            FontWeight.SemiBold
        } else {
            FontWeight.Medium
        },
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (active) {
                    Navy
                } else {
                    Surface
                }
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    Navy
                } else {
                    Line
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp
            )
    )
}