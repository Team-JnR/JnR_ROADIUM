package com.jnr.roadium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 차량 디스플레이용 어두운 테마 (기획서 [그림 1] 시안 색).
 * 주요 문구는 큰 글씨와 높은 대비(배경 대비 4.5:1 이상, Pleos UI 디자인 가이드)를 쓴다.
 */
object RoadiumColors {
    val Bg = Color(0xFF0E1218)
    val Surface = Color(0xFF161C26)
    val SurfaceHigh = Color(0xFF212938)
    val Line = Color(0xFF2E3748)
    val Blue = Color(0xFF3D8BFF)
    val Yellow = Color(0xFFFFC31A)
    val Green = Color(0xFF3DDC97)
    val Red = Color(0xFFFF5A5F)
    val Text = Color(0xFFF2F4F8)
    val TextDim = Color(0xFFA3ACBD)
}

private val scheme = darkColorScheme(
    primary = RoadiumColors.Blue,
    onPrimary = Color.White,
    secondary = RoadiumColors.Yellow,
    onSecondary = Color(0xFF1A1400),
    tertiary = RoadiumColors.Green,
    background = RoadiumColors.Bg,
    surface = RoadiumColors.Surface,
    surfaceVariant = RoadiumColors.SurfaceHigh,
    surfaceContainer = RoadiumColors.Surface,
    surfaceContainerHigh = RoadiumColors.SurfaceHigh,
    onSurface = RoadiumColors.Text,
    onSurfaceVariant = RoadiumColors.TextDim,
    outline = RoadiumColors.Line,
    error = RoadiumColors.Red,
)

// 차량 디스플레이는 시선 거리가 멀어 글자를 크게 쓴다 (최소 18sp)
private val type = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 20.sp),
    bodySmall = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun RoadiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
