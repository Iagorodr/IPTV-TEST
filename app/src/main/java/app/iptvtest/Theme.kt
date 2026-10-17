package app.iptvtest

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Direção A, "noite carmim": azul-noite que vai ao vinho, salmão como destaque e vermelho nos botões principais
val Bg = Color(0xFF161E2F)
val Panel = Color(0xFF242F49)
val PanelAlt = Color(0xFF34415F)
val Accent = Color(0xFFFFA586)
val AccentDark = Color(0xFF541A2E)
val Red = Color(0xFFB51A2B)
val Wine = Color(0xFF541A2E)
val TextMain = Color(0xFFFFF3EC)
val Muted = Color(0xFF9AA3B8)
val ErrorColor = Color(0xFFFF8A8A)
val Good = Color(0xFF3DDC84)
val Lime = Color(0xFF9BE15D)
val Warn = Color(0xFFFFC21A)
val Orange = Color(0xFFFF8A3D)
val FocusRing = Color.White

/** Fundo de todas as telas: azul-noite na esquerda, passando por um azul mais claro e chegando ao vinho. */
val AppBackground: Brush = Brush.linearGradient(
    colorStops = arrayOf(0f to Bg, 0.42f to Color(0xFF242F49), 1f to Wine),
)

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
