package app.iptvtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.TimeZone

private const val MIN = 60_000L
private const val HOUR = 3_600_000L

fun durationText(ms: Long): String {
    val m = (ms / MIN).coerceAtLeast(0L)
    return if (m >= 60) (m / 60).toString() + " h" + (if (m % 60 > 0) " " + (m % 60) + " min" else "") else "$m min"
}

/** "Hoje 20:00" / "Amanhã 20:00" / "Sáb 24/10 20:00". */
fun whenText(ms: Long, now: Long = System.currentTimeMillis()): String {
    val tz = TimeZone.getDefault()
    return SportsTime.dayLabel(ms, now, tz) + " " + SportsTime.clock(ms, tz)
}

@Composable
private fun StepRow(
    label: String,
    value: String,
    minus: List<Pair<String, () -> Unit>>,
    plus: List<Pair<String, () -> Unit>>,
    focus: FocusRequester? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        minus.forEachIndexed { i, (t, f) ->
            TvButton(text = t, minWidth = 72.dp, focusRequester = if (i == 0) focus else null) { f() }
        }
        Text(
            value,
            color = TextMain,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(190.dp),
        )
        for ((t, f) in plus) TvButton(text = t, minWidth = 72.dp) { f() }
    }
}

/** Tela de agendar uma gravação: dia, início, fim e qualidade. */
@Composable
fun ScheduleDialog(vm: MainViewModel, init: SchedInit) {
    val ctx = LocalContext.current
    val tz = remember { TimeZone.getDefault() }
    var start by remember(init) { mutableLongStateOf(init.startMs) }
    var end by remember(init) { mutableLongStateOf(init.endMs) }
    var quality by remember(init) { mutableIntStateOf(Recorder.quality(ctx)) }
    var done by remember(init) { mutableStateOf<String?>(null) }
    var error by remember(init) { mutableStateOf<String?>(null) }
    val firstFocus = remember(init) { FocusRequester() }
    val now = System.currentTimeMillis()
    val freeGb = remember(init) {
        try { RecStore.dir(ctx).usableSpace / 1e9 } catch (e: Exception) { 0.0 }
    }
    val hours = ((end - start).coerceAtLeast(0L)) / HOUR.toDouble()
    val estGb = hours * 2.0 * (if (quality == 1) 0.5 else if (quality == 2) 0.25 else 1.0)

    LaunchedEffect(init, done) {
        delay(80)
        try {
            firstFocus.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }

    Dialog(
        onDismissRequest = { vm.schedInit = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, PanelAlt, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            if (done != null) {
                Text("Agendado!", color = Good, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(done ?: "", color = TextMain, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Deixe a TV (ou a box) ligada e com internet. O app não precisa ficar aberto na tela, mas se você " +
                        "fechar o app à força ou a TV desligar, a gravação não acontece.",
                    color = Warn,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(text = "Ver agendadas", primary = true, focusRequester = firstFocus) {
                        vm.schedInit = null
                        vm.selectTab(Tab.REC)
                    }
                    TvButton(text = "Fechar") { vm.schedInit = null }
                }
                return@Column
            }

            Text("Agendar gravação", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(init.title, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
            Text("Canal: " + init.channelName, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (init.fromGame) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Confira se o jogo realmente passa nesse canal. O app pode errar: se o canal estiver passando outra coisa, " +
                        "a gravação sai com o programa errado.",
                    color = Warn,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepRow(
                    label = "Dia",
                    value = SportsTime.dayLabel(start, now, tz),
                    minus = listOf("◀ dia" to {
                        if (end - 24 * HOUR > now) {
                            start -= 24 * HOUR
                            end -= 24 * HOUR
                        }
                    }),
                    plus = listOf("dia ▶" to {
                        start += 24 * HOUR
                        end += 24 * HOUR
                    }),
                    focus = if (init.fromGame) null else firstFocus,
                )
                StepRow(
                    label = "Início",
                    value = SportsTime.clock(start, tz),
                    minus = listOf("−1 h" to { start -= HOUR }, "−5 min" to { start -= 5 * MIN }),
                    plus = listOf("+5 min" to { start += 5 * MIN }, "+1 h" to { start += HOUR }),
                )
                StepRow(
                    label = "Fim",
                    value = SportsTime.clock(end, tz) + (if (SportsTime.dayNumber(end, tz) != SportsTime.dayNumber(start, tz)) " (+1 dia)" else ""),
                    minus = listOf("−1 h" to { end -= HOUR }, "−5 min" to { end -= 5 * MIN }),
                    plus = listOf("+5 min" to { end += 5 * MIN }, "+1 h" to { end += HOUR }),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Qualidade", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp))
                for (q in 0..2) {
                    ChipItem(text = Recorder.qualityLabel(q), selected = quality == q) { quality = q }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Média e Baixa só funcionam em canais que oferecem mais de uma versão da imagem; nos outros, grava como o canal manda.",
                color = Muted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Duração: " + durationText(end - start) + " · espaço estimado: até " + String.format(Locale.US, "%.1f", estGb) +
                    " GB · livre no aparelho: " + String.format(Locale.US, "%.1f", freeGb) + " GB",
                color = if (estGb > freeGb * 0.8) ErrorColor else TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val err = error
            if (err != null) {
                Spacer(Modifier.height(6.dp))
                Text(err, color = ErrorColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(text = "Agendar", primary = true, focusRequester = if (init.fromGame) firstFocus else null) {
                    val t = System.currentTimeMillis()
                    when {
                        end <= start + MIN -> error = "O fim precisa ser depois do início."
                        end <= t -> error = "Esse horário já passou."
                        end - start > 6 * HOUR -> error = "O máximo é 6 horas por gravação."
                        else -> {
                            Recorder.setQuality(ctx, quality)
                            val msg = vm.addSchedule(init, start, end, quality)
                            if (msg == null) {
                                error = null
                                done = init.title + "\n" + whenText(start) + " até " + SportsTime.clock(end, tz) + " · " + Recorder.qualityLabel(quality)
                            } else {
                                error = msg
                            }
                        }
                    }
                }
                TvButton(text = "Cancelar") { vm.schedInit = null }
            }
        }
    }
}

/** Faixa embaixo da tela enquanto a pessoa escolhe o canal que quer gravar. */
@Composable
fun PickBanner(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = label + "  ·  Voltar cancela",
            color = TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AccentDark)
                .border(1.dp, Accent, RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

/** Aviso rápido (sem botão, não rouba o foco): ex. "Lista atualizada". */
@Composable
fun ListNotice(text: String) {
    PickBanner2(text)
}

@Composable
private fun PickBanner2(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = text,
            color = TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AccentDark)
                .border(1.dp, Accent, RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

/** "Há versão nova": atualizar agora (baixa e abre o instalador) ou depois. */
@Composable
fun UpdateDialog(vm: MainViewModel, u: UpdateInfo) {
    val first = remember(u.code) { FocusRequester() }
    LaunchedEffect(u.code, vm.updateState) {
        delay(80)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    Dialog(
        onDismissRequest = { if (vm.updateState != "baixando") vm.updateDismissed = true },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, PanelAlt, RoundedCornerShape(16.dp))
                .padding(26.dp),
        ) {
            Text("Versão nova disponível", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Você tem o build " + vm.installedBuild() + " · novo: build " + u.code,
                color = Muted,
                fontSize = 13.sp,
            )
            if (u.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("O que mudou", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(u.notes, color = TextMain, fontSize = 14.sp)
            }
            if (vm.anyRecording()) {
                Spacer(Modifier.height(10.dp))
                Text("Há uma gravação em andamento: ela seria cortada. Atualize depois que terminar.", color = Warn, fontSize = 13.sp)
            }
            val msg = vm.updateMsg
            if (msg != null) {
                Spacer(Modifier.height(10.dp))
                Text(msg, color = ErrorColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            if (vm.updateState == "baixando") {
                Text("Baixando... " + vm.updateProgress + "%", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PanelAlt),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(vm.updateProgress / 100f)
                            .fillMaxHeight()
                            .background(Accent),
                    )
                }
                Spacer(Modifier.height(14.dp))
                TvButton(text = "Cancelar", focusRequester = first) { vm.cancelUpdate() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(text = "Atualizar agora", primary = true, focusRequester = first) { vm.startUpdate() }
                    TvButton(text = "Depois") { vm.updateDismissed = true }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ao terminar de baixar, o Android pede para confirmar a instalação. O app fecha e você abre de novo.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** Escolha rápida de países/idiomas: marque os que quer ver (ou "Mostrar tudo") e toque em Aplicar. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CountryDialog(vm: MainViewModel) {
    val stats = remember(vm.catalog) { vm.countryStats() }
    var sel by remember { mutableStateOf(vm.user.countryFilter.toSet()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    Dialog(
        onDismissRequest = { vm.countryDialog = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(820.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, PanelAlt, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text("Que países e idiomas você quer ver?", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Vale para filmes, séries e canais. Dica: escolha 2 ou 3 e a lista fica bem mais leve. O que não tem país indicado aparece sempre.",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipItem(text = "Mostrar tudo", selected = sel.isEmpty(), focusRequester = first) { sel = emptySet() }
                for ((code, n) in stats.entries.sortedByDescending { it.value.first + it.value.second }) {
                    val on = sel.contains(code)
                    ChipItem(
                        text = (if (on) "✓ " else "") + Countries.label(code) + " · " + n.first + " títulos, " + n.second + " canais",
                        selected = on,
                    ) {
                        sel = if (on) sel - code else sel + code
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(text = "Aplicar", primary = true) {
                    vm.countryDialog = false
                    vm.setCountryFilter(sel)
                }
                TvButton(text = "Cancelar") { vm.countryDialog = false }
            }
        }
    }
}

/** Aviso: o relógio da TV/box está errado. */
@Composable
fun ClockDialog(vm: MainViewModel) {
    val tz = remember { TimeZone.getDefault() }
    val real = vm.clockReal
    val now = System.currentTimeMillis()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // o botão ainda não estava na tela
        }
    }
    fun full(ms: Long): String = SportsTime.shortDate(ms, tz) + "/" + java.util.Calendar.getInstance(tz).also { it.timeInMillis = ms }.get(java.util.Calendar.YEAR) +
        " " + SportsTime.clock(ms, tz)
    Dialog(
        onDismissRequest = { vm.clockDismissed = true },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, Warn, RoundedCornerShape(16.dp))
                .padding(26.dp),
        ) {
            Text("O relógio deste aparelho está errado", color = Warn, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text("O aparelho mostra: " + full(now), color = TextMain, fontSize = 15.sp)
            Text("Hora certa: " + full(real), color = Good, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Com a data errada, jogos, capas, atualização do app e gravações agendadas deixam de funcionar. " +
                    "Nas configurações da TV ou da box, abra Data e hora e ligue \"Data e hora automáticas\" (e confira o fuso). Depois reabra o app.",
                color = TextMain,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            TvButton(text = "Entendi", primary = true, focusRequester = first) { vm.clockDismissed = true }
        }
    }
}
