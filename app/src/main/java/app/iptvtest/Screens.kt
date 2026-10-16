package app.iptvtest

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.grid.items as gridItems

// =========================================================================
// Login
// =========================================================================

@Composable
fun LoginScreen(vm: MainViewModel) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV TEST", color = Accent, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Entre com os dados da sua lista (Xtream Codes).\nEles ficam guardados só nesta TV.",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Servidor (ex.: http://exemplo.com:8080)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuário") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Senha") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    vm.login(server, user, pass)
                }),
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvButton(text = if (showPass) "Ocultar senha" else "Mostrar senha") { showPass = !showPass }
                TvButton(text = if (vm.loginBusy) "Entrando..." else "Entrar", primary = true) {
                    vm.login(server, user, pass)
                }
            }

            val err = vm.loginError
            if (err != null) {
                Spacer(Modifier.height(14.dp))
                Text(err, color = ErrorColor, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (vm.loginBusy) {
                Spacer(Modifier.height(14.dp))
                CircularProgressIndicator(color = Accent)
            }
        }
    }
}

// =========================================================================
// Carregando o catálogo (primeira vez)
// =========================================================================

@Composable
fun LoadingScreen(vm: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("IPTV TEST", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            val err = vm.loadError
            if (err != null) {
                Text("Não foi possível carregar o catálogo", color = ErrorColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(err, color = TextMain, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(text = "Tentar de novo", primary = true) { vm.loadCatalog(force = true) }
                    TvButton(text = "Sair da conta") { vm.logout() }
                }
            } else {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(16.dp))
                Text(vm.loadMsg ?: "Preparando...", color = TextMain, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Isso só acontece na primeira vez: depois o catálogo fica guardado na TV e abre na hora.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// =========================================================================
// Menu do topo
// =========================================================================

@Composable
private fun NavItem(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        modifier = Modifier.padding(end = 4.dp),
        shape = RoundedCornerShape(8.dp),
        focusScale = 1f,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .background(if (focused) PanelAlt else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                color = if (selected) Accent else if (focused) TextMain else Muted,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TopNav(vm: MainViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IPTV TEST", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(24.dp))
        NavItem("Início", vm.tab == Tab.HOME) { vm.selectTab(Tab.HOME) }
        NavItem("Filmes", vm.tab == Tab.MOVIES) { vm.selectTab(Tab.MOVIES) }
        NavItem("Séries", vm.tab == Tab.SERIES) { vm.selectTab(Tab.SERIES) }
        NavItem("Canais", vm.tab == Tab.CHANNELS) { vm.selectTab(Tab.CHANNELS) }
        NavItem("Minha lista", vm.tab == Tab.LIST) { vm.selectTab(Tab.LIST) }
        Spacer(Modifier.weight(1f))
        if (vm.refreshing) {
            Text("Atualizando catálogo...", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
        }
        NavItem("Buscar", vm.tab == Tab.SEARCH) { vm.selectTab(Tab.SEARCH) }
        NavItem("Ajustes", vm.tab == Tab.SETTINGS) { vm.selectTab(Tab.SETTINGS) }
    }
}

@Composable
fun TabScreen(vm: MainViewModel) {
    when (vm.tab) {
        Tab.HOME -> HomeTab(vm)
        Tab.MOVIES -> ShelfTab(vm, vm.movieShelves)
        Tab.SERIES -> ShelfTab(vm, vm.seriesShelves)
        Tab.CHANNELS -> ChannelsTab(vm)
        Tab.LIST -> MyListTab(vm)
        Tab.SEARCH -> SearchTab(vm)
        Tab.SETTINGS -> SettingsTab(vm)
    }
}

// =========================================================================
// Início, Filmes, Séries
// =========================================================================

@Composable
private fun HeroBanner(vm: MainViewModel, t: Title) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(Brush.horizontalGradient(listOf(AccentDark, Bg))),
    ) {
        val img = t.image
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 72.dp)
                    .height(210.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
                .width(560.dp),
        ) {
            Text("EM DESTAQUE", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(t.name, color = TextMain, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            val meta = listOf(
                if (t.series) "Série" else "Filme",
                t.year,
                t.genres.take(2).joinToString(", "),
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(meta, color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (t.plot.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(t.plot, color = TextMain, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(text = "Ver detalhes", primary = true) { vm.open(t) }
                TvButton(text = if (vm.inMyList(t.key)) "✓ Minha lista" else "+ Minha lista") { vm.toggleMyList(t.key) }
            }
        }
    }
}

@Composable
fun HomeTab(vm: MainViewModel) {
    val cont = vm.continueEntries()
    val mine = vm.myListTitles()
    val mineShelf = remember(mine) { Shelf("Minha lista", mine) }
    val favs = vm.favoriteChannels()
    val hero = vm.featured

    // "agora" para os horários dos jogos; também puxa jogos novos ao entrar e a cada meio minuto
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            vm.refreshSports()
            delay(30_000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "games") { GamesSection(vm, now) }
            if (hero != null) {
                item(key = "hero") { HeroBanner(vm, hero) }
            }
            item(key = "sports") { SportEventsRow(vm, now) }
            if (cont.isNotEmpty()) {
                item(key = "cont") { ContinueRow(cont, onResume = { vm.resumeEntry(it) }) }
            }
            if (favs.isNotEmpty()) {
                item(key = "favch") {
                    Column {
                        Text(
                            "Seus canais",
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
                            items(favs, key = { it.key }) { c ->
                                ChannelTile(
                                    ch = c,
                                    stability = vm.channelStability(c),
                                    focusId = "homefav|" + c.key,
                                    modifier = Modifier.width(190.dp),
                                    onClick = { vm.playChannel(c) },
                                )
                            }
                        }
                    }
                }
            }
            if (mine.isNotEmpty()) {
                item(key = "mine") {
                    ShelfRow(mineShelf, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(mineShelf) })
                }
            }
            items(vm.homeShelves, key = { it.name }) { s ->
                ShelfRow(s, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(s) })
            }
        }
    }
}

@Composable
fun ShelfTab(vm: MainViewModel, shelves: List<Shelf>) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (shelves.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada por aqui ainda.", color = Muted)
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shelves, key = { it.name }) { s ->
                    ShelfRow(s, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(s) })
                }
            }
        }
    }
}

