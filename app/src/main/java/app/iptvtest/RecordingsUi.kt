package app.iptvtest

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun sizeText(b: Long): String =
    if (b >= 1_000_000_000L) String.format(Locale.US, "%.1f GB", b / 1e9) else String.format(Locale.US, "%d MB", b / 1_000_000L)

private fun lengthText(ms: Long): String {
    val m = (ms / 60_000L).coerceAtLeast(0L)
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** Aba "Gravações": o que está sendo gravado e o que já foi gravado neste aparelho. */
@Composable
fun RecordingsTab(vm: MainViewModel) {
    // atualiza o tamanho enquanto grava
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshRecs()
            vm.refreshScheds()
            delay(2_000L)
        }
    }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    var confirmSched by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val rowFocus = remember { FocusRequester() }
    // ao entrar na aba, o foco vai para a gravação em que você estava (ou a primeira)
    LaunchedEffect(Unit) {
        delay(120)
        if (vm.recs.isNotEmpty()) {
            if (vm.recs.none { it.id == vm.recFocusId }) vm.recFocusId = vm.recs.first().id
            delay(60)
            try {
                rowFocus.requestFocus()
            } catch (e: Exception) {
                // a lista ainda não estava na tela
            }
        }
    }
    // se a gravação em foco sumiu (apagada), passa o foco para a primeira
    LaunchedEffect(vm.recs.size) {
        if (vm.recs.isNotEmpty() && vm.recs.none { it.id == vm.recFocusId }) {
            vm.recFocusId = vm.recs.first().id
            delay(120)
            try {
                rowFocus.requestFocus()
            } catch (e: Exception) {
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 44.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "__new") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(text = "+ Agendar gravação", primary = vm.recs.isEmpty() && vm.scheds.isEmpty()) { vm.recordManual() }
                    Text(
                        "Dica: para um jogo, abra o jogo e toque em \"● Gravar\".",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (vm.recs.isEmpty() && vm.scheds.isEmpty()) {
                item(key = "__empty") {
                    Text(
                        "Nenhuma gravação ainda.\nAbra um canal ao vivo e escolha \"Gravar\", ou agende uma acima.\nA gravação continua com a tela apagada.",
                        color = Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            items(vm.scheds.filter { it.state != "started" }.distinctBy { "s" + it.id }, key = { "s" + it.id }) { sc ->
                val missed = sc.state == "missed"
                val asking = confirmSched == sc.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel.copy(alpha = 0.55f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⏰ " + sc.title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (missed) {
                                whenText(sc.startMs) + " · não gravou: " + sc.error.ifEmpty { "a TV estava desligada ou o app fechado." }
                            } else {
                                "Agendada · " + whenText(sc.startMs) + " até " + SportsTime.clock(sc.endMs, java.util.TimeZone.getDefault()) +
                                    " · " + Recorder.qualityLabel(sc.quality)
                            },
                            color = if (missed) ErrorColor else Warn,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TvButton(
                        text = if (asking) "Confirmar" else if (missed) "Remover" else "Cancelar agendamento",
                    ) {
                        if (!asking) {
                            confirmSched = sc.id
                        } else {
                            confirmSched = null
                            vm.cancelSched(sc.id)
                        }
                    }
                }
            }
            items(vm.recs.distinctBy { it.id }, key = { it.id }) { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel.copy(alpha = 0.55f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        val status = when {
                            r.recording -> "Gravando · " + lengthText(System.currentTimeMillis() - r.startedAt)
                            r.state == "error" -> r.error.ifEmpty { "Não deu certo" }
                            else -> "Gravado"
                        }
                        Text(
                            fmt.format(Date(r.startedAt)) + " · " + sizeText(r.size) + " · " + status,
                            color = if (r.recording) Accent else if (r.state == "error") ErrorColor else Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val askKey = r.id
                    val asking = confirmDelete == askKey
                    if (r.size > 0L) {
                        TvButton(text = "Assistir", primary = true, focusRequester = if (vm.recFocusId == r.id) rowFocus else null) {
                            vm.recFocusId = r.id
                            vm.playRecording(r)
                        }
                    }
                    if (r.recording) {
                        TvButton(text = "Tela preta") {
                            vm.recFocusId = r.id
                            vm.enterRecMode(r.id)
                        }
                    }
                    // um único botão que só troca o texto: assim o foco não se perde ao apertar
                    TvButton(
                        text = when {
                            r.recording && asking -> "Confirmar parar"
                            r.recording -> "Parar"
                            asking -> "Confirmar"
                            else -> "Apagar"
                        },
                        focusRequester = if (vm.recFocusId == r.id && r.size <= 0L) rowFocus else null,
                    ) {
                        vm.recFocusId = r.id
                        if (!asking) {
                            confirmDelete = askKey
                        } else {
                            confirmDelete = null
                            if (r.recording) vm.stopRecording(r.id) else vm.deleteRecording(r.id)
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Modo gravação: tela preta com brilho no mínimo, só um texto discreto. Mantém a TV acordada (ela não entra em espera
 * nem em protetor de tela), para a gravação não ser cortada. Qualquer botão mostra os comandos; Voltar também só os mostra.
 */
@Composable
fun RecordingModeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val recId = vm.recMode ?: return
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controls by remember { mutableStateOf(false) }
    var touch by remember { mutableIntStateOf(0) }
    var askStop by remember { mutableStateOf(false) }
    val rec = vm.recs.firstOrNull { it.id == recId }
    val running = rec?.recording == true
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // brilho mínimo e tela sempre acesa enquanto este modo está aberto
    DisposableEffect(Unit) {
        val w = ctx.findActivity()?.window
        val old = w?.attributes?.screenBrightness ?: -1f
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        w?.attributes = w?.attributes?.also { it.screenBrightness = 0.01f }
        onDispose {
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            w?.attributes = w?.attributes?.also { it.screenBrightness = old }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshRecs()
            now = System.currentTimeMillis()
            delay(2_000L)
        }
    }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // ainda não estava na tela
        }
    }
    // os comandos somem sozinhos depois de 10 s sem mexer
    LaunchedEffect(touch, controls) {
        if (controls) {
            delay(10_000L)
            controls = false
            askStop = false
        }
    }
    LaunchedEffect(controls) {
        if (controls) {
            delay(100)
            try {
                first.requestFocus()
            } catch (e: Exception) {
                // ainda não estava na tela
            }
        } else {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // ainda não estava na tela
            }
        }
    }
    BackHandler(enabled = true) {
        if (controls) controls = false else controls = true
    }
    // o texto anda um pouquinho a cada minuto, para não marcar a tela
    val shift = ((now / 60_000L) % 5).toInt()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (!controls && ev.type == KeyEventType.KeyDown) {
                    controls = true
                    true
                } else {
                    if (ev.type == KeyEventType.KeyDown) touch++
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(x = (shift * 14 - 28).dp, y = (shift * 8 - 16).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val dim = Color(0xFF6B6F7B)
            if (rec == null) {
                Text("Gravação não encontrada.", color = dim, fontSize = 16.sp)
            } else if (running) {
                Text("● Gravando", color = Color(0xFF8A2E3B), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text(rec.title, color = dim, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    fmt.format(Date(rec.startedAt)) + " às " + fmt.format(Date(rec.endsAt)),
                    color = dim, fontSize = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text("Faltam " + lengthText(rec.endsAt - now) + " · " + sizeText(rec.size), color = dim, fontSize = 14.sp)
            } else {
                Text(
                    if (rec.state == "error") "A gravação não deu certo." else "Gravação concluída",
                    color = Color(0xFF9AA0AE), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (rec.state == "error") rec.error else rec.title + " · " + sizeText(rec.size),
                    color = dim, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                if (!controls) {
                    Spacer(Modifier.height(10.dp))
                    Text("Aperte qualquer botão.", color = dim, fontSize = 13.sp)
                }
            }
            if (controls || (rec != null && !running)) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (running) {
                        TvButton(text = "Sair da tela preta", primary = true, focusRequester = first) { vm.exitRecMode() }
                        if (askStop) {
                            TvButton(text = "Confirmar parar") {
                                askStop = false
                                vm.stopRecording(recId)
                            }
                        } else {
                            TvButton(text = "Parar gravação") {
                                touch++
                                askStop = true
                            }
                        }
                        TvButton(text = "Desligar a TV depois") {
                            touch++
                            val i = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                ctx.startActivity(i)
                            } catch (e: Exception) {
                                // esta TV não tem tela de ajustes aberta para apps
                            }
                        }
                    } else {
                        TvButton(text = "Ver gravações", primary = true, focusRequester = first) {
                            vm.exitRecMode()
                            vm.selectTab(Tab.REC)
                        }
                        TvButton(text = "Voltar ao início") { vm.exitRecMode() }
                    }
                }
                if (running) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Para a TV desligar sozinha, ative o temporizador de desligamento nos ajustes dela.",
                        color = dim, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
            }
        }
    }
}
