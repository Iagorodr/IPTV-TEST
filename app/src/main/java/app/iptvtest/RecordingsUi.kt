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
            delay(2_000L)
        }
    }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopNav(vm)
        if (vm.recs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma gravação ainda.\nAbra um canal ao vivo e escolha \"Gravar\".\nA gravação continua com a tela apagada.",
                    color = Muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 44.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.recs, key = { it.id }) { r ->
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
                    if (r.size > 0L) TvButton(text = "Assistir", primary = true) { vm.playRecording(r) }
                    if (r.recording) {
                        TvButton(text = "Parar") { vm.stopRecording(r.id) }
                    } else if (confirmDelete == r.id) {
                        TvButton(text = "Confirmar") {
                            confirmDelete = null
                            vm.deleteRecording(r.id)
                        }
                    } else {
                        TvButton(text = "Apagar") { confirmDelete = r.id }
                    }
                }
            }
        }
    }
}
