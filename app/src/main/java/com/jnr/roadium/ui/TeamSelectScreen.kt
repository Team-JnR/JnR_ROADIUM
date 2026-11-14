package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jnr.roadium.model.League
import com.jnr.roadium.model.Team
import com.jnr.roadium.model.Teams

/** ① 관심 팀 등록 (주차 중에만). KBO 10개 구단, K리그1 구단 중 여러 팀을 고를 수 있다 */
@Composable
fun TeamSelectScreen(initial: Set<String>, onDone: (Set<String>) -> Unit, onCancel: (() -> Unit)?, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(initial) }
    Column(modifier.fillMaxSize().padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("응원 팀을 골라 주세요", style = MaterialTheme.typography.displayMedium, color = RoadiumColors.Text)
                Text(
                    "앱을 열면 응원 팀의 오늘 경기가 가장 먼저 보이고, 주행 중 경기가 시작되면 알려 드립니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RoadiumColors.TextDim,
                )
            }
            if (onCancel != null) {
                BigButton("취소", onCancel, container = RoadiumColors.SurfaceHigh)
                Spacer(Modifier.padding(8.dp))
            }
            BigButton("완료 (${selected.size})", { onDone(selected) }, enabled = selected.isNotEmpty())
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            League.entries.forEach { league ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SectionTitle(league.label)
                        Teams.of(league).chunked(6).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                row.forEach { team ->
                                    TeamChip(team, team.id in selected, Modifier.weight(1f)) {
                                        selected = if (team.id in selected) selected - team.id else selected + team.id
                                    }
                                }
                                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamChip(team: Team, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) RoadiumColors.Blue.copy(alpha = 0.22f) else RoadiumColors.SurfaceHigh)
            .border(3.dp, if (selected) RoadiumColors.Blue else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            TeamBadge(team, 92.dp)
            if (selected) Pill("✓", RoadiumColors.Blue)
        }
        Spacer(Modifier.height(12.dp))
        Text(team.fullName, style = MaterialTheme.typography.bodyMedium, color = RoadiumColors.Text, textAlign = TextAlign.Center, maxLines = 2)
    }
}
