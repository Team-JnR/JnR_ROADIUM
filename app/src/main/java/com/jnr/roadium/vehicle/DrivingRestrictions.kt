package com.jnr.roadium.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 규제 상태(CarUxRestrictions)를 구독한다 (Pleos '주행 규제 개발 가이드').
 * DO(distractionOptimized)를 선언한 앱은 플랫폼이 내려주는 ActiveRestrictions를 직접 반영해야 한다.
 *
 * ROADIUM은 규제가 걸리면(isRequiresDistractionOptimization) 팀 등록·일정 탐색·설정 화면을 잠그고
 * 드라이브 스코어보드·이벤트 카드·큰 버튼만 남긴다. 차량 서비스가 없는 기기에서는 기어 기준으로만 잠근다.
 */
class DrivingRestrictions(private val context: Context) {
    private val _restricted = MutableStateFlow(false)
    /** true면 주행 규제 중 (주행 화면만 허용) */
    val restricted: StateFlow<Boolean> = _restricted

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        if (car != null) return
        if (Build.VERSION.SDK_INT < 29) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            apply(m.currentCarUxRestrictions)
            m.registerListener { apply(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "CarUxRestrictions unavailable: ${e.message}")
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private fun apply(r: CarUxRestrictions?) {
        val restricted = r != null && r.isRequiresDistractionOptimization &&
            (r.activeRestrictions and (CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP or CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD or CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT)) != 0
        if (restricted != _restricted.value) Log.i(TAG, "UX restricted → $restricted (active=${r?.activeRestrictions})")
        _restricted.value = restricted
    }

    companion object {
        private const val TAG = "DrivingRestrictions"
    }
}
