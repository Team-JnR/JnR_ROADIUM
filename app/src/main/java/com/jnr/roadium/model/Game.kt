package com.jnr.roadium.model

enum class Side { HOME, AWAY }

enum class GameStatus { SCHEDULED, LIVE, FINISHED }

/** 오늘의 경기 일정 */
data class GameInfo(
    val id: String,
    val league: League,
    val home: Team,
    val away: Team,
    /** 표시용 시작 시각 ("18:30") */
    val startLabel: String,
    val venue: String,
) {
    val sport: Sport get() = league.sport
    fun team(side: Side): Team = if (side == Side.HOME) home else away
    fun involves(teamIds: Set<String>): Boolean = home.id in teamIds || away.id in teamIds
    val title: String get() = "${home.short} vs ${away.short}"
}

/** 경기의 현재 상태 (스코어보드에 그대로 표시) */
data class GameSnapshot(
    val info: GameInfo,
    val status: GameStatus,
    val homeScore: Int,
    val awayScore: Int,
    /** 진행 상황: "3회말 2아웃", "후반 23분", "경기 종료" */
    val period: String,
    /** 시작 전이면 시작까지 남은 시연 시간(초) */
    val secondsToStart: Int,
    /** 마지막으로 데이터가 바뀐 시각 (wall clock, ms). 0이면 아직 없음 */
    val updatedAt: Long,
) {
    fun score(side: Side) = if (side == Side.HOME) homeScore else awayScore

    /** 앞선 팀. 동점이면 null */
    val leader: Side?
        get() = when {
            homeScore > awayScore -> Side.HOME
            awayScore > homeScore -> Side.AWAY
            else -> null
        }
}

/**
 * 알림 중요도. 낮을수록 중요하다.
 * 1 = 득점·경기 시작/종료, 2 = 주요 이벤트(페널티킥·퇴장·전후반), 3 = 기타(안타·삼진·이닝 종료)
 */
enum class EventType(val importance: Int, val label: String) {
    GAME_START(1, "경기 시작"),
    HOMERUN(1, "홈런"),
    RUN(1, "득점"),
    GOAL(1, "골"),
    GAME_END(1, "경기 종료"),
    PENALTY(2, "페널티킥"),
    RED_CARD(2, "퇴장"),
    HALF_START(2, "후반 시작"),
    HALF_END(2, "전반 종료"),
    HIT(3, "안타"),
    STRIKEOUT(3, "삼진"),
    INNING_END(3, "이닝 종료"),
    ;

    val isScoring: Boolean get() = this == HOMERUN || this == RUN || this == GOAL
}

/** 알림 범위 3단계 (기획서: 득점만 / 주요 이벤트 / 모든 이벤트) */
enum class AlertLevel(val label: String, val maxImportance: Int) {
    SCORE_ONLY("득점만", 1),
    MAJOR("주요 이벤트", 2),
    ALL("모든 이벤트", 3),
    ;

    fun allows(type: EventType): Boolean = type.importance <= maxImportance
}

/**
 * 중계 서버가 내려주는 공통 이벤트 형식 (종목·경기·이벤트 종류·팀·스코어·발생 시각).
 * 공식 데이터 피드와 리플레이 데이터가 같은 형식을 쓰므로, 제휴 후에는 입력부만 바꾸면 된다.
 */
data class GameEvent(
    val gameId: String,
    val seq: Int,
    val type: EventType,
    val side: Side?,
    val homeScore: Int,
    val awayScore: Int,
    val period: String,
    /** 카드 큰 글씨: "홈런!", "역전!", "골!" */
    val headline: String,
    /** 카드 한 줄 설명: "KIA 2점 홈런" */
    val detail: String,
    /** 발생 시각 (wall clock, ms) */
    val at: Long,
    val leadChange: Boolean = false,
    val tied: Boolean = false,
) {
    fun scoreLine(info: GameInfo) = "${info.home.short} $homeScore : $awayScore ${info.away.short}"
}

/** 효과음 종류 (res/raw 음원과 1:1) */
enum class SoundKind(val caption: String, val approxSec: Int) {
    HOMERUN("타격음 + 큰 관중 함성", 4),
    SCORE_BASEBALL("타격음 + 관중 함성", 3),
    HIT("타격음", 2),
    MITT("포수 미트 소리", 1),
    SIREN("경기 종료 사이렌", 3),
    GOAL("골 함성", 4),
    WHISTLE_SHORT("주심 휘슬", 1),
    WHISTLE_HALF("주심 휘슬 두 번", 2),
    WHISTLE_END("종료 휘슬 세 번", 3),
    ;

    companion object {
        fun of(event: GameEvent, sport: Sport): SoundKind? = when (event.type) {
            EventType.HOMERUN -> HOMERUN
            EventType.RUN -> if (event.leadChange) HOMERUN else SCORE_BASEBALL
            EventType.HIT -> HIT
            EventType.STRIKEOUT, EventType.INNING_END -> MITT
            EventType.GOAL -> GOAL
            EventType.PENALTY, EventType.RED_CARD -> WHISTLE_SHORT
            EventType.HALF_START, EventType.HALF_END -> WHISTLE_HALF
            EventType.GAME_START -> if (sport == Sport.SOCCER) WHISTLE_HALF else null
            EventType.GAME_END -> if (sport == Sport.SOCCER) WHISTLE_END else SIREN
        }
    }
}
