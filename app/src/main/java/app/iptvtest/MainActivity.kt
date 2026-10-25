package app.iptvtest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        try {
            ViewModelProvider(this)[MainViewModel::class.java].onForeground(false)
        } catch (e: Exception) {
            // sem problema
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            ViewModelProvider(this)[MainViewModel::class.java].onForeground(true)
        } catch (e: Exception) {
            // sem problema
        }
        // voltou ao app depois de uns minutos fora (botão Home): prateleiras novas, como numa abertura nova
        if (stoppedAt > 0L && SystemClock.elapsedRealtime() - stoppedAt > 3 * 60_000L) {
            try {
                ViewModelProvider(this)[MainViewModel::class.java].reshuffle()
            } catch (e: Exception) {
                // sem problema: fica com as prateleiras de agora
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides TvScrollSpec,
                ) {
                    App()
                }
            }
        }
    }
}

/**
 * Rolagem do foco (listas e fileiras): começa a rolar um pouco antes de o cursor chegar na borda e usa uma mola
 * firme e sem quique, que acompanha bem quando a pessoa segura o botão. O padrão do Compose só rola quando o
 * item já está cortado e com uma animação lenta, o que dava a sensação de travadinhas.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object TvScrollSpec : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = 900f,
        )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.07f
        val end = offset + size
        return when {
            size > containerSize - 2 * margin -> offset - margin
            offset < margin -> offset - margin
            end > containerSize - margin -> end - (containerSize - margin)
            else -> 0f
        }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    // guarda a posição das listas de cada tela, para voltar exatamente onde estava depois de abrir um título
    val holder = rememberSaveableStateHolder()
    val account = vm.account
    val playing = vm.playing
    val catalog = vm.catalog
    val title = vm.openTitle
    val grid = vm.grid
    val chList = vm.channelList
    val game = vm.gameDialog
    val due = vm.dueReminder
    val ctx = LocalContext.current

    // Voltar no Início, sem nenhuma outra tela aberta, pergunta se quer sair do app
    var askExit by remember { mutableStateOf(false) }
    BackHandler(enabled = (account != null && playing == null && !vm.showWelcome) || vm.pickerOpen) {
        if (vm.canGoBack) vm.back() else askExit = true
    }
    if (askExit && account != null && playing == null) {
        val stay = remember { androidx.compose.ui.focus.FocusRequester() }
        val leave = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) {
            delay(60)
            try {
                stay.requestFocus()
            } catch (e: Exception) {
                // o botão ainda não estava na tela
            }
        }
        Dialog(onDismissRequest = { askExit = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Panel)
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Fechar o app?", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (vm.anyRecording()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Há uma gravação em andamento. Ela continua, mas a TV precisa ficar ligada.",
                        color = Accent, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.width(300.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                // esquerda/direita levam direto de um botão ao outro (a navegação automática falhava dentro do pop-up)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.onPreviewKeyEvent { ev ->
                        if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            false
                        } else {
                            when (ev.key) {
                                androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                    try { stay.requestFocus() } catch (e: Exception) { }
                                    true
                                }
                                androidx.compose.ui.input.key.Key.DirectionRight -> {
                                    try { leave.requestFocus() } catch (e: Exception) { }
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                ) {
                    TvButton(text = "Ficar", primary = true, focusRequester = stay, minWidth = 120.dp) { askExit = false }
                    TvButton(text = "Sair", focusRequester = leave, minWidth = 120.dp) {
                        askExit = false
                        (ctx as? android.app.Activity)?.finish()
                    }
                }
            }
        }
    }

    // Android 13+ pede licença para notificações; só pedimos quando a pessoa já tem algum lembrete
    val askedNotif = rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm.reminders.size) {
        if (vm.reminders.isNotEmpty() && !askedNotif.value && Build.VERSION.SDK_INT >= 33) {
            askedNotif.value = true
            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!ok) {
                try {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (e: Exception) {
                    // sem tela para mostrar o pedido: o aviso dentro do app continua valendo
                }
            }
        }
    }

    // confere o relógio ao abrir e a cada 30 min (relógio errado quebra jogos, capas e gravações agendadas)
    LaunchedEffect(account != null) {
        if (account != null) {
            delay(2_500L)
            while (true) {
                vm.checkClock()
                delay(30L * 60_000L)
            }
        }
    }

    // procura versão nova ao abrir e a cada 6 horas
    LaunchedEffect(account != null) {
        if (account != null) {
            delay(4_000L)
            while (true) {
                vm.checkUpdate()
                delay(6L * 3_600_000L)
            }
        }
    }

    // religa o vigia das gravações agendadas (ele some se o app foi fechado à força ou a TV reiniciou)
    LaunchedEffect(account != null) {
        if (account != null) {
            vm.refreshScheds()
            Recorder.watch(ctx.applicationContext)
        }
    }

    // a cada 30 s vê se algum jogo lembrado começa em 10 min (cobre alarmes perdidos, ex.: TV desligada)
    LaunchedEffect(account != null) {
        while (account != null) {
            vm.checkReminders(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    Surface(modifier = Modifier.fillMaxSize().background(AppBackground), color = Color.Transparent, contentColor = TextMain) {
        when {
            vm.blocked -> BlockedScreen(vm)
            vm.recMode != null -> RecordingModeScreen(vm)
            vm.showWelcome && vm.pickerOpen -> holder.SaveableStateProvider("picker") {
                TeamPickerScreen(vm)
            }
            vm.showWelcome -> WelcomeScreen(vm)
            account == null -> LoginScreen(vm)
            vm.addingList -> LoginScreen(vm)
            // key(): ao trocar de praça (Globo SP → Globo Rio) o player é recriado com o novo canal
            playing != null -> key(playing.live?.channel?.key ?: playing.title) {
                PlayerScreen(
                    playing,
                    vm.user,
                    onExit = { vm.stopPlaying() },
                    onVariant = { b, v -> vm.playVariant(b, v) },
                    currentVariantKey = { b -> vm.currentVariantKey(b) },
                    onRecord = { k, t, u, m -> vm.startRecording(k, t, u, m) },
                    recordingId = { k -> vm.recordingIdOf(k) },
                    onStopRecord = { id -> vm.stopRecording(id) },
                    onRecMode = { id -> vm.enterRecMode(id) },
                )
            }
            catalog == null -> LoadingScreen(vm)
            vm.pickerOpen -> holder.SaveableStateProvider("picker") {
                TeamPickerScreen(vm)
            }
            vm.allGamesOpen -> holder.SaveableStateProvider("allgames") {
                AllGamesScreen(vm)
            }
            chList != null -> holder.SaveableStateProvider("chlist|" + chList.title + "|" + chList.subtitle) {
                ChannelListScreen(vm, chList)
            }
            title != null -> holder.SaveableStateProvider("title|" + title.key) {
                TitleScreen(vm, title)
            }
            grid != null -> holder.SaveableStateProvider("grid|" + grid.name + "|" + System.identityHashCode(grid)) {
                GridScreen(vm, grid)
            }
            else -> holder.SaveableStateProvider("tab|" + vm.tab.name + "|" + vm.tabEpoch) {
                TabScreen(vm)
            }
        }

        // aviso de quem desenvolve o app (aparece uma vez por aviso)
        val note = vm.notice
        if (note != null && !vm.blocked) {
            if (vm.noticeSecs > 0) NoticeBanner(vm, note.first, note.second, vm.noticeSecs) else NoticeDialog(vm, note.second)
        }

        // relógio do aparelho errado
        if (vm.clockReal != 0L && !vm.clockDismissed && playing == null && vm.recMode == null && !vm.blocked && account != null) {
            ClockDialog(vm)
        }

        // escolha rápida de países (botão no topo)
        if (vm.countryDialog && playing == null && catalog != null) CountryDialog(vm)

        // versão nova: pergunta uma vez por abertura, e nunca por cima do player ou da tela preta
        val upd = vm.update
        if (upd != null && !vm.updateDismissed && playing == null && vm.recMode == null && !vm.blocked && account != null && catalog != null) {
            UpdateDialog(vm, upd)
        }

        // lista nova ficou pronta em segundo plano
        val note = vm.listNotice
        if (note != null && playing == null && catalog != null) ListNotice(note)

        // agendar gravação: faixa enquanto escolhe o canal, e a tela de horário depois
        if (vm.pickCb != null && playing == null) PickBanner(vm.pickLabel)
        val si = vm.schedInit
        if (si != null && playing == null) ScheduleDialog(vm, si)

        // pop-up do jogo (por cima de qualquer tela, menos do player)
        if (game != null && playing == null && catalog != null) {
            GameDialog(vm, game)
        }

        // "começa em 10 min": com o player aberto vira uma faixa discreta; fora dele, um aviso com botões
        if (due != null && account != null) {
            if (playing == null) {
                DueReminderDialog(vm, due)
            } else {
                DueReminderStrip(vm, due)
            }
        }
    }
}

private fun minutesLeft(startMs: Long): Long {
    val m = (startMs - System.currentTimeMillis() + 59_999L) / 60_000L
    return if (m < 0) 0 else m
}

@Composable
private fun DueReminderDialog(vm: MainViewModel, r: Reminder) {
    val first = remember { FocusRequester() }
    LaunchedEffect(r.gameKey) {
        delay(80)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    Dialog(
        onDismissRequest = { vm.dismissDue(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, PanelAlt, RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⏰ Já vai começar", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(r.title, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            val m = minutesLeft(r.startMs)
            Text(
                (if (m <= 0L) "Está começando agora" else "Começa em $m min") + (if (r.league.isNotEmpty()) " · " + r.league else ""),
                color = Muted,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(text = "Ver canais", primary = true, focusRequester = first) { vm.dismissDue(true) }
                TvButton(text = "Dispensar") { vm.dismissDue(false) }
            }
        }
    }
}

@Composable
private fun DueReminderStrip(vm: MainViewModel, r: Reminder) {
    LaunchedEffect(r.gameKey) {
        delay(12_000L)
        vm.dismissDue(false)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val m = minutesLeft(r.startMs)
        Text(
            "⏰ " + r.title + (if (m <= 0L) " está começando" else " começa em $m min"),
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xDD0B1020))
                .border(1.5.dp, Accent, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BlockedScreen(vm: MainViewModel) {
    val first = remember { FocusRequester() }
    val ctx = LocalContext.current
    var form by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(vm.user.profileName) }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(form) {
        delay(80)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    // o QR code do pedido só fica ligado com o formulário aberto
    LaunchedEffect(form) {
        if (form) vm.startPhoneLogin(PhoneLoginServer.Page.ACCESS) else vm.stopPhoneLogin()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.stopPhoneLogin() }
    }
    BackHandler(enabled = true) {
        if (form) form = false else (ctx as? android.app.Activity)?.finish()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.5f, sunY = 0.3f, sunR = 0.17f)
        Column(modifier = Modifier.width(if (form) 760.dp else 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
            Spacer(Modifier.height(22.dp))
            Text(
                if (vm.pendingApproval) "Aguardando liberação" else "Acesso suspenso",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            if (!form) {
                Text(
                    if (vm.requestState == 2) {
                        "Recebemos o seu pedido. Ele está em análise e a gente entra em contato."
                    } else if (vm.pendingApproval) {
                        "Este aparelho ainda não foi liberado. Você pode pedir a liberação."
                    } else {
                        "O acesso deste aparelho ao app foi pausado. Você pode pedir a liberação."
                    },
                    color = TextMain,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text("Código: " + vm.deviceCode(), color = Muted, fontSize = 14.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.requestState != 2) {
                        TvButton(text = "Pedir liberação", primary = true, focusRequester = first) { form = true }
                        TvButton(text = "Tentar de novo") { vm.retryControl() }
                    } else {
                        TvButton(text = "Tentar de novo", primary = true, focusRequester = first) { vm.retryControl() }
                    }
                    TvButton(text = "Sair") { (ctx as? android.app.Activity)?.finish() }
                }
            } else if (vm.requestState == 2) {
                Text("Pedido enviado! Ele está em análise e a gente entra em contato.", color = TextMain, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                TvButton(text = "Voltar", primary = true, focusRequester = first) { form = false }
            } else {
                Text(
                    "Seus dados vão só para quem desenvolve o app, para ele entrar em contato. Preencha aqui ou pelo celular.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true,
                            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true,
                            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = phone, onValueChange = { phone = it }, label = { Text("Telefone") }, singleLine = true,
                            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                        )
                    }
                    val url = vm.phoneUrl
                    if (url != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            QrCode(text = url, modifier = Modifier.size(140.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Ou aponte a câmera do celular", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (vm.requestState == 3) {
                    Text("Não consegui enviar. Preencha tudo e confira a internet.", color = ErrorColor, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = if (vm.requestState == 1) "Enviando..." else "Enviar pedido",
                        primary = true,
                        focusRequester = first,
                    ) { vm.sendAccessRequest(name, email, phone) }
                    TvButton(text = "Voltar") { form = false }
                }
            }
        }
    }
}

@Composable
private fun NoticeDialog(vm: MainViewModel, text: String) {
    val ok = remember { FocusRequester() }
    LaunchedEffect(text) {
        delay(80)
        try {
            ok.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    Dialog(onDismissRequest = { vm.dismissNotice() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, PanelAlt, RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Aviso", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            Text(text, color = TextMain, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            TvButton(text = "OK", primary = true, focusRequester = ok) { vm.dismissNotice() }
        }
    }
}

/** Aviso com tempo: faixa no topo, sem tirar o controle do filme ou canal; some sozinho. */
@Composable
private fun NoticeBanner(vm: MainViewModel, id: Int, text: String, secs: Int) {
    LaunchedEffect(id) {
        delay(secs * 1000L)
        vm.dismissNotice()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .padding(top = 28.dp)
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Panel.copy(alpha = 0.96f))
                .border(1.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Aviso", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = TextMain, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}
