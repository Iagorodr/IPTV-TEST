package app.iptvtest

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B10)
val Panel = Color(0xFF1A1A24)
val PanelAlt = Color(0xFF2A2A3C)
val Accent = Color(0xFF8B7CFF)
val TextMain = Color(0xFFF2F2F7)
val Muted = Color(0xFF9A9AB0)
val ErrorColor = Color(0xFFFF6B6B)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Panel,
            onSurface = TextMain,
            onBackground = TextMain,
        ),
        content = content,
    )
}
