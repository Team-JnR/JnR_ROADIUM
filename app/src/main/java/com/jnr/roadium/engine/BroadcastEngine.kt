package com.jnr.roadium.engine

import com.jnr.roadium.model.AlertLevel
import com.jnr.roadium.model.EventType
import com.jnr.roadium.model.GameEvent
import com.jnr.roadium.model.GameInfo
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.GameStatus
import com.jnr.roadium.model.SoundKind

/** 엔진이 내리는 지시. 컨트롤러가 화면·효과음·음성으로 실행한다 */
sealed interface Effect {
    /** 한 줄 이벤트 카드 (약 8초). [soundHeld]면 내비 안내 때문에 소리를 미룬 상태 */
    data class ShowCard(val event: GameEvent, val sound: SoundKind?, val soundHeld: Boolean) : Effect
    data class PlaySound(val kind: SoundKind) : Effect
    /** 음성 안내. [listenAfter]면 안내가 끝난 뒤 '응/아니' 음성 응답을 듣는다 */
    data class Speak(val text: String, val listenAfter: Boolean = false) : Effect
    data class ShowPrompt(val game: GameInfo) : Effect
    data object DismissPrompt : Effect
    data class ShowRecap(val recap: Recap) : Effect
}

/** 주차 후 경기 리캡: 이동 중 있었던 득점·역전 장면을 시간순으로 */
data class Recap(
    val info: GameInfo,
    val startHome: Int,
    val startAway: Int,
    val startPeriod: String,
    val items: List<GameEvent>,
    val end: GameSnapshot,
    val drivingMinutes: Int,
)

/** 음성 응답 의도 */
enum class VoiceIntent { YES, NO, SCORE, UNKNOWN }

/**
 * 중계 판단 엔진 (순수 Kotlin, 단위 테스트 대상).
 *
 * 주행 상태·내비 안내 상태·알림 범위를 보고, 경기 이벤트를 화면 카드·효과음·음성 안내로 바꾼다.
 * - 주차 중 예약 → 주행 시작(P단 해제) 시 자동 중계
 * - 예약하지 않은 응원 팀 경기가 주행 중 시작되면 "중계를 시작할까요?" (10초 무응답 시 닫힘)
 * - 내비 안내 중에는 카드만 띄우고 소리는 보류 → 안내가 끝나면 최신 상황을 한 번만 알림
 * - 이벤트가 몰리면 효과음 사이에 최소 간격을 두고, 밀린 이벤트는 가장 중요한 하나로 합침
 * - 주차하면 이동 중 득점 장면 리캡
 */
