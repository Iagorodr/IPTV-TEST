package app.iptvtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    focusScale: Float = 1.05f,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) focusScale else 1f, label = "focusScale")
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .border(width = 3.dp, color = if (focused) FocusRing else Color.Transparent, shape = shape)
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
                .background(
                    if (primary) Accent else if (focused) PanelAlt else if (selected) PanelAlt else Panel,
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (primary) Color.Black else TextMain,
                fontSize = 15.sp,
                fontWeight = if (primary || selected || focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** Opção redonda (idioma, qualidade, tela, categoria...). A escolhida fica laranja. */
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
                .background(if (selected) Accent else if (focused) PanelAlt else Panel)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) Color.Black else TextMain,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

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
        Column(modifier = Modifier.background(if (focused) PanelAlt else Panel)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(PanelAlt),
                contentAlignment = Alignment.Center,
            ) {
                val img = title.image
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
                            .background(Color(0xCC0D0D0F))
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.row, key = { it.key }) { t ->
                TitlePoster(
                    title = t,
                    focusId = shelf.name + "|" + t.key,
                    modifier = Modifier.width(128.dp),
                    onClick = { onOpen(t) },
                )
            }
            if (onSeeAll != null && shelf.all.size > shelf.row.size) {
                item(key = "__all") {
                    SeeAllTile(
                        total = shelf.all.size,
                        modifier = Modifier
                            .width(128.dp)
                            .height(190.dp),
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
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(entries, key = { it.first.key }) { pair ->
                val t = pair.first
                val e = pair.second
                val frac = if (e.durMs > 0L) e.posMs.toFloat() / e.durMs.toFloat() else 0f
                TitlePoster(
                    title = t,
                    focusId = "cont|" + t.key,
                    modifier = Modifier.width(128.dp),
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
        Column(modifier = Modifier.background(if (focused) PanelAlt else Panel)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(PanelAlt),
                contentAlignment = Alignment.Center,
            ) {
                val icon = ch.icon
                if (icon != null) {
                    AsyncImage(
                        model = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                } else {
                    Text(
                        text = ch.name.take(1).uppercase(),
                        color = Muted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC0D0D0F))
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
                        text = if (ch.sources.size == 1) "1 fonte" else "${ch.sources.size} fontes",
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
