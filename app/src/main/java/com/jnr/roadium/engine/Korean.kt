package com.jnr.roadium.engine

/**
 * 음성 안내 문장 틀에 쓰는 조사 처리. 팀 이름(한글·영문 약칭)과 숫자의 마지막 소리에 맞춰
 * 이/가, 과/와, 으로/로를 고른다. ("KIA가", "전북이", "1 대 0으로", "3 대 2로")
 */
object Korean {
    /** 영문 약칭을 읽을 때 받침으로 끝나는 글자: L(엘) M(엠) N(엔) R(알) */
    private val latinWithBatchim = setOf('L', 'M', 'N', 'R')

    /** 받침 종류: null = 받침 없음, 'ㄹ' = ㄹ 받침, 'O' = 그 외 받침 */
    private fun batchim(word: String): Char? {
        val c = word.trim().lastOrNull { !it.isWhitespace() } ?: return null
        return when {
            c in '가'..'힣' -> {
                val jong = (c - '가') % 28
                when (jong) {
                    0 -> null
                    8 -> 'ㄹ'
                    else -> 'O'
                }
            }
            c.isDigit() -> when (c) {
                '0', '3', '6' -> 'O' // 영, 삼, 육
                '1', '7', '8' -> 'ㄹ' // 일, 칠, 팔
                else -> null // 이, 사, 오, 구
            }
            c.uppercaseChar() in latinWithBatchim -> if (c.uppercaseChar() == 'L' || c.uppercaseChar() == 'R') 'ㄹ' else 'O'
            else -> null
        }
    }

    /** 주격: 전북이 / KIA가 */
    fun iGa(word: String) = word + if (batchim(word) != null) "이" else "가"

    /** 접속: 전북과 / KIA와 */
    fun gwaWa(word: String) = word + if (batchim(word) != null) "과" else "와"

    /** 방향·수단: 0으로 / 2로 / 1로(ㄹ 받침은 '로') */
    fun euroRo(word: String) = word + when (batchim(word)) {
        null, 'ㄹ' -> "로"
        else -> "으로"
    }
}
