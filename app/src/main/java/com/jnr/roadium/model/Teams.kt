package com.jnr.roadium.model

enum class Sport { BASEBALL, SOCCER }

enum class League(val label: String, val sport: Sport) {
    KBO("KBO", Sport.BASEBALL),
    KLEAGUE("K리그1", Sport.SOCCER),
}

/**
 * 구단. 화면에는 리그·구단과 협의한 로고 대신 구단 색 원형 배지와 짧은 이름을 쓴다(시안과 동일).
 * @param short 스코어보드·음성 안내에 쓰는 짧은 이름 ("KIA", "전북")
 * @param color 배지 색 (ARGB)
 */
data class Team(
    val id: String,
    val league: League,
    val short: String,
    val fullName: String,
    val color: Long,
)

object Teams {
    val all: List<Team> = listOf(
        // KBO 10개 구단
        Team("kia", League.KBO, "KIA", "KIA 타이거즈", 0xFFD6182B),
        Team("lg", League.KBO, "LG", "LG 트윈스", 0xFF5A5D66),
        Team("samsung", League.KBO, "삼성", "삼성 라이온즈", 0xFF1F5FBF),
        Team("doosan", League.KBO, "두산", "두산 베어스", 0xFF2B2A5C),
        Team("ssg", League.KBO, "SSG", "SSG 랜더스", 0xFFCE0E2D),
        Team("lotte", League.KBO, "롯데", "롯데 자이언츠", 0xFF1B3A6B),
        Team("hanwha", League.KBO, "한화", "한화 이글스", 0xFFF26A1B),
        Team("nc", League.KBO, "NC", "NC 다이노스", 0xFF315288),
        Team("kt", League.KBO, "KT", "KT 위즈", 0xFF3A3A3A),
        Team("kiwoom", League.KBO, "키움", "키움 히어로즈", 0xFF8A1538),
        // K리그1 12개 구단 (프로토타입 표기)
        Team("jeonbuk", League.KLEAGUE, "전북", "전북 현대 모터스", 0xFF1E8B4C),
        Team("seoul", League.KLEAGUE, "서울", "FC서울", 0xFFB3122E),
        Team("ulsan", League.KLEAGUE, "울산", "울산 HD", 0xFF1D4FC4),
        Team("pohang", League.KLEAGUE, "포항", "포항 스틸러스", 0xFFC8102E),
        Team("daejeon", League.KLEAGUE, "대전", "대전 하나시티즌", 0xFF6B2C91),
        Team("gangwon", League.KLEAGUE, "강원", "강원FC", 0xFFE36C0A),
        Team("gwangju", League.KLEAGUE, "광주", "광주FC", 0xFFD9A400),
        Team("jeju", League.KLEAGUE, "제주", "제주SK FC", 0xFFE8501C),
        Team("gimcheon", League.KLEAGUE, "김천", "김천 상무", 0xFF9E1B32),
        Team("suwonfc", League.KLEAGUE, "수원FC", "수원FC", 0xFF16407A),
        Team("anyang", League.KLEAGUE, "안양", "FC안양", 0xFF4B2E83),
        Team("daegu", League.KLEAGUE, "대구", "대구FC", 0xFF4AA3DF),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Team? = byId[id]

    fun of(league: League): List<Team> = all.filter { it.league == league }
}
