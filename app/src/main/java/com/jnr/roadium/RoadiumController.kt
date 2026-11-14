package com.jnr.roadium

import android.content.Context
import android.os.SystemClock
import com.jnr.roadium.audio.SoundPlayer
import com.jnr.roadium.data.Prefs
import com.jnr.roadium.engine.BroadcastEngine
import com.jnr.roadium.engine.Effect
import com.jnr.roadium.engine.Recap
import com.jnr.roadium.engine.VoiceIntent
import com.jnr.roadium.engine.VoiceIntents
import com.jnr.roadium.feed.ReplayEngine
import com.jnr.roadium.feed.ReplayScripts
import com.jnr.roadium.model.AlertLevel
import com.jnr.roadium.model.EventType
import com.jnr.roadium.model.GameEvent
import com.jnr.roadium.model.GameInfo
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.SoundKind
import com.jnr.roadium.navi.NaviGuidanceSource
import com.jnr.roadium.service.BroadcastService
import com.jnr.roadium.vehicle.DrivingRestrictions
import com.jnr.roadium.vehicle.Gear
import com.jnr.roadium.vehicle.PleosVehicleSource
import com.jnr.roadium.voice.Announcer
import com.jnr.roadium.voice.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 이벤트 카드 (약 8초 표시) */
data class CardState(
    val event: GameEvent,
    val info: GameInfo,
    val sound: SoundKind?,
    val soundHeld: Boolean,
    val until: Long,
)

data class PromptState(val game: GameInfo, val secondsLeft: Int)

data class SdkStatus(
    val vehicle: SdkState,
    val tts: SdkState,
    val stt: SdkState,
    val navi: SdkState,
    val uxRestricted: Boolean,
)

data class UiState(
    val games: List<GameSnapshot> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val reservedGameId: String? = null,
    val liveGameId: String? = null,
    val gear: Gear = Gear.PARK,
    val driving: Boolean = false,
    /** 주행 화면을 보여야 하는지 (P단 아님 또는 플랫폼 주행 규제) */
    val driveMode: Boolean = false,
    val guidanceActive: Boolean = false,
    val simGuidance: Boolean = false,
    val naviNextTurnM: Double? = null,
    val card: CardState? = null,
    val prompt: PromptState? = null,
    val recap: Recap? = null,
    val alertLevel: AlertLevel = AlertLevel.MAJOR,
    val soundOn: Boolean = true,
    val volume: Float = 0.8f,
    val speaking: Boolean = false,
    val listening: Boolean = false,
    val heard: String = "",
    /** 음성 안내 자막 (최근 7초) */
    val caption: String? = null,
    val sdk: SdkStatus = SdkStatus(SdkState.CHECKING, SdkState.CHECKING, SdkState.CHECKING, SdkState.CHECKING, false),
    val replaySpeed: Int = 1,
    /** 게임별 최근 이벤트 (최신이 앞) */
    val logs: Map<String, List<GameEvent>> = emptyMap(),
) {
    fun snapshot(id: String?) = games.firstOrNull { it.info.id == id }
    val live: GameSnapshot? get() = snapshot(liveGameId)
    val reserved: GameSnapshot? get() = snapshot(reservedGameId)
}

/**
 * 앱 전체의 연결부: 리플레이 중계 피드 → 중계 판단 엔진 → 화면·효과음·음성,
 * 그리고 Pleos SDK(Vehicle 기어, NaviHelper 안내 상태, TTS, STT)와 주행 규제를 묶는다.
 * Application 수명으로 하나만 존재하며, 중계 예약·중계 중에는 [BroadcastService]가 프로세스를 유지한다.
 */
class RoadiumController(private val context: Context) {
    companion object {
        const val CARD_MS = 8_000L
        private const val TICK_MS = 500L
    }

