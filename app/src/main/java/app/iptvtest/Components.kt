package app.iptvtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Lembra onde o cursor estava quando a pessoa abriu um título/canal, para devolver o foco ao mesmo lugar
 * quando ela voltar (o controle remoto não tem mouse: perder o lugar é muito chato).
 */
object FocusMemory {
    var last: String? = null
    var pending: String? = null
    fun arm() {
        pending = last
    }
}

/**
 * Base de tudo que pode ser selecionado com o controle remoto:
 * mostra borda branca + leve zoom quando está com o foco e reage ao botão central (OK).
 */
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    focusRequester: FocusRequester? = null,
    focusScale: Float = 1.03f,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) focusScale.coerceAtMost(1.012f) else 1f, label = "focusScale")
    val ring by androidx.compose.animation.animateColorAsState(if (focused) FocusRing else Color.Transparent, androidx.compose.animation.core.tween(140), label = "ring")
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .border(width = 2.dp, color = ring, shape = shape)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    minWidth: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        focusRequester = focusRequester,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .then(if (minWidth != null) Modifier.widthIn(min = minWidth) else Modifier)
                .background(
                    if (primary) Brush.horizontalGradient(listOf(Color(0xFFD02338), Color(0xFF9E1626)))
                    else Brush.horizontalGradient(
                        listOf(if (focused || selected) PanelAlt else Panel, if (focused || selected) PanelAlt else Panel.copy(alpha = 0.8f)),
                    ),
                )
                .then(if (!primary) Modifier.border(1.dp, Color(0x1FFFF3EC), RoundedCornerShape(24.dp)) else Modifier)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (primary) Color.White else TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                maxLines = 1,
            )
        }
    }
}

/** Lupa desenhada (não depende de fonte/emoji da TV). */
@Composable
fun SearchIcon(color: Color, modifier: Modifier = Modifier, size: Int = 20) {
    Canvas(modifier = modifier.size(size.dp)) {
        val w = this.size.minDimension
        val stroke = w * 0.11f
        val r = w * 0.32f
        val c = Offset(w * 0.42f, w * 0.42f)
        drawCircle(color = color, radius = r, center = c, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            end = Offset(w * 0.92f, w * 0.92f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Engrenagem desenhada: aro + 8 dentes. */
@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier, size: Int = 18) {
    Canvas(modifier = modifier.size(size.dp)) {
        val w = this.size.minDimension
        val c = Offset(w / 2f, w / 2f)
        val ring = w * 0.30f
        drawCircle(color = color, radius = ring, center = c, style = Stroke(width = w * 0.16f))
        val toothW = w * 0.17f
        val toothH = w * 0.2f
        for (i in 0 until 8) {
            rotate(degrees = i * 45f, pivot = c) {
                drawRect(
                    color = color,
                    topLeft = Offset(c.x - toothW / 2f, c.y - w / 2f),
                    size = androidx.compose.ui.geometry.Size(toothW, toothH),
                )
            }
        }
    }
}

/** Duas setas que se cruzam (sortear de novo). */
@Composable
fun ShuffleIcon(color: Color, modifier: Modifier = Modifier, size: Int = 18) {
    Canvas(modifier = modifier.size(size.dp)) {
        val w = this.size.minDimension
        val sw = w * 0.1f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color = color, start = Offset(w * x1, w * y1), end = Offset(w * x2, w * y2), strokeWidth = sw, cap = StrokeCap.Round)
        line(0.08f, 0.26f, 0.92f, 0.74f)
        line(0.08f, 0.74f, 0.92f, 0.26f)
        // pontas das setas, à direita
        line(0.92f, 0.74f, 0.70f, 0.74f)
        line(0.92f, 0.74f, 0.92f, 0.52f)
        line(0.92f, 0.26f, 0.70f, 0.26f)
        line(0.92f, 0.26f, 0.92f, 0.48f)
    }
}

/** Botão redondo só com ícone (sortear de novo, etc.). */
@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (Color) -> Unit) {
    FocusBox(modifier = modifier, shape = CircleShape, focusScale = 1.06f, onClick = onClick) { focused ->
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (focused) PanelAlt else Color(0x33FFF3EC)),
            contentAlignment = Alignment.Center,
        ) {
            icon(TextMain)
        }
    }
}

/**
 * Opção redonda (idioma, qualidade, tela, categoria, dia...). A escolhida fica com um laranja escuro e translúcido
 * (não chapado) e contorno fino; as outras ficam num cinza escuro.
 */
