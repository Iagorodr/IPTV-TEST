package app.iptvtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val ALL = "__all"
private const val FAVS = "__favs"
private const val K4 = "__4k"
private const val YT = "__yt"

/** Qual canal está na mini tela. Guardado fora da tela para voltar do tela cheia e achar a mini tela como estava. */
private object ChannelsMemory {
    var playing: String? = null
}

/**
 * Canais ao vivo em lista vertical, com uma mini tela ao lado. A mini tela só toca quando a pessoa aperta OK num canal;
 * andar pela lista não troca o que está passando. OK de novo no canal que já está tocando abre em tela cheia.
 * As cópias do mesmo canal ficam dentro dele, como "fontes"; as marcas de TV (Globo, Record, SBT, Band, RedeTV) são
 * uma linha só, com as praças dentro.
 */
@Composable
fun ChannelsTab(vm: MainViewModel) {
    val cat = vm.catalog
    val groups = vm.channelGroups
    var selected by rememberSaveable { mutableStateOf(ALL) }
    val favs = vm.favoriteChannels()
    // voltando da tela cheia (o foco volta para uma linha) a mini tela reaparece; entrando na aba de novo, ela começa vazia
    val returning = remember { FocusMemory.pending?.startsWith("chrow|") == true }
    var playingKey by remember { mutableStateOf<String?>(if (returning) ChannelsMemory.playing else null) }
    var wantedId by remember(playingKey) { mutableStateOf<String?>(null) }
    var shownId by remember(playingKey) { mutableStateOf<String?>(null) }
    // a mini tela é um item do foco: o controle chega nela (para a direita, a partir da lista) e OK abre em tela cheia
    val previewFr = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (cat == null || groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum canal encontrado nesta lista.", color = Muted)
            }
            return@Column
        }

        // "Todos" não inclui o que é só do provedor (jogos do dia, avisos): isso fica na pasta "Do provedor"
        val all = remember(groups) { groups.filter { it.first != ChannelGroups.PROVIDER }.flatMap { it.second } }
        val byKey = remember(groups) {
            val m = HashMap<String, Channel>()
            for (g in groups) for (c in g.second) m[c.key] = c
            m
        }
        val sel = if (selected == FAVS && favs.isEmpty()) ALL else selected
        val list = remember(sel, groups, favs) {
            when (sel) {
                ALL -> all
                FAVS -> favs
                K4 -> all.filter { ChannelGroups.has4k(it) }
                YT -> all.filter { ChannelGroups.isYoutube(it) }
                else -> groups.firstOrNull { it.first == sel }?.second ?: emptyList()
            }
        }
        val playingCh = playingKey?.let { k -> byKey[k] ?: favs.firstOrNull { it.key == k } }
        val live = playingCh?.let { vm.previewLive(it) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 40.dp, end = 40.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // ---------------- lista
            Column(modifier = Modifier.weight(0.46f).fillMaxSize()) {
                LazyRow(
                    modifier = Modifier.fadeRight(0.1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = ALL) {
                        ChipItem(text = "Todos", selected = sel == ALL) { selected = ALL }
                    }
                    if (favs.isNotEmpty()) {
                        item(key = FAVS) {
                            ChipItem(text = "★ Favoritos", selected = sel == FAVS) { selected = FAVS }
                        }
                    }
                    items(groups, key = { it.first }) { g ->
                        ChipItem(text = g.first, selected = sel == g.first) { selected = g.first }
                    }
                    item(key = K4) {
                        ChipItem(text = "4K", selected = sel == K4) { selected = K4 }
                    }
                    item(key = YT) {
                        ChipItem(text = "YouTube", selected = sel == YT) { selected = YT }
                    }
                }
                key(sel) {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(list, key = { it.key }) { ch ->
                            ChannelRow(
                                ch = ch,
                                stability = vm.channelStability(ch),
                                playing = ch.key == playingKey,
                                fav = vm.isFavChannel(ch.key),
                                focusId = "chrow|" + sel + "|" + ch.key,
                                rightTo = if (playingCh != null && live != null) previewFr else null,
                                onClick = {
                                    if (ch.key == playingKey) {
                                        vm.playChannel(ch)
                                    } else {
                                        ChannelsMemory.playing = ch.key
                                        playingKey = ch.key
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ---------------- mini tela
            Column(modifier = Modifier.weight(0.54f).fillMaxSize()) {
                if (playingCh != null && live != null) {
                    FocusBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(16.dp),
                        focusRequester = previewFr,
                        focusScale = 1f,
                        onClick = { vm.playChannel(playingCh) },
                    ) { _ ->
                        ChannelPreview(
                            live = live,
                            wantedSource = live.channel.sources.firstOrNull { it.id == wantedId },
                            user = vm.user,
                            modifier = Modifier.fillMaxSize(),
                            onCurrent = { shownId = it?.id },
                        )
                    }
                } else {
                    ChannelPreview(
                        live = null,
                        wantedSource = null,
                        user = vm.user,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        onCurrent = { shownId = it?.id },
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (playingCh == null || live == null) {
                    Text(
                        "Escolha um canal na lista e aperte OK: ele toca aqui. Aperte OK de novo no mesmo canal para abrir em tela cheia.",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                } else {
                    PreviewInfo(vm, playingCh, live, shownId, wantedId) { wantedId = it }
                }
            }
        }
    }
}

@Composable
private fun PreviewInfo(
    vm: MainViewModel,
    ch: Channel,
    live: LiveInfo,
    shownId: String?,
    wantedId: String?,
    onWanted: (String?) -> Unit,
) {
    val real = live.channel
    val ranked = vm.user.rank(real)
    val shownIdx = ranked.indexOfFirst { it.id == shownId }
    val shown = ranked.getOrNull(shownIdx)
    val stab = vm.channelStability(real)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                ch.name,
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            val info = ArrayList<String>()
            if (shown != null) {
                info.add("Fonte ${shownIdx + 1} de ${ranked.size}")
                if (shown.quality.isNotEmpty()) info.add(shown.quality)
            }
            if (stab != null) info.add(statusLabel(stab).lowercase())
            Text(
                info.joinToString(" · "),
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(text = "▶ Tela cheia", primary = true) { vm.playChannel(ch) }
            TvButton(text = if (vm.isFavChannel(ch.key)) "★ Favorito" else "☆ Favoritar") { vm.toggleFavChannel(ch.key) }
        }
        if (ch.isBrand) {
            Spacer(Modifier.height(10.dp))
            ChipsLine("PRAÇA") {
                items(ch.variants, key = { it.channel.key }) { v ->
                    ChipItem(text = v.label, selected = v.channel.key == real.key) { vm.chooseVariant(ch, v) }
                }
            }
        }
        if (ranked.size > 1) {
            Spacer(Modifier.height(10.dp))
            val pinned = vm.user.pinnedSource(real.key)
            ChipsLine("FONTES") {
                item(key = "__auto") {
                    ChipItem(text = "Auto", selected = pinned == null && wantedId == null) {
                        vm.pinSource(real.key, null)
                        onWanted(null)
                    }
                }
                items(ranked.size, key = { ranked[it].id }) { i ->
                    val s = ranked[i]
                    val label = "Fonte ${i + 1}" + if (s.quality.isNotEmpty()) " · " + s.quality else ""
                    ChipItem(text = label, selected = (wantedId ?: pinned) == s.id) {
                        vm.pinSource(real.key, s.id)
                        onWanted(s.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipsLine(label: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.width(60.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 2.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ChannelRow(
    ch: Channel,
    stability: Float?,
    playing: Boolean,
    fav: Boolean,
    focusId: String,
    rightTo: FocusRequester?,
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
                // a linha ainda não estava na tela
            }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    FocusBox(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rightTo != null) Modifier.focusProperties { right = rightTo } else Modifier),
        shape = shape,
        focusRequester = fr,
        focusScale = 1f,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) PanelAlt else if (playing) Accent.copy(alpha = 0.16f) else Color.Transparent)
                .then(if (playing) Modifier.border(1.5.dp, Accent.copy(alpha = 0.7f), shape) else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelAlt),
            ) {
                ChannelLogo(name = ch.name, icon = ch.icon, modifier = Modifier.fillMaxSize(), pad = 5, padPlaceholder = 0, textSp = 15)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ch.name,
                    color = if (focused) Color.White else TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val count = if (ch.isBrand) "${ch.variants.size} praças" else if (ch.sources.size == 1) "1 fonte" else "${ch.sources.size} fontes"
                val sub = if (stability != null && stability >= 0.7f) count + " · " + statusLabel(stability).lowercase() else count
                Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (fav) {
                Text("★", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            }
            val q = if (ch.isBrand) null else ch.sources.maxByOrNull { it.rank }?.quality
            if (!q.isNullOrEmpty()) {
                Text(
                    q,
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}
