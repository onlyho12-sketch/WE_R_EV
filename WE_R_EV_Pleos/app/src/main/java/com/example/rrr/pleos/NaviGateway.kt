package com.example.rrr.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.example.rrr.GasStation
import com.example.rrr.LatLng
import com.example.rrr.Place
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WerEv/Navi"

/** 내비에 설정된 목적지 */
data class NaviDest(val name: String, val at: LatLng)

sealed interface NaviEvent {
    /** 길안내 시작 → 트립 시작 */
    data object RouteStarted : NaviEvent
    /** 경유지가 내비 경로에 반영됨 */
    data object WaypointChanged : NaviEvent
    /** 경유지(주유소) 도착 */
    data class WaypointArrived(val name: String) : NaviEvent
    /** 목적지 도착 → 트립 종료 */
    data class DestinationArrived(val name: String) : NaviEvent
    data class Error(val code: String) : NaviEvent
}

interface NaviGateway {
    val source: StateFlow<Source>
    /** NaviHelper.getDestinationInfo / onDestinationChanged */
    val destination: StateFlow<NaviDest?>
    val events: SharedFlow<NaviEvent>
    fun start()
    fun stop()

    /** NaviHelper.addWaypoint(WaypointIndex.FIRST) — 결과는 events(WaypointChanged / Error)로 온다 */
    fun addFuelStop(station: GasStation): Result<Unit>

    /** NaviHelper.requestRoute — 내비에 목적지를 보내 길안내를 시작 */
    fun requestRoute(dest: Place): Result<Unit>
}

/** SDK 가 없는 환경용: 목적지는 앱 안에서 직접 고르고, 경유지 등록은 화면 표시만 */
class DemoNaviGateway : NaviGateway {
    override val source: StateFlow<Source> = MutableStateFlow(Source.DEMO)
    override val destination: StateFlow<NaviDest?> = MutableStateFlow(null)
    override val events: SharedFlow<NaviEvent> = MutableSharedFlow()
    override fun start() = Unit
    override fun stop() = Unit
    override fun addFuelStop(station: GasStation): Result<Unit> = Result.success(Unit)
    override fun requestRoute(dest: Place): Result<Unit> = Result.success(Unit)
}

class PleosNaviGateway(private val context: Context) : NaviGateway {
    private val _source = MutableStateFlow(Source.DEMO)
    override val source: StateFlow<Source> = _source.asStateFlow()

    private val _destination = MutableStateFlow<NaviDest?>(null)
    override val destination: StateFlow<NaviDest?> = _destination.asStateFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private var helper: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(info: DestinationInfo) {
            val valid = info.latitude != 0.0 || info.longitude != 0.0
            Log.i(TAG, "destination: ${info.name} (${info.latitude}, ${info.longitude})")
            _destination.value = if (valid) NaviDest(info.name.ifBlank { "내비 목적지" }, LatLng(info.latitude, info.longitude)) else null
        }

        override fun onDestinationChanged() {
            runCatching { helper?.getDestinationInfo() }
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            _events.tryEmit(NaviEvent.RouteStarted)
            runCatching { helper?.getDestinationInfo() }
        }

        override fun onWaypointChanged(info: WaypointChangedInfo) {
            _events.tryEmit(NaviEvent.WaypointChanged)
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            _events.tryEmit(NaviEvent.WaypointArrived(info.name))
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            _events.tryEmit(NaviEvent.DestinationArrived(info.name))
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "navi error: $code")
            _events.tryEmit(NaviEvent.Error(code.name))
        }
    }

    override fun start() {
        if (helper != null) stop()
        try {
            val h = NaviHelper(context.applicationContext)
            helper = h
            h.initialize(
                {
                    Log.i(TAG, "NaviHelper connected")
                    _source.value = Source.PLEOS
                    runCatching {
                        h.addListener(listener)
                        h.getDestinationInfo()
                    }.onFailure { Log.w(TAG, "listener/destination failed: $it") }
                    Unit
                },
                { t ->
                    Log.w(TAG, "NaviHelper unavailable → DEMO: $t")
                    _source.value = Source.DEMO
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper SDK unavailable → DEMO: $t")
            helper = null
        }
    }

    override fun stop() {
        val h = helper ?: return
        helper = null
        _source.value = Source.DEMO
        runCatching { h.removeListener(listener) }
        runCatching { h.release() }
    }

    override fun addFuelStop(station: GasStation): Result<Unit> {
        val h = helper
        if (h == null || _source.value == Source.DEMO) return Result.success(Unit)
        return runCatching {
            h.addWaypoint(
                RequestWaypointInfo(
                    latitude = station.at.lat,
                    longitude = station.at.lon,
                    poiName = station.name,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = "",
                    address = "",
                    poiSubId = "",
                )
            )
        }.onFailure { Log.w(TAG, "addWaypoint failed: $it") }
    }

    override fun requestRoute(dest: Place): Result<Unit> {
        val h = helper
        if (h == null || _source.value == Source.DEMO) return Result.success(Unit)
        return runCatching {
            h.requestRoute(
                RouteInfo(
                    latitude = dest.at.lat, longitude = dest.at.lon, poiName = dest.name,
                    poiId = "", address = "", poiSubId = "",
                )
            )
        }.onFailure { Log.w(TAG, "requestRoute failed: $it") }
    }
}
