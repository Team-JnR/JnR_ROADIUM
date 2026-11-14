package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnr.roadium.CardState
import com.jnr.roadium.PromptState
import com.jnr.roadium.RoadiumController
import com.jnr.roadium.UiState
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.GameStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 주행 중 화면. 플랫폼 주행 규제를 따라 스코어보드·이벤트 카드·경기 시작 질문만 남긴다.
 * 움직이는 요소(애니메이션·자동 스크롤)는 없고, 상태가 바뀌면 화면만 교체한다.
 * 터치 대상은 '중계 시작/다음에', '소리 끄기', '점수 묻기' 큰 버튼뿐이다.
 */
@Composable
fun DriveScreen(ui: UiState, controller: RoadiumController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(36.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val prompt = ui.prompt
            val card = ui.card?.takeIf { it.event.gameId == ui.liveGameId || ui.liveGameId == null }
            val live = ui.live
            when {
                prompt != null -> StartPrompt(prompt, ui, controller)
                card != null -> EventCard(card, ui)
                live != null -> Scoreboard(live, ui)
                ui.reserved != null -> WaitingBoard(ui.reserved!!)
                else -> IdleBoard()
            }
        }
        // 자막 자리는 늘 비워 둔다: 자막이 나타날 때 버튼이 움직여 잘못 누르지 않도록
        Box(Modifier.fillMaxWidth().padding(top = 20.dp).height(64.dp)) {
            ui.caption?.let { VoiceCaption(it) }
        }
        if (ui.prompt == null) DriveControls(ui, controller)
    }
}

/** 음성 안내 자막: 무엇을 말했는지 짧게 보여 준다 (말할 엔진이 없거나 소리를 못 들었을 때도 확인 가능) */
@Composable
private fun VoiceCaption(text: String) {
    Text(
        "🔊  $text",
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(RoadiumColors.SurfaceHigh)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        color = RoadiumColors.Text,
        fontSize = 26.sp,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
private fun BoardFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(36.dp))
            .background(RoadiumColors.Surface)
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Header(text: String) {
    Text(text, color = RoadiumColors.TextDim, fontSize = 34.sp, fontWeight = FontWeight.Medium, maxLines = 1)
}

/** ① 평상시: 양 팀과 스코어만 */
@Composable
private fun Scoreboard(s: GameSnapshot, ui: UiState) {
    BoardFrame {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 900.dp
            val badge = if (compact) 150.dp else 210.dp
            val scoreSize = if (compact) 120 else 190
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Header("${s.info.league.label}  ·  ${s.period}")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 56.dp)) {
                    Labeled("홈") { TeamBadge(s.info.home, badge) }
                    ScoreText(s, scoreSize)
                    Labeled("원정") { TeamBadge(s.info.away, badge) }
                }
                StatusStrip(ui, s)
            }
        }
    }
}