@Composable
fun ChipItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        focusRequester = focusRequester,
        focusScale = 1.03f,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .background(if (focused) PanelAlt else if (selected) Accent.copy(alpha = 0.22f) else Panel.copy(alpha = 0.45f))
                .then(if (selected) Modifier.border(1.5.dp, Accent.copy(alpha = 0.75f), RoundedCornerShape(20.dp)) else Modifier)
                .padding(horizontal = 13.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) Color(0xFFFFE3D8) else Muted.copy(alpha = 0.95f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** Largura do cartão de filme/série nas prateleiras. */
val PosterW = 98.dp

/** Cartão de filme/série: capa, nome e, se houver, barra de progresso ("continuar assistindo"). */
@Composable
fun TitlePoster(
    title: Title,
    focusId: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (FocusMemory.pending == focusId) {
            delay(120)
            FocusMemory.pending = null
            try {
                fr.requestFocus()
            } catch (e: Exception) {
                // o cartão ainda não estava na tela
            }
        }
    }
    FocusBox(
        modifier = modifier,
        focusRequester = fr,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Column(modifier = Modifier.background(if (focused) PanelAlt else Color.Transparent)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(PanelAlt),
                contentAlignment = Alignment.Center,
            ) {
                val tm by rememberTmdb(title, needed = title.image == null)
                val img = title.image ?: tm?.poster
                if (img != null) {
                    AsyncImage(
                        model = img,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = title.name.take(1).uppercase(),
                        color = Muted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (title.has4k) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC161E2F))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("4K", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (progress != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color(0x99000000)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Accent),
                        )
                    }
                }
            }
            Text(
                text = title.name,
                color = if (focused) Color.White else TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
fun SeeAllTile(total: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(modifier = modifier, onClick = onClick) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (focused) PanelAlt else Panel),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ver todos", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("$total títulos", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ShelfRow(
    shelf: Shelf,
    onOpen: (Title) -> Unit,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = shelf.name,
            color = TextMain,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(2.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.row.distinctBy { it.key }, key = { it.key }) { t ->
                TitlePoster(
                    title = t,
                    focusId = shelf.name + "|" + t.key,
                    modifier = Modifier.width(PosterW),
                    onClick = { onOpen(t) },
                )
            }
            if (onSeeAll != null && shelf.all.size > shelf.row.size) {
                item(key = "__all") {
                    SeeAllTile(
                        total = shelf.all.size,
                        modifier = Modifier
                            .width(PosterW)
                            .height(PosterW * 3 / 2),
                        onClick = onSeeAll,
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueRow(
    entries: List<Pair<Title, ResumeEntry>>,
    onResume: (ResumeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Continuar assistindo",
            color = TextMain,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(2.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries.distinctBy { it.first.key }, key = { it.first.key }) { pair ->
                val t = pair.first
                val e = pair.second
                val frac = if (e.durMs > 0L) e.posMs.toFloat() / e.durMs.toFloat() else 0f
                TitlePoster(
                    title = t,
                    focusId = "cont|" + t.key,
                    modifier = Modifier.width(PosterW),
                    progress = frac,
                    subtitle = if (e.label.isEmpty()) null else e.label,
                    onClick = { onResume(e) },
                )
            }
        }
    }
}

/** Barrinhas de estabilidade (0 a 5), como no layout combinado. */
@Composable
fun StabilityBars(r: Float?) {
    val n = statusBars(r)
    val color = statusColor(r)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= n) color else PanelAlt),
            )
        }
    }
}

@Composable
fun ChannelTile(
    ch: Channel,
    stability: Float?,
    focusId: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (FocusMemory.pending == focusId) {
            delay(120)
            FocusMemory.pending = null
            try {
                fr.requestFocus()
            } catch (e: Exception) {
                // o cartão ainda não estava na tela
            }
        }
    }
    FocusBox(
        modifier = modifier,
        focusRequester = fr,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Column(modifier = Modifier.background(if (focused) PanelAlt else Color.Transparent)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(PanelAlt),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(name = ch.name, icon = ch.icon, modifier = Modifier.fillMaxSize())
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC161E2F))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor(stability)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (ch.isBrand) "${ch.variants.size} praças" else if (ch.sources.size == 1) "1 fonte" else "${ch.sources.size} fontes",
                        color = TextMain,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = ch.name,
                color = if (focused) Color.White else TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}


// =========================================================================
// Logo do canal (com quadradinho colorido quando não tem imagem ou a imagem não abre)
// =========================================================================

private val PLACEHOLDER_COLORS = listOf(
    Color(0xFF3D5A99), Color(0xFF2F7D6B), Color(0xFF8A4F9E), Color(0xFFB0573A),
    Color(0xFF4F7A28), Color(0xFF9B3D5A), Color(0xFF3A7CA5), Color(0xFF7A6A2F),
)

/** "Globo Minas" → "GM", "Zapping Music Rock" → "ZM", "SBT" → "SB". */
fun channelInitials(name: String): String {
    val words = name.split(' ', '-', '|', '•').filter { w -> w.isNotEmpty() && w[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
fun ChannelLogo(
    name: String,
    icon: String?,
    modifier: Modifier = Modifier,
    pad: Int = 14,
    padPlaceholder: Int = 10,
    textSp: Int = 30,
) {
    var failed by remember(icon) { mutableStateOf(false) }
    if (icon != null && !failed) {
        AsyncImage(
            model = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            modifier = modifier.padding(pad.dp),
        )
    } else {
        val color = PLACEHOLDER_COLORS[Math.floorMod(name.hashCode(), PLACEHOLDER_COLORS.size)]
        Box(
            modifier = modifier
                .padding(padPlaceholder.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channelInitials(name),
                color = Color.White,
                fontSize = textSp.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
