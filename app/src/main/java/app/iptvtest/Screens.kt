package app.iptvtest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
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
            Text("IPTV TEST", color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.Bold)
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
                TvButton(text = if (vm.loginBusy) "Entrando..." else "Entrar", selected = true) {
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
// Home: abas + categorias + grade
// =========================================================================

@Composable
fun HomeScreen(vm: MainViewModel) {
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (kind in Kind.values()) {
                TvButton(text = kind.label, selected = vm.tab == kind) { vm.selectTab(kind) }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = vm.accountInfo,
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(12.dp))
            TvButton(text = if (confirmLogout) "Confirmar saída?" else "Sair") {
                if (confirmLogout) vm.logout() else confirmLogout = true
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CategoryColumn(vm, Modifier.width(250.dp).fillMaxHeight())
            Spacer(Modifier.width(16.dp))
            ItemsPane(vm, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun CategoryColumn(vm: MainViewModel, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Panel)) {
        val cats = vm.categories
        when {
            vm.loadingCats -> CircularProgressIndicator(
                color = Accent,
                modifier = Modifier.align(Alignment.Center),
            )
            cats.isEmpty() -> Text(
                text = vm.catError ?: "Sem categorias.",
                color = if (vm.catError != null) ErrorColor else Muted,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                textAlign = TextAlign.Center,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(cats) { cat ->
                    val selected = vm.selectedCategory?.id == cat.id
                    FocusBox(modifier = Modifier.fillMaxWidth(), onClick = { vm.selectCategory(cat) }) { focused ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (focused) PanelAlt else if (selected) Color0f else Panel)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = cat.name,
                                color = if (selected || focused) TextMain else Muted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val Color0f = androidx.compose.ui.graphics.Color(0xFF22222F)

@Composable
private fun ItemsPane(vm: MainViewModel, modifier: Modifier) {
    Box(modifier = modifier) {
        val list = vm.items
        val err = vm.error
        when {
            vm.loadingItems -> CircularProgressIndicator(
                color = Accent,
                modifier = Modifier.align(Alignment.Center),
            )
            err != null -> Text(
                text = err,
                color = ErrorColor,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                textAlign = TextAlign.Center,
            )
            vm.selectedCategory == null -> Text(
                text = "Escolha uma categoria",
                color = Muted,
                modifier = Modifier.align(Alignment.Center),
            )
            list.isEmpty() -> Text(
                text = "Nada nesta categoria.",
                color = Muted,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> {
                val live = vm.tab == Kind.LIVE
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (live) 190.dp else 140.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(list, key = { it.id }) { entry ->
                        PosterCard(
                            entry = entry,
                            aspect = if (live) 16f / 9f else 2f / 3f,
                            fit = live,
                            onClick = { vm.open(entry) },
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// Série: temporadas + episódios
// =========================================================================

@Composable
fun SeriesScreen(vm: MainViewModel) {
    val detail = vm.seriesDetail ?: return
    BackHandler { vm.closeSeries() }

    val seasons = remember(detail.episodes) { detail.episodes.map { it.season }.distinct().sorted() }
    var season by remember(detail.episodes) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(text = "Voltar") { vm.closeSeries() }
            Spacer(Modifier.width(16.dp))
            Text(
                text = detail.entry.title,
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                detail.loading -> CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
                detail.error != null -> Text(
                    text = detail.error,
                    color = ErrorColor,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                )
                else -> Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(seasons) { s ->
                            FocusBox(modifier = Modifier.fillMaxWidth(), onClick = { season = s }) { focused ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (focused) PanelAlt else if (s == season) Color0f else Panel)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = if (s == 0) "Especiais" else "Temporada $s",
                                        color = if (s == season || focused) TextMain else Muted,
                                        fontWeight = if (s == season) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    val eps = detail.episodes.filter { it.season == season }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(eps) { ep ->
                            FocusBox(modifier = Modifier.fillMaxWidth(), onClick = { vm.playEpisode(detail.entry, ep) }) { focused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (focused) PanelAlt else Panel)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = String.format("E%02d", ep.number),
                                        color = Accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(52.dp),
                                    )
                                    Text(
                                        text = ep.title,
                                        color = TextMain,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
