package com.jnr.roadium.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.jnr.roadium.SdkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성 안내. Pleos TextToSpeech SDK를 우선 쓰고, Pleos 음성 서비스가 없거나 오류가 나면
 * Android 표준 TTS로 대체한다 (기획서: 음성 기능은 별도 계층으로 분리해 대체 가능하게).
 *
 * - Pleos TTS는 ON_DEVICE 모드를 쓴다. HYBRID/SERVER 모드는 My Project 앱 등록(registerApp) 자격 증명이
 *   필요해, 자격 증명을 받은 뒤 [PLEOS_MODE]만 바꾸면 된다.
 * - 한 번에 한 문장씩 순서대로 말하고, 끝나면 [speak]의 onDone을 부른다.
 * - 대체 TTS 경로는 앞에 150ms 묵음을 넣고(Pleos '음성 안내 선행 묵음 가이드'),
 *   일시적 오디오 포커스(덕킹 허용)를 받아 음악을 잠시 줄인 뒤 반납한다.
 */
class Announcer(private val context: Context) {
    companion object {
        private const val TAG = "Announcer"
        private val PLEOS_MODE = Mode.ON_DEVICE
        private const val INIT_TIMEOUT_MS = 4_000L
        private const val PREROLL_MS = 150L
        private const val CAAS_PACKAGE = "ai.pleos.playground.caas"
    }

    private data class Utterance(val text: String, val onDone: (() -> Unit)?)

    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _state = MutableStateFlow(SdkState.CHECKING)
    /** CONNECTED = Pleos TTS, FALLBACK = Android TTS */
    val state: StateFlow<SdkState> = _state

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    /** 마지막으로 안내한 문장과 시각(elapsedRealtime). 화면 자막으로도 보여 준다 (말할 엔진이 없어도) */
    private val _caption = MutableStateFlow<Pair<String, Long>?>(null)
    val caption: StateFlow<Pair<String, Long>?> = _caption

    /** Android 표준 TTS가 준비됐는지 (대체 경로 사용 가능 여부) */
    val systemReady: Boolean get() = androidReady

    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var android: AndroidTts? = null
    private var androidReady = false

    private val queue = ArrayDeque<Utterance>()
    private var current: Utterance? = null
    private var focus: AudioFocusRequest? = null
    private var started = false

    private val pleosListener = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() {
            main.post { onPleosFinished() }
        }

        override fun onStop() {
            main.post { onPleosFinished() }
        }
        override fun onUpdatedRms(rms: Double) {}
        override fun onError(type: ErrorType) {
            Log.w(TAG, "Pleos TTS error: $type → Android TTS로 대체")
            main.post {
                // 이번 문장은 대체 TTS로 다시 말한다
                val u = current
                current = null
                if (type == ErrorType.PERMISSION_DENIED || type == ErrorType.NOT_SUPPORTED_API || type == ErrorType.ON_DEVICE_ENGINE_ERROR) {
                    pleosReady = false
                    _state.value = SdkState.FALLBACK
                }
                if (u != null) speakWithAndroid(u) else next()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        initAndroid()
        initPleos()
    }

    private fun caasInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(CAAS_PACKAGE, 0); true
    }.getOrDefault(false)

