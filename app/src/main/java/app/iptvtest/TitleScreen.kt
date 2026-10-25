package app.iptvtest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
private fun copiesOf(pool: List<Version>, leg: Boolean, k4: Boolean): List<Version> {
    val byLang = pool.filter { it.leg == leg }
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

/**
 * Ficha do título: dublado, legendado e 4K ficam todos neste mesmo cartão; a pessoa só escolhe o idioma/qualidade e
 * aperta Assistir. Séries mostram os episódios em cartões na horizontal.
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
    // idioma/país da cópia ("" = todos): vem das categorias do provedor (BR, PT, FR...)
    var lang by rememberSaveable(title.key) {
        mutableStateOf(resume?.let { r -> title.versions.firstOrNull { it.id == r.versionId }?.cc } ?: "")
    }
    var copyIdx by rememberSaveable(title.key) { mutableIntStateOf(0) }
    var zoom by rememberSaveable(title.key) { mutableStateOf(resume?.zoom ?: false) }
    var season by rememberSaveable(title.key) { mutableIntStateOf(-1) }

    val ccs = title.versions.map { it.cc }.distinct()
    val langOptions = ccs.sortedWith(compareBy({ it.isEmpty() }, { Countries.label(it) }))
    val showLang = ccs.size > 1
    val langEff = if (lang.isNotEmpty() && ccs.contains(lang)) lang else ""
    val pool = if (showLang && langEff.isNotEmpty()) title.versions.filter { it.cc == langEff } else title.versions
    val hasDub = pool.any { !it.leg }
    val hasLeg = pool.any { it.leg }
    val legEff = if (pool.any { it.leg == leg }) leg else !leg
    val langVersions = pool.filter { it.leg == legEff }
    val has4kHere = langVersions.any { it.is4k }
    val hasStdHere = langVersions.any { !it.is4k }
    val k4Eff = if (langVersions.any { it.is4k == k4 }) k4 else !k4
    val copies = copiesOf(pool, legEff, k4Eff)
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
    val tm by rememberTmdb(title)
    val plot = (info?.plot ?: "").ifEmpty { title.plot }.ifEmpty { tm?.overview ?: "" }

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

    val img = tm?.backdrop ?: title.image
    Box(modifier = Modifier.fillMaxSize()) {
        // a capa do título ocupa o fundo, à direita, e se dissolve no degradê (sem capa: o sol da marca)
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                alpha = 0.8f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight(0.82f)
                    .fillMaxWidth(0.58f)
                    .fadeEdges(left = 0.55f, bottom = 0.4f),
            )
        } else {
            SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.8f, sunY = 0.3f, sunR = 0.22f, mountains = false)
        }

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "head") {
                Column(modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 26.dp, bottom = 10.dp)) {
                    TvButton(text = "‹ Voltar") { vm.back() }
                    Spacer(Modifier.height(14.dp))
                    Column(modifier = Modifier.width(520.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (title.series) "SÉRIE" else "FILME",
                                color = Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp,
                            )
                            if (title.year.isNotEmpty()) {
                                Spacer(Modifier.width(10.dp))
                                Text(title.year, color = TextMain, fontSize = 13.sp)
                            }
                            if (title.series && seasons.isNotEmpty()) {
                                Spacer(Modifier.width(10.dp))
                                Text(if (seasons.size == 1) "1 temporada" else "${seasons.size} temporadas", color = TextMain, fontSize = 13.sp)
                            }
                            val dur = durationText(info?.durationSecs ?: 0)
                            if (dur.isNotEmpty()) {
                                Spacer(Modifier.width(10.dp))
                                Text(dur, color = TextMain, fontSize = 13.sp)
                            }
                            if (title.has4k) {
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
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = title.name.uppercase(),
                            color = TextMain,
                            fontSize = 32.sp,
                            lineHeight = 35.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        val rate = if ((info?.rating ?: 0f) > 0f) info?.rating ?: 0f else title.rating
                        val ratingText = if (rate > 0f) "★ " + String.format(Locale.US, "%.1f", rate).replace('.', ',') else ""
                        val meta = listOf(ratingText, title.genres.take(3).joinToString(" · ")).filter { it.isNotEmpty() }.joinToString("   ")
                        if (meta.isNotEmpty()) {
                            Text(meta, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (title.platforms.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(title.platforms.take(3).joinToString(" · "), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        if (plot.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(plot, color = TextMain.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        } else if (!title.series && info == null) {
                            Spacer(Modifier.height(6.dp))
                            Text("Carregando sinopse...", color = Muted, fontSize = 13.sp)
                        }
                        if (info != null && (info.cast.isNotEmpty() || info.director.isNotEmpty())) {
                            Spacer(Modifier.height(4.dp))
                            val extra = ArrayList<String>()
                            if (info.director.isNotEmpty()) extra.add("Direção: " + info.director)
                            if (info.cast.isNotEmpty()) extra.add("Elenco: " + info.cast.take(110))
                            Text(extra.joinToString("   "), color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvButton(text = "▶ " + watchLabel, primary = true, focusRequester = firstPlay) { play() }
                        FocusBox(shape = CircleShape, focusScale = 1.06f, onClick = { vm.toggleMyList(title.key) }) { focused ->
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(if (focused) PanelAlt else Color(0x33FFF3EC)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (vm.inMyList(title.key)) "✓" else "+",
                                    color = TextMain,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (hasDub && hasLeg) {
                            Spacer(Modifier.width(6.dp))
                            ChipItem(text = if (title.national) "Original" else "Dublado", selected = !legEff) {
                                leg = false
                                copyIdx = 0
                            }
                            ChipItem(text = "Legendado", selected = legEff) {
                                leg = true
                                copyIdx = 0
                            }
                        } else if (hasLeg && !hasDub) {
                            Spacer(Modifier.width(6.dp))
                            Text("Só legendado", color = Muted, fontSize = 14.sp)
                        }
                    }

                    // idioma/país da cópia: só quando o título existe em mais de um
                    if (showLang) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                            item(key = "l-all") {
                                ChipItem(text = "Idioma: todos", selected = langEff.isEmpty()) { lang = ""; copyIdx = 0 }
                            }
                            items(langOptions, key = { "l|$it" }) { cc ->
                                ChipItem(text = if (cc.isEmpty()) "Outros" else Countries.label(cc), selected = langEff == cc) {
                                    lang = cc
                                    copyIdx = 0
                                }
                            }
                        }
                    }

                    // qualidade, cópia e tela: só aparecem as que fazem sentido para este título
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        if (has4kHere && hasStdHere) {
                            item(key = "q-std") { ChipItem(text = "Padrão", selected = !k4Eff) { k4 = false; copyIdx = 0 } }
                            item(key = "q-4k") { ChipItem(text = "4K", selected = k4Eff) { k4 = true; copyIdx = 0 } }
                        } else if (has4kHere) {
                            item(key = "q-only") { Text("Só 4K", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)) }
                        }
                        if (copies.size > 1) {
                            items(copies.size, key = { "c|$it" }) { i ->
                                val v = copies[i]
                                ChipItem(
                                    text = "Cópia ${i + 1}" + (if (v.cam) " (cinema)" else ""),
                                    selected = copyIdx.coerceIn(0, copies.lastIndex) == i,
                                ) { copyIdx = i }
                            }
                        }
                        item(key = "t-fit") { ChipItem(text = "Tela: ajustar", selected = !zoom) { zoom = false } }
                        item(key = "t-fill") { ChipItem(text = "Tela: preencher", selected = zoom) { zoom = true } }
                    }
                    if (chosen != null && chosen.cam) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Gravação de cinema: a imagem e o som podem ter qualidade menor.",
                            color = Warn,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (title.series) {
                item(key = "eps-head") {
                    Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)) {
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
                if (seasonEps.isNotEmpty()) {
                    item(key = "eps") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(seasonEps, key = { "ep|" + it.id }) { ep ->
                                val here = resEp != null && resEp.id == ep.id
                                val frac = if (here && resume != null && resume.durMs > 0L) resume.posMs.toFloat() / resume.durMs.toFloat() else null
                                EpisodeCard(ep = ep, resumeHere = here, progress = frac) {
                                    val v = chosen
                                    if (v != null) vm.playEpisode(title, v, ep, zoom)
                                }
                            }
                        }
                    }
                }
            }

            if (similar.isNotEmpty()) {
                item(key = "similar") {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
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

private val EP_GRADIENTS = listOf(
    listOf(Color(0xFF34415F), Color(0xFF1F2A44)),
    listOf(Color(0xFF541A2E), Color(0xFF2A1626)),
    listOf(Color(0xFF3A3558), Color(0xFF5A2038)),
)

/** Cartão de episódio: faixa colorida com o número, e o título embaixo. O episódio onde parou fica em salmão com a barra de progresso. */
@Composable
private fun EpisodeCard(ep: Episode, resumeHere: Boolean, progress: Float?, onClick: () -> Unit) {
    Column(modifier = Modifier.width(172.dp)) {
        FocusBox(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), focusScale = 1.04f, onClick = onClick) { _ ->
            val colors = if (resumeHere) listOf(Accent, Red) else EP_GRADIENTS[Math.floorMod(ep.number, EP_GRADIENTS.size)]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.linearGradient(colors)),
            ) {
                Text(
                    text = "E" + ep.number,
                    color = TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99161E2F))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                if (progress != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x66FFFFFF)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                                .fillMaxHeight()
                                .background(Accent),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = ep.title.ifEmpty { "Episódio " + ep.number },
            color = TextMain,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (resumeHere) "parou aqui" else "Episódio " + ep.number,
            color = if (resumeHere) Accent else Muted,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

private fun formatClock(ms: Long): String {
    val total = if (ms < 0L) 0L else ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
