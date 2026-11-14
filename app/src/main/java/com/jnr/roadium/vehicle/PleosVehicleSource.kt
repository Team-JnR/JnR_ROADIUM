package com.jnr.roadium.vehicle

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import com.jnr.roadium.SdkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Gear(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("-") }

/**
 * Pleos Vehicle SDK로 기어 상태를 구독한다.
 * P단에서 벗어나면 주행(드라이브 스코어보드 + 예약 중계 시작), 다시 P단이면 주차(리캡)로 본다.
 * 모든 콜백은 메인 스레드로 옮겨 [onGear]로 전달한다.
 */
class PleosVehicleSource(
    private val context: Context,
    private val onGear: (Gear) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var cleanup: (() -> Unit)? = null

    private val _state = MutableStateFlow(SdkState.CHECKING)
    val state: StateFlow<SdkState> = _state

    /** Pleos 차량 서비스가 설치된 기기(Pleos Connect 에뮬레이터/실차)인지 */
    private fun serviceAvailable(): Boolean =
        context.packageManager.queryIntentServices(Intent("ai.pleos.playground.vehicle.VEHICLE_START"), 0).isNotEmpty()

    fun start() {
        if (vehicle != null) return
        if (!serviceAvailable()) {
            _state.value = SdkState.UNAVAILABLE
            return
        }
        try {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            val api = v.getGear()
            api.getCurrentGearSelectionState({ g ->
                main.post {
                    _state.value = SdkState.CONNECTED
                    onGear(map(g))
                }
            }, { e ->
                Log.w(TAG, "gear get failed: ${e.message}")
                main.post { if (_state.value != SdkState.CONNECTED) _state.value = SdkState.UNAVAILABLE }
            })
            val listener = object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) {
                    main.post {
                        _state.value = SdkState.CONNECTED
                        onGear(map(gear))
                    }
                }

                override fun onFailed(e: Exception) {
                    Log.w(TAG, "gear listener failed: ${e.message}")
                }
            }
            api.registerGearSelection(listener)
            cleanup = { api.unregisterGearSelection(listener) }
        } catch (e: Throwable) {
            Log.e(TAG, "Vehicle SDK init failed", e)
            _state.value = SdkState.UNAVAILABLE
            runCatching { vehicle?.release() }
            vehicle = null
        }
    }

    fun stop() {
        runCatching { cleanup?.invoke() }
        cleanup = null
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle?.release() }
        vehicle = null
        _state.value = SdkState.CHECKING
    }

    private fun map(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE // D 및 수동 단수
    }

    companion object {
        private const val TAG = "PleosVehicleSource"
    }
}