// =========================================================================
// "Ver todos": grade com tudo da prateleira
// =========================================================================

@Composable
fun GridScreen(vm: MainViewModel, shelf: Shelf) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(text = "Voltar") { vm.back() }
            Spacer(Modifier.width(16.dp))
            Text(shelf.name, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            Text("${shelf.all.size} títulos", color = Muted, fontSize = 14.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(shelf.all, key = { it.key }) { t ->
                TitlePoster(
                    title = t,
                    focusId = "grid|" + shelf.name + "|" + t.key,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.open(t) },
                )
            }
        }
    }
}

// =========================================================================
// Busca
// =========================================================================

@Composable
fun SearchTab(vm: MainViewModel) {
    var q by rememberSaveable { mutableStateOf("") }
    val prefill = vm.searchPrefill
    LaunchedEffect(prefill) {
        if (prefill != null) {
            q = prefill
            vm.clearSearchPrefill()
        }
    }
    val cat = vm.catalog
    val titles = remember(q, cat) {
        val f = Organizer.fold(q)
        if (f.length < 2) {
            emptyList()
        } else {
            vm.allTitles.filter { it.fold.contains(f) }.sortedByDescending { it.score() }.take(150)
        }
    }
    val chans = remember(q, cat) {
        val f = Organizer.fold(q)
        if (f.length < 2 || cat == null) {
            emptyList()
        } else {
            cat.channels.filter { it.fold.contains(f) }.take(30)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            label = { Text("Buscar filmes, séries e canais") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        )
        if (chans.isNotEmpty()) {
            Text(
                "Canais",
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(chans, key = { it.key }) { c ->
                    ChannelTile(
                        ch = c,
                        stability = vm.channelStability(c),
                        focusId = "search|" + c.key,
                        modifier = Modifier.width(170.dp),
                        onClick = { vm.playChannel(c) },
                    )
                }
            }
        }
        if (titles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (Organizer.fold(q).length < 2) "Digite pelo menos 2 letras." else if (chans.isEmpty()) "Nada encontrado." else "",
                    color = Muted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                gridItems(titles, key = { it.key }) { t ->
                    TitlePoster(
                        title = t,
                        focusId = "search|" + t.key,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.open(t) },
                    )
                }
            }
        }
    }
}

