package com.jnr.roadium

import com.jnr.roadium.engine.BroadcastEngine
import com.jnr.roadium.engine.Effect
import com.jnr.roadium.engine.VoiceIntent
import com.jnr.roadium.feed.ReplayEngine
import com.jnr.roadium.feed.ReplayScripts
import com.jnr.roadium.model.AlertLevel
import com.jnr.roadium.model.EventType
import com.jnr.roadium.model.SoundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기획서 시나리오 A·B와 예외 처리 규칙을 엔진 단위로 검증한다 */
class BroadcastEngineTest {
    private val kia = ReplayScripts.kiaLg.info.id
    private val jeonbuk = ReplayScripts.jeonbukSeoul.info.id

    private val replay = ReplayEngine(ReplayScripts.today)
    private val engine = BroadcastEngine { replay.snapshot(it) }.apply {
        favorites = setOf("kia", "jeonbuk")
        alertLevel = AlertLevel.MAJOR
    }
    private var clock = 0L

    /** 세션 시각 [toSec]까지 진행하며 엔진 지시를 모은다 */
    private fun runTo(toSec: Int): List<Effect> {
        val out = mutableListOf<Effect>()
        while (clock < toSec * 1000L) {
            clock += 500
            replay.advance(clock, clock).forEach { out += engine.onEvent(it, clock) }
            out += engine.tick(clock)
        }
        return out
    }

    private fun List<Effect>.speech() = filterIsInstance<Effect.Speak>().map { it.text }
    private fun List<Effect>.sounds() = filterIsInstance<Effect.PlaySound>().map { it.kind }
    private fun List<Effect>.cards() = filterIsInstance<Effect.ShowCard>()

    @Test
    fun `시나리오 A - 예약 후 주행 시작하면 현재 상황을 한 번 알리고 홈런에 효과음과 카드`() {
        engine.reserve(kia)
        val start = engine.setDriving(true, clock)
        assertEquals(kia, engine.liveGameId)
        assertEquals(listOf("중계를 시작합니다. 현재 3회말 1아웃, KIA가 1 대 0으로 앞서고 있습니다."), start.speech())

        val hr = runTo(70)
        val card = hr.cards().single { it.event.type == EventType.HOMERUN }
        assertEquals("KIA 2점 홈런", card.event.detail)
        assertEquals(listOf(SoundKind.HOMERUN), hr.sounds())
        // 주요 이벤트 범위에서는 안타·삼진·이닝 종료는 소리도 카드도 없다
        assertTrue(hr.cards().none { it.event.type == EventType.HIT || it.event.type == EventType.STRIKEOUT })
    }

    @Test
    fun `시나리오 A - 내비 안내 중 득점은 카드만, 안내가 끝나면 한 번에 요약`() {
        engine.reserve(kia)
        engine.setDriving(true, clock)
        runTo(100)
        engine.setGuidance(true)
        val during = runTo(115) // 6회초 LG 2점 (경기 212초 = 세션 112초)
        val card = during.cards().single { it.event.type == EventType.RUN }
        assertTrue(card.soundHeld)
        assertTrue(during.sounds().isEmpty())
        assertTrue(during.speech().isEmpty())

        val after = engine.setGuidance(false)
        assertEquals(listOf("그사이 LG가 2점을 냈습니다. 현재 KIA가 3 대 2로 앞서고 있습니다."), after.speech())
    }

    @Test
    fun `시나리오 A - 주차하면 이동 중 득점 장면 리캡`() {
        engine.reserve(kia)
        engine.setDriving(true, clock)
        runTo(115)
        val parked = engine.setDriving(false, clock)
        val recap = parked.filterIsInstance<Effect.ShowRecap>().single().recap
        assertEquals(1 to 0, recap.startHome to recap.startAway)
        assertEquals(listOf("3:0", "3:2"), recap.items.map { "${it.homeScore}:${it.awayScore}" })
    }

    @Test
    fun `시나리오 B - 예약하지 않은 응원 팀 경기가 주행 중 시작되면 묻고, 응 하면 중계`() {
        engine.setDriving(true, clock)
        val start = runTo(46)
        val prompt = start.filterIsInstance<Effect.ShowPrompt>().single()
        assertEquals(jeonbuk, prompt.game.id)
        val ask = start.filterIsInstance<Effect.Speak>().single()
        assertEquals("전북과 서울의 경기가 시작됐습니다. 중계를 시작할까요?", ask.text)
        assertTrue(ask.listenAfter)

        val yes = engine.onVoice(VoiceIntent.YES)
        assertEquals(jeonbuk, engine.liveGameId)
        assertTrue(yes.contains(Effect.DismissPrompt))

        val goal = runTo(150)
        assertTrue(goal.sounds().contains(SoundKind.GOAL))
        val end = runTo(210)
        assertTrue(end.sounds().contains(SoundKind.WHISTLE_END))
        assertTrue(end.speech().contains("경기 종료. 전북이 1 대 0으로 승리했습니다."))
        assertNull(engine.liveGameId)
    }

    @Test
    fun `시나리오 B - 10초간 응답이 없으면 조용히 닫는다`() {
        engine.setDriving(true, clock)
        runTo(46)
        val later = runTo(57)
        assertTrue(later.contains(Effect.DismissPrompt))
        assertNull(engine.promptGameId)
        assertNull(engine.liveGameId)
    }

    @Test
    fun `예약한 경기가 주행 중 시작되면 조작 없이 바로 중계`() {
        engine.reserve(jeonbuk)
        engine.setDriving(true, clock)
        val start = runTo(46)
        assertEquals(jeonbuk, engine.liveGameId)
        assertTrue(start.filterIsInstance<Effect.ShowPrompt>().isEmpty())
        assertTrue(start.speech().single().contains("예약한 중계를 시작합니다"))
    }

    @Test
    fun `이벤트가 몰리면 효과음 사이 최소 간격을 두고 밀린 것은 하나로 합친다`() {
        engine.alertLevel = AlertLevel.ALL
        engine.reserve(kia)
        engine.setDriving(true, clock)
        // 경기 시간 20초를 한 번에 건너뛰어 안타(150초)·홈런(168초)을 몰리게 한다
        clock = 49_000
        replay.advance(clock, clock)
        clock = 69_000
        val burst = replay.advance(clock, clock).flatMap { engine.onEvent(it, clock) }
        assertEquals(1, burst.sounds().size)
        val next = engine.tick(clock + BroadcastEngine.MIN_SOUND_GAP_MS)
        assertEquals(1, next.sounds().size)
    }

    @Test
    fun `소리를 끄면 카드만 보인다`() {
        engine.soundOn = false
        engine.reserve(kia)
        engine.setDriving(true, clock)
        val effects = runTo(70)
        assertTrue(effects.sounds().isEmpty())
        assertTrue(effects.cards().isNotEmpty())
    }

    @Test
    fun `지금 몇 대 몇이야`() {
        assertEquals(listOf("지금 중계 중인 경기가 없습니다."), engine.askScore().speech())
        engine.reserve(kia)
        engine.setDriving(true, clock)
        assertEquals(listOf("현재 3회말 1아웃, KIA가 1 대 0으로 앞서고 있습니다."), engine.onVoice(VoiceIntent.SCORE).speech())
    }
}
