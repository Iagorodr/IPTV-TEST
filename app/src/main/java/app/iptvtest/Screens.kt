package app.iptvtest

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
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

/** Formulário de entrar com a lista (Xtream, link M3U ou pelo celular). Usado no login e no fim da primeira abertura. */
@Composable
fun ListEntryForm(vm: MainViewModel) {
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

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ChipItem(text = tr("Xtream"), selected = mode == 0) { mode = 0; vm.clearLoginError() }
        ChipItem(text = tr("Link M3U"), selected = mode == 1) { mode = 1; vm.clearLoginError() }
        ChipItem(text = tr("Pelo celular (QR)"), selected = mode == 2) { mode = 2; vm.clearLoginError() }
    }
    Spacer(Modifier.height(16.dp))

    when (mode) {
        0 -> {
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(tr("Servidor (ex.: http://exemplo.com:8080)")) },
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
                label = { Text(tr("Usuário")) },
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
                label = { Text(tr("Senha")) },
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
                TvButton(text = if (showPass) tr("Ocultar senha") else tr("Mostrar senha")) { showPass = !showPass }
                TvButton(text = if (vm.loginBusy) tr("Entrando...") else tr("Entrar"), primary = true) {
                    vm.login(server, user, pass)
                }
            }
        }

        1 -> {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(tr("Link da lista (ex.: http://exemplo.com/lista.m3u)")) },
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
                tr("Links do tipo get.php?username=...&password=... entram como Xtream (mais completo, com sinopse e episódios). ") +
                    tr("Listas M3U comuns trazem só nome, imagem e categoria."),
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            TvButton(text = if (vm.loginBusy) tr("Entrando...") else tr("Entrar"), primary = true) {
                vm.loginM3u(link)
            }
        }

        else -> {
            val url = vm.phoneUrl
            if (url != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    QrCode(text = url, modifier = Modifier.size(170.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("1. Conecte o celular no mesmo Wi-Fi da TV."), color = TextMain, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(tr("2. Aponte a câmera para o QR code."), color = TextMain, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(tr("3. Digite os dados no celular e toque em Enviar."), color = TextMain, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(tr("Ou abra no navegador do celular:"), color = Muted, fontSize = 12.sp)
                        Text(url, color = Accent, fontSize = 11.sp)
                    }
                }
            } else {
                Text(
                    vm.phoneMsg ?: tr("Preparando o QR code..."),
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
        val st = vm.loginStatus
        if (st != null) {
            Spacer(Modifier.height(8.dp))
            Text(st, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel) {
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
                    if (vm.addingList) tr("Adicionar outra lista.\nElas ficam guardadas só nesta TV.") else tr("Entre com a sua lista.\nOs dados ficam guardados só nesta TV."),
                    color = TextMain,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                if (vm.addingList) {
                    Spacer(Modifier.height(14.dp))
                    TvButton(text = tr("Cancelar")) { vm.cancelAddList() }
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
            ListEntryForm(vm)
        }
        }
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
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

/** Só o "D que aponta" (sem selo), em degradê salmão -> vermelho, com a altura das maiúsculas [capDp]. */
@Composable
fun DMark(capDp: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.width((capDp * 73f / 80f).dp).height(capDp.dp)) {
        val k = size.height / 80f
        fun X(u: Float) = (u - 18f) * k
        fun Y(v: Float) = (v - 10f) * k
        val d = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(X(25f), Y(10f)); lineTo(X(50f), Y(10f))
            cubicTo(X(72f), Y(10f), X(86f), Y(30f), X(91f), Y(50f))
            cubicTo(X(86f), Y(70f), X(72f), Y(90f), X(50f), Y(90f))
            lineTo(X(25f), Y(90f)); quadraticBezierTo(X(18f), Y(90f), X(18f), Y(83f))
            lineTo(X(18f), Y(17f)); quadraticBezierTo(X(18f), Y(10f), X(25f), Y(10f)); close()
            moveTo(X(38f), Y(31f)); quadraticBezierTo(X(35f), Y(29.5f), X(35f), Y(33f))
            lineTo(X(35f), Y(67f)); quadraticBezierTo(X(35f), Y(70.5f), X(38f), Y(69f))
            lineTo(X(64f), Y(52f)); quadraticBezierTo(X(66.5f), Y(50f), X(64f), Y(48f)); close()
        }
        drawPath(d, brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Accent, Red), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, size.height)))
    }
}

/**
 * A logo escrita: o nome do app com o último "D" virando o símbolo, mais o selinho PRO (o PRO é só da logo, não do nome).
 * [capDp]: altura das letras maiúsculas.
 */
@Composable
fun AppWordmark(capDp: Int, pulse: Float = 1f) {
    val name = stringResource(R.string.app_name)
    val useMark = name.length > 1 && name.endsWith("D", ignoreCase = true)
    val letters = if (useMark) name.dropLast(1) else name
    // Poppins/padrão: maiúscula ~0,72 do tamanho da fonte
    val fs = (capDp / 0.72f)
    val capPx = with(androidx.compose.ui.platform.LocalDensity.current) { capDp.dp.roundToPx() }
    // tudo alinhado pela linha de base das letras: o D fica em pé nela e o PRO encosta no topo das maiúsculas
    Row {
        Text(letters, color = TextMain, fontSize = fs.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, modifier = Modifier.alignByBaseline())
        if (useMark) {
            Spacer(Modifier.width((capDp * 0.06f).dp))
            DMark(capDp, modifier = Modifier.alignBy { it.measuredHeight }.graphicsLayer(scaleX = pulse, scaleY = pulse))
        }
        Spacer(Modifier.width((capDp * 0.3f).dp))
        Text(
            "PRO",
            color = Accent,
            fontSize = (capDp * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .alignBy { capPx }
                .border(1.dp, Accent, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** Uma tela de dica/novidade do carregamento: ilustração (sem texto, vale para todos os idiomas) + título + explicação. */
private class LoadTip(val image: Int, val title: String, val text: String)

/** Link fixo de doação (PayPal). Usado no QR discreto do carregamento e no de Ajustes > Fale com a gente. */
private const val DONATE_URL = "https://paypal.me/1GROD"

private val LOAD_TIPS = listOf(
    LoadTip(R.drawable.load_genres, "Sua lista, organizada", "Filmes, séries e canais se organizam sozinhos por gênero, com visual de Netflix — não importa de onde vem a sua lista."),
    LoadTip(R.drawable.load_unify, "Mais de uma lista? Junte tudo", "Tem mais de uma assinatura? Entre com as duas e o app mostra tudo junto, num catálogo só."),
    LoadTip(R.drawable.load_feedback, "Novo: Fale com a gente", "Em Ajustes, aponte o celular para o QR code e mande um problema, uma ideia ou um elogio. A resposta aparece aqui na TV."),
    LoadTip(R.drawable.load_vivid, "Novo: Modo jogo", "No player, toque em Modo jogo: cores mais vivas e mais contraste, ótimo para futebol."),
    LoadTip(R.drawable.load_langs, "O app no seu idioma", "Português, inglês, espanhol, francês, italiano, alemão ou holandês. Troque quando quiser em Ajustes > Perfil."),
    LoadTip(R.drawable.load_games, "Nunca perca um jogo", "Escolha seus times e ligas: os jogos aparecem no Início, com o dia, a hora e o canal onde passam."),
    LoadTip(R.drawable.load_sources, "Canal travou? A gente troca", "Quando um canal tem várias fontes, o app escolhe a mais estável ou a de melhor imagem (você decide em Ajustes) e troca sozinho se travar."),
)

@Composable
fun LoadingScreen(vm: MainViewModel) {
    // pulso suave do logo: mostra que o app está trabalhando
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween<Float>(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "scale",
    )
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 36.dp)) {
            // a logo escrita (IPTVRO + o D que aponta + PRO), com o D pulsando enquanto carrega
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppWordmark(capDp = 30, pulse = scale) }
            val err = vm.loadError
            if (err != null) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(tr("Não foi possível carregar o catálogo"), color = ErrorColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = TextMain, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp))
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(text = tr("Tentar de novo"), primary = true) { vm.loadCatalog(force = true) }
                        TvButton(text = tr("Sair da conta")) { vm.logout() }
                    }
                }
                return@Column
            }

            // dicas e novidades: uma de cada vez, trocando a cada 10 segundos
            var tipIdx by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(5_000)
                    tipIdx = (tipIdx + 1) % LOAD_TIPS.size
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(targetState = tipIdx, animationSpec = tween(700), label = "tip") { i ->
                    val tip = LOAD_TIPS[i]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(tip.image),
                            contentDescription = null,
                            modifier = Modifier.width(440.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                        )
                        Column(modifier = Modifier.width(380.dp)) {
                            Text(tr(tip.title), color = Accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(tr(tip.text), color = TextMain, fontSize = 16.sp, lineHeight = 23.sp)
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (k in LOAD_TIPS.indices) {
                                    Box(
                                        Modifier
                                            .width(if (k == i) 22.dp else 8.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (k == i) Accent else Color(0x33FFF3EC)),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // progresso real: etapa atual + itens já lidos + tempo
            var tick by remember { mutableStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    tick = System.currentTimeMillis()
                    kotlinx.coroutines.delay(500)
                }
            }
            val step = vm.loadStep
            val weights = floatArrayOf(0.02f, 0.03f, 0.40f, 0.12f, 0.28f, 0.15f)
            val done = if (step <= 1) 0f else weights.take(step - 1).sum()
            val frac = if (step == 0) 0f else (done + weights[(step - 1).coerceIn(0, 5)] * 0.5f).coerceIn(0.02f, 0.98f)
            val shown by androidx.compose.animation.core.animateFloatAsState(frac, tween(600), label = "progress")
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (step == 0) {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.width(420.dp).height(4.dp), color = Accent, trackColor = Color(0x22FFF3EC))
                } else {
                    androidx.compose.material3.LinearProgressIndicator(progress = { shown }, modifier = Modifier.width(420.dp).height(4.dp), color = Accent, trackColor = Color(0x22FFF3EC))
                }
                Spacer(Modifier.height(10.dp))
                val count = if (step in 3..6) vm.loadCount() else 0
                val msg = (vm.loadMsg ?: tr("Preparando...")) + (if (count > 0) ": " + String.format(java.util.Locale.GERMANY, "%,d", count) + tr(" itens") else "")
                val secs = ((tick - vm.loadStartMs).coerceAtLeast(0L) / 1000L).toInt()
                Text(
                    msg + (if (step > 0) "  ·  " + tr("Etapa {0} de 6", step) + "  ·  " + (secs / 60) + ":" + String.format("%02d", secs % 60) else ""),
                    color = TextMain,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Listas muito grandes podem levar alguns minutos. Isso só acontece na primeira vez: depois o catálogo fica guardado e abre na hora, e as atualizações acontecem em segundo plano."),
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
        }
        // QR discreto e fixo (não some com o carrossel de dicas) para quem quiser apoiar o projeto
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrCode(text = DONATE_URL, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(4.dp))
            Text(tr("Apoiar"), color = Muted, fontSize = 10.sp)
        }
    }
}

// =========================================================================
// Menu do topo
// =========================================================================

/** Aba do menu: só texto. A aba atual ganha um fundo salmão bem translúcido (nada de cor chapada). */
@Composable
private fun NavItem(text: String, selected: Boolean, vm: MainViewModel? = null, onClick: () -> Unit) {
    val fr = rememberNavRefocus(selected, vm)
    FocusBox(
        modifier = Modifier.padding(end = 2.dp),
        shape = RoundedCornerShape(10.dp),
        focusRequester = fr,
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
                // mesmo peso nas duas situações: com peso diferente a largura muda e o menu andava de lado
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * Depois de trocar de aba pelo menu do topo, a tela nova nasce sem foco e o controle voltava para o começo.
 * O item da aba escolhida pede o foco de volta, uma vez.
 */
@Composable
private fun rememberNavRefocus(selected: Boolean, vm: MainViewModel?): FocusRequester? {
    if (vm == null || !selected) return null
    val fr = remember { FocusRequester() }
    LaunchedEffect(vm.tabEpoch) {
        if (vm.navRefocus) {
            delay(60)
            try {
                fr.requestFocus()
            } catch (e: Exception) {
                // a tela ainda não estava pronta: sem problema
            }
            vm.navRefocus = false
        }
    }
    return fr
}

/** Botão "País: todos ▾" das telas: abre a lista para escolher um país só nesta tela. */
@Composable
fun LocalCountryChip(vm: MainViewModel, tab: Tab) {
    if (vm.localChoices().size < 2) return
    val cc = vm.localCc[tab] ?: ""
    ChipItem(text = vm.localLabel(tab) + " ▾", selected = cc.isNotEmpty()) { vm.localPickFor = tab }
}

/**
 * Logo da IPTVROD: o "D que aponta" (a curva do D fecha numa ponta, como um play, e o miolo vazado tem forma de play),
 * num selo com o degradê vinho -> vermelho da marca. Mesmo desenho de tools/brandmark.py (ícone e capa da TV).
 */
@Composable
fun AppLogoMark(sizeDp: Int = 32) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.minDimension
        val r = w * 0.24f
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Wine, Red), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, w)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        // o D é desenhado numa grade de 100x100 (caixa do D: x 18..91, y 10..90) e centralizado no selo
        val k = w * 0.0075f
        fun X(u: Float) = (u - 54.5f) * k + w / 2f
        fun Y(v: Float) = (v - 50f) * k + w / 2f
        val d = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            moveTo(X(25f), Y(10f))
            lineTo(X(50f), Y(10f))
            cubicTo(X(72f), Y(10f), X(86f), Y(30f), X(91f), Y(50f))
            cubicTo(X(86f), Y(70f), X(72f), Y(90f), X(50f), Y(90f))
            lineTo(X(25f), Y(90f))
            quadraticBezierTo(X(18f), Y(90f), X(18f), Y(83f))
            lineTo(X(18f), Y(17f))
            quadraticBezierTo(X(18f), Y(10f), X(25f), Y(10f))
            close()
            // miolo em forma de play (vazado)
            moveTo(X(38f), Y(31f))
            quadraticBezierTo(X(35f), Y(29.5f), X(35f), Y(33f))
            lineTo(X(35f), Y(67f))
            quadraticBezierTo(X(35f), Y(70.5f), X(38f), Y(69f))
            lineTo(X(64f), Y(52f))
            quadraticBezierTo(X(66.5f), Y(50f), X(64f), Y(48f))
            close()
        }
        drawPath(d, color = TextMain)
    }
}

