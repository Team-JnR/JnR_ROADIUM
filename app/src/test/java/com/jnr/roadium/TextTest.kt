package com.jnr.roadium

import com.jnr.roadium.audio.SoundPlayer
import com.jnr.roadium.engine.Korean
import com.jnr.roadium.engine.VoiceIntent
import com.jnr.roadium.engine.VoiceIntents
import com.jnr.roadium.model.SoundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TextTest {
    @Test
    fun `조사`() {
        assertEquals("KIA가", Korean.iGa("KIA"))
        assertEquals("LG가", Korean.iGa("LG"))
        assertEquals("전북이", Korean.iGa("전북"))
        assertEquals("삼성이", Korean.iGa("삼성"))
        assertEquals("서울이", Korean.iGa("서울"))
        assertEquals("전북과", Korean.gwaWa("전북"))
        assertEquals("KIA와", Korean.gwaWa("KIA"))
        assertEquals("1 대 0으로", Korean.euroRo("1 대 0"))
        assertEquals("3 대 2로", Korean.euroRo("3 대 2"))
        assertEquals("5 대 1로", Korean.euroRo("5 대 1"))
        assertEquals("4 대 3으로", Korean.euroRo("4 대 3"))
    }

    @Test
    fun `음성 응답 의도`() {
        assertEquals(VoiceIntent.YES, VoiceIntents.parse("응"))
        assertEquals(VoiceIntent.YES, VoiceIntents.parse("네 시작해줘"))
        assertEquals(VoiceIntent.YES, VoiceIntents.parse("그래."))
        assertEquals(VoiceIntent.NO, VoiceIntents.parse("아니"))
        assertEquals(VoiceIntent.NO, VoiceIntents.parse("아니요 다음에"))
        assertEquals(VoiceIntent.NO, VoiceIntents.parse("싫어"))
        assertEquals(VoiceIntent.SCORE, VoiceIntents.parse("지금 몇 대 몇이야?"))
        assertEquals(VoiceIntent.SCORE, VoiceIntents.parse("점수 알려줘"))
        assertEquals(VoiceIntent.UNKNOWN, VoiceIntents.parse("뭐라고 했어"))
        assertEquals(VoiceIntent.UNKNOWN, VoiceIntents.parse(null))
    }

    @Test
    fun `효과음 파일이 모두 있고 길이가 2~4초 기준과 앱 설정에 맞다`() {
        val raw = File("src/main/res/raw")
        SoundKind.entries.forEach { kind ->
            val name = "sfx_" + when (kind) {
                SoundKind.SCORE_BASEBALL -> "score"
                else -> kind.name.lowercase()
            }
            val f = File(raw, "$name.wav")
            assertTrue("없음: ${f.path}", f.exists())
            val header = ByteBuffer.wrap(f.readBytes().copyOf(44)).order(ByteOrder.LITTLE_ENDIAN)
            val ms = (f.length() - 44) * 1000 / header.getInt(28)
            val expected = SoundPlayer.durations.getValue(kind)
            assertTrue("${f.name}: ${ms}ms ≠ ${expected}ms", kotlin.math.abs(ms - expected) <= 20)
            assertTrue("${f.name}: ${ms}ms > 4.2s", ms <= SoundPlayer.MAX_SOUND_MS)
        }
    }
}
