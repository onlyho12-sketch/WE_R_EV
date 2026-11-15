package com.example.rrr.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "WerEv/Vehicle"

/** 값이 어디서 왔는지 — 화면에 작은 배지로 보여준다 */
enum class Source { PLEOS, DEMO }

data class EnergyState(
    /** EvBattery.getEvBatteryStateOfCharge */
    val socPct: Int,
    /** Fuel.getFuelPercent */
    val fuelPct: Int,
    /** EvBattery.getSinceChargeEvEfficiency — 단위는 차량 설정을 따름 (표시용) */
    val evEfficiency: Float? = null,
    /** Fuel.getCurrentFuelEconomy — 단위는 차량 설정을 따름 (표시용) */
    val fuelEconomy: Float? = null,
    /** Odometer.getTripCurrentDrivingDistance */
    val tripDistance: Float? = null,
    val source: Source = Source.DEMO,
)

interface VehicleGateway {
    val state: StateFlow<EnergyState>
    fun start()
    fun stop()

    /** EvBattery.setEvChargePercentLimit */
    suspend fun setChargeLimit(pct: Int): Result<Unit>

    /** EvBattery.setEvChargeSwitch */
    suspend fun setCharging(on: Boolean): Result<Unit>

    /** 시연용 값 변경 — source 가 DEMO 일 때만 반영된다 (실차 값이 있으면 무시) */
    fun setDemo(soc: Int? = null, fuel: Int? = null)
}

/** SDK 가 없는 환경(일반 폰·일반 에뮬레이터)용: 화면의 −/+ 와 프리셋 값 그대로 */
class DemoVehicleGateway : VehicleGateway {
    private val _state = MutableStateFlow(EnergyState(socPct = 62, fuelPct = 48))
    override val state: StateFlow<EnergyState> = _state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
    override suspend fun setChargeLimit(pct: Int): Result<Unit> = Result.success(Unit)
    override suspend fun setCharging(on: Boolean): Result<Unit> = Result.success(Unit)
    override fun setDemo(soc: Int?, fuel: Int?) {
        _state.update {
            it.copy(
                socPct = (soc ?: it.socPct).coerceIn(0, 100),
                fuelPct = (fuel ?: it.fuelPct).coerceIn(0, 100),
            )
        }
    }
}

/**
 * Pleos Vehicle SDK 구현.
 *
 * Vehicle SDK 는 IPC 라 결과가 onComplete / onFailed 람다로 온다.
 * 연결·권한에 실패하면 로그만 남기고 DEMO 값으로 계속 동작한다 (앱이 죽지 않게).
 */
class PleosVehicleGateway(private val context: Context) : VehicleGateway {
    private val _state = MutableStateFlow(EnergyState(socPct = 62, fuelPct = 48))
    override val state: StateFlow<EnergyState> = _state.asStateFlow()

    private var vehicle: Vehicle? = null

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            value?.let { v -> _state.update { it.copy(socPct = v.coerceIn(0, 100), source = Source.PLEOS) } }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "SOC listener failed: ${e.message}")
        }
    }

    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(value: Int?) {
            value?.let { v -> _state.update { it.copy(fuelPct = v.coerceIn(0, 100), source = Source.PLEOS) } }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Fuel listener failed: ${e.message}")
        }
    }

    override fun start() {
        if (vehicle != null) stop()
        try {
            val v = Vehicle(context.applicationContext)
            v.initialize()
            vehicle = v

            val battery = v.getEvBattery()
            battery.getEvBatteryStateOfCharge(
                { soc ->
                    Log.i(TAG, "SOC from Pleos: $soc")
                    val v: Int? = soc
                    if (v != null) _state.update { it.copy(socPct = v.coerceIn(0, 100), source = Source.PLEOS) }
                },
                { e -> Log.w(TAG, "getEvBatteryStateOfCharge failed → DEMO: ${e.message}") },
            )
            battery.registerEvBatteryStateOfCharge(socListener)
            battery.getSinceChargeEvEfficiency(
                { eff -> val v: Float? = eff; _state.update { it.copy(evEfficiency = v) } },
                { e -> Log.w(TAG, "getSinceChargeEvEfficiency failed: ${e.message}") },
            )

            val fuel = v.getFuel()
            fuel.getFuelPercent(
                { pct ->
                    Log.i(TAG, "Fuel from Pleos: $pct")
                    val v: Int? = pct
                    if (v != null) _state.update { it.copy(fuelPct = v.coerceIn(0, 100), source = Source.PLEOS) }
                },
                { e -> Log.w(TAG, "getFuelPercent failed → DEMO: ${e.message}") },
            )
            fuel.registerFuelPercent(fuelListener)
            fuel.getCurrentFuelEconomy(
                { eco -> val v: Float? = eco; _state.update { it.copy(fuelEconomy = v) } },
                { e -> Log.w(TAG, "getCurrentFuelEconomy failed: ${e.message}") },
            )

            v.getOdometer().getTripCurrentDrivingDistance(
                { d -> val v: Float? = d; _state.update { it.copy(tripDistance = v) } },
                { e -> Log.w(TAG, "getTripCurrentDrivingDistance failed: ${e.message}") },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK unavailable → DEMO: $t")
            vehicle = null
        }
    }

    override fun stop() {
        val v = vehicle ?: return
        vehicle = null
        runCatching { v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener) }
        runCatching { v.getFuel().unregisterFuelPercent(fuelListener) }
        runCatching { v.release() }
    }

    override suspend fun setChargeLimit(pct: Int): Result<Unit> = call("setEvChargePercentLimit") { v, done ->
        v.getEvBattery().setEvChargePercentLimit(
            pct.toFloat(),
            { done(Result.success(Unit)) },
            { e -> done(Result.failure(e)) },
        )
    }

    override suspend fun setCharging(on: Boolean): Result<Unit> = call("setEvChargeSwitch") { v, done ->
        v.getEvBattery().setEvChargeSwitch(
            on,
            { done(Result.success(Unit)) },
            { e -> done(Result.failure(e)) },
        )
    }

    /** 콜백 API → suspend. 실차 연결이 없으면(DEMO) 성공으로 처리해 시연 흐름이 끊기지 않게 한다 */
    private suspend fun call(name: String, block: (Vehicle, (Result<Unit>) -> Unit) -> Unit): Result<Unit> {
        val v = vehicle
        if (v == null || _state.value.source == Source.DEMO) return Result.success(Unit)
        val result = withTimeoutOrNull(4000) {
            suspendCancellableCoroutine { cont ->
                try {
                    block(v) { r -> if (cont.isActive) cont.resume(r) }
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(Result.failure(t))
                }
            }
        } ?: Result.failure(IllegalStateException("$name timeout"))
        result.exceptionOrNull()?.let { Log.w(TAG, "$name failed: ${it.message}") }
        return result
    }

    override fun setDemo(soc: Int?, fuel: Int?) {
        _state.update {
            if (it.source == Source.PLEOS) it
            else it.copy(
                socPct = (soc ?: it.socPct).coerceIn(0, 100),
                fuelPct = (fuel ?: it.fuelPct).coerceIn(0, 100),
            )
        }
    }
}
