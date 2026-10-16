package app.iptvtest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                App()
            }
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

    BackHandler(enabled = account != null && playing == null && vm.canGoBack) {
        vm.back()
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

    // a cada 30 s vê se algum jogo lembrado começa em 10 min (cobre alarmes perdidos, ex.: TV desligada)
    LaunchedEffect(account != null) {
        while (account != null) {
            vm.checkReminders(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        when {
            account == null -> LoginScreen(vm)
            // key(): ao trocar de praça (Globo SP → Globo Rio) o player é recriado com o novo canal
            playing != null -> key(playing.live?.channel?.key ?: playing.title) {
                PlayerScreen(
                    playing,
                    vm.user,
                    onExit = { vm.stopPlaying() },
                    onVariant = { b, v -> vm.playVariant(b, v) },
                    currentVariantKey = { b -> vm.currentVariantKey(b) },
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
            grid != null -> holder.SaveableStateProvider("grid|" + grid.name) {
                GridScreen(vm, grid)
            }
            else -> holder.SaveableStateProvider("tab|" + vm.tab.name) {
                TabScreen(vm)
            }
        }

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
                .background(Color(0xFF151416))
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
                .background(Color(0xDD000000))
                .border(1.5.dp, Accent, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
