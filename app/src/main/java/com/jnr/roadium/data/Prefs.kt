package com.jnr.roadium.data

import android.content.Context
import com.jnr.roadium.model.AlertLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 응원 팀·알림 범위·효과음 켜기를 기억한다 */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("roadium", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(prefs.getStringSet(KEY_FAV, emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites

    fun setFavorites(ids: Set<String>) {
        _favorites.value = ids
        prefs.edit().putStringSet(KEY_FAV, ids).apply()
    }

    private val _alertLevel = MutableStateFlow(
        AlertLevel.entries.firstOrNull { it.name == prefs.getString(KEY_LEVEL, null) } ?: AlertLevel.MAJOR
    )
    val alertLevel: StateFlow<AlertLevel> = _alertLevel

    fun setAlertLevel(level: AlertLevel) {
        _alertLevel.value = level
        prefs.edit().putString(KEY_LEVEL, level.name).apply()
    }

    private val _soundOn = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundOn: StateFlow<Boolean> = _soundOn

    fun setSoundOn(on: Boolean) {
        _soundOn.value = on
        prefs.edit().putBoolean(KEY_SOUND, on).apply()
    }

    private val _volume = MutableStateFlow(prefs.getFloat(KEY_VOLUME, 0.8f))
    /** 효과음 볼륨 (0.2~1.0) */
    val volume: StateFlow<Float> = _volume

    fun setVolume(v: Float) {
        _volume.value = v.coerceIn(0.2f, 1f)
        prefs.edit().putFloat(KEY_VOLUME, _volume.value).apply()
    }

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_LEVEL = "alert_level"
        const val KEY_SOUND = "sound_on"
        const val KEY_VOLUME = "volume"
    }
}
