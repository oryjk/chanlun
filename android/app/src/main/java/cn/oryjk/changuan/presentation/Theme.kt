package cn.oryjk.changuan.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ChanTheme {
    val Sheet = Color(0xFFFFFFFF)
    val Page = Color(0xFFEEF0F2)
    val Line = Color(0xFFE3E6EA)
    val Accent = Color(0xFF2E4A6E)
    val Up = Color(0xFFD94F5E)
    val Down = Color(0xFF15936E)
    val Gold = Color(0xFFB48A42)
    val Violet = Color(0xFF7D68A8)
    val Fractal = Color(0xFF7294AA)
    val TextPrimary = Color(0xFF1A222C)
    val TextSecondary = Color(0xFF606E7E)
    val TextMuted = Color(0xFF8A97A6)

    fun directionColor(value: Double): Color =
        if (value >= 0.0) Up else Down
}

private val LightColors = lightColorScheme(
    primary = ChanTheme.Accent,
    onPrimary = Color.White,
    surface = ChanTheme.Sheet,
    onSurface = ChanTheme.TextPrimary,
    background = ChanTheme.Page,
    onBackground = ChanTheme.TextPrimary,
    outline = ChanTheme.Line,
    error = ChanTheme.Up,
)

@Composable
fun ChanGuanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
