package kr.busrk.precool.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 홈페이지·AI 시뮬레이터와 같은 색 */
object BoxColors {
    val Red = Color(0xFFC9372C)
    val Yellow = Color(0xFFE0A100)
    val Green = Color(0xFF2E9E6B)
    val Blue = Color(0xFF1F8FD6)
    val Purple = Color(0xFF5E68ED)
    val Gray = Color(0xFF9AA0AE)
    val Muted = Color(0xFF6B7385)
    val Line = Color(0xFFEEF0F5)
    val Bg = Color(0xFFF6F7FB)
}

@Composable
fun BusrkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BoxColors.Purple,
            secondary = BoxColors.Blue,
            error = BoxColors.Red,
            background = BoxColors.Bg,
            surface = Color.White,
        ),
        content = content,
    )
}
