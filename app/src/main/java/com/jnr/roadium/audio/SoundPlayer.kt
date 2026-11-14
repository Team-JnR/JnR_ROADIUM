package com.jnr.roadium.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.jnr.roadium.R
import com.jnr.roadium.model.SoundKind

/**
 * 경기장 효과음 재생부.
 * - 음원(res/raw, 자체 합성)은 SoundPool에 미리 올려 두고 바로 재생한다. 음원 앞에 60ms 선행 묵음이 들어 있다.
 * - 재생 전에 일시적 오디오 포커스(덕킹 허용)를 요청해 음악은 잠시 작아지게만 하고,
 *   받지 못하면 재생하지 않는다. 재생이 끝나면 즉시 반환한다.
 * - 포커스를 잃으면(통화·음성 명령 등) 바로 멈춘다 (Pleos 오디오 포커스 가이드).
 */
class SoundPlayer(private val context: Context) {
    companion object {
        /** 기획서: 효과음 2~4초 */
        const val MAX_SOUND_MS = 4_200L
        const val VOLUME_CAP = 1.0f

        private val files = mapOf(
            SoundKind.HOMERUN to R.raw.sfx_homerun,
            SoundKind.SCORE_BASEBALL to R.raw.sfx_score,
            SoundKind.HIT to R.raw.sfx_hit,
            SoundKind.MITT to R.raw.sfx_mitt,
            SoundKind.SIREN to R.raw.sfx_siren,
            SoundKind.GOAL to R.raw.sfx_goal,
            SoundKind.WHISTLE_SHORT to R.raw.sfx_whistle_short,
            SoundKind.WHISTLE_HALF to R.raw.sfx_whistle_half,
            SoundKind.WHISTLE_END to R.raw.sfx_whistle_end,
        )

        /** 음원 길이(ms) — tools/generate_sounds.py가 만드는 길이와 같게 유지 */
        val durations = mapOf(
            SoundKind.HOMERUN to 4_000L,
            SoundKind.SCORE_BASEBALL to 3_000L,
            SoundKind.HIT to 1_600L,
            SoundKind.MITT to 900L,
            SoundKind.SIREN to 3_000L,
            SoundKind.GOAL to 4_000L,
            SoundKind.WHISTLE_SHORT to 1_000L,
            SoundKind.WHISTLE_HALF to 1_800L,
            SoundKind.WHISTLE_END to 3_000L,
        )
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<SoundKind, Int>()
    private var streamId = 0
    private var focus: AudioFocusRequest? = null
    private val stopRunnable = Runnable { stopNow() }

    fun open() {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
        pool = p
        files.forEach { (kind, res) -> ids[kind] = p.load(context, res, 1) }
    }

    /** @return 실제로 재생을 시작했으면 true */
    fun play(kind: SoundKind, volume: Float): Boolean {
        val p = pool ?: return false
        val id = ids[kind] ?: return false
        handler.removeCallbacks(stopRunnable)
        stopStream()
        if (!requestFocus()) return false
        val v = volume.coerceIn(0f, VOLUME_CAP)
        val s = p.play(id, v, v, 1, 0, 1f)
        if (s == 0) {
            abandonFocus()
            return false
        }
        streamId = s
        handler.postDelayed(stopRunnable, minOf(durations[kind] ?: 2_000L, MAX_SOUND_MS) + 80)
        return true
    }

    fun stopNow() {
        handler.removeCallbacks(stopRunnable)
        stopStream()
        abandonFocus()
    }

    private fun requestFocus(): Boolean {
        if (focus != null) return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    handler.post { stopNow() }
                }
            }
            .build()
        if (audioManager.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = req
        return true
    }

    private fun stopStream() {
        if (streamId != 0) pool?.stop(streamId)
        streamId = 0
    }

    private fun abandonFocus() {
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun release() {
        stopNow()
        handler.removeCallbacksAndMessages(null)
        pool?.release()
        pool = null
        ids.clear()
    }
}
