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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A cena da marca: um sol salmão com brilho atrás de duas camadas de montanhas, desenhada só com formas
 * (não depende de imagem). Aparece no login, na abertura e quando um destaque não tem capa.
 *
 * [sunX] e [sunY] são frações da tela (0 a 1) para o centro do sol; [sunR] é o raio como fração da altura.
 */
@Composable
fun SunBackdrop(
    modifier: Modifier = Modifier,
    sunX: Float = 0.76f,
    sunY: Float = 0.36f,
    sunR: Float = 0.2f,
    mountains: Boolean = true,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c = Offset(w * sunX, h * sunY)
        val r = h * sunR
        // brilho
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Accent.copy(alpha = 0.55f), Accent.copy(alpha = 0.2f), Color.Transparent),
                center = c,
                radius = r * 2.6f,
            ),
            radius = r * 2.6f,
            center = c,
        )
        // sol
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Accent, Red),
                startY = c.y - r,
                endY = c.y + r,
            ),
            radius = r,
            center = c,
        )
        if (mountains) {
            drawRidge(w, h, 0.42f, Color(0xFF1F2A44), listOf(0f to 0.62f, 0.12f to 0.48f, 0.24f to 0.58f, 0.38f to 0.36f, 0.52f to 0.55f, 0.66f to 0.40f, 0.8f to 0.58f, 0.92f to 0.46f, 1f to 0.56f))
            drawRidge(w, h, 0.42f, Bg, listOf(0f to 0.78f, 0.18f to 0.66f, 0.34f to 0.76f, 0.55f to 0.60f, 0.74f to 0.74f, 0.9f to 0.64f, 1f to 0.72f))
        }
    }
}

/** Uma serra: [top] é a fração da altura onde a faixa começa; os pontos são (x em fração, y em fração da faixa). */
private fun DrawScope.drawRidge(w: Float, h: Float, top: Float, color: Color, pts: List<Pair<Float, Float>>) {
    val y0 = h * top
    val bandH = h - y0
    val path = Path()
    path.moveTo(0f, h)
    for ((fx, fy) in pts) path.lineTo(w * fx, y0 + bandH * fy)
    path.lineTo(w, h)
    path.close()
    drawPath(path, color)
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
