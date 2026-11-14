package com.jnr.roadium.engine

/** STT 결과 문장 → 의도. 짧은 대답 위주라 키워드로 판단한다 */
object VoiceIntents {
    private val score = listOf("몇 대 몇", "몇대몇", "몇 대몇", "점수", "스코어", "어떻게 되", "이기고", "지고")
    private val no = listOf("아니", "아뇨", "싫어", "다음에", "됐어", "괜찮아", "그만", "취소", "노")
    private val yes = listOf("응", "네", "예", "그래", "좋아", "시작", "해줘", "틀어", "켜", "어", "오케이", "콜")

    fun parse(text: String?): VoiceIntent {
        val t = text?.replace(Regex("[.,!?~]"), "")?.trim()?.lowercase() ?: return VoiceIntent.UNKNOWN
        if (t.isEmpty()) return VoiceIntent.UNKNOWN
        if (score.any { t.contains(it) }) return VoiceIntent.SCORE
        // '아니'에 들어 있는 글자가 긍정 단어와 겹치지 않도록 부정을 먼저 본다
        if (no.any { t.contains(it) }) return VoiceIntent.NO
        // 한 글자 대답("응", "네", "어")은 문장 맨 앞일 때만, 두 글자 이상은 어디에 있어도 긍정으로 본다
        if (yes.any { t == it || t.startsWith(it) || (it.length >= 2 && t.contains(it)) }) return VoiceIntent.YES
        if (t == "yes" || t == "ok") return VoiceIntent.YES
        return VoiceIntent.UNKNOWN
    }
}
