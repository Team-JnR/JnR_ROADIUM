package com.jnr.roadium.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import com.jnr.roadium.SdkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 음성 응답. Pleos SpeechToText SDK(ON_DEVICE)로 "응/아니", "지금 몇 대 몇이야?"를 듣는다.
 * SDK는 텍스트만 돌려주고, 의도 판단은 앱([com.jnr.roadium.engine.VoiceIntents])이 한다.
 * Pleos 음성 서비스가 없는 기기에서는 음성 입력 없이 큰 버튼으로만 응답한다.
 */
class VoiceInput(private val context: Context) {
    companion object {
        private const val TAG = "VoiceInput"
        private const val LISTEN_TIMEOUT_MS = 8_000L
        private const val CAAS_PACKAGE = "ai.pleos.playground.caas"
    }

    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var ready = false

    private val _state = MutableStateFlow(SdkState.CHECKING)
    val state: StateFlow<SdkState> = _state

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _partial = MutableStateFlow("")
    /** 인식 중인 문장 (화면에 작게 표시) */
    val partial: StateFlow<String> = _partial

    private var callback: ((String?) -> Unit)? = null
    private val timeout = Runnable { finish(null) }

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            main.post {
                _partial.value = text
                if (isFinal) finish(text)
            }
        }

        override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}
        override fun onStartedRecognition() {}
        override fun onEndedRecognition() {
            main.post { finish(_partial.value.ifBlank { null }) }
        }

        override fun onError(type: ErrorType) {
            Log.w(TAG, "STT error: $type")
            main.post {
                if (type == ErrorType.PERMISSION_DENIED || type == ErrorType.NOT_SUPPORTED_API) {
                    ready = false
                    _state.value = SdkState.UNAVAILABLE
                }
                finish(null)
            }
        }

        override fun onReady() {}
        override fun onUpdatedRms(rms: Double) {}
    }

    val available: Boolean get() = ready

    fun start() {
        if (stt != null) return
        val installed = runCatching { context.packageManager.getPackageInfo(CAAS_PACKAGE, 0); true }.getOrDefault(false)
        if (!installed) {
            _state.value = SdkState.UNAVAILABLE
            return
        }
        try {
            val s = SpeechToText(context, Mode.ON_DEVICE)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching { s.setLocale(Locale.KOREA) }
                        runCatching { s.addListener(listener) }
                        ready = true
                        _state.value = SdkState.CONNECTED
                    }
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "STT init failed: $type")
                    main.post { _state.value = SdkState.UNAVAILABLE }
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "STT unavailable: ${e.message}")
            stt = null
            _state.value = SdkState.UNAVAILABLE
        }
    }

    /** 한 번 듣는다. 결과(또는 실패 시 null)를 [onResult]로 한 번 돌려준다 */
    fun listen(onResult: (String?) -> Unit) {
        val s = stt
        if (!ready || s == null) {
            onResult(null)
            return
        }
        cancel()
        callback = onResult
        _partial.value = ""
        _listening.value = true
        main.postDelayed(timeout, LISTEN_TIMEOUT_MS)
        try {
            s.request()
        } catch (e: Throwable) {
            Log.w(TAG, "STT request failed: ${e.message}")
            finish(null)
        }
    }

    fun cancel() {
        if (!_listening.value) return
        runCatching { stt?.stop() }
        finish(null, notify = false)
    }

    private fun finish(text: String?, notify: Boolean = true) {
        main.removeCallbacks(timeout)
        if (!_listening.value) return
        _listening.value = false
        val cb = callback
        callback = null
        if (text == null) runCatching { stt?.stop() }
        if (notify) cb?.invoke(text)
    }

    fun release() {
        cancel()
        main.removeCallbacksAndMessages(null)
        runCatching { stt?.removeListener(listener) }
        runCatching { stt?.release() }
        stt = null
        ready = false
        _state.value = SdkState.CHECKING
    }
}
