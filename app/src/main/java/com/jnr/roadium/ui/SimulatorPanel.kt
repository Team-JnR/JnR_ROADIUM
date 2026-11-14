package com.jnr.roadium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jnr.roadium.RoadiumController
import com.jnr.roadium.SdkState
import com.jnr.roadium.UiState
import com.jnr.roadium.vehicle.Gear

/**
 * 시연 도구 (개발용). Pleos Connect 에뮬레이터에서 Virtual Console 없이도 기어·내비 안내 상태를 바꾸고,
 * 리플레이 데이터의 속도를 조절할 수 있다. 실차 배포 시에는 숨긴다.
 */
@Composable
fun SimulatorPanel(ui: UiState, controller: RoadiumController, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .background(RoadiumColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시연 도구", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = RoadiumColors.Yellow)
            Choice("닫기", false, onClose)
        }
        Text("개발·심사용 · 실차 배포 시 숨김", style = MaterialTheme.typography.bodySmall, color = RoadiumColors.TextDim)

        Label("기어 (Vehicle SDK 대체 입력)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Gear.PARK, Gear.DRIVE).forEach { g ->
                Choice(if (g == Gear.PARK) "P 주차" else "D 주행", ui.gear == g, { controller.simSetGear(g) })
            }
        }

        Label("내비 음성 안내 (NaviHelper 대체 입력)")
        Choice(if (ui.simGuidance) "안내 중 (소리 보류)" else "안내 없음", ui.simGuidance, { controller.simSetGuidance(!ui.simGuidance) })
        ui.naviNextTurnM?.let {
            Text("다음 안내 지점 ${it.toInt()}m", style = MaterialTheme.typography.bodySmall, color = RoadiumColors.TextDim)
        }

        Label("리플레이 속도")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(1, 2, 4).forEach { x -> Choice("×$x", ui.replaySpeed == x, { controller.simSetSpeed(x) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("+15초", false, { controller.simSkip(15) })
            Choice("처음부터", false, controller::simRestart)
        }

        Label("Pleos SDK")
        Status("Vehicle", ui.sdk.vehicle)
        Status("TextToSpeech", ui.sdk.tts, fallback = "시스템 TTS", unavailable = "엔진 없음 · 자막")
        Status("SpeechToText", ui.sdk.stt, unavailable = "없음 · 버튼만")
        Status("NaviHelper", ui.sdk.navi)
        Text(
            "주행 규제: ${if (ui.sdk.uxRestricted) "적용 중" else "없음"}",
            style = MaterialTheme.typography.bodySmall,
            color = RoadiumColors.Text,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = RoadiumColors.TextDim)
}

@Composable
private fun Status(name: String, s: SdkState, fallback: String = s.label, unavailable: String = "없음 · 대체 입력") {
    val (text, color) = when (s) {
        SdkState.CONNECTED -> "연결됨" to RoadiumColors.Green
        SdkState.FALLBACK -> fallback to RoadiumColors.Yellow
        SdkState.UNAVAILABLE -> unavailable to RoadiumColors.TextDim
        SdkState.CHECKING -> "확인 중" to RoadiumColors.TextDim
    }
    Row {
        Text("$name  ", style = MaterialTheme.typography.bodySmall, color = RoadiumColors.Text)
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
