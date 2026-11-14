package com.jnr.roadium.feed

import com.jnr.roadium.model.EventType
import com.jnr.roadium.model.GameInfo
import com.jnr.roadium.model.League
import com.jnr.roadium.model.Side
import com.jnr.roadium.model.Sport
import com.jnr.roadium.model.Teams
import kotlin.random.Random

/**
 * 리플레이 데이터의 한 줄. 실제 경기 흐름을 본떠 자체 제작한 모의 데이터이며,
 * 시연을 위해 경기 시간을 압축했다 (KBO 한 경기 약 6분, K리그 약 3분).
 *
 * @param atSec 경기 시작으로부터의 시연 시간(초)
 * @param runs 득점 이벤트의 점수 (홈런 2점이면 2)
 * @param detail 카드 설명을 직접 지정할 때만 (없으면 자동 문구)
 */
data class ScriptStep(
    val atSec: Int,
    val type: EventType,
    val side: Side?,
    val period: String,
    val runs: Int = 0,
    val detail: String? = null,
)

/**
 * @param startOffsetSec 앱(시연 세션) 시작 기준 경기 시작 시각. 음수면 이미 진행 중인 경기
 */
data class GameScript(
    val info: GameInfo,
    val startOffsetSec: Int,
    val steps: List<ScriptStep>,
)

object ReplayScripts {
    private fun t(id: String) = Teams.byId(id)!!

    private fun game(id: String, league: League, home: String, away: String, start: String, venue: String) =
        GameInfo(id, league, t(home), t(away), start, venue)

    private val H = Side.HOME
    private val A = Side.AWAY

    /**
     * 시나리오 A (기획서): 평일 퇴근길, KIA 팬. 앱을 열면 3회말 KIA 1:0으로 이미 진행 중.
     * → 5회말 KIA 2점 홈런(3:0) → 6회초 LG 2점(3:2, 내비 안내와 겹치는 장면) → 역전과 재역전 → 경기 종료.
     */
    val kiaLg = GameScript(
        info = game("kbo-kia-lg", League.KBO, "kia", "lg", "18:30", "광주-기아 챔피언스 필드"),
        startOffsetSec = -100,
        steps = listOf(
            ScriptStep(0, EventType.GAME_START, null, "1회초"),
            ScriptStep(40, EventType.INNING_END, null, "1회말"),
            ScriptStep(70, EventType.HIT, H, "2회말", detail = "KIA 2루타"),
            ScriptStep(95, EventType.RUN, H, "3회말 1아웃", runs = 1, detail = "KIA 적시타, 1점"),
            // ── 여기부터 앱 시작 이후 (시연 세션 0초 = 경기 100초) ──
            ScriptStep(118, EventType.STRIKEOUT, H, "4회초 2아웃", detail = "KIA 투수 삼진"),
            ScriptStep(135, EventType.INNING_END, null, "4회말"),
            ScriptStep(150, EventType.HIT, H, "5회말 무사", detail = "KIA 안타, 주자 1루"),
            ScriptStep(168, EventType.HOMERUN, H, "5회말 무사", runs = 2),
            ScriptStep(195, EventType.HIT, A, "6회초 1아웃", detail = "LG 안타, 주자 1·2루"),
            ScriptStep(212, EventType.RUN, A, "6회초 1아웃", runs = 2, detail = "LG 2타점 적시타"),
            ScriptStep(240, EventType.INNING_END, null, "6회말"),
            ScriptStep(262, EventType.STRIKEOUT, H, "7회초 1아웃", detail = "KIA 투수 삼진"),
            ScriptStep(285, EventType.HOMERUN, A, "7회초 1아웃", runs = 2),
            ScriptStep(312, EventType.HOMERUN, H, "7회말 2아웃", runs = 2),
            ScriptStep(345, EventType.INNING_END, null, "8회말"),
            ScriptStep(362, EventType.STRIKEOUT, H, "9회초 2아웃", detail = "KIA 마무리 삼진"),
            ScriptStep(380, EventType.GAME_END, null, "9회초"),
        ),
    )