/** Logo + "Olá, nome" (nome escolhido na primeira abertura; nomes longos são cortados com "..."). */
@Composable
fun AppLogo(name: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppLogoMark(32)
        val n = name.trim().split(' ').firstOrNull { it.isNotEmpty() } ?: ""
        if (n.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Olá, {0}", n),
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

/** Modo infantil: um círculo pequeno com carinha, como o avatar de perfil dos apps de streaming. Ligado = colorido. */
@Composable
private fun KidsAvatar(on: Boolean, onClick: () -> Unit) {
    val kidA = Color(0xFFFFC145)
    val kidB = Color(0xFFFF7A9C)
    FocusBox(shape = CircleShape, focusScale = 1.08f, onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (on) androidx.compose.ui.graphics.Brush.linearGradient(listOf(kidA, kidB))
                        else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))),
                    )
                    .border(2.dp, if (focused) TextMain else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val face = if (on) Color(0xFF3A1A00) else if (focused) TextMain else Muted
                androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
                    val w = size.minDimension
                    val st = w * 0.11f
                    drawCircle(color = face, radius = w * 0.07f, center = androidx.compose.ui.geometry.Offset(w * 0.33f, w * 0.38f))
                    drawCircle(color = face, radius = w * 0.07f, center = androidx.compose.ui.geometry.Offset(w * 0.67f, w * 0.38f))
                    drawArc(
                        color = face, startAngle = 15f, sweepAngle = 150f, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.24f, w * 0.30f),
                        size = androidx.compose.ui.geometry.Size(w * 0.52f, w * 0.42f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = st, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
            // o nome só aparece com o foco ou com o modo ligado (fica discreto no resto do tempo)
            if (focused || on) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (on) (if (focused) tr("Sair do infantil") else tr("Infantil")) else tr("Infantil"),
                    color = if (on) kidA else TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Troca rápida Normal/Infantil: cápsula colorida com carinha, diferente das abas de texto. */
@Composable
private fun KidsSwitch(on: Boolean, onClick: () -> Unit) {
    val kidA = Color(0xFFFFB02E)
    val kidB = Color(0xFFFF6B8B)
    FocusBox(
        shape = RoundedCornerShape(20.dp),
        focusScale = 1f,
        onClick = onClick,
    ) { focused ->
        Row(
            modifier = Modifier
                .background(
                    if (on) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(kidA, kidB))
                    else androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(if (focused) kidA.copy(alpha = 0.28f) else kidA.copy(alpha = 0.14f), if (focused) kidB.copy(alpha = 0.28f) else kidB.copy(alpha = 0.14f)),
                    ),
                )
                .border(1.5.dp, if (on) Color.Transparent else kidA.copy(alpha = if (focused) 0.95f else 0.65f), RoundedCornerShape(20.dp))
                .widthIn(min = 150.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val face = if (on) Color(0xFF3A1A00) else kidA
            androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                val w = size.minDimension
                val st = w * 0.1f
                drawCircle(color = face, radius = w * 0.45f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = st))
                drawCircle(color = face, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.36f, w * 0.40f))
                drawCircle(color = face, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.64f, w * 0.40f))
                drawArc(
                    color = face,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.30f, w * 0.38f),
                    size = androidx.compose.ui.geometry.Size(w * 0.40f, w * 0.30f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = st, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (on) tr("Sair do infantil") else tr("Infantil"),
                color = if (on) Color(0xFF3A1A00) else kidA,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Botão só com ícone (lupa). */
@Composable
private fun NavIcon(selected: Boolean, onClick: () -> Unit, vm: MainViewModel? = null, icon: @Composable (Color) -> Unit) {
    val fr = rememberNavRefocus(selected, vm)
    FocusBox(
        shape = RoundedCornerShape(10.dp),
        focusRequester = fr,
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
    // lê a versão dos dados para o menu se refazer quando o modo infantil muda
    @Suppress("UNUSED_VARIABLE") val rev = vm.dataRev
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // margem de segurança de TV: muitas TVs "comem" a borda da imagem (overscan), por isso o menu fica mais para dentro
            .padding(start = 40.dp, end = 44.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(vm.user.profileName)
        Spacer(Modifier.width(18.dp))
        NavItem(tr("Início"), vm.tab == Tab.HOME, vm) { vm.selectTab(Tab.HOME, fromNav = true) }
        if (!vm.user.kidsMode) NavItem(tr("Jogos"), vm.tab == Tab.GAMES, vm) { vm.selectTab(Tab.GAMES, fromNav = true) }
        NavItem(tr("Filmes"), vm.tab == Tab.MOVIES, vm) { vm.selectTab(Tab.MOVIES, fromNav = true) }
        NavItem(tr("Séries"), vm.tab == Tab.SERIES, vm) { vm.selectTab(Tab.SERIES, fromNav = true) }
        NavItem(tr("Canais"), vm.tab == Tab.CHANNELS, vm) { vm.selectTab(Tab.CHANNELS, fromNav = true) }
        NavItem(tr("Favoritos"), vm.tab == Tab.LIST, vm) { vm.selectTab(Tab.LIST, fromNav = true) }
        if (!vm.user.kidsMode) NavItem(tr("Gravações"), vm.tab == Tab.REC, vm) { vm.selectTab(Tab.REC, fromNav = true) }
        Spacer(Modifier.weight(1f))
        // atualizando a lista em segundo plano: só o símbolo girando (o texto apertava o botão do infantil)
        if (vm.refreshing) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(14.dp))
        }
        NavIcon(vm.tab == Tab.SEARCH, { vm.selectTab(Tab.SEARCH, fromNav = true) }, vm) { c -> SearchIcon(color = c) }
        Spacer(Modifier.width(4.dp))
        NavIcon(vm.tab == Tab.SETTINGS, { vm.selectTab(Tab.SETTINGS, fromNav = true) }, vm) { c -> GearIcon(color = c, size = 20) }
        Spacer(Modifier.width(10.dp))
        KidsAvatar(vm.user.kidsMode) { vm.setKids(!vm.user.kidsMode) }
    }
}

@Composable
fun TabScreen(vm: MainViewModel) {
    when (vm.tab) {
        Tab.HOME -> HomeTab(vm)
        Tab.GAMES -> GamesTab(vm)
        Tab.MOVIES -> LocalShelfTab(vm, Tab.MOVIES)
        Tab.SERIES -> LocalShelfTab(vm, Tab.SERIES)
        Tab.CHANNELS -> ChannelsTab(vm)
        Tab.LIST -> FavoritesTab(vm)
        Tab.REC -> RecordingsTab(vm)
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
    val tm by rememberTmdb(t)
    Box(modifier = Modifier.fillMaxSize()) {
        val img = tm?.backdrop ?: t.image
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
                    if (t.series) tr("SÉRIE") else tr("FILME"),
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
                    if (t.versions.any { !it.leg && !it.cam }) tr("Dublado") else null,
                    if (t.versions.any { it.leg }) tr("Legendado") else null,
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
            val shown = shownRating(t.rating, tm)
            val rating = if (shown > 0f) "★ " + String.format(java.util.Locale.US, "%.1f", shown).replace('.', ',') else ""
            val meta = listOf(rating, t.genres.take(2).joinToString(" · ")).filter { it.isNotEmpty() }.joinToString("   ")
            if (meta.isNotEmpty()) {
                Text(meta, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val heroPlot = t.plot.ifEmpty { tm?.overview ?: "" }
            if (heroPlot.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    heroPlot,
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
            TvButton(text = tr("Ver detalhes"), primary = true) { vm.open(t) }
            CircleAction(if (vm.inMyList(t.key)) "✓" else "+") { vm.toggleMyList(t.key) }
            if (n > 1) {
                CircleAction("‹") { idx = (idx - 1 + n) % n }
                CircleAction("›") { idx = (idx + 1) % n }
                Text(
                    text = tr("{0} / {1}", idx + 1, n),
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Fileira de filtros: sortear de novo, "Todos" e um chip por gênero/plataforma. Escolher um mostra só aquele grupo. */
private const val CONTINUE_TAB = "Continuar assistindo"

@Composable
private fun FilterChips(vm: MainViewModel, shelves: List<Shelf>, selected: String?, hasContinue: Boolean = false, lead: (@Composable () -> Unit)? = null, onSelect: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fadeRight(0.07f),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) item(key = "__lead") { lead() }
        item(key = "__todos") {
            ChipItem(text = tr("Todos"), selected = selected == null) { onSelect(null) }
        }
        if (hasContinue) {
            item(key = "__cont") {
                ChipItem(text = tr(CONTINUE_TAB), selected = selected == CONTINUE_TAB) { onSelect(CONTINUE_TAB) }
            }
        }
        items(shelves.take(16).distinctBy { "chip|" + it.name }, key = { "chip|" + it.name }) { sh ->
            ChipItem(text = tr(sh.name), selected = selected == sh.name) { onSelect(sh.name) }
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
                    text = tr(shelf.name) + "  ·  " + shelf.all.size + tr(" títulos"),
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
                items(row.distinctBy { it.key }, key = { it.key }) { t ->
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
    val mineShelf = remember(mine) { Shelf(tr("Favoritos"), mine) }
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
                Box(modifier = Modifier.topOnFocus(listState)) { FilterChips(vm, shelves, sel, hasContinue = cont.isNotEmpty()) { sel = it } }
            }
            if (sel == CONTINUE_TAB && cont.isNotEmpty()) {
                item(key = "cont") { ContinueRow(cont, onResume = { vm.resumeEntry(it) }) }
            } else if (picked != null) {
                filteredShelf(picked) { vm.open(it) }
            } else {
                if (favs.isNotEmpty()) {
                    item(key = "favch") {
                        Column {
                            Text(
                                tr("Seus canais"),
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
                                items(favs.distinctBy { it.key }, key = { it.key }) { c ->
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
                items(shelves.distinctBy { it.name }, key = { it.name }) { s ->
                    ShelfRow(s, onOpen = { vm.open(it) }, onSeeAll = { vm.openGrid(s) })
                }
            }
        }
    }
}

/** Filmes/Séries com o filtro rápido de país desta tela. */
@Composable
fun LocalShelfTab(vm: MainViewModel, tab: Tab) {
    val series = tab == Tab.SERIES
    val cc = vm.localCc[tab] ?: ""
    val base = if (series) Pair(vm.seriesShelves, vm.heroSeries) else Pair(vm.movieShelves, vm.heroMovies)
    var local by remember(cc, vm.catalog, vm.dataRev) { mutableStateOf<Pair<List<Shelf>, List<Title>>?>(null) }
    LaunchedEffect(cc, vm.catalog, vm.dataRev) {
        local = if (cc.isEmpty()) null else vm.localShelves(series, cc)
    }
    val shown = if (cc.isEmpty()) base else (local ?: Pair(emptyList(), emptyList()))
    ShelfTab(vm, shown.first, shown.second, tab, loading = cc.isNotEmpty() && local == null)
}

@Composable
fun ShelfTab(vm: MainViewModel, shelves: List<Shelf>, hero: List<Title>, tab: Tab? = null, loading: Boolean = false) {
    val listState = rememberLazyListState()
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = if (sel == null) null else shelves.firstOrNull { it.name == sel }
    // "Continuar assistindo" também em Filmes e Séries, só com o tipo da página
    val cont = if (tab == Tab.MOVIES || tab == Tab.SERIES) vm.continueEntries().filter { it.first.series == (tab == Tab.SERIES) } else emptyList()
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (shelves.isEmpty()) {
            if (tab != null) Row(modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)) { LocalCountryChip(vm, tab) }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (loading) tr("Separando por país...") else tr("Nada por aqui com esse país."), color = Muted)
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
                    Box(modifier = Modifier.topOnFocus(listState)) {
                        FilterChips(vm, shelves, sel, hasContinue = cont.isNotEmpty(), lead = if (tab != null) ({ LocalCountryChip(vm, tab) }) else null) { sel = it }
                    }
                }
                if (sel == CONTINUE_TAB && cont.isNotEmpty()) {
                    item(key = "cont") { ContinueRow(cont, onResume = { vm.resumeEntry(it) }) }
                } else if (picked != null) {
                    filteredShelf(picked) { vm.open(it) }
                } else {
                    items(shelves.distinctBy { it.name }, key = { it.name }) { s ->
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
            TvButton(text = tr("Voltar")) { vm.back() }
            Spacer(Modifier.width(16.dp))
            Text(tr(shelf.name), color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            Text(tr("{0} títulos", shelf.all.size), color = Muted, fontSize = 14.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(shelf.all.distinctBy { it.key }, key = { it.key }) { t ->
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
            label = { Text(tr("Buscar filmes, séries e canais")) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        )
        if (chans.isNotEmpty()) {
            Text(
                tr("Canais"),
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(chans.distinctBy { it.key }, key = { it.key }) { c ->
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
                    text = if (Organizer.fold(q).length < 2) tr("Digite pelo menos 2 letras.") else if (chans.isEmpty()) tr("Nada encontrado.") else "",
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
                gridItems(titles.distinctBy { it.key }, key = { it.key }) { t ->
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
    val mineShelf = remember(mine) { Shelf(tr("Meus filmes e séries"), mine) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (mine.isEmpty() && favs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    tr("Nada por aqui ainda.\nAbra um filme ou série e escolha \"+ Favoritos\".\nNos canais, use a estrela ★ dentro do player."),
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
                            tr("Canais favoritos"),
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
                            items(favs.distinctBy { it.key }, key = { it.key }) { c ->
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
        Text(tr("Fuso horário"), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Os horários dos jogos usam este fuso. Agora, no app: ") + clock + " (" + AppTz.describe() + "). " +
                tr("Se a hora estiver errada, escolha o seu fuso (ou acerte o fuso nas configurações da TV)."),
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AppTz.OPTIONS.distinctBy { it.first }, key = { it.first }) { o ->
                ChipItem(text = o.second, selected = chosen == o.first) { vm.setTimeZone(o.first) }
            }
        }
    }
}

/** Países da tabela "onde passa": até 3, ou automático (os países que a sua lista realmente tem). */
@Composable
private fun BroadcastSettings(vm: MainViewModel) {
    // a tabela entra como chave: quando ela muda, esta parte da tela se refaz
    val t = remember(BroadcastStore.rev) { BroadcastStore.table }
    val manual = vm.user.bcCountries.toList()
    var msg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    Column {
        Text(tr("Países da busca (onde passa)"), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        val jogos = if (t.games.isEmpty()) tr("sem jogos confirmados ainda") else t.games.size.toString() + tr(" jogos dos próximos dias")
        Text(
            tr("Escolha até 3 países de onde vêm as emissoras de cada jogo. Em \"Automático\" o app usa os países dos canais que você tem ") +
                tr("(ou os marcados no botão País). Agora: ") + BroadcastStore.countriesLine() +
                tr(", tabela de ") + t.updatedPretty() + " (" + jogos + tr("). Pesquisada na internet e atualizada toda semana."),
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item(key = "auto") {
                ChipItem(text = tr("Automático"), selected = manual.isEmpty()) { vm.setBroadcastCountries(emptyList()) }
            }
            items(BroadcastStore.COUNTRIES.distinctBy { it.first }, key = { it.first }) { c ->
                val on = manual.contains(c.first)
                ChipItem(text = (if (on) "✓ " else "") + c.second, selected = on) {
                    val next = if (on) manual - c.first else if (manual.size >= 3) manual else manual + c.first
                    vm.setBroadcastCountries(next)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TvButton(text = if (checking) tr("Conferindo...") else tr("Conferir tabelas agora")) {
            if (!checking) {
                checking = true
                vm.recheckBroadcasts { m ->
                    checking = false
                    msg = m
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
    val kind = if (a.m3u.isNotEmpty()) "M3U" else tr("Xtream")
    return tr("Lista {0}  ·  {1}", i + 1, kind) + (if (host.isNotEmpty()) tr("  ·  {0}", host) else "") +
        (if (a.m3u.isEmpty() && a.user.isNotEmpty()) tr("  ·  {0}", a.user) else "")
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
                    tr("Suas listas"),
                    tr("Guarde quantas listas quiser. Use uma por vez ou junte todas num só conteúdo."),
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
                    if (!inUse) tr("Guardada") else if (vm.mergeLists) tr("Incluída no conteúdo junto") else tr("Em uso"),
                    color = if (inUse) Accent else Muted,
                    fontSize = 12.sp,
                )
                if (inUse && !vm.mergeLists && vm.accountInfo.isNotEmpty()) {
                    Text(vm.accountInfo, color = Muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!vm.mergeLists && i != vm.activeList) {
                        TvButton(text = tr("Usar esta")) { vm.selectList(i) }
                    }
                    TvButton(text = if (confirmRemove == i) tr("Confirmar remover?") else tr("Remover")) {
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
            TvButton(text = if (lists.isEmpty()) tr("Adicionar lista") else tr("+ Adicionar outra lista")) { vm.startAddList() }
        }
        if (lists.size > 1) {
            item {
                Column {
                    SectionTitle(
                        tr("Juntar listas"),
                        tr("Junta os filmes, séries e canais de todas as listas num só conteúdo. Os mesmos títulos viram um cartão ") +
                            tr("só, e os canais ganham as fontes de todas as listas."),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChipItem(text = tr("Juntar todas"), selected = vm.mergeLists) { vm.setMerge(true) }
                        ChipItem(text = tr("Uma por vez"), selected = !vm.mergeLists) { vm.setMerge(false) }
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
                SectionTitle(tr("Sair"))
                Spacer(Modifier.height(6.dp))
                TvButton(text = if (confirmLogout) tr("Confirmar? Remove todas as listas") else tr("Sair e remover as listas")) {
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
                    tr("País e idioma"),
                    tr("Para listas com conteúdo de vários países. Escolha o que quer ver: o resto fica escondido. ") +
                        tr("Dentro de Canais, Filmes e Séries ainda dá para escolher um só destes países pelo botão \"País\"."),
                )
            }
        }
        if (stats.isEmpty()) {
            item {
                Text(
                    if (vm.catalog == null) tr("Catálogo ainda não carregado.") else tr("Esta lista não indica país nas categorias: nada para filtrar."),
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChipItem(text = tr("Mostrar tudo"), selected = picked.isEmpty()) { vm.setCountryFilter(emptySet()) }
                    Text(
                        if (picked.isEmpty()) tr("Nenhum filtro") else tr("Mostrando: ") + picked.joinToString(", ") { Countries.label(it) },
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
                            text = Countries.label(code) + "  ·  " + n.first + tr(" títulos, ") + n.second + tr(" canais"),
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
        SectionTitle(tr("Catálogo"))
        Spacer(Modifier.height(4.dp))
        val resumo = if (cat == null) {
            tr("Ainda não carregado.")
        } else {
            tr("{0} filmes · {1} séries · {2} canais · atualizado {3}", cat.movies.size, cat.series.size, cat.channels.size, vm.catalogAgeText())
        }
        Text(resumo, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        TvButton(text = if (vm.refreshing) tr("Atualizando...") else tr("Atualizar catálogo agora")) {
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
        SectionTitle(tr("Canais ao vivo"), tr("Quando um canal tem várias fontes, o app escolhe a melhor para você."))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Priorizar"), color = Muted, fontSize = 14.sp)
            ChipItem(text = tr("Estabilidade"), selected = vm.user.stabilityFirst) { vm.setStabilityFirst(true) }
            ChipItem(text = tr("Qualidade máxima"), selected = !vm.user.stabilityFirst) { vm.setStabilityFirst(false) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Trocar de fonte sozinho se travar"), color = Muted, fontSize = 14.sp)
            ChipItem(text = tr("Sim"), selected = vm.user.autoSwitch) { vm.setAutoSwitch(true) }
            ChipItem(text = tr("Não"), selected = !vm.user.autoSwitch) { vm.setAutoSwitch(false) }
        }
        Spacer(Modifier.height(10.dp))
        TvButton(text = tr("Apagar notas de estabilidade")) { vm.clearStats() }
        Spacer(Modifier.height(18.dp))
        SectionTitle(tr("Voltar no tempo (catch-up)"), tr("Com isso você volta lances no ao vivo e revê jogos de até 24 h."))
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf(vm.user.profileName) }
    var lang by rememberSaveable { mutableStateOf(vm.user.profileLang.ifEmpty { detectLang(vm.deviceLang()) }) }
    var saved by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(end = 40.dp, top = 6.dp, bottom = 30.dp)) {
        item {
            SectionTitle(tr("Perfil"), tr("Seu nome, idioma e gêneros preferidos."))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    saved = false
                },
                label = { Text(tr("Seu nome")) },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.width(360.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(tr("Idioma"), color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((tag, label) in APP_LANGS) ChipItem(text = label, selected = lang == tag) {
                    lang = tag
                    saved = false
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(text = tr("Salvar"), primary = true) {
                    if (name.isNotBlank()) {
                        vm.updateProfile(name, lang)
                        saved = true
                    }
                }
                if (saved) Text(tr("Salvo."), color = Good, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(tr("Gêneros preferidos (até 3)"), color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            GenreChips(vm)
            Spacer(Modifier.height(20.dp))
            Text(tr("Times e ligas favoritos"), color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            TvButton(text = tr("Escolher times")) { vm.openPicker() }
        }
    }
}

@Composable
private fun AboutSection(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(end = 40.dp, top = 6.dp)) {
        SectionTitle(tr("Sobre"), tr("Informações deste aparelho."))
        Spacer(Modifier.height(16.dp))
        Text(tr("Código deste aparelho"), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(vm.deviceCode(), color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            tr("Se precisar de ajuda, passe este código a quem te deu o app. Ele serve só para contar quantos aparelhos usam o app: ") +
                tr("não envia o link da sua lista, senha nem o que você assiste."),
            color = Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            tr("Capas e sinopses extras: este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB."),
            color = Muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(tr("Versão instalada: build ") + vm.installedBuild(), color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        TvButton(text = tr("Procurar atualização")) { vm.checkUpdate(manual = true) }
        val um = vm.updateMsg
        if (um != null) {
            Spacer(Modifier.height(6.dp))
            Text(um, color = Muted, fontSize = 12.sp)
        }
    }
}

/** Ajustes > Fale com a gente: QR code que abre, no celular, a página para mandar problema, ideia ou elogio (e doar). */
@Composable
private fun FeedbackSection(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(end = 40.dp, top = 6.dp)) {
        SectionTitle(tr("Fale com a gente"), tr("Problema, ideia, elogio ou apoio a quem fez o app."))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            QrCode(text = vm.feedbackUrl(), modifier = Modifier.size(200.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                Text(tr("1. Aponte a câmera do celular para o QR code."), color = TextMain, fontSize = 15.sp)
                Text(tr("2. Escreva sua mensagem e toque em Enviar."), color = TextMain, fontSize = 15.sp)
                Text(tr("3. A resposta aparece aqui na TV e na mesma página do celular."), color = TextMain, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(tr("Código deste aparelho: ") + vm.deviceCode(), color = Muted, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            QrCode(text = DONATE_URL, modifier = Modifier.size(130.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                Text(tr("Apoiar o projeto"), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(tr("Curtiu o app? Aponte o celular aqui e doe pelo PayPal. Qualquer valor ajuda a manter tudo no ar."), color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingsTab(vm: MainViewModel) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf(tr("Listas"), tr("País e idioma"), tr("Catálogo"), tr("Esportes"), tr("Canais ao vivo"), tr("Perfil"), tr("Fale com a gente"), tr("Sobre"))
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
                    4 -> LiveSection(vm)
                    5 -> ProfileSection(vm)
                    6 -> FeedbackSection(vm)
                    else -> AboutSection(vm)
                }
            }
        }
    }
}
