package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnr.roadium.model.GameSnapshot
import com.jnr.roadium.model.GameStatus
import com.jnr.roadium.model.Team

/** 구단 배지: 구단 색 원 + 짧은 이름 (실제 서비스에서는 리그·구단과 협의한 로고로 교체) */
@Composable
fun TeamBadge(team: Team, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(team.color))
            .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val fs = when {
            team.short.length >= 4 -> size.value * 0.22f
            team.short.length == 3 -> size.value * 0.28f
            else -> size.value * 0.32f
        }
        Text(team.short, color = Color.White, fontWeight = FontWeight.Black, fontSize = fs.sp, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/** 큰 버튼: 최소 높이 88dp (주행 중 터치 대상은 크게) */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = RoadiumColors.Blue,
    content: Color = Color.White,
    minHeight: Dp = 88.dp,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = RoadiumColors.SurfaceHigh,
            disabledContentColor = RoadiumColors.TextDim,
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleLarge, color = RoadiumColors.Text)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(RoadiumColors.Surface)
            .padding(28.dp),
    ) { content() }
}

/** 선택형 칩 (알림 범위 등) */
@Composable
fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) RoadiumColors.Blue else RoadiumColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        color = if (enabled) Color.White else RoadiumColors.TextDim,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** "18:30 시작 · 45초 후" / "3회말 1아웃" / "경기 종료" */
fun statusText(s: GameSnapshot): String = when (s.status) {
    GameStatus.SCHEDULED -> if (s.secondsToStart > 0) "${s.info.startLabel} 시작 · 시연 ${formatSec(s.secondsToStart)} 후" else "${s.info.startLabel} 시작"
    GameStatus.LIVE -> s.period
    GameStatus.FINISHED -> "경기 종료"
}

fun formatSec(sec: Int): String = if (sec >= 60) "${sec / 60}분 ${sec % 60}초" else "${sec}초"

/** 스코어 숫자 (시작 전에는 "vs") */
@Composable
fun ScoreText(s: GameSnapshot, fontSize: Int, modifier: Modifier = Modifier) {
    val text = if (s.status == GameStatus.SCHEDULED) "vs" else "${s.homeScore} : ${s.awayScore}"
    Text(text, modifier, fontSize = fontSize.sp, fontWeight = FontWeight.Black, color = RoadiumColors.Text, maxLines = 1)
}

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Text(label, Modifier.padding(top = 10.dp), color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}
