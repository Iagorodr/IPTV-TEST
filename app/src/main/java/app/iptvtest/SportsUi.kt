package app.iptvtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.TimeZone
import androidx.compose.foundation.lazy.grid.items as gridItems

// =========================================================================
// Peças comuns
// =========================================================================

/** Devolve o foco ao cartão em que a pessoa estava quando ela voltar de outra tela. */
@Composable
private fun restoringRequester(focusId: String): FocusRequester {
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
    return fr
}

@Composable
private fun TeamLogo(url: String?, dp: Int) {
    Box(modifier = Modifier.size(dp.dp), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// =========================================================================
// Início: jogos dos times favoritos
// =========================================================================

@Composable
private fun StatusPill(game: Game, now: Long) {
    val tz = remember(AppTz.rev) { TimeZone.getDefault() }
    val text: String
    val bg: Color
    val fg: Color
    when (game.state) {
        1 -> {
            text = if (game.detail.isEmpty()) "AO VIVO" else "AO VIVO · " + game.detail
            bg = Red
            fg = Color.White
        }
        2 -> {
            text = "ENCERRADO"
            bg = PanelAlt
            fg = Muted
        }
        else -> {
            text = (SportsTime.dayLabel(game.startMs, now, tz) + " " + SportsTime.clock(game.startMs, tz)).uppercase()
            bg = PanelAlt
            fg = Accent
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun TeamLine(side: Side, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(side.logo, 22)
        Spacer(Modifier.width(8.dp))
        Text(
            text = side.short,
            color = TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showScore) {
            Text(
                text = side.score ?: "-",
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
fun GameCard(
    game: Game,
    now: Long,
    channelCount: Int,
    focusId: String,
    modifier: Modifier = Modifier,
    fav: Boolean = false,
    reminded: Boolean = false,
    onClick: () -> Unit,
) {
    val fr = restoringRequester(focusId)
    FocusBox(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusRequester = fr,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Column(
            modifier = Modifier
                .background(if (focused) PanelAlt else if (fav) Color(0xFF30263F) else Panel)
                .then(if (fav) Modifier.border(2.dp, Accent, RoundedCornerShape(14.dp)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) {
                    Text("★ ", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = game.leagueName,
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                StatusPill(game, now)
            }
            Spacer(Modifier.height(5.dp))
            val showScore = game.state != 0
            TeamLine(game.home, showScore)
            Spacer(Modifier.height(3.dp))
            TeamLine(game.away, showScore)
            Spacer(Modifier.height(5.dp))
            Text(
                text = when {
                    reminded -> "⏰ Lembrete ligado"
                    channelCount == 1 -> "▶ 1 canal na sua lista"
                    channelCount > 1 -> "▶ $channelCount canais na sua lista"
                    else -> "Toque para ver detalhes"
                },
                color = if (reminded) Warn else if (channelCount > 0) Good else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private val LiveRed = Color(0xFFFF4D5E)

/** Um jogo na faixa do Início: só "Time × Time" e o dia/hora (ou "Ao vivo"). Sem cartão, sem moldura. */
@Composable
private fun StripGameItem(sg: StripGame, now: Long, tz: TimeZone, onClick: () -> Unit) {
    val g = sg.game
    val focusId = "game|" + g.key
    val fr = restoringRequester(focusId)
    FocusBox(
        modifier = Modifier.padding(end = 4.dp),
        shape = RoundedCornerShape(10.dp),
        focusRequester = fr,
        focusScale = 1f,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Column(
            modifier = Modifier
                .background(if (focused) PanelAlt else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sg.fav) Text("★ ", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = g.home.short + " × " + g.away.short,
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 230.dp),
                )
            }
            Spacer(Modifier.height(1.dp))
            StripWhen(live = g.state == 1, done = g.state == 2, detail = g.detail, startMs = g.startMs, now = now, tz = tz)
        }
    }
}

@Composable
private fun StripWhen(live: Boolean, done: Boolean, detail: String, startMs: Long, now: Long, tz: TimeZone) {
    if (live) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(LiveRed))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (detail.isEmpty()) "Ao vivo" else "Ao vivo · $detail",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    } else {
        Text(
            text = if (done) "Encerrado" else SportsTime.dayLabel(startMs, now, tz) + " · " + SportsTime.clock(startMs, tz),
            color = Muted,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Fórmula 1, UFC, tênis: entram no fim da faixa. Sem evento marcado, só o nome e "ver canais". */
@Composable
private fun StripSportItem(kind: SportKind, ev: SportEvent?, now: Long, tz: TimeZone, onClick: () -> Unit) {
    val focusId = "sport|" + kind.name
    val fr = restoringRequester(focusId)
    FocusBox(
        modifier = Modifier.padding(end = 4.dp),
        shape = RoundedCornerShape(10.dp),
        focusRequester = fr,
        focusScale = 1f,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Column(
            modifier = Modifier
                .background(if (focused) PanelAlt else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(kind.label, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(1.dp))
            if (ev == null) {
                Text("ver canais", color = Muted, fontSize = 12.sp, maxLines = 1)
            } else {
                StripWhen(live = ev.state == 1, done = false, detail = "", startMs = ev.startMs, now = now, tz = tz)
            }
        }
    }
}

@Composable
private fun StripTextItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(modifier = modifier.padding(end = 4.dp), shape = RoundedCornerShape(10.dp), focusScale = 1f, onClick = onClick) { focused ->
        Box(
            modifier = Modifier
                .background(if (focused) PanelAlt else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(text, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Faixa mínima de jogos do Início (combinado com o Iago: só nome, dia e horário). Jogos dos meus times (★) e das ligas
 * escolhidas, depois F1/UFC/tênis; "Ver todos" abre a lista completa. Os cartões com liga e canais ficam na aba Jogos.
 */
@Composable
fun GamesStrip(vm: MainViewModel, now: Long) {
    val tz = remember(AppTz.rev) { TimeZone.getDefault() }
    val noPicks = vm.favTeams.isEmpty() && vm.favLeagues.isEmpty()
    val shown = if (noPicks) emptyList() else vm.stripGames(now)
    val events = vm.sportEvents
    val lineColor = Color(0x22FFF3EC)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(lineColor))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "JOGOS",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Box(modifier = Modifier.width(1.dp).height(26.dp).background(lineColor))
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (noPicks) {
                    item(key = "__pick") { StripTextItem("Escolher meus times e ligas", onClick = { vm.openPicker() }) }
                } else if (shown.isEmpty()) {
                    item(key = "__none") {
                        val err = vm.gamesError
                        if (!vm.gamesLoading && err != null) {
                            StripTextItem("Não foi possível buscar. Tentar de novo", onClick = { vm.refreshSports(force = true) })
                        } else {
                            Text(
                                text = if (vm.gamesLoading) "Buscando os jogos..." else "Nenhum jogo dos seus times hoje ou amanhã",
                                color = Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
                items(shown.distinctBy { it.game.key }, key = { it.game.key }) { sg ->
                    StripGameItem(sg, now, tz, onClick = { vm.showGame(sg.game) })
                }
                items(SportKind.values().toList(), key = { "sp|" + it.name }) { k ->
                    StripSportItem(k, events[k], now, tz, onClick = { vm.openSport(k) })
                }
            }
            StripTextItem("Ver todos ›", onClick = { vm.openAllGames() })
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(lineColor))
    }
}

// =========================================================================
// Pop-up do jogo: informações básicas, onde costuma passar e lembrete
// =========================================================================

@Composable
fun GameDialog(vm: MainViewModel, g: Game) {
    val tz = remember(AppTz.rev) { TimeZone.getDefault() }
    val now = remember(g.key) { System.currentTimeMillis() }
    val info = remember(g.key, vm.catalog, BroadcastStore.rev) { vm.gameInfo(g) }
    var notice by remember(g.key) { mutableStateOf<String?>(null) }
    var checking by remember(g.key) { mutableStateOf(false) }
    var checkMsg by remember(g.key) { mutableStateOf<String?>(null) }
    val reminded = vm.isReminded(g)
    val finished = g.state == 2
    val first = remember { FocusRequester() }
    val canReplay = finished && vm.canReplay(g)
    val replayList = remember(g.key, vm.catalog) { if (canReplay) vm.replayChannels(g) else emptyList() }
    LaunchedEffect(g.key) {
        delay(80)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    Dialog(
        onDismissRequest = { vm.closeGame() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, PanelAlt, RoundedCornerShape(16.dp))
                .padding(22.dp),
        ) {
            Text(
                text = g.leagueName + " · " + SportsTime.dayLabel(g.startMs, now, tz) + " " + SportsTime.clock(g.startMs, tz),
                color = Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamLogo(g.home.logo, 64)
                    Spacer(Modifier.height(6.dp))
                    Text(g.home.short, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = if (g.state == 0) "x" else (g.home.score ?: "-") + "  x  " + (g.away.score ?: "-"),
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamLogo(g.away.logo, 64)
                    Spacer(Modifier.height(6.dp))
                    Text(g.away.short, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = when (g.state) {
                    1 -> "AO VIVO" + (if (g.detail.isEmpty()) "" else " · " + g.detail)
                    2 -> "Encerrado"
                    else -> "Começa " + SportsTime.dayLabel(g.startMs, now, tz).lowercase() + " às " + SportsTime.clock(g.startMs, tz) + " (hora daqui)"
                },
                color = if (g.state == 1) Good else TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(14.dp))
            val comp = if (finished) null else info.comp
            val bg = if (finished) null else info.bgame
            if (finished) {
                Text(
                    text = when {
                        replayList.isNotEmpty() -> "Jogo encerrado. Você pode rever: escolha o canal e o app abre no começo do jogo."
                        canReplay -> "Jogo encerrado. Nenhum canal da sua lista guarda a programação passada para rever agora."
                        else -> "Jogo encerrado há mais de 24 horas: não dá mais para rever."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
                if (replayList.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(replayList.take(12), key = { _, c -> c.key }) { i, c ->
                            ChipItem(
                                text = "⏪ " + c.name,
                                selected = false,
                                focusRequester = if (i == 0) first else null,
                            ) { vm.replayGame(g, c) }
                        }
                    }
                }
            } else if (bg != null) {
                // o jogo está na tabela: mostra onde passa, e se foi confirmado por uma fonte do próprio jogo
                Text(
                    text = if (bg.confirmed) "Passa em (" + info.country + ")" else "Provavelmente passa em (" + info.country + ")",
                    color = if (bg.confirmed) Good else Warn,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Broadcasts.line(bg.tv, info.inList),
                    color = TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (bg.obs.isNotEmpty()) {
                    Text(bg.obs, color = TextMain, fontSize = 12.sp)
                }
                Text(
                    text = "✓ = tem na sua lista. " + (if (bg.confirmed) "Confirmado" else "Ainda não confirmado") +
                        (if (bg.src.isNotEmpty()) " em " + bg.src else "") + " · tabela de " + info.tableDate + ".",
                    color = Muted,
                    fontSize = 11.sp,
                )
            } else if (comp != null) {
                Text("Costuma passar em (" + info.country + ")", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Broadcasts.line(comp.options, info.inList),
                    color = TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (comp.note.isNotEmpty()) {
                    Text(comp.note, color = Muted, fontSize = 12.sp)
                }
                Text(
                    text = "✓ = tem na sua lista. Este jogo ainda não foi confirmado na tabela de " + info.tableDate +
                        ": pode variar por jogo e por região.",
                    color = Muted,
                    fontSize = 11.sp,
                )
                if (comp.options.size < 2) {
                    Text("Só tenho uma opção registrada: confira também os canais de esporte da sua lista.", color = Warn, fontSize = 12.sp)
                }
            } else {
                Text(
                    "Ainda não tenho o registro de onde esse campeonato passa em " + info.country + ". Veja os canais de esporte da sua lista.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            if (!finished) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChipItem(text = if (checking) "Conferindo..." else "Conferir de novo", selected = false) {
                        if (!checking) {
                            checking = true
                            checkMsg = null
                            vm.recheckBroadcasts { m ->
                                checking = false
                                checkMsg = m
                            }
                        }
                    }
                    val cm = checkMsg
                    if (cm != null) {
                        Text(cm, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (!finished && info.matched.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (info.matched.size == 1) "▶ Achei 1 canal desse jogo na sua lista" else "▶ Achei ${info.matched.size} canais desse jogo na sua lista",
                    color = Good,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (finished) {
                    if (replayList.isNotEmpty()) {
                        TvButton(text = "Fechar") { vm.closeGame() }
                    } else {
                        TvButton(text = "Fechar", primary = true, focusRequester = first) { vm.closeGame() }
                    }
                } else {
                    if (info.matched.isNotEmpty()) {
                        TvButton(text = "Assistir agora", primary = true, focusRequester = first) { vm.watchNow(g) }
                        TvButton(text = "Ver canais") { vm.openGameChannels(g) }
                    } else {
                        TvButton(text = "Ver canais", primary = true, focusRequester = first) { vm.openGameChannels(g) }
                    }
                    if (g.state == 0) {
                        TvButton(text = "● Gravar") { vm.recordGame(g) }
                        TvButton(text = if (reminded) "⏰ Lembrete ligado" else "⏰ Lembrar") {
                            notice = vm.toggleReminder(g)
                        }
                    }
                    TvButton(text = "Fechar") { vm.closeGame() }
                }
            }
            val n = notice
            if (n != null) {
                Spacer(Modifier.height(8.dp))
                Text(n, color = Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =========================================================================
// "Ver todos": jogos dos próximos dias agrupados por dia
// =========================================================================

@Composable
fun AllGamesScreen(vm: MainViewModel) {
    val tz = remember(AppTz.rev) { TimeZone.getDefault() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val days = vm.gamesByDay(now)
    val loading = vm.allGamesLoading
    val err = vm.allGamesError

    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive(262.dp),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(text = "Voltar") { vm.back() }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Próximos jogos", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        val parts = ArrayList<String>()
                        if (vm.favTeams.isNotEmpty()) parts.add("★ " + vm.favTeams.joinToString(", ") { it.short })
                        for (l in vm.favLeagues) parts.add(SportsData.leagueLabel(l, l))
                        Text(
                            parts.joinToString(" · ").ifEmpty { "Escolha times e ligas para ver os jogos aqui." },
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (loading) {
                    Spacer(Modifier.height(8.dp))
                    Text("Buscando os jogos...", color = Muted, fontSize = 13.sp)
                } else if (err != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, color = ErrorColor, fontSize = 13.sp)
                        TvButton(text = "Tentar de novo") { vm.loadAllGames(force = true) }
                    }
                } else if (days.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhum jogo nos próximos dias.", color = Muted, fontSize = 13.sp)
                }
            }
        }
        for ((day, list) in days) {
            item(key = "day|$day", span = { GridItemSpan(maxLineSpan) }) {
                val label = SportsTime.dayLabel(day * SportsTime.DAY + SportsTime.DAY / 2 - tz.rawOffset, now, tz)
                Text(label, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
            }
            gridItems(list.distinctBy { "g|" + it.game.key }, key = { "g|" + it.game.key }) { sg ->
                GameCard(
                    game = sg.game,
                    now = now,
                    channelCount = vm.gameChannelCount(sg.game),
                    focusId = "all|" + sg.game.key,
                    modifier = Modifier.fillMaxWidth(),
                    fav = sg.fav,
                    reminded = vm.isReminded(sg.game),
                    onClick = { vm.showGame(sg.game) },
                )
            }
        }
    }
}

// =========================================================================
// Aba Jogos: ontem, hoje, amanhã e os próximos dias
// =========================================================================

@Composable
fun GamesTab(vm: MainViewModel) {
    val tz = remember(AppTz.rev) { TimeZone.getDefault() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            vm.loadDayGames()
            vm.refreshSports()
            delay(30_000L)
        }
    }
    var offset by rememberSaveable { mutableStateOf(0) }
    val offsets = remember { (-1..5).toList() }
    val perDay = offsets.map { vm.dayGames(it, now) }
    val list = perDay[(offset + 1).coerceIn(0, offsets.size - 1)]
    val loading = vm.popLoading
    val err = vm.popError

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(offsets.distinctBy { it }, key = { it }) { o ->
                val label = if (o == 0) "Hoje" else SportsTime.dayLabel(now + o * SportsTime.DAY, now, tz)
                ChipItem(text = label + " (" + perDay[o + 1].size + ")", selected = o == offset) { offset = o }
            }
            item(key = "__teams") {
                ChipItem(text = "Meus times e ligas", selected = false) { vm.openPicker() }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 44.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when {
                    list.isNotEmpty() -> "${list.size} jogos · ao vivo primeiro, depois os mais populares · ★ = jogo de um time seu · toque para ver canais e lembrar"
                    loading -> "Buscando os jogos..."
                    err != null -> err
                    else -> "Nenhum jogo das principais ligas neste dia."
                },
                color = if (list.isEmpty() && err != null && !loading) ErrorColor else Muted,
                fontSize = 12.sp,
            )
            if (err != null && !loading && list.isEmpty()) {
                TvButton(text = "Tentar de novo") { vm.loadDayGames(force = true) }
            }
        }
        LazyVerticalGrid(
            state = rememberLazyGridState(),
            columns = GridCells.Adaptive(262.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            gridItems(list.distinctBy { "gd|" + it.game.key }, key = { "gd|" + it.game.key }) { sg ->
                GameCard(
                    game = sg.game,
                    now = now,
                    channelCount = if (sg.game.state == 2) 0 else vm.gameChannelCount(sg.game),
                    focusId = "gd|" + offset + "|" + sg.game.key,
                    modifier = Modifier.fillMaxWidth(),
                    fav = sg.fav,
                    reminded = vm.isReminded(sg.game),
                    onClick = { vm.showGame(sg.game) },
                )
            }
        }
    }
}

// =========================================================================
// Escolha dos times favoritos
// =========================================================================

private const val NATIONAL = "__national"

@Composable
private fun CountryCard(c: Country, focusId: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fr = restoringRequester(focusId)
    FocusBox(
        modifier = modifier,
        focusRequester = fr,
        onClick = {
            FocusMemory.last = focusId
            onClick()
        },
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) PanelAlt else Panel)
                .padding(14.dp),
        ) {
            Text(c.name, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(
                text = c.leagues.joinToString(" · ") { it.label } + " · Seleção",
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TeamCard(info: TeamInfo, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(modifier = modifier, onClick = onClick) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) AccentDark else if (focused) PanelAlt else Panel)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TeamLogo(info.logo, 56)
            Spacer(Modifier.height(6.dp))
            Text(
                text = info.short,
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (selected) "✓ Escolhido" else " ",
                color = if (selected) Color.White else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CountryTeams(vm: MainViewModel, country: Country, onNotice: (String?) -> Unit) {
    var tab by rememberSaveable(country.code) { mutableStateOf(country.leagues.first().slug) }
    var teams by remember(country.code, tab) { mutableStateOf<List<TeamInfo>?>(null) }
    var error by remember(country.code, tab) { mutableStateOf<String?>(null) }

    LaunchedEffect(country.code, tab) {
        teams = null
        error = null
        try {
            val list = if (tab == NATIONAL) {
                listOfNotNull(SportsApi.nationalTeam(country))
            } else {
                SportsApi.teams(tab)
            }
            if (list.isEmpty()) error = "Não encontrei times aqui."
            teams = list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Não consegui carregar os times. Confira a internet e tente de novo."
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(country.leagues.distinctBy { it.slug }, key = { it.slug }) { lg ->
            ChipItem(text = lg.label, selected = tab == lg.slug) { tab = lg.slug }
        }
        item(key = NATIONAL) {
            ChipItem(text = country.nationalLabel, selected = tab == NATIONAL) { tab = NATIONAL }
        }
    }

    val list = teams
    if (list == null || error != null) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.TopCenter) {
            Text(error ?: "Carregando times...", color = if (error != null) ErrorColor else Muted, fontSize = 14.sp)
        }
        return
    }

    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(list.distinctBy { it.id }, key = { it.id }) { info ->
            val national = tab == NATIONAL
            TeamCard(
                info = info,
                selected = vm.isFavTeam(info.id),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val fav = FavTeam(
                        id = info.id,
                        name = info.name,
                        short = if (national) country.nationalLabel else info.short,
                        logo = info.logo,
                        leagues = if (national) SportsData.nationalLeagues(country) else SportsData.clubLeagues(country, tab),
                        national = national,
                    )
                    onNotice(vm.toggleFavTeam(fav))
                },
            )
        }
    }
}

private const val LEAGUES = "__leagues"

@Composable
private fun LeagueCard(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(modifier = modifier, onClick = onClick) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) AccentDark else if (focused) PanelAlt else Panel)
                .padding(14.dp),
        ) {
            Text(label, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (selected) "✓ Escolhida" else " ",
                color = if (selected) Color.White else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LeaguePicker(vm: MainViewModel, onNotice: (String?) -> Unit) {
    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive(220.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        for ((group, slugs) in SportsData.leagueGroups) {
            item(key = "h|$group", span = { GridItemSpan(maxLineSpan) }) {
                Text(group, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
            }
            gridItems(slugs.distinctBy { "l|$it" }, key = { "l|$it" }) { slug ->
                LeagueCard(
                    label = SportsData.leagueLabel(slug, slug),
                    selected = vm.isFavLeague(slug),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNotice(vm.toggleFavLeague(slug)) },
                )
            }
        }
    }
}

@Composable
fun TeamPickerScreen(vm: MainViewModel) {
    val code = vm.pickerCountry
    val isLeagues = code == LEAGUES
    val country = if (code == null || isLeagues) null else SportsData.country(code)
    var notice by remember { mutableStateOf<String?>(null) }
    val chosen = vm.favTeams
    val leagues = vm.favLeagues

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(text = "Voltar") { vm.back() }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLeagues) "Ligas favoritas" else "Times e ligas favoritos",
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = when {
                        isLeagues -> "Escolha até ${SportsData.MAX_LEAGUES} competições: todos os jogos delas de hoje e amanhã aparecem no Início."
                        country == null -> "Escolha o país do seu time (até 5 times) ou escolha ligas (até ${SportsData.MAX_LEAGUES})."
                        else -> country.name + " · aperte OK para escolher ou tirar um time."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = "${chosen.size}/5 times · ${leagues.size}/${SportsData.MAX_LEAGUES} ligas",
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(14.dp))
            if (code == null) {
                TvButton(text = "Escolher ligas") {
                    notice = null
                    vm.pickCountry(LEAGUES)
                }
                Spacer(Modifier.width(10.dp))
            }
            TvButton(text = "Concluir", primary = true) { vm.closePicker() }
        }

        if (chosen.isNotEmpty() || leagues.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(chosen.distinctBy { "t|" + it.id }, key = { "t|" + it.id }) { t ->
                    ChipItem(text = "★ " + t.short + "  ✕", selected = true) { notice = vm.toggleFavTeam(t) }
                }
                items(leagues.distinctBy { "l|$it" }, key = { "l|$it" }) { slug ->
                    ChipItem(text = SportsData.leagueLabel(slug, slug) + "  ✕", selected = true) { notice = vm.toggleFavLeague(slug) }
                }
            }
        }
        val n = notice
        if (n != null) {
            Text(n, color = Warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp))
        }

        if (isLeagues) {
            LeaguePicker(vm, onNotice = { notice = it })
        } else if (country == null) {
            LazyVerticalGrid(
                state = rememberLazyGridState(),
                columns = GridCells.Adaptive(210.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(SportsData.countries.distinctBy { it.code }, key = { it.code }) { c ->
                    CountryCard(
                        c = c,
                        focusId = "ctry|" + c.code,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            notice = null
                            vm.pickCountry(c.code)
                        },
                    )
                }
            }
        } else {
            CountryTeams(vm, country, onNotice = { notice = it })
        }
    }
}

// =========================================================================
// Onde assistir: canais achados + canais de esporte da lista
// =========================================================================

@Composable
fun ChannelListScreen(vm: MainViewModel, req: ChannelListRequest) {
    val cat = vm.catalog
    val cats = remember(cat) { vm.sportCategoriesForList() }
    var selected by rememberSaveable { mutableStateOf(cats.firstOrNull() ?: "") }
    val list = remember(selected, cat) {
        if (cat == null || selected.isEmpty()) emptyList() else vm.channelsInCategory(selected)
    }

    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Adaptive(168.dp),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(text = "Voltar") { vm.back() }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(req.title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(req.subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = req.hint,
                    color = if (req.highlight.isNotEmpty()) Good else Muted,
                    fontSize = 13.sp,
                )
            }
        }
        if (req.highlight.isNotEmpty()) {
            gridItems(req.highlight.distinctBy { "hl|" + it.key }, key = { "hl|" + it.key }) { ch ->
                ChannelTile(
                    ch = ch,
                    stability = vm.channelStability(ch),
                    focusId = "hl|" + ch.key,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.playChannel(ch) },
                )
            }
        }
        if (req.extra.isNotEmpty()) {
            item(key = "extra-head", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "Canais que costumam passar",
                        color = TextMain,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (req.extraTitle.isNotEmpty()) {
                        Text(req.extraTitle + " (pode variar por jogo)", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            gridItems(req.extra.distinctBy { "ex|" + it.key }, key = { "ex|" + it.key }) { ch ->
                ChannelTile(
                    ch = ch,
                    stability = vm.channelStability(ch),
                    focusId = "ex|" + ch.key,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.playChannel(ch) },
                )
            }
        }
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvButton(text = "Buscar \"" + req.searchQuery + "\" na lista") { vm.searchFor(req.searchQuery) }
            }
        }
        if (cats.isNotEmpty()) {
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "Canais de esporte da sua lista",
                        color = TextMain,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(cats.distinctBy { it }, key = { it }) { name ->
                            ChipItem(text = name, selected = selected == name) { selected = name }
                        }
                    }
                }
            }
            gridItems(list.distinctBy { "cat|" + it.key }, key = { "cat|" + it.key }) { ch ->
                ChannelTile(
                    ch = ch,
                    stability = vm.channelStability(ch),
                    focusId = "cat|" + selected + "|" + ch.key,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.playChannel(ch) },
                )
            }
        }
    }
}

// =========================================================================
// Ajustes: seção dos times favoritos
// =========================================================================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FavTeamsSettings(vm: MainViewModel) {
    Column {
        Text("Times e ligas favoritos", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (vm.favTeams.isEmpty() && vm.favLeagues.isEmpty()) {
                "Nada escolhido. Os jogos de times e ligas aparecem no topo do Início."
            } else {
                "${vm.favTeams.size}/5 times · ${vm.favLeagues.size}/${SportsData.MAX_LEAGUES} ligas · os jogos aparecem no topo do Início."
            },
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvButton(text = if (vm.favTeams.isEmpty() && vm.favLeagues.isEmpty()) "Escolher times e ligas" else "Trocar times e ligas") { vm.openPicker() }
            for (t in vm.favTeams) {
                Text("★ " + t.short, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            for (l in vm.favLeagues) {
                Text(SportsData.leagueLabel(l, l), color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Onde passa: tabela " + BroadcastStore.countriesLine() + " de " + BroadcastStore.table.updatedPretty() + " (atualizada toda semana).",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}
