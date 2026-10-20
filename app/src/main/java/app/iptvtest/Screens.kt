package app.iptvtest

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems

// =========================================================================
// Login
// =========================================================================

@Composable
fun LoginScreen(vm: MainViewModel) {
    var mode by rememberSaveable { mutableStateOf(0) }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    // o servidorzinho do celular só fica ligado enquanto a aba do QR code está aberta
    LaunchedEffect(mode) {
        if (mode == 2) vm.startPhoneLogin() else vm.stopPhoneLogin()
    }
    DisposableEffect(Unit) {
        onDispose { vm.stopPhoneLogin() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // o sol e as montanhas da marca ficam à esquerda, com o nome embaixo; o formulário vai num cartão à direita
        SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.23f, sunY = 0.36f, sunR = 0.19f)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(stringResource(R.string.app_name), color = Accent, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (vm.addingList) "Adicionar outra lista.\nElas ficam guardadas só nesta TV." else "Entre com a sua lista.\nOs dados ficam guardados só nesta TV.",
                    color = TextMain,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                if (vm.addingList) {
                    Spacer(Modifier.height(14.dp))
                    TvButton(text = "Cancelar") { vm.cancelAddList() }
                }
                Spacer(Modifier.height(34.dp))
            }
            Spacer(Modifier.width(28.dp))
        Column(
            modifier = Modifier
                .weight(0.58f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE6161E2F))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ChipItem(text = "Xtream", selected = mode == 0) { mode = 0; vm.clearLoginError() }
                ChipItem(text = "Link M3U", selected = mode == 1) { mode = 1; vm.clearLoginError() }
                ChipItem(text = "Pelo celular (QR)", selected = mode == 2) { mode = 2; vm.clearLoginError() }
            }
            Spacer(Modifier.height(16.dp))

            when (mode) {
                0 -> {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Servidor (ex.: http://exemplo.com:8080)") },
                        singleLine = true,
                        colors = fieldColors(),
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
                        colors = fieldColors(),
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
                        colors = fieldColors(),
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
                }

                1 -> {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link da lista (ex.: http://exemplo.com/lista.m3u)") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focus.clearFocus()
                            vm.loginM3u(link)
                        }),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Links do tipo get.php?username=...&password=... entram como Xtream (mais completo, com sinopse e episódios). " +
                            "Listas M3U comuns trazem só nome, imagem e categoria.",
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    TvButton(text = if (vm.loginBusy) "Entrando..." else "Entrar", primary = true) {
                        vm.loginM3u(link)
                    }
                }

                else -> {
                    val url = vm.phoneUrl
                    if (url != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            QrCode(text = url, modifier = Modifier.size(170.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("1. Conecte o celular no mesmo Wi-Fi da TV.", color = TextMain, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("2. Aponte a câmera para o QR code.", color = TextMain, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("3. Digite os dados no celular e toque em Enviar.", color = TextMain, fontSize = 13.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("Ou abra no navegador do celular:", color = Muted, fontSize = 12.sp)
                                Text(url, color = Accent, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Text(
                            vm.phoneMsg ?: "Preparando o QR code...",
                            color = Muted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    val pm = vm.phoneMsg
                    if (url != null && pm != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(pm, color = Accent, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
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
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    cursorColor = Accent,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Muted.copy(alpha = 0.55f),
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
)

// =========================================================================
// Carregando o catálogo (primeira vez)
// =========================================================================

@Composable
fun LoadingScreen(vm: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.5f, sunY = 0.3f, sunR = 0.17f)
        Column(
            modifier = Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))
            Text(
                stringResource(R.string.app_name),
                color = TextMain,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(36.dp).height(2.dp).background(Accent))
            Spacer(Modifier.height(36.dp))
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
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.width(200.dp).height(2.dp),
                    color = Accent,
                    trackColor = Color(0x22FFF3EC),
                )
                Spacer(Modifier.height(18.dp))
                Text(vm.loadMsg ?: "Preparando...", color = TextMain, fontSize = 15.sp, textAlign = TextAlign.Center)
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

/** Aba do menu: só texto. A aba atual ganha um fundo salmão bem translúcido (nada de cor chapada). */
@Composable
private fun NavItem(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        modifier = Modifier.padding(end = 2.dp),
        shape = RoundedCornerShape(10.dp),
        focusScale = 1f,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .background(if (focused) PanelAlt else if (selected) Accent.copy(alpha = 0.20f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = if (selected || focused) TextMain else Muted,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Botão só com ícone (lupa). */
@Composable
private fun NavIcon(selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    FocusBox(
        shape = RoundedCornerShape(10.dp),
        focusScale = 1f,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .background(if (focused) PanelAlt else if (selected) Accent.copy(alpha = 0.20f) else Color.Transparent)
                .padding(horizontal = 13.dp, vertical = 8.dp),
        ) {
            icon(if (selected || focused) TextMain else Muted)
        }
    }
}

/** O nome do app, que também abre os Ajustes (por isso a engrenagem ao lado). */
@Composable
private fun BrandButton(selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        shape = RoundedCornerShape(10.dp),
        focusScale = 1f,
        onClick = onClick,
    ) { focused ->
        Row(
            modifier = Modifier
                .background(if (focused) PanelAlt else if (selected) Accent.copy(alpha = 0.20f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            GearIcon(color = if (selected || focused) TextMain else Muted)
        }
    }
}

/** Menu do topo: sem faixa nem linha, solto sobre o fundo em degradê. */
@Composable
fun TopNav(vm: MainViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // margem de segurança de TV: muitas TVs "comem" a borda da imagem (overscan), por isso o menu fica mais para dentro
            .padding(start = 40.dp, end = 44.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandButton(vm.tab == Tab.SETTINGS) { vm.selectTab(Tab.SETTINGS) }
        Spacer(Modifier.width(16.dp))
        NavItem("Início", vm.tab == Tab.HOME) { vm.selectTab(Tab.HOME) }
        NavItem("Jogos", vm.tab == Tab.GAMES) { vm.selectTab(Tab.GAMES) }
        NavItem("Filmes", vm.tab == Tab.MOVIES) { vm.selectTab(Tab.MOVIES) }
        NavItem("Séries", vm.tab == Tab.SERIES) { vm.selectTab(Tab.SERIES) }
        NavItem("Canais", vm.tab == Tab.CHANNELS) { vm.selectTab(Tab.CHANNELS) }
        NavItem("Favoritos", vm.tab == Tab.LIST) { vm.selectTab(Tab.LIST) }
        Spacer(Modifier.weight(1f))
        if (vm.refreshing) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        NavIcon(vm.tab == Tab.SEARCH, { vm.selectTab(Tab.SEARCH) }) { c -> SearchIcon(color = c) }
    }
}

@Composable
fun TabScreen(vm: MainViewModel) {
    when (vm.tab) {
        Tab.HOME -> HomeTab(vm)
        Tab.GAMES -> GamesTab(vm)
        Tab.MOVIES -> ShelfTab(vm, vm.movieShelves, vm.heroMovies)
        Tab.SERIES -> ShelfTab(vm, vm.seriesShelves, vm.heroSeries)
        Tab.CHANNELS -> ChannelsTab(vm)
        Tab.LIST -> FavoritesTab(vm)
        Tab.SEARCH -> SearchTab(vm)
        Tab.SETTINGS -> SettingsTab(vm)
    }
}

// =========================================================================
// Início, Filmes, Séries
// =========================================================================

/** Texto e capa do destaque (a parte que troca com um esmaecer; os botões ficam fixos por cima). */
@Composable
private fun HeroContent(t: Title) {
    Box(modifier = Modifier.fillMaxSize()) {
        val img = t.image
        if (img != null) {
            // a capa do título ocupa o fundo, à direita, e se dissolve no degradê da tela
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                alpha = 0.85f,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    .fadeEdges(left = 0.55f, bottom = 0.3f),
            )
        } else {
            SunBackdrop(modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 12.dp)
                .width(440.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (t.series) "SÉRIE" else "FILME",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                )
                if (t.year.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(t.year, color = TextMain, fontSize = 13.sp)
                }
                if (t.has4k) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "4K",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .border(1.dp, Accent, RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                val langs = listOfNotNull(
                    if (t.versions.any { !it.leg && !it.cam }) "Dublado" else null,
                    if (t.versions.any { it.leg }) "Legendado" else null,
                ).joinToString(" · ")
                if (langs.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(langs, color = Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                t.name.uppercase(),
                color = TextMain,
                fontSize = 26.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            val rating = if (t.rating > 0f) "★ " + String.format(java.util.Locale.US, "%.1f", t.rating).replace('.', ',') else ""
            val meta = listOf(rating, t.genres.take(2).joinToString(" · ")).filter { it.isNotEmpty() }.joinToString("   ")
            if (meta.isNotEmpty()) {
                Text(meta, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (t.plot.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    t.plot,
                    color = TextMain.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(400.dp),
                )
            }
        }
    }
}

/**
 * Quando o foco entra neste bloco (destaque, jogos ou chips), a lista volta ao topo e o destaque aparece inteiro.
 * Espera um instante porque o próprio foco também puxa a lista só até o botão aparecer, e essa rolagem curta é que
 * acabava ganhando e deixava o destaque cortado em cima.
 */
@Composable
private fun Modifier.topOnFocus(listState: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    return this.onFocusChanged {
        if (it.hasFocus) {
            scope.launch {
                delay(90)
                try {
                    listState.animateScrollToItem(0)
                } catch (e: Exception) {
                    // a lista saiu da tela: sem importância
                }
            }
        }
    }
}

/** Botão redondo com um símbolo (+, ✓, ‹, ›). */
@Composable
private fun CircleAction(symbol: String, onClick: () -> Unit) {
    FocusBox(shape = CircleShape, focusScale = 1.06f, onClick = onClick) { focused ->
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (focused) PanelAlt else Color(0x22FFF3EC)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Destaques que trocam sozinhos (ordem aleatória, a cada 7 s) com um esmaecer. Dá para andar de um para o outro
 * com ‹ e ›. Quando o cursor está nos botões a troca pausa, para o botão sempre abrir o título que a pessoa está
 * vendo, e a lista rola de volta para mostrar o destaque inteiro (aparece 100% mesmo depois de descer e subir).
 */
@Composable
private fun HeroCarousel(vm: MainViewModel, titles: List<Title>, listState: LazyListState, anchor: Int) {
    if (titles.isEmpty()) return
    val scope = rememberCoroutineScope()
    var idx by remember(titles) { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(titles, paused) {
        while (!paused && titles.size > 1) {
            delay(7_000L)
            idx = (idx + 1) % titles.size
        }
    }
    val n = titles.size
    val t = titles[idx.coerceIn(0, n - 1)]
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp),
    ) {
        Crossfade(targetState = t, animationSpec = tween(700), label = "hero") { cur ->
            HeroContent(cur)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = 10.dp)
                .onFocusChanged {
                    val f = it.hasFocus
                    if (f && !paused) {
                        scope.launch {
                            delay(90)
                            try {
                                listState.animateScrollToItem(anchor)
                            } catch (e: Exception) {
                                // a lista saiu da tela: sem importância
                            }
                        }
                    }
                    paused = f
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(text = "Ver detalhes", primary = true) { vm.open(t) }
            CircleAction(if (vm.inMyList(t.key)) "✓" else "+") { vm.toggleMyList(t.key) }
            if (n > 1) {
                CircleAction("‹") { idx = (idx - 1 + n) % n }
                CircleAction("›") { idx = (idx + 1) % n }
                Text(
                    text = "${idx + 1} / $n",
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Fileira de filtros: sortear de novo, "Todos" e um chip por gênero/plataforma. Escolher um mostra só aquele grupo. */
@Composable
private fun FilterChips(vm: MainViewModel, shelves: List<Shelf>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fadeRight(0.07f),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "__shuffle") {
            RoundIconButton(onClick = { vm.reshuffle() }) { c -> ShuffleIcon(color = c) }
        }
        item(key = "__todos") {
            ChipItem(text = "Todos", selected = selected == null) { onSelect(null) }
        }
        items(shelves.take(16), key = { "chip|" + it.name }) { sh ->
            ChipItem(text = sh.name, selected = selected == sh.name) { onSelect(sh.name) }
        }
    }
}

/** Todos os títulos de um gênero/plataforma, em fileiras (aparece quando um chip está escolhido). */
private fun LazyListScope.filteredShelf(shelf: Shelf, onOpen: (Title) -> Unit) {
    val rows = shelf.all.take(112).chunked(7)
    itemsIndexed(rows, key = { i, _ -> "flt|$i" }) { i, row ->
        Column {
            if (i == 0) {
                Text(
                    text = shelf.name + "  ·  " + shelf.all.size + " títulos",
                    color = TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(row, key = { it.key }) { t ->
                    TitlePoster(
                        title = t,
                        focusId = "flt|" + shelf.name + "|" + t.key,
                        modifier = Modifier.width(PosterW),
                        onClick = { onOpen(t) },
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTab(vm: MainViewModel) {
    val cont = vm.continueEntries()
    val mine = vm.myListTitles()
    val mineShelf = remember(mine) { Shelf("Favoritos", mine) }
    val favs = vm.favoriteChannels()
    val hero = vm.heroAll
    val shelves = vm.homeShelves
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = if (sel == null) null else shelves.firstOrNull { it.name == sel }

    // "agora" para os horários dos jogos; também puxa jogos novos ao entrar e a cada meio minuto
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            vm.refreshSports()
            delay(30_000L)
        }
    }

    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hero.isNotEmpty()) {
                // âncora 0: ao voltar para o destaque a lista sobe até o topo e mostra o destaque inteiro
                item(key = "hero") { HeroCarousel(vm, hero, listState, 0) }
            }
            item(key = "games") { Box(modifier = Modifier.topOnFocus(listState)) { GamesStrip(vm, now) } }
            item(key = "chips") {
                Box(modifier = Modifier.topOnFocus(listState)) { FilterChips(vm, shelves, sel) { sel = it } }
            }
            if (picked != null) {
                filteredShelf(picked) { vm.open(it) }
            } else {
                if (cont.isNotEmpty()) {
                    item(key = "cont") { ContinueRow(cont, onResume = { vm.resumeEntry(it) }) }
                }
                if (favs.isNotEmpty()) {
                    item(key = "favch") {
                        Column {
                            Text(
                                "Seus canais",
                                color = TextMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 40.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(favs, key = { it.key }) { c ->
                                    ChannelTile(
                                        ch = c,
                                        stability = vm.channelStability(c),
                                        focusId = "homefav|" + c.key,
                                        modifier = Modifier.width(150.dp),
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
                items(shelves, key = { it.name }) { s ->
                    ShelfRow(s, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(s) })
                }
            }
        }
    }
}

@Composable
fun ShelfTab(vm: MainViewModel, shelves: List<Shelf>, hero: List<Title>) {
    val listState = rememberLazyListState()
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = if (sel == null) null else shelves.firstOrNull { it.name == sel }
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (shelves.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada por aqui ainda.", color = Muted)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hero.isNotEmpty()) {
                    item(key = "hero") { HeroCarousel(vm, hero, listState, 0) }
                }
                item(key = "chips") {
                    Box(modifier = Modifier.topOnFocus(listState)) { FilterChips(vm, shelves, sel) { sel = it } }
                }
                if (picked != null) {
                    filteredShelf(picked) { vm.open(it) }
                } else {
                    items(shelves, key = { it.name }) { s ->
                        ShelfRow(s, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(s) })
                    }
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
            columns = GridCells.Adaptive(108.dp),
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
    val chans = remember(q, cat) { vm.searchChannels(q) }

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
                        modifier = Modifier.width(150.dp),
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
                columns = GridCells.Adaptive(108.dp),
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
// Meus favoritos: filmes/séries salvos + canais favoritos
// =========================================================================

@Composable
fun FavoritesTab(vm: MainViewModel) {
    val mine = vm.myListTitles()
    val favs = vm.favoriteChannels()
    val mineShelf = remember(mine) { Shelf("Meus filmes e séries", mine) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (mine.isEmpty() && favs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nada por aqui ainda.\nAbra um filme ou série e escolha \"+ Favoritos\".\nNos canais, use a estrela ★ dentro do player.",
                    color = Muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
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
                                    modifier = Modifier.width(160.dp),
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

// =========================================================================
// Ajustes
// =========================================================================

@Composable
private fun TimeZoneSettings(vm: MainViewModel) {
    val rev = AppTz.rev
    val now = remember(rev) { System.currentTimeMillis() }
    val clock = remember(rev) { SportsTime.clock(now, java.util.TimeZone.getDefault()) }
    val chosen = vm.user.tzId
    Column {
        Text("Fuso horário", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Os horários dos jogos usam este fuso. Agora, no app: " + clock + " (" + AppTz.describe() + "). " +
                "Se a hora estiver errada, escolha o seu fuso (ou acerte o fuso nas configurações da TV).",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AppTz.OPTIONS, key = { it.first }) { o ->
                ChipItem(text = o.second, selected = chosen == o.first) { vm.setTimeZone(o.first) }
            }
        }
    }
}

/** País da tabela "onde passa": define de onde vêm as emissoras de cada jogo (e os nomes delas). */
@Composable
private fun BroadcastSettings(vm: MainViewModel) {
    // a tabela entra como chave: quando ela muda, esta parte da tela se refaz
    val t = remember(BroadcastStore.rev) { BroadcastStore.table }
    val chosen = vm.user.bcCountry
    var msg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    Column {
        Text("País da busca (onde passa)", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        val jogos = if (t.games.isEmpty()) "sem jogos confirmados ainda" else t.games.size.toString() + " jogos dos próximos dias"
        Text(
            "Define de qual país vem a tabela de emissoras de cada jogo, com os nomes dos canais do país (use o da sua lista: " +
                "lista do Brasil → Brasil; lista de Portugal → Portugal). Agora: " + BroadcastStore.countryName() +
                ", tabela de " + t.updatedPretty() + " (" + jogos + "). Pesquisada na internet e atualizada toda semana.",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in BroadcastStore.COUNTRIES) {
                ChipItem(text = c.second, selected = chosen == c.first) { vm.setBroadcastCountry(c.first) }
            }
            TvButton(text = if (checking) "Conferindo..." else "Conferir tabela agora") {
                if (!checking) {
                    checking = true
                    vm.recheckBroadcasts { m ->
                        checking = false
                        msg = m
                    }
                }
            }
        }
        val m = msg
        if (m != null) {
            Spacer(Modifier.height(4.dp))
            Text(m, color = Muted, fontSize = 13.sp)
        }
    }
}

private fun listLabel(a: Account, i: Int): String {
    val link = if (a.m3u.isNotEmpty()) a.m3u else a.server
    val host = try {
        java.net.URI(link).host ?: ""
    } catch (e: Exception) {
        ""
    }
    val kind = if (a.m3u.isNotEmpty()) "M3U" else "Xtream"
    return "Lista ${i + 1}  ·  $kind" + (if (host.isNotEmpty()) "  ·  $host" else "") +
        (if (a.m3u.isEmpty() && a.user.isNotEmpty()) "  ·  ${a.user}" else "")
}

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Text(text, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    if (sub != null) {
        Spacer(Modifier.height(3.dp))
        Text(sub, color = Muted, fontSize = 13.sp)
    }
}

/** Ajustes > Listas: as listas guardadas, qual usar (ou juntar todas num só conteúdo), adicionar e remover. */
@Composable
private fun ListsSection(vm: MainViewModel) {
    var confirmRemove by remember { mutableStateOf(-1) }
    var confirmLogout by remember { mutableStateOf(false) }
    val lists = vm.lists
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                SectionTitle(
                    "Suas listas",
                    "Guarde quantas listas quiser. Use uma por vez ou junte todas num só conteúdo.",
                )
            }
        }
        itemsIndexed(lists, key = { i, a -> "list|$i|" + CatalogCache.accKey(a) }) { i, a ->
            val inUse = if (vm.mergeLists) true else i == vm.activeList
            Column {
                Text(
                    listLabel(a, i),
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (!inUse) "Guardada" else if (vm.mergeLists) "Incluída no conteúdo junto" else "Em uso",
                    color = if (inUse) Accent else Muted,
                    fontSize = 12.sp,
                )
                if (inUse && !vm.mergeLists && vm.accountInfo.isNotEmpty()) {
                    Text(vm.accountInfo, color = Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!vm.mergeLists && i != vm.activeList) {
                        TvButton(text = "Usar esta") { vm.selectList(i) }
                    }
                    TvButton(text = if (confirmRemove == i) "Confirmar remover?" else "Remover") {
                        if (confirmRemove == i) {
                            confirmRemove = -1
                            vm.removeList(i)
                        } else {
                            confirmRemove = i
                        }
                    }
                }
            }
        }
        item {
            TvButton(text = if (lists.isEmpty()) "Adicionar lista" else "+ Adicionar outra lista") { vm.startAddList() }
        }
        if (lists.size > 1) {
            item {
                Column {
                    SectionTitle(
                        "Juntar listas",
                        "Junta os filmes, séries e canais de todas as listas num só conteúdo. Os mesmos títulos viram um cartão " +
                            "só, e os canais ganham as fontes de todas as listas.",
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChipItem(text = "Juntar todas", selected = vm.mergeLists) { vm.setMerge(true) }
                        ChipItem(text = "Uma por vez", selected = !vm.mergeLists) { vm.setMerge(false) }
                    }
                    if (vm.mergeLists && vm.accountInfo.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(vm.accountInfo, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Column {
                SectionTitle("Sair")
                Spacer(Modifier.height(6.dp))
                TvButton(text = if (confirmLogout) "Confirmar? Remove todas as listas" else "Sair e remover as listas") {
                    if (confirmLogout) vm.logout() else confirmLogout = true
                }
            }
        }
    }
}

/** Ajustes > País e idioma: para listas com conteúdo de vários países, escolhe o que aparece. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CountrySection(vm: MainViewModel) {
    val stats = remember(vm.catalog) { vm.countryStats() }
    val picked = vm.user.countryFilter.toSet()
    // lê a versão dos dados para a tela se refazer quando a escolha muda
    @Suppress("UNUSED_VARIABLE") val rev = vm.dataRev
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                SectionTitle(
                    "País e idioma",
                    "Para listas com conteúdo de vários países. Escolha o que quer ver: o resto fica escondido. " +
                        "O que não tem país indicado na lista aparece sempre.",
                )
            }
        }
        if (stats.isEmpty()) {
            item {
                Text(
                    if (vm.catalog == null) "Catálogo ainda não carregado." else "Esta lista não indica país nas categorias: nada para filtrar.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChipItem(text = "Mostrar tudo", selected = picked.isEmpty()) { vm.setCountryFilter(emptySet()) }
                    Text(
                        if (picked.isEmpty()) "Nenhum filtro" else "Mostrando: " + picked.joinToString(", ") { Countries.label(it) },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((code, n) in stats) {
                        val on = picked.contains(code)
                        ChipItem(
                            text = Countries.label(code) + "  ·  " + n.first + " títulos, " + n.second + " canais",
                            selected = on,
                        ) {
                            val next = picked.toMutableSet()
                            if (on) next.remove(code) else next.add(code)
                            vm.setCountryFilter(next)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSection(vm: MainViewModel) {
    val cat = vm.catalog
    Column(modifier = Modifier.padding(top = 6.dp, end = 40.dp)) {
        SectionTitle("Catálogo")
        Spacer(Modifier.height(4.dp))
        val resumo = if (cat == null) {
            "Ainda não carregado."
        } else {
            "${cat.movies.size} filmes · ${cat.series.size} séries · ${cat.channels.size} canais · atualizado ${vm.catalogAgeText()}"
        }
        Text(resumo, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        TvButton(text = if (vm.refreshing) "Atualizando..." else "Atualizar catálogo agora") {
            if (!vm.refreshing) vm.loadCatalog(force = true)
        }
    }
}

@Composable
private fun SportsSection(vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { FavTeamsSettings(vm) }
        item { TimeZoneSettings(vm) }
        item { BroadcastSettings(vm) }
    }
}

@Composable
private fun LiveSection(vm: MainViewModel) {
    Column(modifier = Modifier.padding(top = 6.dp, end = 40.dp)) {
        SectionTitle("Canais ao vivo", "Quando um canal tem várias fontes, o app escolhe a melhor para você.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Priorizar", color = Muted, fontSize = 14.sp)
            ChipItem(text = "Estabilidade", selected = vm.user.stabilityFirst) { vm.setStabilityFirst(true) }
            ChipItem(text = "Qualidade máxima", selected = !vm.user.stabilityFirst) { vm.setStabilityFirst(false) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Trocar de fonte sozinho se travar", color = Muted, fontSize = 14.sp)
            ChipItem(text = "Sim", selected = vm.user.autoSwitch) { vm.setAutoSwitch(true) }
            ChipItem(text = "Não", selected = !vm.user.autoSwitch) { vm.setAutoSwitch(false) }
        }
        Spacer(Modifier.height(10.dp))
        TvButton(text = "Apagar notas de estabilidade") { vm.clearStats() }
        Spacer(Modifier.height(18.dp))
        SectionTitle("Voltar no tempo (catch-up)", "Com isso você volta lances no ao vivo e revê jogos de até 24 h.")
        Spacer(Modifier.height(4.dp))
        Text(vm.catchupSummary(), color = Muted, fontSize = 13.sp)
    }
}

/** Um item do menu lateral dos Ajustes: o conteúdo ao lado muda assim que o cursor chega nele. */
@Composable
private fun SideItem(text: String, selected: Boolean, onFocus: () -> Unit) {
    Box(modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocus() }) {
        FocusBox(
            shape = RoundedCornerShape(10.dp),
            focusScale = 1f,
            onClick = onFocus,
        ) { focused ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focused) PanelAlt else if (selected) Accent.copy(alpha = 0.20f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text,
                    color = if (selected || focused) TextMain else Muted,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SettingsTab(vm: MainViewModel) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("Listas", "País e idioma", "Catálogo", "Esportes", "Canais ao vivo")
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        Row(modifier = Modifier.fillMaxSize().padding(start = 40.dp, top = 8.dp)) {
            Column(
                modifier = Modifier.width(190.dp).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                names.forEachIndexed { i, n -> SideItem(n, section == i) { section = i } }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    0 -> ListsSection(vm)
                    1 -> CountrySection(vm)
                    2 -> CatalogSection(vm)
                    3 -> SportsSection(vm)
                    else -> LiveSection(vm)
                }
            }
        }
    }
}