    private fun initPleos() {
        if (!caasInstalled()) {
            _state.value = SdkState.FALLBACK
            return
        }
        try {
            val tts = PleosTts(context, PLEOS_MODE)
            pleos = tts
            main.postDelayed({
                if (!pleosReady && _state.value == SdkState.CHECKING) {
                    Log.w(TAG, "Pleos TTS init timeout → Android TTS")
                    _state.value = SdkState.FALLBACK
                }
            }, INIT_TIMEOUT_MS)
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        // 리스너·설정은 initialize 성공 뒤에 붙인다 (그 전에는 내부 엔진이 없음)
                        runCatching { tts.addEventListener(pleosListener) }
                            .onFailure { Log.w(TAG, "addEventListener failed: ${it.message}") }
                        runCatching { tts.setLocale(Locale.KOREA) }
                        runCatching { tts.setAudioAttributes(attributes) }
                        pleosReady = true
                        _state.value = SdkState.CONNECTED
                    }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TTS init failed: $type")
                    main.post { _state.value = SdkState.FALLBACK }
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos TTS unavailable: ${e.message}")
            pleos = null
            _state.value = SdkState.FALLBACK
        }
    }

    private fun initAndroid() {
        android = AndroidTts(context) { status ->
            main.post {
                androidReady = status == AndroidTts.SUCCESS
                android?.let { tts ->
                    runCatching { tts.setLanguage(Locale.KOREA) }
                    tts.setAudioAttributes(attributes)
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != "preroll") main.post { finishCurrent() }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId != "preroll") main.post { finishCurrent() }
                        }
                    })
                }
                if (current == null) next()
            }
        }
    }

    /** 문장을 대기열에 넣는다. [onDone]은 다 말한 뒤(또는 말하지 못했을 때) 한 번 불린다 */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        queue.addLast(Utterance(text, onDone))
        if (current == null) next()
    }

    /** 말하는 중인 문장과 대기열을 모두 멈춘다 */
    fun stopAll() {
        val pending = listOfNotNull(current) + queue
        queue.clear()
        current = null
        runCatching { pleos?.stop() }
        runCatching { android?.stop() }
        abandonFocus()
        _speaking.value = false
        pending.forEach { it.onDone?.invoke() }
    }

    private fun next() {
        if (current != null) return
        val u = queue.removeFirstOrNull() ?: run {
            abandonFocus()
            _speaking.value = false
            return
        }
        current = u
        _speaking.value = true
        _caption.value = u.text to SystemClock.elapsedRealtime()
        val p = pleos
        if (pleosReady && p != null) {
            try {
                pleosStartedAt = System.currentTimeMillis()
                p.speak(u.text, true)
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Pleos speak failed: ${e.message}")
            }
        }
        current = null
        speakWithAndroid(u)
    }

    private fun speakWithAndroid(u: Utterance) {
        val tts = android
        if (tts == null || !androidReady) {
            // 말할 수 있는 엔진이 없음: 화면 표시만으로 진행
            Log.w(TAG, "no TTS engine: ${u.text}")
            _caption.value = u.text to SystemClock.elapsedRealtime()
            current = null
            u.onDone?.invoke()
            next()
            return
        }
        current = u
        _speaking.value = true
        requestFocus()
        val params = Bundle()
        tts.playSilentUtterance(PREROLL_MS, AndroidTts.QUEUE_ADD, "preroll")
        tts.speak(u.text, AndroidTts.QUEUE_ADD, params, "u${System.nanoTime()}")
    }

    /** Pleos 문장 시작 시각: 이전 문장의 늦게 온 onStop/onDone이 새 문장을 끝내지 않도록 */
    private var pleosStartedAt = 0L

    private fun onPleosFinished() {
        if (System.currentTimeMillis() - pleosStartedAt < 300) return
        finishCurrent()
    }

    private fun finishCurrent() {
        val u = current ?: return
        current = null
        u.onDone?.invoke()
        next()
    }

    private fun requestFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                // 통화·Gleo 음성 명령 등으로 포커스를 잃으면 즉시 멈춘다 (Pleos 오디오 포커스 가이드)
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    main.post { stopAll() }
                }
            }
            .build()
        if (audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focus = req
    }

    private fun abandonFocus() {
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun release() {
        stopAll()
        main.removeCallbacksAndMessages(null)
        runCatching { pleos?.removeEventListener(pleosListener) }
        runCatching { pleos?.release() }
        pleos = null
        pleosReady = false
        runCatching { android?.shutdown() }
        android = null
        androidReady = false
        started = false
        _state.value = SdkState.CHECKING
    }
}
