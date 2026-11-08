package app.iptvtest

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Liga o tema do modo infantil: é um estado do Compose, então toda tela que lê uma cor abaixo se refaz
 * sozinha quando a chave muda (antes o app lia um Boolean comum e nada era redesenhado na hora).
 */
object KidsTheme {
    var on by androidx.compose.runtime.mutableStateOf(false)
}

// Direção A, "noite carmim": azul-noite que vai ao vinho, salmão como destaque e vermelho nos botões principais.
// No modo infantil as mesmas cores viram uma paleta quente e viva (âmbar, laranja e rosa), bem diferente do normal.
val Bg: Color get() = if (KidsTheme.on) Color(0xFF3A2208) else Color(0xFF161E2F)
val Panel: Color get() = if (KidsTheme.on) Color(0xFF5A3410) else Color(0xFF242F49)
val PanelAlt: Color get() = if (KidsTheme.on) Color(0xFF7A4818) else Color(0xFF34415F)
val Accent: Color get() = if (KidsTheme.on) Color(0xFFFFCB47) else Color(0xFFFFA586)
val AccentDark: Color get() = if (KidsTheme.on) Color(0xFF8A2A55) else Color(0xFF541A2E)
val Red = Color(0xFFB51A2B)
val Wine: Color get() = if (KidsTheme.on) Color(0xFF8A2A55) else Color(0xFF541A2E)
val TextMain = Color(0xFFFFF3EC)
val Muted: Color get() = if (KidsTheme.on) Color(0xFFE0BE95) else Color(0xFF9AA3B8)
val ErrorColor = Color(0xFFFF8A8A)
val Good = Color(0xFF3DDC84)
val Lime = Color(0xFF9BE15D)
val Warn = Color(0xFFFFC21A)
val Orange = Color(0xFFFF8A3D)
val FocusRing = Color.White

private val NormalBackground: Brush =
    Brush.linearGradient(colorStops = arrayOf(0f to Color(0xFF161E2F), 0.42f to Color(0xFF242F49), 1f to Color(0xFF541A2E)))
private val KidsBackground: Brush =
    Brush.linearGradient(colorStops = arrayOf(0f to Color(0xFF4A2A08), 0.45f to Color(0xFF6B3A10), 1f to Color(0xFF8A2A55)))

/** Fundo de todas as telas: azul-noite → vinho; no modo infantil, âmbar → rosa-framboesa. */
val AppBackground: Brush get() = if (KidsTheme.on) KidsBackground else NormalBackground

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // visual minimalista: todo o texto do app um pouco menor
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * 0.9f),
    ) {
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
}
