package app.iptvtest

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D0D0F)
val Panel = Color(0xFF1B1A1C)
val PanelAlt = Color(0xFF2A2826)
val Accent = Color(0xFFFF6B2B)
val AccentDark = Color(0xFF3B1409)
val TextMain = Color(0xFFF4F1EC)
val Muted = Color(0xFF9A948A)
val ErrorColor = Color(0xFFFF6B6B)
val Good = Color(0xFF3DDC84)
val Lime = Color(0xFF9BE15D)
val Warn = Color(0xFFFFC21A)
val Orange = Color(0xFFFF8A3D)
val FocusRing = Color.White

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
