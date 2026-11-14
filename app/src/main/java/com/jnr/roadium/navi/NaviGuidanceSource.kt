package com.jnr.roadium.navi

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.jnr.roadium.SdkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pleos NaviHelper SDK로 '곧 내비 음성 안내가 나오는 구간'인지 판단한다.
 *
 * 경로 안내 중(ROUTE_DRIVE / isRouting)이고 다음 회전 안내 지점(TBT)까지 [GUIDANCE_DISTANCE_M] 이내면
 * 안내 구간으로 보고 ROADIUM의 효과음·음성을 미룬다 (기획서: 운전 안내 우선).
 * TBT 거리는 콜백으로도 오지만, 경로 안내 중에는 [POLL_MS]마다 getTBTInfo()로 갱신한다.
 * 경로 안내가 없으면 타이머를 멈춘다 (Pleos 성능 가이드: 불필요한 폴링 최소화).
 */
class NaviGuidanceSource(
    private val context: Context,
    private val onGuidance: (Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "NaviGuidance"
        const val GUIDANCE_DISTANCE_M = 300.0
        private const val POLL_MS = 2_000L
        private const val NAVI_PACKAGE = "ai.pleos.playground.service.navi"
        private const val NAVI_ACTION = "ai.pleos.playground.intent.action.NAVI_START"
    }

    private val main = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null

    private val _state = MutableStateFlow(SdkState.CHECKING)
    val state: StateFlow<SdkState> = _state

    private val _nextTurnM = MutableStateFlow<Double?>(null)
    /** 다음 안내 지점까지 거리(m). 경로 안내 중이 아니면 null */
    val nextTurnM: StateFlow<Double?> = _nextTurnM

    private var routing = false
    private var active = false

    private val poll = object : Runnable {
        override fun run() {
            if (!routing) return
            runCatching { navi?.getTBTInfo() }
            main.postDelayed(this, POLL_MS)
        }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            main.post { setRouting(status == NavigationStatus.ROUTE_DRIVE) }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            main.post { setRouting(routeStateInfo.isRouting) }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            main.post { setRouting(true) }
        }

        override fun onRouteEnded() {
            main.post { setRouting(false) }
        }

        override fun onRouteCancelled() {
            main.post { setRouting(false) }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            main.post { setRouting(false) }
        }

        override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
            val d = tbtInfo.firstOrNull()?.distance
            main.post {
                _nextTurnM.value = if (routing) d else null
                update()
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper error: $errorCode")
        }
    }

    private fun serviceAvailable(): Boolean =
        context.packageManager.queryIntentServices(Intent(NAVI_ACTION).setPackage(NAVI_PACKAGE), 0).isNotEmpty()

    fun start() {
        if (navi != null) return
        if (!serviceAvailable()) {
            _state.value = SdkState.UNAVAILABLE
            return
        }
        try {
            val n = NaviHelper(context)
            navi = n
            n.initialize({
                main.post {
                    _state.value = SdkState.CONNECTED
                    runCatching {
                        n.addListener(listener)
                        n.getRouteStateInfo()
                        n.getTBTInfo()
                    }.onFailure { Log.w(TAG, "NaviHelper query failed: ${it.message}") }
                }
            }, { e ->
                Log.w(TAG, "NaviHelper init failed: ${e.message}")
                main.post { _state.value = SdkState.UNAVAILABLE }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "NaviHelper unavailable: ${e.message}")
            _state.value = SdkState.UNAVAILABLE
            navi = null
        }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { navi?.removeListener(listener) }
        runCatching { navi?.release() }
        navi = null
        routing = false
        _nextTurnM.value = null
        if (active) {
            active = false
            onGuidance(false)
        }
        _state.value = SdkState.CHECKING
    }

    private fun setRouting(value: Boolean) {
        if (value == routing) return
        routing = value
        main.removeCallbacks(poll)
        if (value) main.post(poll) else _nextTurnM.value = null
        update()
    }

    private fun update() {
        val d = _nextTurnM.value
        val now = routing && d != null && d <= GUIDANCE_DISTANCE_M
        if (now != active) {
            active = now
            onGuidance(now)
        }
    }
}