class BroadcastEngine(
    private val snapshotOf: (String) -> GameSnapshot?,
) {
    companion object {
        /** 효과음 사이 최소 간격 */
        const val MIN_SOUND_GAP_MS = 4_000L
        /** 경기 시작 질문 자동 닫힘 */
        const val PROMPT_TIMEOUT_MS = 10_000L
    }

    var favorites: Set<String> = emptySet()
    var alertLevel: AlertLevel = AlertLevel.MAJOR
    var soundOn: Boolean = true

    var reservedGameId: String? = null
        private set
    var liveGameId: String? = null
        private set
    var promptGameId: String? = null
        private set
    var driving: Boolean = false
        private set
    var guidanceActive: Boolean = false
        private set

    /** 내비 안내로 소리를 보류 중인지 (보류된 이벤트가 있거나 상태 안내가 밀려 있음) */
    val holding: Boolean get() = guidanceActive && (held.isNotEmpty() || pendingStatus)

    private val held = mutableListOf<GameEvent>()
    private var heldFromHome = 0
    private var heldFromAway = 0
    private var pendingStatus = false
    private var burst: GameEvent? = null
    private var lastSoundAt = Long.MIN_VALUE / 2
    private var promptShownAt = 0L
    private val declined = mutableSetOf<String>()

    // 리캡
    private var driveStartedAt = 0L
    private var recapGameId: String? = null
    private var recapStart: GameSnapshot? = null
    private val driveLog = mutableListOf<GameEvent>()

    // ── 사용자 조작 (주차 중) ─────────────────────────────────

    fun reserve(gameId: String): List<Effect> {
        val s = snapshotOf(gameId) ?: return emptyList()
        if (s.status == GameStatus.FINISHED) return emptyList()
        reservedGameId = gameId
        return emptyList()
    }

    fun cancelReservation() {
        reservedGameId = null
    }

    /** 중계 끄기 (예약도 함께 해제) */
    fun stopRelay(): List<Effect> {
        if (reservedGameId == liveGameId) reservedGameId = null
        liveGameId = null
        held.clear()
        pendingStatus = false
        burst = null
        return emptyList()
    }

    // ── 차량 상태 ───────────────────────────────────────────

    fun setDriving(value: Boolean, now: Long): List<Effect> {
        if (value == driving) return emptyList()
        driving = value
        val out = mutableListOf<Effect>()
        if (value) {
            driveStartedAt = now
            driveLog.clear()
            recapGameId = null
            recapStart = null
            val live = liveGameId
            val reserved = reservedGameId
            when {
                live != null -> {
                    markRecapStart(live)
                    out += speak(snapshotOf(live)?.let { Phrases.status(it) })
                }
                reserved != null -> {
                    val s = snapshotOf(reserved)
                    when (s?.status) {
                        GameStatus.LIVE -> out += goLive(reserved, Phrases.relayStart(s))
                        GameStatus.SCHEDULED -> out += speak(Phrases.reservedWaiting(s.info))
                        else -> reservedGameId = null
                    }
                }
            }
        } else {
            if (promptGameId != null) {
                promptGameId = null
                out += Effect.DismissPrompt
            }
            buildRecap(now)?.let { out += Effect.ShowRecap(it) }
            driveLog.clear()
            recapGameId = null
            recapStart = null
        }
        return out
    }

    /** 내비게이션 음성 안내(다음 안내 지점 접근) 상태 */
    fun setGuidance(active: Boolean): List<Effect> {
        if (active == guidanceActive) return emptyList()
        guidanceActive = active
        val live = liveGameId
        if (active) {
            val s = live?.let(snapshotOf)
            heldFromHome = s?.homeScore ?: 0
            heldFromAway = s?.awayScore ?: 0
            return emptyList()
        }
        // 안내 종료: 그사이 변화를 한 번으로 묶어 알린다
        val out = mutableListOf<Effect>()
        val gameId = held.lastOrNull()?.gameId ?: live
        val s = gameId?.let(snapshotOf)
        if (s != null && held.isNotEmpty()) {
            val summary = Phrases.heldSummary(heldFromHome, heldFromAway, s)
            if (summary != null) out += Effect.Speak(summary)
            else if (pendingStatus) out += Effect.Speak(Phrases.status(s))
        } else if (s != null && pendingStatus) {
            out += Effect.Speak(Phrases.status(s))
        }
        held.clear()
        pendingStatus = false
        burst = null
        return out
    }

    // ── 경기 이벤트 ─────────────────────────────────────────

    fun onEvent(e: GameEvent, now: Long): List<Effect> {
        if (e.type == EventType.GAME_START) return onGameStart(e, now)
        if (e.gameId != liveGameId) return emptyList()
        val s = snapshotOf(e.gameId) ?: return emptyList()
        if (driving) driveLog += e

        if (e.type == EventType.GAME_END) {
            val out = mutableListOf<Effect>()
            val sound = SoundKind.of(e, s.info.sport)
            if (guidanceActive) {
                held += e
                out += Effect.ShowCard(e, sound, soundHeld = true)
            } else {
                out += Effect.ShowCard(e, sound, soundHeld = false)
                if (soundOn && sound != null) out += play(sound, now)
                out += Effect.Speak(Phrases.final(s))
            }
            if (reservedGameId == e.gameId) reservedGameId = null
            liveGameId = null
            burst = null
            return out
        }

        if (!alertLevel.allows(e.type)) return emptyList()
        val sound = SoundKind.of(e, s.info.sport)
        return when {
            !soundOn || sound == null -> listOf(Effect.ShowCard(e, sound, soundHeld = false))
            guidanceActive -> {
                held += e
                listOf(Effect.ShowCard(e, sound, soundHeld = true))
            }
            now - lastSoundAt < MIN_SOUND_GAP_MS -> {
                // 이벤트 몰림: 가장 중요한(같으면 최신) 이벤트 하나만 간격 뒤에 재생
                val b = burst
                burst = if (b == null || e.type.importance <= b.type.importance) e else b
                listOf(Effect.ShowCard(e, sound, soundHeld = false))
            }
            else -> listOf(Effect.ShowCard(e, sound, soundHeld = false), play(sound, now))
        }
    }

    private fun onGameStart(e: GameEvent, now: Long): List<Effect> {
        val s = snapshotOf(e.gameId) ?: return emptyList()
        // 예약한 경기: 주행 중이면 별도 조작 없이 자동으로 중계 시작
        if (reservedGameId == e.gameId) {
            return if (driving) {
                goLive(e.gameId, Phrases.reservedStarted(s.info)) +
                    listOfNotNull(SoundKind.of(e, s.info.sport)?.takeIf { soundOn && !guidanceActive }?.let { play(it, now) })
            } else emptyList()
        }
        // 예약하지 않은 응원 팀 경기: 주행 중이면 시작할지 묻는다
        val canAsk = driving && liveGameId == null && promptGameId == null &&
            (reservedGameId == null) && s.info.involves(favorites) && e.gameId !in declined
        if (!canAsk) return emptyList()
        promptGameId = e.gameId
        promptShownAt = now
        val out = mutableListOf<Effect>(Effect.ShowPrompt(s.info))
        // 내비 안내 중이면 묻는 음성은 생략하고 화면 버튼만 띄운다
        if (!guidanceActive) out += Effect.Speak(Phrases.startQuestion(s.info), listenAfter = true)
        return out
    }

    fun acceptPrompt(): List<Effect> {
        val id = promptGameId ?: return emptyList()
        promptGameId = null
        val s = snapshotOf(id) ?: return listOf(Effect.DismissPrompt)
        return listOf(Effect.DismissPrompt) + goLive(id, Phrases.relayStart(s))
    }

    fun declinePrompt(): List<Effect> {
        val id = promptGameId ?: return emptyList()
        declined += id
        promptGameId = null
        return listOf(Effect.DismissPrompt)
    }

    /** 음성 응답 처리: 질문 중이면 응/아니, 아니면 "지금 몇 대 몇이야?" */
    fun onVoice(intent: VoiceIntent): List<Effect> = when {
        intent == VoiceIntent.SCORE -> askScore()
        promptGameId != null && intent == VoiceIntent.YES -> acceptPrompt()
        promptGameId != null && intent == VoiceIntent.NO -> declinePrompt()
        else -> emptyList()
    }

    fun askScore(): List<Effect> {
        val live = liveGameId?.let(snapshotOf)
        if (live != null) return listOf(Effect.Speak(Phrases.status(live)))
        val reserved = reservedGameId?.let(snapshotOf)
        if (reserved != null) return listOf(Effect.Speak(Phrases.status(reserved)))
        return listOf(Effect.Speak("지금 중계 중인 경기가 없습니다."))
    }

    /** 0.5초마다: 밀린 효과음 재생, 질문 자동 닫힘 */
    fun tick(now: Long): List<Effect> {
        val out = mutableListOf<Effect>()
        val b = burst
        if (b != null && !guidanceActive && now - lastSoundAt >= MIN_SOUND_GAP_MS) {
            burst = null
            snapshotOf(b.gameId)?.let { s -> SoundKind.of(b, s.info.sport)?.let { out += play(it, now) } }
        }
        if (promptGameId != null && now - promptShownAt >= PROMPT_TIMEOUT_MS) {
            out += declinePrompt()
        }
        return out
    }

    // ── 내부 ───────────────────────────────────────────────

    private fun goLive(gameId: String, announcement: String): List<Effect> {
        liveGameId = gameId
        if (driving) markRecapStart(gameId)
        return speak(announcement)
    }

    private fun markRecapStart(gameId: String) {
        if (recapGameId == gameId) return
        recapGameId = gameId
        recapStart = snapshotOf(gameId)
    }

    /** 내비 안내 중이면 말하지 않고, 안내가 끝난 뒤 최신 상황을 알린다 */
    private fun speak(text: String?): List<Effect> {
        if (text == null) return emptyList()
        if (guidanceActive) {
            pendingStatus = true
            return emptyList()
        }
        return listOf(Effect.Speak(text))
    }

    private fun play(kind: SoundKind, now: Long): Effect {
        lastSoundAt = now
        return Effect.PlaySound(kind)
    }

    private fun buildRecap(now: Long): Recap? {
        val id = recapGameId ?: return null
        val start = recapStart ?: return null
        val end = snapshotOf(id) ?: return null
        val items = driveLog.filter { it.gameId == id && (it.type.isScoring || it.type == EventType.GAME_END) }
        return Recap(
            info = end.info,
            startHome = start.homeScore,
            startAway = start.awayScore,
            startPeriod = start.period,
            items = items,
            end = end,
            drivingMinutes = ((now - driveStartedAt) / 60_000L).toInt(),
        )
    }
}