// =========================================================================
// Minha lista
// =========================================================================

@Composable
fun MyListTab(vm: MainViewModel) {
    val mine = vm.myListTitles()
    val favs = vm.favoriteChannels()
    val mineShelf = remember(mine) { Shelf("Minha lista", mine) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (mine.isEmpty() && favs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Sua lista está vazia.\nAbra um filme ou série e escolha \"+ Minha lista\".\nNos canais, use a estrela ★ dentro do player.",
                    color = Muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (mine.isNotEmpty()) {
                    item(key = "mine") {
                        ShelfRow(mineShelf, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(mineShelf) })
                    }
                }
                if (favs.isNotEmpty()) {
                    item(key = "favch") {
                        Column {
                            Text(
                                "Canais favoritos",
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
                                items(favs, key = { it.key }) { c ->
                                    ChannelTile(
                                        ch = c,
                                        stability = vm.channelStability(c),
                                        focusId = "fav|" + c.key,
                                        modifier = Modifier.width(190.dp),
                                        onClick = { vm.playChannel(c) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Ajustes
// =========================================================================

@Composable
fun SettingsTab(vm: MainViewModel) {
    var confirmLogout by remember { mutableStateOf(false) }
    val cat = vm.catalog
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column {
                    Text("Conta", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(vm.accountInfo.ifEmpty { "..." }, color = Muted, fontSize = 14.sp)
                }
            }
            item {
                Column {
                    Text("Catálogo", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    val resumo = if (cat == null) {
                        "Ainda não carregado."
                    } else {
                        "${cat.movies.size} filmes · ${cat.series.size} séries · ${cat.channels.size} canais · atualizado ${vm.catalogAgeText()}"
                    }
                    Text(resumo, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    TvButton(text = if (vm.refreshing) "Atualizando..." else "Atualizar catálogo agora") {
                        if (!vm.refreshing) vm.loadCatalog(force = true)
                    }
                }
            }
            item { FavTeamsSettings(vm) }
            item {
                Column {
                    Text("Canais ao vivo", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Quando um canal tem várias fontes, o app escolhe a melhor para você.",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Priorizar", color = Muted, fontSize = 15.sp)
                        ChipItem(text = "Estabilidade", selected = vm.user.stabilityFirst) { vm.setStabilityFirst(true) }
                        ChipItem(text = "Qualidade máxima", selected = !vm.user.stabilityFirst) { vm.setStabilityFirst(false) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Trocar de fonte sozinho se travar", color = Muted, fontSize = 15.sp)
                        ChipItem(text = "Sim", selected = vm.user.autoSwitch) { vm.setAutoSwitch(true) }
                        ChipItem(text = "Não", selected = !vm.user.autoSwitch) { vm.setAutoSwitch(false) }
                    }
                    Spacer(Modifier.height(8.dp))
                    TvButton(text = "Apagar notas de estabilidade") { vm.clearStats() }
                }
            }
            item {
                Column {
                    Text("Sair", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    TvButton(text = if (confirmLogout) "Confirmar saída?" else "Sair da conta") {
                        if (confirmLogout) vm.logout() else confirmLogout = true
                    }
                }
            }
        }
    }
}
