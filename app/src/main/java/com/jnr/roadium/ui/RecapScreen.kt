package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.jnr.roadium.engine.Recap
import com.jnr.roadium.model.GameStatus

/** ⑦ 주차 후 경기 리캡: 이동 중 있었던 득점·역전 장면을 시간순으로 */
@Composable
fun RecapScreen(recap: Recap, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val info = recap.info
    Column(modifier.fillMaxSize().padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("이동 중 경기 리캡", style = MaterialTheme.typography.displayMedium, color = RoadiumColors.Text)
                Text(
                    "주행 ${if (recap.drivingMinutes < 1) "1분 미만" else "${recap.drivingMinutes}분"} 동안의 ${info.title} 경기 흐름입니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RoadiumColors.TextDim,
                )
            }
            BigButton("닫기", onClose)
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Panel(Modifier.weight(1f).fillMaxSize()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        "${info.league.label} · ${if (recap.end.status == GameStatus.FINISHED) "경기 종료" else "현재 ${recap.end.period}"}",
                        color = RoadiumColors.TextDim,
                        fontSize = 28.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Labeled("홈") { TeamBadge(info.home, 104.dp) }
                        ScoreText(recap.end, 88)
                        Labeled("원정") { TeamBadge(info.away, 104.dp) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "출발 ${recap.startHome}:${recap.startAway}  →  도착 ${recap.end.homeScore}:${recap.end.awayScore}",
                        color = RoadiumColors.Text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Panel(Modifier.weight(1.2f).fillMaxSize()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SectionTitle("득점 타임라인")
                    Spacer(Modifier.height(20.dp))
                    TimelineRow("출발", "${recap.startPeriod} 시점", "${recap.startHome}:${recap.startAway}", RoadiumColors.TextDim)
                    if (recap.items.isEmpty()) {
                        Text("이동하는 동안 점수 변화가 없었습니다.", color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 18.dp))
                    }
                    recap.items.forEach { e ->
                        val color = when {
                            e.leadChange -> RoadiumColors.Red
                            e.type.isScoring -> RoadiumColors.Yellow
                            else -> RoadiumColors.Text
                        }
                        TimelineRow(e.period, "${e.headline}  ${e.detail}", "${e.homeScore}:${e.awayScore}", color)
                    }
                    TimelineRow("도착", if (recap.end.status == GameStatus.FINISHED) "경기 종료" else "현재 ${recap.end.period}", "${recap.end.homeScore}:${recap.end.awayScore}", RoadiumColors.TextDim)
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(period: String, text: String, score: String, dot: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(18.dp))
        Text(period, color = RoadiumColors.TextDim, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(180.dp), maxLines = 1)
        Text(text, color = RoadiumColors.Text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            score,
            Modifier.clip(RoundedCornerShape(12.dp)).background(RoadiumColors.SurfaceHigh).padding(horizontal = 16.dp, vertical = 6.dp),
            color = RoadiumColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
    }
}