    /**
     * 시나리오 B (기획서): 주말 나들이, 전북 팬. 예약하지 않았는데 주행 중 경기가 시작된다.
     * → "중계를 시작할까요?" → 후반 23분 전북 골(1:0) → 경기 종료 휘슬.
     */
    val jeonbukSeoul = GameScript(
        info = game("kl-jeonbuk-seoul", League.KLEAGUE, "jeonbuk", "seoul", "16:30", "전주월드컵경기장"),
        startOffsetSec = 45,
        steps = listOf(
            ScriptStep(0, EventType.GAME_START, null, "전반 1분"),
            ScriptStep(55, EventType.HALF_END, null, "전반 종료"),
            ScriptStep(70, EventType.HALF_START, null, "후반 1분"),
            ScriptStep(100, EventType.GOAL, H, "후반 23분", runs = 1),
            ScriptStep(125, EventType.PENALTY, A, "후반 35분", detail = "서울 페널티킥 선언"),
            ScriptStep(138, EventType.RED_CARD, A, "후반 41분", detail = "서울 선수 퇴장"),
            ScriptStep(160, EventType.GAME_END, null, "후반 45+3분"),
        ),
    )

    /** 오늘의 나머지 경기: 모든 구단에 오늘 경기가 하나씩 있도록 자동 생성 (시드 고정) */
    private val others: List<GameScript> = listOf(
        generated(game("kbo-samsung-doosan", League.KBO, "samsung", "doosan", "18:30", "대구 삼성 라이온즈 파크"), -40),
        generated(game("kbo-ssg-lotte", League.KBO, "ssg", "lotte", "18:30", "인천 SSG 랜더스필드"), 120),
        generated(game("kbo-hanwha-nc", League.KBO, "hanwha", "nc", "18:30", "대전 한화생명 볼파크"), -20),
        generated(game("kbo-kt-kiwoom", League.KBO, "kt", "kiwoom", "18:30", "수원 KT 위즈 파크"), 200),
        generated(game("kl-ulsan-pohang", League.KLEAGUE, "ulsan", "pohang", "16:30", "울산 문수축구경기장"), -30),
        generated(game("kl-daejeon-gangwon", League.KLEAGUE, "daejeon", "gangwon", "16:30", "대전월드컵경기장"), 90),
        generated(game("kl-gwangju-jeju", League.KLEAGUE, "gwangju", "jeju", "19:00", "광주축구전용구장"), 150),
        generated(game("kl-gimcheon-suwonfc", League.KLEAGUE, "gimcheon", "suwonfc", "14:00", "김천종합운동장"), -60),
        generated(game("kl-anyang-daegu", League.KLEAGUE, "anyang", "daegu", "19:00", "안양종합운동장"), 240),
    )

    val today: List<GameScript> = listOf(kiaLg, jeonbukSeoul) + others

    /** 자동 생성 경기 (KBO 약 5분, K리그 약 3분) */
    fun generated(info: GameInfo, startOffsetSec: Int): GameScript {
        val rnd = Random(info.id.hashCode())
        val steps = mutableListOf(ScriptStep(0, EventType.GAME_START, null, if (info.sport == Sport.BASEBALL) "1회초" else "전반 1분"))
        if (info.sport == Sport.BASEBALL) {
            for (inning in 1..9) {
                val base = (inning - 1) * 32
                for ((half, side) in listOf("초" to A, "말" to H)) {
                    if (inning == 9 && half == "말") continue
                    val at = base + if (half == "초") 6 else 22
                    val roll = rnd.nextInt(100)
                    val period = "${inning}회$half"
                    when {
                        roll < 10 -> steps += ScriptStep(at, EventType.HOMERUN, side, period, runs = 1 + rnd.nextInt(3))
                        roll < 30 -> steps += ScriptStep(at, EventType.RUN, side, period, runs = 1 + rnd.nextInt(2))
                        roll < 50 -> steps += ScriptStep(at, EventType.HIT, side, period)
                    }
                }
            }
            steps += ScriptStep(9 * 32 - 4, EventType.GAME_END, null, "9회초")
        } else {
            steps += ScriptStep(80, EventType.HALF_END, null, "전반 종료")
            steps += ScriptStep(92, EventType.HALF_START, null, "후반 1분")
            val goals = rnd.nextInt(4)
            repeat(goals) {
                val first = rnd.nextBoolean()
                val minute = 5 + rnd.nextInt(40)
                val at = if (first) 2 + minute * 78 / 45 else 92 + minute * 84 / 45
                steps += ScriptStep(at, EventType.GOAL, if (rnd.nextBoolean()) H else A, "${if (first) "전반" else "후반"} ${minute}분", runs = 1)
            }
            steps += ScriptStep(178, EventType.GAME_END, null, "후반 45+2분")
        }
        return GameScript(info, startOffsetSec, steps.sortedBy { it.atSec })
    }
}
