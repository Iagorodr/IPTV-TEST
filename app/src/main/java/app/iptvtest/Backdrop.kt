package app.iptvtest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fundo da marca: dois brilhos suaves (salmão e vinho) sobre o azul-noite, sem formas duras.
 * Aparece no login, na abertura e quando um destaque não tem capa.
 *
 * [sunX] e [sunY] são frações da tela (0 a 1) para o centro do brilho principal; [sunR] é o raio como fração da altura.
 * [mountains] ficou só por compatibilidade e não faz nada.
 */
@Composable
fun SunBackdrop(
    modifier: Modifier = Modifier,
    sunX: Float = 0.76f,
    sunY: Float = 0.36f,
    sunR: Float = 0.2f,
    @Suppress("UNUSED_PARAMETER") mountains: Boolean = true,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c = Offset(w * sunX, h * sunY)
        val r = h * (sunR * 4.2f + 0.25f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Accent.copy(alpha = 0.20f), Accent.copy(alpha = 0.07f), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        val c2 = Offset(w * (1f - sunX * 0.6f), h * 1.02f)
        val r2 = h * 0.9f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Red.copy(alpha = 0.22f), Color.Transparent),
                center = c2,
                radius = r2,
            ),
            radius = r2,
            center = c2,
        )
    }
}

/**
 * Esmaece a borda esquerda (e a de baixo) do conteúdo até ficar transparente, de verdade (não pinta uma cor por cima),
 * então a capa do destaque se dissolve no fundo em degradê seja qual for a cor dele.
 */
fun Modifier.fadeEdges(left: Float = 0.45f, bottom: Float = 0.3f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.horizontalGradient(0f to Color.Transparent, left to Color.Black, 1f to Color.Black),
                blendMode = BlendMode.DstIn,
            )
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Black, (1f - bottom) to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }

/** Esmaece a ponta direita (fileiras de chips que continuam para o lado: some aos poucos em vez de cortar seco). */
fun Modifier.fadeRight(fraction: Float = 0.08f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.horizontalGradient(0f to Color.Black, (1f - fraction) to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }
