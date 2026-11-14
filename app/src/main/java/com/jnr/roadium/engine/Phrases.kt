package com.jnr.roadium.engine

import com.jnr.roadium.engine.Korean.euroRo
import com.jnr.roadium.engine.Korean.gwaWa
import com.jnr.roadium.engine.Korean.iGa
import com.jnr.roadium.model.GameInfo
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.GameStatus
import com.jnr.roadium.model.Side
import com.jnr.roadium.model.Sport

/**
 * 음성 안내 문장 틀. 생성형 해설 없이 정해진 틀에 데이터만 채운다
 * (기획서: 데이터에 없는 내용을 말할 위험이 있어 초기에는 정해진 문장 틀만 사용).
 */
object Phrases {
    /** "KIA가 1 대 0으로 앞서고 있습니다" / "3 대 3 동점입니다" */
    fun scoreState(s: GameSnapshot): String {
        val leader = s.leader ?: return "${s.homeScore} 대 ${s.awayScore} 동점입니다."
        val lead = s.score(leader)
        val trail = s.score(if (leader == Side.HOME) Side.AWAY else Side.HOME)
        return "${iGa(s.info.team(leader).short)} ${euroRo("$lead 대 $trail")} 앞서고 있습니다."
    }

    /** "현재 3회말 1아웃, KIA가 1 대 0으로 앞서고 있습니다." */
    fun status(s: GameSnapshot): String = when (s.status) {
        GameStatus.SCHEDULED -> "${matchup(s.info)} 경기는 아직 시작 전입니다."
        GameStatus.FINISHED -> final(s)
        GameStatus.LIVE -> "현재 ${s.period}, ${scoreState(s)}"
    }

    fun matchup(info: GameInfo) = "${gwaWa(info.home.short)} ${info.away.short}의"

    /** "전북과 서울의 경기가 시작됐습니다. 중계를 시작할까요?" */
    fun startQuestion(info: GameInfo) = "${matchup(info)} 경기가 시작됐습니다. 중계를 시작할까요?"

    fun reservedStarted(info: GameInfo) = "${matchup(info)} 경기가 시작됐습니다. 예약한 중계를 시작합니다."

    fun reservedWaiting(info: GameInfo) = "${matchup(info)} 경기는 아직 시작 전입니다. 시작하면 자동으로 중계합니다."

    fun relayStart(s: GameSnapshot) = "중계를 시작합니다. ${status(s)}"

    /** "경기 종료. 전북이 1 대 0으로 승리했습니다." */
    fun final(s: GameSnapshot): String {
        val winner = s.leader ?: return "경기 종료. ${s.homeScore} 대 ${s.awayScore} 무승부입니다."
        val w = s.score(winner)
        val l = s.score(if (winner == Side.HOME) Side.AWAY else Side.HOME)
        return "경기 종료. ${iGa(s.info.team(winner).short)} ${euroRo("$w 대 $l")} 승리했습니다."
    }

    /**
     * 내비 안내로 소리를 보류한 사이의 변화를 한 번으로 묶는다.
     * "그사이 LG가 2점을 냈습니다. 현재 KIA가 3 대 2로 앞서고 있습니다."
     * @return 점수 변화도 경기 종료도 없으면 null (말하지 않음)
     */
    fun heldSummary(fromHome: Int, fromAway: Int, now: GameSnapshot): String? {
        val dHome = now.homeScore - fromHome
        val dAway = now.awayScore - fromAway
        val soccer = now.info.sport == Sport.SOCCER
        // "KIA가 2점" / "서울이 1골"
        fun gained(side: Side, n: Int) = "${iGa(now.info.team(side).short)} $n${if (soccer) "골" else "점"}"
        val verb = if (soccer) "을 넣었습니다." else "을 냈습니다."
        val parts = buildList {
            if (dHome > 0) add(gained(Side.HOME, dHome))
            if (dAway > 0) add(gained(Side.AWAY, dAway))
        }
        val scored = if (parts.isEmpty()) null else "그사이 ${parts.joinToString(", ")}$verb"
        return when {
            now.status == GameStatus.FINISHED -> listOfNotNull(scored, final(now)).joinToString(" ")
            scored != null -> "$scored 현재 ${scoreState(now)}"
            else -> null
        }
    }
}
