package com.jnr.roadium.feed

import com.jnr.roadium.model.EventType
import com.jnr.roadium.model.GameEvent
import com.jnr.roadium.model.GameInfo
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.GameStatus
import com.jnr.roadium.model.Side
import com.jnr.roadium.model.Sport

/**
 * 리플레이 중계 서버의 핵심부 (순수 Kotlin, 단위 테스트 대상).
 *
 * 시연 세션 시각(ms)을 받아, 그 사이에 일어난 경기 이벤트를 공통 이벤트 형식([GameEvent])으로 만들고
 * 각 경기의 현재 상태([GameSnapshot])를 계산한다. 실제 서비스에서는 이 자리에 공식 데이터 피드
 * 변환기가 들어가고, 차량 앱은 같은 형식의 메시지를 받는다.
 */
class ReplayEngine(private val scripts: List<GameScript>) {

    private class State(val script: GameScript) {
        var next = 0
        var home = 0
        var away = 0
        var status = GameStatus.SCHEDULED
        var period = ""
        var updatedAt = 0L
    }

    private val states = LinkedHashMap<String, State>()
    private var seq = 0

    /** 마지막으로 처리한 시연 세션 시각 (ms) */
    var sessionMs: Long = 0
        private set

    val games: List<GameInfo> get() = scripts.map { it.info }

    init {
        reset(0L)
    }

    /**
     * 처음부터 다시. 세션 0초 이전에 이미 일어난 이벤트(진행 중인 경기의 앞부분)는
     * 소리 없이 상태에만 반영한다.
     */
    fun reset(wallNow: Long) {
        states.clear()
        scripts.forEach { states[it.info.id] = State(it) }
        seq = 0
        sessionMs = 0
        states.values.forEach { applyUntil(it, 0L, wallNow) }
    }

    /** 세션 시각을 [toMs]까지 진행하고, 그 사이 새로 일어난 이벤트를 시간순으로 돌려준다 */
    fun advance(toMs: Long, wallNow: Long): List<GameEvent> {
        if (toMs <= sessionMs) return emptyList()
        sessionMs = toMs
        return states.values
            .flatMap { applyUntil(it, toMs, wallNow) }
            .sortedBy { it.seq }
    }

    private fun gameMs(state: State, sessionMs: Long) = sessionMs - state.script.startOffsetSec * 1000L

    private fun applyUntil(s: State, sessionMs: Long, wallNow: Long): List<GameEvent> {
        val out = mutableListOf<GameEvent>()
        val gameMs = gameMs(s, sessionMs)
        val steps = s.script.steps
        while (s.next < steps.size && steps[s.next].atSec * 1000L <= gameMs) {
            out += apply(s, steps[s.next], wallNow)
            s.next++
        }
        return out
    }

    private fun apply(s: State, step: ScriptStep, wallNow: Long): GameEvent {
        val info = s.script.info
        val beforeLeader = leader(s.home, s.away)
        if (step.type.isScoring && step.side != null) {
            if (step.side == Side.HOME) s.home += step.runs else s.away += step.runs
        }
        val afterLeader = leader(s.home, s.away)
        val leadChange = step.type.isScoring && beforeLeader != null && afterLeader != null && beforeLeader != afterLeader
        val tied = step.type.isScoring && afterLeader == null
        s.period = step.period
        s.status = when (step.type) {
            EventType.GAME_START -> GameStatus.LIVE
            EventType.GAME_END -> GameStatus.FINISHED
            else -> s.status
        }
        s.updatedAt = wallNow
        val (headline, detail) = EventText.of(info, step, s.home, s.away, leadChange, tied)
        return GameEvent(
            gameId = info.id,
            seq = ++seq,
            type = step.type,
            side = step.side,
            homeScore = s.home,
            awayScore = s.away,
            period = step.period,
            headline = headline,
            detail = detail,
            at = wallNow,
            leadChange = leadChange,
            tied = tied,
        )
    }

    fun snapshot(gameId: String): GameSnapshot? {
        val s = states[gameId] ?: return null
        val toStart = -gameMs(s, sessionMs)
        return GameSnapshot(
            info = s.script.info,
            status = s.status,
            homeScore = s.home,
            awayScore = s.away,
            period = when (s.status) {
                GameStatus.SCHEDULED -> "${s.script.info.startLabel} 시작"
                GameStatus.FINISHED -> "경기 종료"
                GameStatus.LIVE -> s.period
            },
            secondsToStart = if (s.status == GameStatus.SCHEDULED) ((toStart + 999) / 1000).toInt().coerceAtLeast(0) else 0,
            updatedAt = s.updatedAt,
        )
    }

    fun snapshots(): List<GameSnapshot> = states.keys.mapNotNull { snapshot(it) }

    private fun leader(home: Int, away: Int): Side? = when {
        home > away -> Side.HOME
        away > home -> Side.AWAY
        else -> null
    }
}

/** 이벤트 카드 문구 (정해진 문장 틀만 사용: 데이터에 없는 내용을 만들지 않는다) */
object EventText {
    fun of(info: GameInfo, step: ScriptStep, home: Int, away: Int, leadChange: Boolean, tied: Boolean): Pair<String, String> {
        val team = step.side?.let { info.team(it).short } ?: ""
        val score = "$home:$away"
        val headline = when (step.type) {
            EventType.HOMERUN, EventType.RUN -> when {
                leadChange -> "역전!"
                tied -> "동점!"
                step.type == EventType.HOMERUN -> "홈런!"
                else -> "득점!"
            }
            EventType.GOAL -> when {
                leadChange -> "역전 골!"
                tied -> "동점 골!"
                else -> "골!"
            }
            EventType.HIT -> "안타"
            EventType.STRIKEOUT -> "삼진"
            EventType.INNING_END -> "이닝 종료"
            EventType.PENALTY -> "페널티킥!"
            EventType.RED_CARD -> "퇴장!"
            EventType.HALF_END -> "전반 종료"
            EventType.HALF_START -> "후반 시작"
            EventType.GAME_START -> "경기 시작"
            EventType.GAME_END -> "경기 종료"
        }
        val detail = step.detail ?: when (step.type) {
            EventType.HOMERUN -> when (step.runs) {
                1 -> "$team 솔로 홈런"
                4 -> "$team 만루 홈런"
                else -> "$team ${step.runs}점 홈런"
            }
            EventType.RUN -> "$team ${step.runs}점 득점"
            EventType.GOAL -> "$team $score (${step.period})"
            EventType.HIT -> "$team 안타"
            EventType.STRIKEOUT -> "$team 투수 삼진"
            EventType.INNING_END -> "${step.period} 종료 · $score"
            EventType.PENALTY -> "$team 페널티킥 선언"
            EventType.RED_CARD -> "$team 선수 퇴장"
            EventType.HALF_END, EventType.HALF_START -> "${info.home.short} $score ${info.away.short}"
            EventType.GAME_START -> "${info.home.short} vs ${info.away.short}"
            EventType.GAME_END -> when {
                home > away -> "${info.home.short} $score 승리"
                away > home -> "${info.away.short} $score 승리"
                else -> "$score 무승부"
            }
        }
        // 역전·동점 카드에는 무슨 일이 있었는지를 함께 보여 준다 ("KIA 2점 홈런 · 5:4")
        val withScore = if ((leadChange || tied) && step.type != EventType.GOAL) "$detail · $score" else detail
        return headline to withScore
    }
}
