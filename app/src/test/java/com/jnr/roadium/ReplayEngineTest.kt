package com.jnr.roadium

import com.jnr.roadium.feed.ReplayEngine
import com.jnr.roadium.feed.ReplayScripts
import com.jnr.roadium.model.EventType
import com.jnr.roadium.model.GameStatus
import com.jnr.roadium.model.Teams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayEngineTest {
    private val kia = ReplayScripts.kiaLg.info.id
    private val jeonbuk = ReplayScripts.jeonbukSeoul.info.id

    @Test
    fun `앱 시작 시 KIA 경기는 3회말 1대0으로 진행 중이고 지난 이벤트는 소리 없이 반영된다`() {
        val r = ReplayEngine(ReplayScripts.today)
        val s = r.snapshot(kia)!!
        assertEquals(GameStatus.LIVE, s.status)
        assertEquals(1, s.homeScore)
        assertEquals(0, s.awayScore)
        assertEquals("3회말 1아웃", s.period)
        assertTrue(r.advance(1_000, 0).none { it.gameId == kia })
    }

    @Test
    fun `5회말 KIA 2점 홈런은 3대0 홈런 카드`() {
        val r = ReplayEngine(ReplayScripts.today)
        val events = r.advance(70_000, 0).filter { it.gameId == kia }
        val hr = events.single { it.type == EventType.HOMERUN }
        assertEquals("홈런!", hr.headline)
        assertEquals("KIA 2점 홈런", hr.detail)
        assertEquals(3 to 0, hr.homeScore to hr.awayScore)
    }

    @Test
    fun `역전은 역전 카드로 표시된다`() {
        val r = ReplayEngine(ReplayScripts.today)
        val events = r.advance(300_000, 0).filter { it.gameId == kia }
        val lgHr = events.first { it.type == EventType.HOMERUN && it.awayScore == 4 }
        assertTrue(lgHr.leadChange)
        assertEquals("역전!", lgHr.headline)
        assertEquals("LG 2점 홈런 · 3:4", lgHr.detail)
        val end = events.last()
        assertEquals(EventType.GAME_END, end.type)
        assertEquals("KIA 5:4 승리", end.detail)
        assertEquals(GameStatus.FINISHED, r.snapshot(kia)!!.status)
    }

    @Test
    fun `전북 경기는 45초 뒤 시작한다`() {
        val r = ReplayEngine(ReplayScripts.today)
        assertEquals(GameStatus.SCHEDULED, r.snapshot(jeonbuk)!!.status)
        assertEquals(45, r.snapshot(jeonbuk)!!.secondsToStart)
        assertTrue(r.advance(44_000, 0).none { it.gameId == jeonbuk })
        assertEquals(EventType.GAME_START, r.advance(45_000, 0).single { it.gameId == jeonbuk }.type)
        val goal = r.advance(145_000, 0).single { it.gameId == jeonbuk && it.type == EventType.GOAL }
        assertEquals("골!", goal.headline)
        assertEquals("전북 1:0 (후반 23분)", goal.detail)
    }

    @Test
    fun `모든 구단에 오늘 경기가 하나씩 있다`() {
        val ids = ReplayScripts.today.flatMap { listOf(it.info.home.id, it.info.away.id) }
        assertEquals(Teams.all.map { it.id }.sorted(), ids.sorted())
    }

    @Test
    fun `모든 경기는 결국 끝난다`() {
        val r = ReplayEngine(ReplayScripts.today)
        r.advance(30 * 60_000L, 0)
        assertTrue(r.snapshots().all { it.status == GameStatus.FINISHED })
    }
}