@Composable
private fun StatusStrip(ui: UiState, s: GameSnapshot?) {
    val parts = buildList {
        add(if (s?.status == GameStatus.FINISHED) "중계 종료" else "중계 중")
        add(
            when {
                !ui.soundOn -> "효과음 꺼짐"
                ui.guidanceActive -> "내비 안내 중 · 소리 보류"
                else -> "효과음 켜짐"
            }
        )
        s?.updatedAt?.takeIf { it > 0 }?.let { add("마지막 갱신 ${TIME.format(Date(it))}") }
    }
    Text(
        parts.joinToString("  ·  "),
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RoadiumColors.SurfaceHigh)
            .padding(vertical = 18.dp),
        color = if (ui.guidanceActive) RoadiumColors.Yellow else RoadiumColors.TextDim,
        fontSize = 26.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private val TIME = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

/** ② 이벤트 발생: 정적 카드 + 효과음 */
@Composable
private fun EventCard(card: CardState, ui: UiState) {
    val e = card.event
    BoardFrame {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Header("${card.info.league.label}  ·  ${e.period}")
            Column(
                Modifier
                    .widthIn(max = 1100.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(5.dp, RoadiumColors.Yellow, RoundedCornerShape(28.dp))
                    .background(RoadiumColors.SurfaceHigh)
                    .padding(vertical = 36.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(e.headline, color = RoadiumColors.Yellow, fontSize = 120.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(e.detail, color = RoadiumColors.Text, fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(e.scoreLine(card.info), color = RoadiumColors.Text, fontSize = 64.sp, fontWeight = FontWeight.Black)
                val soundLine = when {
                    card.sound == null -> ""
                    !ui.soundOn -> "🔕 효과음 꺼짐"
                    card.soundHeld && ui.guidanceActive -> "🔕 내비 안내 중 · 안내가 끝나면 한 번에 알려 드립니다"
                    card.soundHeld -> "🔊 내비 안내가 끝나 최신 상황을 음성으로 알려 드렸습니다"
                    else -> "♪ ${card.sound.caption} (약 ${card.sound.approxSec}초)"
                }
                if (soundLine.isNotEmpty()) {
                    Text(soundLine, color = if (card.soundHeld && ui.guidanceActive) RoadiumColors.Yellow else RoadiumColors.TextDim, fontSize = 26.sp)
                }
            }
        }
    }
}

/** ③ 주행 중 경기 시작: 음성으로 묻고 큰 버튼으로 응답 */
@Composable
private fun StartPrompt(p: PromptState, ui: UiState, controller: RoadiumController) {
    BoardFrame {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Header("${p.game.league.label}  ·  경기 시작")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${p.game.home.short}  vs  ${p.game.away.short}", color = RoadiumColors.Text, fontSize = 96.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(20.dp))
                Text(
                    "응원 팀 경기가 시작됐습니다.\n중계를 시작할까요?",
                    color = RoadiumColors.Text,
                    fontSize = 40.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 54.sp,
                )
                Spacer(Modifier.height(14.dp))
                val hint = when {
                    ui.listening -> "🎤 듣고 있어요 · \"응\" 또는 \"아니\"" + if (ui.heard.isNotBlank()) "  —  \"${ui.heard}\"" else ""
                    ui.speaking -> "🔊 안내 중…"
                    else -> "${p.secondsLeft}초 동안 응답이 없으면 조용히 닫힙니다"
                }
                Text(hint, color = RoadiumColors.TextDim, fontSize = 26.sp, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                BigButton("중계 시작", controller::acceptPrompt, Modifier.weight(1f), minHeight = 120.dp)
                BigButton("다음에", controller::declinePrompt, Modifier.weight(1f), container = RoadiumColors.SurfaceHigh, minHeight = 120.dp)
            }
        }
    }
}

@Composable
private fun WaitingBoard(s: GameSnapshot) {
    BoardFrame {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Header("${s.info.league.label}  ·  중계 예약됨")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                TeamBadge(s.info.home, 170.dp)
                Text("vs", color = RoadiumColors.TextDim, fontSize = 96.sp, fontWeight = FontWeight.Black)
                TeamBadge(s.info.away, 170.dp)
            }
            Text(
                "${statusText(s)}\n경기가 시작되면 자동으로 중계합니다",
                color = RoadiumColors.TextDim,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                lineHeight = 44.sp,
            )
        }
    }
}

@Composable
private fun IdleBoard() {
    BoardFrame {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROADIUM", color = RoadiumColors.Yellow, fontSize = 72.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            Text(
                "중계 중인 경기가 없습니다\n응원 팀 경기가 시작되면 알려 드릴게요",
                color = RoadiumColors.TextDim,
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
                lineHeight = 48.sp,
            )
        }
    }
}

@Composable
private fun DriveControls(ui: UiState, controller: RoadiumController) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BigButton(
            if (ui.soundOn) "🔇  소리 끄기" else "🔊  소리 켜기",
            { controller.setSoundOn(!ui.soundOn) },
            Modifier.weight(1f),
            container = RoadiumColors.SurfaceHigh,
        )
        val label = when {
            ui.listening -> "🎤  듣고 있어요…"
            else -> "🎤  지금 몇 대 몇?"
        }
        BigButton(label, controller::askScore, Modifier.weight(1f), container = RoadiumColors.SurfaceHigh)
    }
    if (ui.listening && ui.heard.isNotBlank()) {
        Text(
            "\"${ui.heard}\"",
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = RoadiumColors.TextDim,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
