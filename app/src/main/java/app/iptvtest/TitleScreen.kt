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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale

/** Cópias do título que combinam com o idioma e a qualidade escolhidos (sem cinema primeiro). */
private fun copiesOf(title: Title, leg: Boolean, k4: Boolean): List<Version> {
    val byLang = title.versions.filter { it.leg == leg }
    val byQuality = byLang.filter { it.is4k == k4 }
    val base = if (byQuality.isNotEmpty()) byQuality else byLang
    return base.sortedBy { if (it.cam) 1 else 0 }
}

private fun durationText(secs: Int): String {
    if (secs <= 0) return ""
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}

private fun epCode(ep: Episode): String = String.format(Locale.US, "T%d E%d", ep.season, ep.number)

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            color = Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(86.dp),
        )
        content()
    }
}

/**
 * Cartão do título (layout A): dublado, legendado e 4K ficam todos neste mesmo cartão;
 * a pessoa só escolhe o idioma/qualidade e aperta Assistir.
 */
@Composable
fun TitleScreen(vm: MainViewModel, title: Title) {
    val firstPlay = remember { FocusRequester() }
    val resume = vm.user.resume[title.key]
    // lê a revisão para a tela atualizar quando "minha lista" ou "continuar" mudarem
    val rev = vm.dataRev

    // ---- escolhas (guardadas para sobreviver à ida e volta do player)
    var leg by rememberSaveable(title.key) {
        mutableStateOf(
            run {
                val rv = resume?.let { r -> title.versions.firstOrNull { it.id == r.versionId } }
                if (rv != null) rv.leg else title.versions.none { !it.leg }
            },
        )
    }
    var k4 by rememberSaveable(title.key) {
        mutableStateOf(
            run {
                val rv = resume?.let { r -> title.versions.firstOrNull { it.id == r.versionId } }
                if (rv != null) rv.is4k else title.versions.none { !it.is4k }
            },
        )
    }
    var copyIdx by rememberSaveable(title.key) { mutableIntStateOf(0) }
    var zoom by rememberSaveable(title.key) { mutableStateOf(resume?.zoom ?: false) }
    var season by rememberSaveable(title.key) { mutableIntStateOf(-1) }

    val hasDub = title.versions.any { !it.leg }
    val hasLeg = title.versions.any { it.leg }
    val legEff = if (title.versions.any { it.leg == leg }) leg else !leg
    val langVersions = title.versions.filter { it.leg == legEff }
    val has4kHere = langVersions.any { it.is4k }
    val hasStdHere = langVersions.any { !it.is4k }
    val k4Eff = if (langVersions.any { it.is4k == k4 }) k4 else !k4
    val copies = copiesOf(title, legEff, k4Eff)
    val chosen: Version? = if (copies.isEmpty()) null else copies[copyIdx.coerceIn(0, copies.lastIndex)]

    LaunchedEffect(title.key, chosen?.id) {
        if (chosen != null) vm.ensureInfo(title, chosen)
    }
    LaunchedEffect(title.key) {
        // o foco volta para o botão principal; qualquer memória de poster antigo não vale mais aqui
        FocusMemory.pending = null
        delay(200)
        try {
            firstPlay.requestFocus()
        } catch (e: Exception) {
            // a tela ainda não estava pronta
        }
    }

    val info = if (!title.series && chosen != null) vm.vodInfos[chosen.id] else null
    val epState = if (title.series && chosen != null) vm.episodes[chosen.id] else null
    val eps = epState?.episodes ?: emptyList()
    val plot = (info?.plot ?: "").ifEmpty { title.plot }

    // ---- série: episódio onde parou / primeiro episódio
    val resumeSame = resume != null && chosen != null && resume.versionId == chosen.id && rev >= 0
    val resEp: Episode? = if (title.series && resumeSame && resume != null) eps.firstOrNull { it.id == resume.epId } else null
    val firstEp: Episode? = run {
        val regular = eps.filter { it.season > 0 }
        val pool = if (regular.isEmpty()) eps else regular
        pool.minWithOrNull(compareBy<Episode>({ it.season }, { it.number }))
    }
    val seasons = eps.map { it.season }.distinct().sorted()
    val seasonSel = if (seasons.contains(season)) {
        season
    } else {
        resEp?.season ?: (if (seasons.contains(1)) 1 else (seasons.firstOrNull() ?: 1))
    }
    val seasonEps = eps.filter { it.season == seasonSel }.sortedBy { it.number }

    val similar = remember(title.key) {
        val g = title.genres.firstOrNull { it != "Nacionais" } ?: title.genres.firstOrNull()
        if (g == null) {
            emptyList()
        } else {
            vm.allTitles
                .filter { it.series == title.series && it.key != title.key && it.genres.contains(g) && !it.onlyCam }
                .sortedByDescending { it.score() }
                .take(30)
        }
    }

    val watchLabel: String = when {
        chosen == null -> "Indisponível"
        title.series -> when {
            resEp != null -> "Continuar " + epCode(resEp)
            firstEp != null -> "Assistir " + epCode(firstEp)
            epState != null && epState.loading -> "Carregando episódios..."
            else -> "Assistir"
        }
        resumeSame && resume != null && resume.posMs > 30_000L -> "Continuar de " + formatClock(resume.posMs)
        else -> "Assistir"
    }

    fun play() {
        val v = chosen ?: return
        if (title.series) {
            val ep = resEp ?: firstEp ?: return
            vm.playEpisode(title, v, ep, zoom)
        } else {
            vm.playMovie(title, v, zoom)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // fundo suave com a própria capa
        val img = title.image
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.18f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color(0xF00D0D0F), Color(0xB30D0D0F)))),
        )

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "head") {
                Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp)) {
                    TvButton(text = "Voltar") { vm.back() }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(170.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PanelAlt),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (img != null) {
                                AsyncImage(
                                    model = img,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(title.name.take(1).uppercase(), color = Muted, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(28.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.name,
                                color = TextMain,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            val meta = ArrayList<String>()
                            meta.add(if (title.series) "Série" else "Filme")
                            if (title.year.isNotEmpty()) meta.add(title.year)
                            val dur = durationText(info?.durationSecs ?: 0)
                            if (dur.isNotEmpty()) meta.add(dur)
                            val rate = if ((info?.rating ?: 0f) > 0f) info?.rating ?: 0f else title.rating
                            if (rate > 0f) meta.add("★ " + String.format(Locale.US, "%.1f", rate))
                            if (title.genres.isNotEmpty()) meta.add(title.genres.take(3).joinToString(", "))
                            Text(meta.joinToString(" · "), color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (title.platforms.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(title.platforms.take(3).joinToString(" · "), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            if (plot.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(plot, color = TextMain, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            } else if (!title.series && info == null) {
                                Spacer(Modifier.height(8.dp))
                                Text("Carregando sinopse...", color = Muted, fontSize = 13.sp)
                            }
                            if (info != null && (info.cast.isNotEmpty() || info.director.isNotEmpty())) {
                                Spacer(Modifier.height(4.dp))
                                val extra = ArrayList<String>()
                                if (info.director.isNotEmpty()) extra.add("Direção: " + info.director)
                                if (info.cast.isNotEmpty()) extra.add("Elenco: " + info.cast.take(110))
                                Text(extra.joinToString("   "), color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }

                            Spacer(Modifier.height(10.dp))
                            if (hasDub && hasLeg) {
                                OptionRow("Idioma") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ChipItem(
                                            text = if (title.national) "Original" else "Dublado",
                                            selected = !legEff,
                                        ) {
                                            leg = false
                                            copyIdx = 0
                                        }
                                        ChipItem(text = "Legendado", selected = legEff) {
                                            leg = true
                                            copyIdx = 0
                                        }
                                    }
                                }
                            } else if (hasLeg && !hasDub) {
                                OptionRow("Idioma") { Text("Só legendado", color = TextMain, fontSize = 14.sp) }
                            }
                            if (has4kHere && hasStdHere) {
                                OptionRow("Qualidade") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ChipItem(text = "Padrão", selected = !k4Eff) {
                                            k4 = false
                                            copyIdx = 0
                                        }
                                        ChipItem(text = "4K", selected = k4Eff) {
                                            k4 = true
                                            copyIdx = 0
                                        }
                                    }
                                }
                            } else if (has4kHere) {
                                OptionRow("Qualidade") { Text("4K", color = TextMain, fontSize = 14.sp) }
                            }
                            if (copies.size > 1) {
                                OptionRow("Cópia") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        for ((i, v) in copies.withIndex()) {
                                            ChipItem(
                                                text = "Cópia ${i + 1}" + (if (v.cam) " (cinema)" else ""),
                                                selected = copyIdx.coerceIn(0, copies.lastIndex) == i,
                                            ) { copyIdx = i }
                                        }
                                    }
                                }
                            }
                            if (chosen != null && chosen.cam) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Gravação de cinema: a imagem e o som podem ter qualidade menor.",
                                    color = Warn,
                                    fontSize = 12.sp,
                                )
                            }
                            OptionRow("Tela") {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ChipItem(text = "Ajustar", selected = !zoom) { zoom = false }
                                    ChipItem(text = "Preencher", selected = zoom) { zoom = true }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TvButton(text = watchLabel, primary = true, focusRequester = firstPlay) { play() }
                                TvButton(text = if (vm.inMyList(title.key)) "✓ Favoritos" else "+ Favoritos") {
                                    vm.toggleMyList(title.key)
                                }
                            }
                        }
                    }
                }
            }

            if (title.series) {
                item(key = "eps-head") {
                    Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp)) {
                        Text("Episódios", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(8.dp))
                        if (seasons.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(seasons, key = { it }) { s ->
                                    ChipItem(text = if (s == 0) "Especiais" else "Temporada $s", selected = s == seasonSel) { season = s }
                                }
                            }
                        }
                        if (epState != null && epState.loading) {
                            Spacer(Modifier.height(10.dp))
                            CircularProgressIndicator(color = Accent)
                        }
                        val err = epState?.error
                        if (err != null && eps.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(err, color = ErrorColor, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            TvButton(text = "Tentar de novo") {
                                if (chosen != null) vm.ensureInfo(title, chosen)
                            }
                        }
                    }
                }
                items(seasonEps, key = { "ep|" + it.id }) { ep ->
                    val v = chosen
                    FocusBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 3.dp),
                        focusScale = 1.01f,
                        onClick = { if (v != null) vm.playEpisode(title, v, ep, zoom) },
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (focused) PanelAlt else Panel)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "E" + ep.number,
                                color = Accent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(
                                text = ep.title.ifEmpty { "Episódio " + ep.number },
                                color = TextMain,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (resEp != null && resEp.id == ep.id) {
                                Spacer(Modifier.width(12.dp))
                                Text("parou aqui", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (similar.isNotEmpty()) {
                item(key = "similar") {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        ShelfRow(
                            shelf = Shelf("Parecidos", similar),
                            onOpen = { vm.open(it) },
                            onSeeAll = null,
                        )
                    }
                }
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = if (ms < 0L) 0L else ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