    val prefs = Prefs(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val replay = ReplayEngine(ReplayScripts.today)
    private var engine = newEngine()

    private val vehicle = PleosVehicleSource(context) { onGear(it, fromSimulator = false) }
    val restrictions = DrivingRestrictions(context)
    private val navi = NaviGuidanceSource(context) { realGuidance = it; applyGuidance() }
    private val announcer = Announcer(context)
    private val voice = VoiceInput(context)
    private val sound = SoundPlayer(context)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var tickJob: Job? = null
    private var started = false
    private var lastTick = 0L
    private var speed = 1

    private var gear = Gear.PARK
    private var realGuidance = false
    private var simGuidance = false
    private var card: CardState? = null
    private var promptGame: GameInfo? = null
    private var promptShownAt = 0L
    private var recap: Recap? = null
    private var soundBusyUntil = 0L
    private val logs = mutableMapOf<String, MutableList<GameEvent>>()
    private var serviceRunning = false

    private fun newEngine() = BroadcastEngine { replay.snapshot(it) }.also {
        it.favorites = prefs.favorites.value
        it.alertLevel = prefs.alertLevel.value
        it.soundOn = prefs.soundOn.value
    }

    private fun now() = SystemClock.elapsedRealtime()

    // ── 수명 ───────────────────────────────────────────────

    fun start() {
        if (started) return
        started = true
        restrictions.start()
        vehicle.start()
        navi.start()
        announcer.start()
        voice.start()
        sound.open()
        lastTick = now()
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
        refresh()
    }

    /** 화면이 내려갔을 때: 예약·중계가 없으면 SDK와 타이머를 모두 해제한다 (BG→CCH 전환 가이드) */
    fun onUiHidden() {
        if (!needsService()) stop()
    }

    fun stop() {
        if (!started) return
        started = false
        tickJob?.cancel()
        tickJob = null
        voice.release()
        announcer.release()
        sound.release()
        navi.stop()
        vehicle.stop()
        restrictions.stop()
        realGuidance = false
    }

    private fun needsService() = engine.reservedGameId != null || engine.liveGameId != null

    private fun syncService() {
        val need = needsService()
        if (need && !serviceRunning) {
            serviceRunning = true
            BroadcastService.start(context)
        } else if (!need && serviceRunning) {
            serviceRunning = false
            BroadcastService.stop(context)
        } else if (need) {
            BroadcastService.update(context)
        }
    }

    // ── 주기 처리 ──────────────────────────────────────────

    /** @param skipMs 시연 도구 '건너뛰기'로 추가로 진행할 시연 시간 */
    private fun tick(skipMs: Long = 0) {
        val t = now()
        val dt = (t - lastTick).coerceIn(0, 2_000)
        lastTick = t
        val events = replay.advance(replay.sessionMs + dt * speed + skipMs, System.currentTimeMillis())
        events.forEach { e ->
            logs.getOrPut(e.gameId) { mutableListOf() }.add(0, e)
            logs[e.gameId]?.let { while (it.size > 30) it.removeAt(it.size - 1) }
            run(engine.onEvent(e, t))
        }
        run(engine.tick(t))
        if (card?.let { t >= it.until } == true) card = null
        refresh()
    }

    private fun refresh() {
        val p = promptGame
        _ui.value = UiState(
            games = replay.snapshots(),
            favorites = prefs.favorites.value,
            reservedGameId = engine.reservedGameId,
            liveGameId = engine.liveGameId,
            gear = gear,
            driving = engine.driving,
            driveMode = engine.driving || restrictions.restricted.value,
            guidanceActive = engine.guidanceActive,
            simGuidance = simGuidance,
            naviNextTurnM = navi.nextTurnM.value,
            card = card,
            prompt = p?.let {
                PromptState(it, (((BroadcastEngine.PROMPT_TIMEOUT_MS - (now() - promptShownAt)) + 999) / 1000).toInt().coerceAtLeast(0))
            },
            recap = recap,
            alertLevel = prefs.alertLevel.value,
            soundOn = prefs.soundOn.value,
            volume = prefs.volume.value,
            speaking = announcer.speaking.value,
            listening = voice.listening.value,
            heard = voice.partial.value,
            caption = announcer.caption.value?.takeIf { now() - it.second < 7_000 }?.first,
            sdk = SdkStatus(vehicle.state.value, ttsState(), voice.state.value, navi.state.value, restrictions.restricted.value),
            replaySpeed = speed,
            logs = logs.mapValues { it.value.toList() },
        )
        syncService()
    }

    /** Pleos TTS가 안 되고 시스템 TTS도 없으면 '없음'(자막만) */
    private fun ttsState(): SdkState {
        val s = announcer.state.value
        return if (s == SdkState.FALLBACK && !announcer.systemReady) SdkState.UNAVAILABLE else s
    }

    // ── 엔진 지시 실행 ──────────────────────────────────────

    private fun run(effects: List<Effect>) {
        effects.forEach { e ->
            when (e) {
                is Effect.ShowCard -> {
                    val info = replay.snapshot(e.event.gameId)?.info ?: return@forEach
                    // 이벤트가 몰릴 때: 표시 중인 더 중요한 카드(득점 등)를 덜 중요한 카드로 덮지 않는다
                    val shown = card
                    val keep = shown != null && now() < shown.until &&
                        shown.event.type.importance < e.event.type.importance && e.event.type != EventType.GAME_END
                    if (!keep) card = CardState(e.event, info, e.sound, e.soundHeld, now() + CARD_MS)
                }
                is Effect.PlaySound -> {
                    if (sound.play(e.kind, prefs.volume.value)) {
                        soundBusyUntil = now() + (SoundPlayer.durations[e.kind] ?: 0L)
                    }
                }
                is Effect.Speak -> speakAfterSound(e.text, e.listenAfter)
                is Effect.ShowPrompt -> {
                    promptGame = e.game
                    promptShownAt = now()
                }
                Effect.DismissPrompt -> {
                    promptGame = null
                    voice.cancel()
                }
                is Effect.ShowRecap -> recap = e.recap
            }
        }
    }

    /** 효과음이 끝난 뒤에 말한다 (소리와 음성이 겹치지 않게) */
    private fun speakAfterSound(text: String, listenAfter: Boolean) {
        val wait = soundBusyUntil - now()
        val action = {
            announcer.speak(text) {
                if (listenAfter && promptGame != null) listenForAnswer()
            }
        }
        if (wait > 0) scope.launch { delay(wait); action() } else action()
    }

    private fun listenForAnswer() {
        voice.listen { text ->
            run(engine.onVoice(VoiceIntents.parse(text)))
            refresh()
        }
        refresh()
    }

    // ── 차량 상태 ───────────────────────────────────────────

    fun onGear(g: Gear, fromSimulator: Boolean) {
        gear = g
        val driving = g != Gear.PARK && g != Gear.UNKNOWN
        if (driving) recap = null
        run(engine.setDriving(driving, now()))
        if (!driving) card = null
        refresh()
    }

    private fun applyGuidance() {
        run(engine.setGuidance(realGuidance || simGuidance))
        refresh()
    }

    // ── 사용자 조작 ─────────────────────────────────────────

    fun setFavorites(ids: Set<String>) {
        prefs.setFavorites(ids)
        engine.favorites = ids
        refresh()
    }

    fun reserve(gameId: String) {
        run(engine.reserve(gameId))
        refresh()
    }

    fun cancelReservation() {
        engine.cancelReservation()
        refresh()
    }

    fun stopRelay() {
        run(engine.stopRelay())
        engine.cancelReservation()
        sound.stopNow()
        announcer.stopAll()
        card = null
        refresh()
    }

    fun acceptPrompt() {
        voice.cancel()
        run(engine.acceptPrompt())
        refresh()
    }

    fun declinePrompt() {
        voice.cancel()
        run(engine.declinePrompt())
        refresh()
    }

    /** "지금 몇 대 몇이야?": 음성 인식이 되면 듣고, 안 되면 바로 현재 상황을 말한다 */
    fun askScore() {
        if (voice.available) {
            announcer.stopAll()
            voice.listen { text ->
                val intent = VoiceIntents.parse(text)
                run(if (intent == VoiceIntent.UNKNOWN) engine.askScore() else engine.onVoice(intent))
                refresh()
            }
        } else {
            run(engine.askScore())
        }
        refresh()
    }

    fun setSoundOn(on: Boolean) {
        prefs.setSoundOn(on)
        engine.soundOn = on
        if (!on) sound.stopNow()
        refresh()
    }

    fun setAlertLevel(level: AlertLevel) {
        prefs.setAlertLevel(level)
        engine.alertLevel = level
        refresh()
    }

    fun setVolume(v: Float) {
        prefs.setVolume(v)
        refresh()
    }

    fun previewSound(kind: SoundKind) {
        sound.play(kind, prefs.volume.value)
    }

    fun closeRecap() {
        recap = null
        refresh()
    }

    // ── 시연 도구 ───────────────────────────────────────────

    fun simSetGear(g: Gear) = onGear(g, fromSimulator = true)

    fun simSetGuidance(on: Boolean) {
        simGuidance = on
        applyGuidance()
    }

    fun simSetSpeed(x: Int) {
        speed = x
        refresh()
    }

    /** 리플레이를 처음부터 (예약·중계·리캡 초기화) */
    fun simRestart() {
        sound.stopNow()
        announcer.stopAll()
        voice.cancel()
        replay.reset(System.currentTimeMillis())
        val wasDriving = engine.driving
        engine = newEngine()
        if (wasDriving) engine.setDriving(true, now())
        engine.setGuidance(realGuidance || simGuidance)
        logs.clear()
        card = null
        promptGame = null
        recap = null
        lastTick = now()
        refresh()
    }

    /** 다음 이벤트 직전까지 건너뛰기 (시연 시간 단축) */
    fun simSkip(seconds: Int) {
        tick(seconds * 1000L)
    }
}
