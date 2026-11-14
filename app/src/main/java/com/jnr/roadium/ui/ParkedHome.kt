package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnr.roadium.RoadiumController
import com.jnr.roadium.UiState
import com.jnr.roadium.model.AlertLevel
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.GameStatus
import com.jnr.roadium.model.SoundKind
import kotlin.math.roundToInt

/**
 * 주차 중 화면: 응원 팀 오늘 경기 + ② 중계 예약, 알림 설정, 오늘의 다른 경기.
 * 주행을 시작하면(또는 주행 규제가 걸리면) 이 화면은 잠기고 드라이브 스코어보드로 바뀐다.
 */
@Composable
fun ParkedHome(ui: UiState, controller: RoadiumController, onEditTeams: () -> Unit, modifier: Modifier = Modifier) {
    val favoriteGames = ui.games.filter { it.info.involves(ui.favorites) }
        .sortedBy { if (it.status == GameStatus.FINISHED) 2 else if (it.status == GameStatus.LIVE) 0 else 1 }
    val otherGames = ui.games.filterNot { it.info.involves(ui.favorites) }

    Column(modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
        TopBar(ui, onEditTeams)
        Spacer(Modifier.height(24.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth > 1000.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    FavoriteList(favoriteGames, ui, controller, Modifier.weight(1.45f))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SettingsPanel(ui, controller)
                        OtherGames(otherGames, ui, controller)
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    favoriteGames.forEach { FavoriteGameCard(it, ui, controller) }
                    SettingsPanel(ui, controller)
                    OtherGames(otherGames, ui, controller)
                }
            }
        }
    }
}

@Composable
private fun TopBar(ui: UiState, onEditTeams: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ROADIUM", color = RoadiumColors.Yellow, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(20.dp))
        Pill("🅿  주차 중", RoadiumColors.SurfaceHigh)
        Spacer(Modifier.weight(1f))
        Choice("응원 팀 편집", false, onEditTeams)
    }
}

@Composable
private fun FavoriteList(games: List<GameSnapshot>, ui: UiState, controller: RoadiumController, modifier: Modifier) {
    Column(modifier) {
        SectionTitle("응원 팀 오늘 경기")
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(games, key = { it.info.id }) { FavoriteGameCard(it, ui, controller) }
        }
    }
}

@Composable
private fun FavoriteGameCard(s: GameSnapshot, ui: UiState, controller: RoadiumController) {
    val isLive = ui.liveGameId == s.info.id
    val isReserved = ui.reservedGameId == s.info.id
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RoadiumColors.Surface)
            .border(3.dp, if (isLive || isReserved) RoadiumColors.Blue else Color.Transparent, shape)
            .padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.info.league.label}  ·  ${statusText(s)}", color = RoadiumColors.TextDim, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (s.status == GameStatus.LIVE) Pill("LIVE", RoadiumColors.Red)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Labeled(s.info.home.short + " (홈)") { TeamBadge(s.info.home, 110.dp) }
            ScoreText(s, 88)
            Labeled(s.info.away.short + " (원정)") { TeamBadge(s.info.away, 110.dp) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    s.status == GameStatus.FINISHED -> Pill("경기 종료", RoadiumColors.SurfaceHigh)
                    isLive -> {
                        Pill("📡 중계 중", RoadiumColors.Blue)
                        BigButton("중계 끄기", controller::stopRelay, container = RoadiumColors.SurfaceHigh)
                    }
                    isReserved -> {
                        Pill("✓ 중계 예약됨", RoadiumColors.Blue)
                        BigButton("예약 취소", controller::cancelReservation, container = RoadiumColors.SurfaceHigh)
                    }
                    else -> BigButton("중계 예약", { controller.reserve(s.info.id) })
                }
            }
        }
        if (isReserved && !isLive) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (s.status == GameStatus.LIVE) "주행을 시작하면 별도 조작 없이 바로 중계합니다."
                else "주행 중 경기가 시작되면 별도 조작 없이 중계를 시작합니다.",
                color = RoadiumColors.TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val log = ui.logs[s.info.id].orEmpty().filter { it.type.importance <= 2 }.take(3)
        if (log.isNotEmpty() && s.status != GameStatus.SCHEDULED) {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RoadiumColors.SurfaceHigh).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                log.forEachIndexed { i, e ->
                    Row {
                        Text(e.period, color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(170.dp))
                        Text(
                            "${e.headline}  ${e.detail}",
                            color = if (i == 0) RoadiumColors.Yellow else RoadiumColors.Text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text("${e.homeScore}:${e.awayScore}", color = RoadiumColors.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(ui: UiState, controller: RoadiumController) {
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("알림 설정")
            Text("알림 범위", color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodyMedium)
            ChoiceRow {
                AlertLevel.entries.forEach { l -> Choice(l.label, ui.alertLevel == l, { controller.setAlertLevel(l) }) }
            }
            Text(
                when (ui.alertLevel) {
                    AlertLevel.SCORE_ONLY -> "홈런·득점·골과 경기 시작·종료만 알립니다."
                    AlertLevel.MAJOR -> "득점에 더해 페널티킥·퇴장·전후반 시작과 종료를 알립니다."
                    AlertLevel.ALL -> "안타·삼진·이닝 종료까지 모두 알립니다."
                },
                color = RoadiumColors.TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("효과음", color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodyMedium)
            ChoiceRow {
                Choice(if (ui.soundOn) "🔊 켜짐" else "🔇 꺼짐", ui.soundOn, { controller.setSoundOn(!ui.soundOn) })
                Choice("－", false, { controller.setVolume(ui.volume - 0.1f) })
                Text("볼륨 ${(ui.volume * 100).roundToInt()}%", color = RoadiumColors.Text, style = MaterialTheme.typography.labelLarge)
                Choice("＋", false, { controller.setVolume(ui.volume + 0.1f) })
            }
            Text("미리 듣기", color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodyMedium)
            ChoiceRow {
                Choice("⚾ 홈런", false, { controller.previewSound(SoundKind.HOMERUN) })
                Choice("⚽ 골", false, { controller.previewSound(SoundKind.GOAL) })
                Choice("휘슬", false, { controller.previewSound(SoundKind.WHISTLE_END) })
            }
        }
    }
}

@Composable
private fun OtherGames(games: List<GameSnapshot>, ui: UiState, controller: RoadiumController) {
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("오늘의 다른 경기")
            games.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(s.info.home, 52.dp)
                    Spacer(Modifier.width(8.dp))
                    TeamBadge(s.info.away, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${s.info.home.short} ${if (s.status == GameStatus.SCHEDULED) "vs" else "${s.homeScore} : ${s.awayScore}"} ${s.info.away.short}",
                            color = RoadiumColors.Text,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text("${s.info.league.label} · ${statusText(s)}", color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    when {
                        ui.liveGameId == s.info.id -> Pill("중계 중", RoadiumColors.Blue)
                        ui.reservedGameId == s.info.id -> Choice("예약 취소", true, controller::cancelReservation)
                        s.status != GameStatus.FINISHED -> Choice("예약", false, { controller.reserve(s.info.id) })
                    }
                }
            }
        }
    }
}
