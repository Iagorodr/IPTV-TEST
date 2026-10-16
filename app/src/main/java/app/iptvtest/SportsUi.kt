package app.iptvtest

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextMain,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.padding(horizontal = 48.dp),
    )
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
    val tz = remember { TimeZone.getDefault() }
    val text: String
    val bg: Color
    val fg: Color
    when (game.state) {
        1 -> {
            text = if (game.detail.isEmpty()) "AO VIVO" else "AO VIVO · " + game.detail
            bg = Good
            fg = Color.Black
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
        TeamLogo(side.logo, 28)
        Spacer(Modifier.width(8.dp))
        Text(
            text = side.short,
            color = TextMain,
            fontSize = 15.sp,
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
    onClick: () -> Unit,
) {
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
                .background(if (focused) PanelAlt else Panel)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = game.leagueName,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                StatusPill(game, now)
            }
            Spacer(Modifier.height(10.dp))
            val showScore = game.state != 0
            TeamLine(game.home, showScore)
            Spacer(Modifier.height(6.dp))
            TeamLine(game.away, showScore)
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    channelCount == 1 -> "▶ 1 canal na sua lista"
                    channelCount > 1 -> "▶ $channelCount canais na sua lista"
                    else -> "Ver onde assistir"
                },
                color = if (channelCount > 0) Good else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EditTeamsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusBox(modifier = modifier, onClick = onClick) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (focused) PanelAlt else Panel),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Meus times", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("escolher ou trocar", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

/** Faixa do topo do Início: os jogos de hoje/amanhã dos times escolhidos (ou o convite para escolher). */
@Composable
fun GamesSection(vm: MainViewModel, now: Long) {
    val teams = vm.favTeams
    Column {
        SectionTitle("Jogos dos seus times")
        Spacer(Modifier.height(6.dp))
        if (teams.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Escolha até 5 times e os jogos deles aparecem aqui todo dia.",
                    color = Muted,
                    fontSize = 14.sp,
                )
                TvButton(text = "Escolher times", primary = true) { vm.openPicker() }
            }
            return@Column
        }
        val shown = vm.shownGames(now)
        if (shown.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val err = vm.gamesError
                Text(
                    text = when {
                        vm.gamesLoading -> "Buscando os jogos..."
                        err != null -> err
                        else -> "Nenhum jogo dos seus times nos próximos dias."
                    },
                    color = Muted,
                    fontSize = 14.sp,
                )
                if (!vm.gamesLoading && err != null) {
                    TvButton(text = "Tentar de novo") { vm.refreshSports(force = true) }
                }
                TvButton(text = "Meus times") { vm.openPicker() }
            }
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shown, key = { it.key }) { g ->
                GameCard(
                    game = g,
                    now = now,
                    channelCount = vm.gameChannelCount(g),
                    focusId = "game|" + g.key,
                    modifier = Modifier.width(262.dp),
                    onClick = { vm.openGame(g) },
                )
            }
            item(key = "__teams") {
                EditTeamsTile(onClick = { vm.openPicker() }, modifier = Modifier.width(140.dp).height(132.dp))
            }
        }
    }
}

// =========================================================================
// Início: Fórmula 1, UFC e tênis (só eventos)
// =========================================================================

@Composable
private fun SportCard(
    kind: SportKind,
    ev: SportEvent?,
    now: Long,
    focusId: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tz = remember { TimeZone.getDefault() }
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
                .background(if (focused) PanelAlt else Panel)
                .padding(14.dp),
        ) {
            Text(kind.label.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = ev?.name ?: "Toque para ver os canais",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val line = when {
                ev == null -> "Sem data confirmada"
                ev.state == 1 -> "AO VIVO · até " + SportsTime.shortDate(ev.endMs, tz)
                else -> SportsTime.dayLabel(ev.startMs, now, tz) + " " + SportsTime.clock(ev.startMs, tz)
            }
            Text(
                text = line,
                color = if (ev != null && ev.state == 1) Good else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SportEventsRow(vm: MainViewModel, now: Long) {
    val events = vm.sportEvents
    Column {
        SectionTitle("Outros esportes")
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(SportKind.values().toList(), key = { it.name }) { k ->
                SportCard(
                    kind = k,
                    ev = events[k],
                    now = now,
                    focusId = "sport|" + k.name,
                    modifier = Modifier.width(236.dp),
                    onClick = { vm.openSport(k) },
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
        items(country.leagues, key = { it.slug }) { lg ->
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
        gridItems(list, key = { it.id }) { info ->
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

@Composable
fun TeamPickerScreen(vm: MainViewModel) {
    val code = vm.pickerCountry
    val country = if (code == null) null else SportsData.country(code)
    var notice by remember { mutableStateOf<String?>(null) }
    val chosen = vm.favTeams

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
                Text("Times favoritos", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = if (country == null) {
                        "Escolha o país do seu time (até 5 times no total)."
                    } else {
                        country.name + " · aperte OK para escolher ou tirar um time."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            Text("${chosen.size}/5", color = if (chosen.size >= 5) Accent else TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(14.dp))
            TvButton(text = "Concluir", primary = true) { vm.closePicker() }
        }

        if (chosen.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(chosen, key = { it.id }) { t ->
                    ChipItem(text = t.short + "  ✕", selected = true) { notice = vm.toggleFavTeam(t) }
                }
            }
        }
        val n = notice
        if (n != null) {
            Text(n, color = Warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp))
        }

        if (country == null) {
            LazyVerticalGrid(
                state = rememberLazyGridState(),
                columns = GridCells.Adaptive(210.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(SportsData.countries, key = { it.code }) { c ->
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
        if (cat == null || selected.isEmpty()) emptyList() else cat.channels.filter { it.category == selected }
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
            gridItems(req.highlight, key = { "hl|" + it.key }) { ch ->
                ChannelTile(
                    ch = ch,
                    stability = vm.channelStability(ch),
                    focusId = "hl|" + ch.key,
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
                        items(cats, key = { it }) { name ->
                            ChipItem(text = name, selected = selected == name) { selected = name }
                        }
                    }
                }
            }
            gridItems(list, key = { "cat|" + it.key }) { ch ->
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

@Composable
fun FavTeamsSettings(vm: MainViewModel) {
    Column {
        Text("Times favoritos", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (vm.favTeams.isEmpty()) {
                "Nenhum time escolhido. Os jogos deles aparecem no topo do Início."
            } else {
                "${vm.favTeams.size}/5 · os jogos deles aparecem no topo do Início."
            },
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvButton(text = if (vm.favTeams.isEmpty()) "Escolher times" else "Trocar times") { vm.openPicker() }
            for (t in vm.favTeams) {
                Text(t.short, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
