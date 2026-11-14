package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jnr.roadium.RoadiumController

/**
 * 화면 전환: 주행(또는 주행 규제) 중이면 드라이브 화면만, 주차 중이면 팀 등록·홈·리캡.
 * 가로(기본)·세로 레이아웃을 모두 지원하고, 시연 도구는 가로면 오른쪽, 세로면 아래에 둔다.
 */
@Composable
fun RoadiumRoot(controller: RoadiumController) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    var editingTeams by rememberSaveable { mutableStateOf(false) }
    var showSim by rememberSaveable { mutableStateOf(true) }

    BoxWithConstraints(Modifier.fillMaxSize().background(RoadiumColors.Bg)) {
        val landscape = maxWidth >= maxHeight
        val main: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                val recap = ui.recap
                when {
                    ui.driveMode -> DriveScreen(ui, controller)
                    ui.favorites.isEmpty() || editingTeams -> TeamSelectScreen(
                        initial = ui.favorites,
                        onDone = {
                            controller.setFavorites(it)
                            editingTeams = false
                        },
                        onCancel = if (ui.favorites.isEmpty()) null else ({ editingTeams = false }),
                    )
                    recap != null -> RecapScreen(recap, controller::closeRecap)
                    else -> ParkedHome(ui, controller, onEditTeams = { editingTeams = true })
                }
            }
        }
        // 시연 도구를 닫으면 가장자리에 얇은 손잡이만 남겨 본 화면을 가리지 않는다
        val handle: @Composable (Modifier) -> Unit = { m ->
            Box(m.background(RoadiumColors.Surface).clickable { showSim = true }, contentAlignment = Alignment.Center) {
                Text(if (landscape) "◀\n시\n연" else "▲ 시연 도구", color = RoadiumColors.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                main(Modifier.weight(1f).fillMaxSize())
                if (showSim) SimulatorPanel(ui, controller, Modifier.width(360.dp), onClose = { showSim = false })
                else handle(Modifier.width(44.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                main(Modifier.weight(1f).fillMaxWidth())
                if (showSim) SimulatorPanel(ui, controller, Modifier.fillMaxWidth().height(380.dp), onClose = { showSim = false })
                else handle(Modifier.fillMaxWidth().height(44.dp))
            }
        }
    }
}
