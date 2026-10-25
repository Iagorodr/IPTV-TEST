@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.iptvtest

import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private class TrackOption(
    val label: String,
    val selected: Boolean,
    val supported: Boolean,
    val onSelect: () -> Unit,
)

/** Medições da fonte que está tocando agora (viram uma nota de estabilidade ao trocar/sair). */
private class LiveSession {
    var sourceId: String? = null
    var startedAt: Long = 0L
    var ready: Boolean = false
    var startupMs: Long = 0L
    var watchedMs: Long = 0L
    var stalledMs: Long = 0L
    var stalls: Int = 0
    var inStall: Boolean = false
    var curStallMs: Long = 0L
    var retries: Int = 0
    var lastSwitchAt: Long = 0L
    val stallTimes = ArrayList<Long>()
    val tried = HashSet<String>()

    fun reset(id: String) {
        sourceId = id
        startedAt = SystemClock.elapsedRealtime()
        ready = false
        startupMs = 0L
        watchedMs = 0L
        stalledMs = 0L
        stalls = 0
        inStall = false
        curStallMs = 0L
        retries = 0
        stallTimes.clear()
    }
}

private fun liveItem(url: String): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                // pode desacelerar um pouquinho para encher a reserva, nunca acelera (acelerar esvazia a reserva)
                .setMinPlaybackSpeed(0.97f)
                .setMaxPlaybackSpeed(1.0f)
                .build(),
        )
        .build()

@Composable
fun PlayerScreen(
    req: PlaybackRequest,
    user: UserData,
    onExit: () -> Unit,
    onVariant: (Channel, Variant) -> Unit = { _, _ -> },
    currentVariantKey: (Channel) -> String = { "" },
    onRecord: (String, String, String, Int) -> String? = { _, _, _, _ -> null },
    recordingId: (String) -> String? = { null },
    onStopRecord: (String) -> Unit = {},
    onRecMode: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveInfo = req.live
    val channel = liveInfo?.channel
    val isLive = req.isLive && liveInfo != null && channel != null
    // lê estado da ViewModel: o botão "Próximo episódio" aparece sozinho quando a lista de episódios termina de carregar
    val nextEp = if (isLive) null else req.next?.invoke()
    // marca (Globo...) tocando uma praça: favoritar vale para a marca toda
    val brand = liveInfo?.brand
    val favKey = brand?.key ?: channel?.key

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(if (isLive) 10_000 else 15_000)
            .setReadTimeoutMs(if (isLive) 12_000 else 30_000)
        val load = if (isLive) {
            // canal ao vivo: reserva grande (até 50 s) para aguentar oscilação da internet sem travar
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 3_000, 6_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 60_000, 2_500, 5_000)
                .build()
        }
        val player = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http)))
            .setLoadControl(load)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguages("pt", "por")
            .build()
        player
    }

    val session = remember { LiveSession() }

    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableStateOf(req.zoom) }
    var panel by remember { mutableStateOf<String?>(null) }

    // ---- canal ao vivo
    var ranked by remember { mutableStateOf<List<Source>>(if (channel != null) user.rank(channel) else emptyList()) }
    var current by remember { mutableStateOf<Source?>(null) }
    var pinnedId by remember { mutableStateOf<String?>(if (channel != null) user.pinnedSource(channel.key) else null) }
    var note by remember { mutableStateOf<String?>(null) }
    var recQ by remember { mutableIntStateOf(Recorder.quality(context)) }
    var recAskStop by remember { mutableStateOf(false) }
    var recActive by remember { mutableStateOf(channel != null && recordingId(channel.key) != null) }
    var testing by remember { mutableStateOf(false) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var autoSwitch by remember { mutableStateOf(user.autoSwitch) }
    var stabilityFirst by remember { mutableStateOf(user.stabilityFirst) }
    var favorite by remember { mutableStateOf(favKey != null && user.favChannels.contains(favKey)) }
    var bestAfterTest by remember { mutableStateOf(false) }
    val testResults = remember { mutableStateMapOf<String, ProbeResult>() }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testPrev by remember { mutableStateOf<Source?>(null) }

    // ---- voltar no tempo (catch-up). tsStart = horário (relógio real) do ponto em que o vídeo começou; 0 = ao vivo
    var tsStart by remember { mutableLongStateOf(0L) }
    var tsVariant by remember { mutableIntStateOf(0) }
    var tsSrc by remember { mutableStateOf<Source?>(null) }
    val tsTried = remember { mutableStateListOf<String>() }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    // ------------------------------------------------------------------ fontes de canal

    /** Guarda a nota desta sessão (kind 0 = normal, 1 = erro, 2 = travou) na memória de estabilidade. */
    fun recordSession(kind: Int) {
        val id = session.sourceId ?: return
        val watched = session.watchedMs
        val minutes = watched / 60000f
        when (kind) {
            1 -> user.record(id, 0.1f, session.startupMs, 0f, session.stalls, minutes)
            2 -> user.record(id, 0.25f, session.startupMs, 0f, session.stalls, minutes)
            else -> if (watched >= 90_000L) {
                val perMin = session.stalls / maxOf(minutes, 0.5f)
                val ratio = session.stalledMs.toFloat() / (watched + session.stalledMs).toFloat()
                val r = (1f - perMin * 0.35f - ratio * 3f).coerceIn(0.05f, 1f)
                user.record(id, r, session.startupMs, 0f, session.stalls, minutes)
            }
        }
    }

    fun playSource(s: Source, why: String?) {
        val info = liveInfo ?: return
        session.reset(s.id)
        current = s
        tsStart = 0L
        note = why
        error = null
        buffering = true
        exo.setMediaItem(liveItem(info.urlOf(s)))
        exo.prepare()
        exo.playWhenReady = true
    }

    /** Toca o canal a partir de [startMs] (no passado), com a fonte [src]. [variant] escolhe qual formato de link tentar. */
    fun playTimeshift(startMs: Long, src: Source?, variant: Int = 0): Boolean {
        val info = liveInfo ?: return false
        val ts = info.timeshift ?: return false
        val s = src ?: current?.takeIf { it.arch > 0 } ?: ranked.firstOrNull { it.arch > 0 } ?: return false
        val nowMs = System.currentTimeMillis()
        val mins = (((nowMs - startMs) / 60_000L).toInt() + 20).coerceIn(10, 24 * 60)
        val urls = ts(s, startMs, mins)
        if (variant >= urls.size) return false
        tsStart = startMs
        tsVariant = variant
        tsSrc = s
        error = null
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(urls[variant]))
        exo.prepare()
        exo.playWhenReady = true
        return true
    }

    /** Anda [deltaMin] minutos para trás (negativo) ou para a frente. Chegando perto do agora, volta ao vivo. */
    fun shift(deltaMin: Int) {
        val nowMs = System.currentTimeMillis()
        val base = if (tsStart > 0L) tsStart + exo.currentPosition else nowMs
        val target = base + deltaMin * 60_000L
        if (target >= nowMs - 45_000L) {
            val c = current ?: ranked.firstOrNull() ?: return
            playSource(c, "Ao vivo.")
            return
        }
        val src = tsSrc ?: current?.takeIf { it.arch > 0 } ?: ranked.firstOrNull { it.arch > 0 }
        if (src == null) {
            note = "Este canal não guarda a programação passada."
            return
        }
        if (nowMs - target > src.arch * 24L * 3_600_000L) {
            note = "O provedor guarda só ${src.arch} dia(s) deste canal."
            return
        }
        tsTried.clear()
        tsTried.add(src.id)
        if (playTimeshift(target, src)) {
            note = "Voltando para " + SportsTime.clock(target, java.util.TimeZone.getDefault()) + "."
        } else {
            note = "Não consegui voltar no tempo neste canal."
        }
    }

    /** Troca para a próxima fonte ainda não tentada. Devolve false se não deu para trocar. */
    fun failover(reason: String, kind: Int): Boolean {
        val ch = channel ?: return false
        if (!autoSwitch) return false
        recordSession(kind)
        session.tried.add(session.sourceId ?: "")
        val order = user.rank(ch)
        ranked = order
        val next = order.firstOrNull { !session.tried.contains(it.id) } ?: return false
        session.lastSwitchAt = SystemClock.elapsedRealtime()
        playSource(next, reason + " Trocamos para " + next.label + ".")
        return true
    }

    fun pickSource(s: Source) {
        val ch = channel ?: return
        if (tsStart > 0L) {
            // vendo uma reprise: troca a fonte no mesmo horário
            if (s.arch > 0) {
                val at = tsStart + exo.currentPosition
                tsTried.clear()
                tsTried.add(s.id)
                playTimeshift(at, s)
                note = "Fonte: " + s.label + " (mesmo horário)."
            } else {
                note = "Esta fonte não guarda a programação passada."
            }
            panel = null
            return
        }
        recordSession(0)
        user.setPinned(ch.key, s.id)
        pinnedId = s.id
        session.tried.clear()
        session.tried.add(s.id)
        playSource(s, "Fonte fixada: " + s.label + ".")
        panel = null
    }

    fun backToAuto() {
        val ch = channel ?: return
        recordSession(0)
        user.setPinned(ch.key, null)
        pinnedId = null
        val order = user.rank(ch)
        ranked = order
        val first = order.firstOrNull() ?: return
        session.tried.clear()
        session.tried.add(first.id)
        playSource(first, "Automático: " + first.label + ".")
        panel = null
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        testing = false
        testingId = null
        val prev = testPrev
        testPrev = null
        val ch = channel
        if (ch != null) ranked = user.rank(ch)
        if (prev != null) playSource(prev, "Teste cancelado.")
    }

    fun startTest(pickBest: Boolean = false) {
        val ch = channel ?: return
        val info = liveInfo ?: return
        if (testing) return
        recordSession(0)
        testPrev = current
        bestAfterTest = pickBest
        testing = true
        testResults.clear()
        error = null
        // para o vídeo: a conta só permite 2 telas, então o teste usa a conexão do player
        exo.stop()
        exo.clearMediaItems()
        buffering = false
        testJob = scope.launch {
            try {
                // "melhor fonte": só mede quem ainda não tem nota (no máximo 8, para não demorar demais)
                val all = user.rank(ch)
                val order = if (pickBest) all.filter { user.reliability(it.id) == null }.take(8) else all
                for (s in order) {
                    testingId = s.id
                    val res = Probe.run(info.urlOf(s))
                    testResults[s.id] = res
                    user.record(s.id, res.r, res.startupMs, res.headroom, 0, 0f)
                    ranked = user.rank(ch)
                }
                testingId = null
                testing = false
                testPrev = null
                val best = user.rank(ch)
                ranked = best
                val pin = pinnedId
                val target = if (pickBest) {
                    user.bestBalanced(ch)
                } else {
                    best.firstOrNull { it.id == pin } ?: best.firstOrNull()
                }
                if (target != null) {
                    session.tried.clear()
                    session.tried.add(target.id)
                    playSource(
                        target,
                        if (pickBest) "Melhor fonte: " + target.label + "." else "Teste concluído. Reproduzindo " + target.label + ".",
                    )
                }
                if (pickBest) panel = null
            } finally {
                testingId = null
                testing = false
            }
        }
    }

    /** "Melhor fonte (automático)": mede as que ainda não têm nota e toca a de melhor estabilidade + qualidade. */
    fun playBest() {
        val ch = channel ?: return
        if (testing) return
        if (user.unmeasured(ch) > 0) {
            user.setPinned(ch.key, null)
            pinnedId = null
            startTest(true)
            return
        }
        recordSession(0)
        user.setPinned(ch.key, null)
        pinnedId = null
        val target = user.bestBalanced(ch) ?: return
        ranked = user.rank(ch)
        session.tried.clear()
        session.tried.add(target.id)
        playSource(target, "Melhor fonte: " + target.label + ".")
        panel = null
    }

    // sai do app (Home, YouTube...): pausa o som; ao voltar, retoma (no ao vivo, pulando para o agora)
    val hostCtx = LocalContext.current
    DisposableEffect(exo) {
        var owner: androidx.lifecycle.LifecycleOwner? = null
        var c: android.content.Context? = hostCtx
        while (c != null) {
            if (c is androidx.lifecycle.LifecycleOwner) {
                owner = c
                break
            }
            c = (c as? android.content.ContextWrapper)?.baseContext
        }
        var pausedByUs = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            try {
                if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    pausedByUs = exo.playWhenReady
                    exo.pause()
                } else if (ev == androidx.lifecycle.Lifecycle.Event.ON_START && pausedByUs) {
                    pausedByUs = false
                    if (isLive) {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                    }
                    exo.play()
                }
            } catch (e: Exception) {
                // player já fechado
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    // ------------------------------------------------------------------ ciclo de vida do player

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks2: Tracks) {
                tracks = tracks2
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    if (!session.ready) {
                        session.ready = true
                        session.startupMs = SystemClock.elapsedRealtime() - session.startedAt
                        val chNow = channel
                        val sid = session.sourceId
                        if (chNow != null && isLive && !sid.isNullOrEmpty()) user.setLastOk(chNow.key, sid)
                    }
                    session.retries = 0
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(e: PlaybackException) {
                if (tsStart > 0L) {
                    // reprise que não abriu: outro formato de link, depois outra fonte que guarde o passado
                    if (playTimeshift(tsStart, tsSrc, tsVariant + 1)) return
                    val other = ranked.firstOrNull { it.arch > 0 && !tsTried.contains(it.id) }
                    if (other != null) {
                        tsTried.add(other.id)
                        if (playTimeshift(tsStart, other, 0)) return
                    }
                    error = "Não consegui abrir esse horário neste canal. Tente outro canal ou volte ao vivo."
                    return
                }
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                if (isLive) {
                    if (session.retries < 1) {
                        session.retries++
                        scope.launch {
                            delay(1500)
                            exo.prepare()
                        }
                        return
                    }
                    if (failover("A fonte falhou.", 1)) return
                }
                error = describeError(e)
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            try {
                if (isLive) {
                    if (!testing) recordSession(0)
                } else {
                    val d = exo.duration
                    if (d != C.TIME_UNSET && d > 0L) req.onProgress?.invoke(exo.currentPosition, d)
                }
            } catch (e: Exception) {
                // sem importância: só deixa de guardar a nota/posição desta vez
            }
            exo.release()
        }
    }

    LaunchedEffect(req) {
        error = null
        if (isLive && channel != null) {
            val order = user.rank(channel)
            ranked = order
            val pin = user.pinnedSource(channel.key)
            // fixada > a última que tocou nesse canal > a melhor nota
            val start = order.firstOrNull { it.id == pin }
                ?: order.firstOrNull { it.id == user.lastOkSource(channel.key) }
                ?: order.firstOrNull()
            if (start == null) {
                error = "Este canal não tem fontes disponíveis."
            } else if (req.catchupStartMs > 0L) {
                // rever um jogo: já abre voltando para o horário dele (se a fonte não guardar o passado, tenta outra)
                val arch = order.firstOrNull { it.id == start.id && it.arch > 0 } ?: order.firstOrNull { it.arch > 0 }
                tsTried.clear()
                if (arch != null) tsTried.add(arch.id)
                current = arch ?: start
                if (arch == null || !playTimeshift(req.catchupStartMs, arch)) {
                    session.tried.clear()
                    session.tried.add(start.id)
                    playSource(start, "Este canal não guarda a programação passada: abrindo ao vivo.")
                }
            } else {
                session.tried.clear()
                session.tried.add(start.id)
                playSource(start, null)
            }
        } else {
            val item = MediaItem.fromUri(req.url)
            if (req.startMs > 0L) {
                exo.setMediaItem(item, maxOf(0L, req.startMs - 3_000L))
            } else {
                exo.setMediaItem(item)
            }
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    // a cada meio segundo: posição, medição de travadas e (ao vivo) decisão de trocar de fonte
    LaunchedEffect(exo) {
        var progressTick = 0
        while (true) {
            position = exo.currentPosition
            val d = exo.duration
            duration = if (d == C.TIME_UNSET) 0L else d

            if (isLive && !testing && error == null && session.sourceId != null && tsStart == 0L) {
                val now = SystemClock.elapsedRealtime()
                val stalledNow = session.ready && exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING
                if (stalledNow) {
                    if (!session.inStall) {
                        session.inStall = true
                        session.stalls++
                        session.stallTimes.add(now)
                    }
                    session.stalledMs += 500L
                    session.curStallMs += 500L
                } else {
                    session.inStall = false
                    session.curStallMs = 0L
                    if (exo.isPlaying) session.watchedMs += 500L
                }
                val recent = session.stallTimes.count { now - it < 60_000L }
                val canSwitch = now - session.lastSwitchAt > 20_000L && autoSwitch
                if (canSwitch) {
                    if (!session.ready && now - session.startedAt > 14_000L) {
                        if (!failover("A fonte demorou demais para abrir.", 2)) session.startedAt = now
                    } else if (session.curStallMs > 8_000L) {
                        if (!failover("A fonte travou.", 2)) session.curStallMs = 0L
                    } else if (recent >= 3) {
                        session.stallTimes.clear()
                        failover("A fonte estava oscilando.", 2)
                    }
                }
            } else if (!isLive) {
                progressTick++
                if (progressTick >= 20) {
                    progressTick = 0
                    if (exo.isPlaying && d != C.TIME_UNSET && d > 0L) req.onProgress?.invoke(exo.currentPosition, d)
                }
            }

            // some a mensagem de troca depois de um tempo
            delay(500)
        }
    }

    LaunchedEffect(note) {
        if (note != null) {
            delay(7_000)
            note = null
        }
    }

    // esconde os controles depois de alguns segundos sem mexer
    LaunchedEffect(controls, touch, panel) {
        if (controls && panel == null) {
            delay(5000)
            controls = false
        }
    }

    // mantém o foco no lugar certo: painel > botões > tela inteira
    LaunchedEffect(controls, panel) {
        try {
            if (panel != null) {
                delay(50)
                panelFocus.requestFocus()
            } else if (controls) {
                delay(50)
                playFocus.requestFocus()
            } else {
                rootFocus.requestFocus()
            }
        } catch (e: Exception) {
            // o elemento ainda não estava na tela; o próximo botão do controle resolve
        }
    }

    BackHandler {
        when {
            testing -> cancelTest()
            panel != null -> panel = null
            else -> onExit()
        }
    }

    fun seekBy(ms: Long) {
        if (!isLive && exo.isCurrentMediaItemSeekable) {
            var target = exo.currentPosition + ms
            if (target < 0L) target = 0L
            val d = exo.duration
            if (d != C.TIME_UNSET && target > d) target = d
            exo.seekTo(target)
        }
        controls = true
        touch++
    }

    fun togglePlay() {
        if (exo.isPlaying) exo.pause() else exo.play()
        touch++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch++
                when (ev.key) {
                    Key.MediaPlayPause -> {
                        togglePlay()
                        true
                    }
                    Key.MediaPlay -> {
                        exo.play()
                        true
                    }
                    Key.MediaPause -> {
                        exo.pause()
                        true
                    }
                    Key.MediaFastForward -> {
                        seekBy(30_000)
                        true
                    }
                    Key.MediaRewind -> {
                        seekBy(-30_000)
                        true
                    }
                    Key.Menu -> {
                        if (isLive) {
                            panel = "sources"
                            controls = true
                        }
                        true
                    }
                    else -> {
                        if (controls || panel != null) {
                            false
                        } else {
                            when (ev.key) {
                                Key.DirectionLeft -> {
                                    if (isLive) false else {
                                        seekBy(-10_000)
                                        true
                                    }
                                }
                                Key.DirectionRight -> {
                                    if (isLive) false else {
                                        seekBy(10_000)
                                        true
                                    }
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                                    controls = true
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null && !testing) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
        }

        val err = error
        if (err != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6161E2F))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Não foi possível reproduzir", color = ErrorColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(err, color = TextMain, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isLive) "Abra \"Fontes\" para escolher outra, ou aperte Voltar para sair." else "Aperte Voltar para sair.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        }

        if (testing) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6161E2F))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Testando as fontes de " + req.title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("O vídeo fica parado enquanto medimos. Voltar cancela.", color = Muted, fontSize = 12.sp)
            }
        }

        // aviso rápido de troca automática de fonte
        val n = note
        if (n != null && error == null) {
            Text(
                text = n,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Warn)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        if (controls || err != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC0B1020), Color.Transparent)))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                Text(req.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val cur = current
                if (isLive && cur != null) {
                    Spacer(Modifier.height(2.dp))
                    val rel = user.reliability(cur.id)
                    Text(
                        text = "Fonte: " + cur.label + "  ·  " + statusLabel(rel) + (if (pinnedId == cur.id) "  ·  fixada" else "  ·  automática"),
                        color = statusColor(rel),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (tsStart > 0L) {
                    Text(
                        text = "⏪ Reprise · " + SportsTime.clock(tsStart + position, java.util.TimeZone.getDefault()),
                        color = Warn,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(trackSummary(tracks), color = Color(0xFFC9C9D8), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        if (controls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0B1020))))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                ProgressRow(position, duration, isLive)
                Spacer(Modifier.height(12.dp))
                if (isLive && channel != null && liveInfo?.timeshift != null && channel.sources.any { it.arch > 0 }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp),
                    ) {
                        TvButton(text = "⏪ 10 min") { touch++; shift(-10) }
                        TvButton(text = "⏪ 2 min") { touch++; shift(-2) }
                        if (tsStart > 0L) {
                            TvButton(text = "2 min ⏩") { touch++; shift(2) }
                            TvButton(text = "10 min ⏩") { touch++; shift(10) }
                            TvButton(text = "AO VIVO", primary = true) {
                                touch++
                                val c = current ?: ranked.firstOrNull()
                                if (c != null) playSource(c, "Ao vivo.")
                            }
                        }
                    }
                }
                if (!isLive && req.url.startsWith("file:")) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp),
                    ) {
                        TvButton(text = "⏪ 10 min", minWidth = 110.dp) { seekBy(-600_000) }
                        TvButton(text = "⏪ 1 min", minWidth = 110.dp) { seekBy(-60_000) }
                        TvButton(text = "⏪ 10 s", minWidth = 110.dp) { seekBy(-10_000) }
                        TvButton(text = "10 s ⏩", minWidth = 110.dp) { seekBy(10_000) }
                        TvButton(text = "1 min ⏩", minWidth = 110.dp) { seekBy(60_000) }
                        TvButton(text = "10 min ⏩", minWidth = 110.dp) { seekBy(600_000) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(text = if (isPlaying) "Pausar" else "Reproduzir", focusRequester = playFocus) { togglePlay() }
                    if (isLive) {
                        TvButton(text = "Fontes (${channel?.sources?.size ?: 0})", primary = true) {
                            touch++
                            panel = "sources"
                        }
                        if (brand != null && brand.variants.size > 1) {
                            val here = brand.variants.firstOrNull { it.channel.key == channel?.key }
                            TvButton(text = "Praça: " + (here?.label ?: "trocar")) {
                                touch++
                                panel = "variants"
                            }
                        }
                        TvButton(text = "Áudio") {
                            touch++
                            panel = "audio"
                        }
                        TvButton(text = if (favorite) "✓ Favorito" else "Favoritar") {
                            touch++
                            if (favKey != null) favorite = user.toggleFavChannel(favKey)
                        }
                        if (channel != null) {
                            if (recActive) {
                                TvButton(text = "Tela preta") {
                                    touch++
                                    recordingId(channel.key)?.let(onRecMode)
                                }
                                TvButton(text = if (recAskStop) "Confirmar parar" else "■ Parar gravação") {
                                    touch++
                                    if (!recAskStop) {
                                        recAskStop = true
                                    } else {
                                        recAskStop = false
                                        recordingId(channel.key)?.let(onStopRecord)
                                        recActive = false
                                        note = "Gravação parada. Ela fica na aba Gravações."
                                    }
                                }
                            } else {
                                TvButton(text = "● Gravar") {
                                    touch++
                                    panel = "rec"
                                }
                            }
                        }
                    } else {
                        if (!req.url.startsWith("file:")) {
                            TvButton(text = "-10s") { seekBy(-10_000) }
                            TvButton(text = "+10s") { seekBy(10_000) }
                        }
                        if (nextEp != null) {
                            TvButton(text = "Próximo episódio", primary = true) { nextEp.go() }
                        }
                        if ((req.versions?.invoke()?.size ?: 0) > 1) {
                            TvButton(text = "Idioma / versão") {
                                touch++
                                panel = "versions"
                            }
                        }
                        TvButton(text = "Áudio") {
                            touch++
                            panel = "audio"
                        }
                        TvButton(text = "Legendas") {
                            touch++
                            panel = "text"
                        }
                    }
                    TvButton(text = if (zoom) "Tela: preencher" else "Tela: ajustar") {
                        touch++
                        zoom = !zoom
                    }
                }
            }
        }

        val which = panel
        if (which == "sources" && isLive && channel != null) {
            SourcesPanel(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(470.dp),
                channelName = channel.name,
                ranked = ranked,
                currentId = current?.id,
                pinnedId = pinnedId,
                testing = testing,
                testingId = testingId,
                results = testResults,
                user = user,
                autoSwitch = autoSwitch,
                stabilityFirst = stabilityFirst,
                firstFocus = panelFocus,
                onAuto = { backToAuto() },
                onBest = { playBest() },
                onPick = { pickSource(it) },
                onTest = { startTest() },
                onToggleAuto = {
                    user.setAutoSwitch(it)
                    autoSwitch = it
                },
                onToggleStability = {
                    user.setStabilityFirst(it)
                    stabilityFirst = it
                    ranked = user.rank(channel)
                },
            )
        } else if (which == "variants" && isLive && brand != null && channel != null) {
            VariantsPanel(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(420.dp),
                brand = brand,
                currentKey = channel.key,
                firstFocus = panelFocus,
                onPick = { v ->
                    panel = null
                    if (v.channel.key != channel.key) onVariant(brand, v)
                },
            )
        } else if (which == "rec" && isLive && channel != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(340.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF0161E2F))
                    .padding(14.dp),
            ) {
                Text("Gravar por quanto tempo?", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Usa mais uma conexão da sua lista.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val opts = listOf(30 to "30 minutos", 60 to "1 hora", 120 to "2 horas", 180 to "3 horas", 240 to "4 horas")
                    for ((i, o) in opts.withIndex()) {
                        FocusBox(
                            modifier = Modifier.fillMaxWidth(),
                            focusRequester = if (i == 2) panelFocus else null,
                            onClick = {
                                panel = null
                                val c = current ?: ranked.firstOrNull()
                                val u = if (c != null) liveInfo?.urlOf?.invoke(c) ?: "" else ""
                                val msg = onRecord(channel.key, channel.name, u, o.first)
                                if (msg == null) {
                                    recActive = true
                                    note = "Gravando por " + o.second + ". Veja na aba Gravações."
                                } else {
                                    note = msg
                                }
                            },
                        ) { focused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (focused) PanelAlt else Panel.copy(alpha = 0.6f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(o.second, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    FocusBox(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            recQ = (recQ + 1) % 3
                            Recorder.setQuality(context, recQ)
                        },
                    ) { focused ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (focused) PanelAlt else Panel.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text("Qualidade: " + Recorder.qualityLabel(recQ), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Média e Baixa só valem para canais com mais de uma versão da imagem.",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        } else if (which == "audio" || which == "text" || which == "versions") {
            val type = if (which == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            val options = if (which == "versions") {
                (req.versions?.invoke() ?: emptyList()).map { pv -> TrackOption(pv.label, pv.selected, true, pv.onSelect) }
            } else {
                buildOptions(exo, tracks, type)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(380.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF0161E2F))
                    .padding(14.dp),
            ) {
                Text(
                    text = if (which == "audio") "Áudio" else if (which == "versions") "Idioma / versão" else "Legendas",
                    color = TextMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                if (options.isEmpty()) {
                    Text("Nenhuma faixa encontrada neste vídeo.", color = Muted, fontSize = 14.sp)
                    // sem opção para focar: Voltar fecha o painel
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(0.7f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(options) { index, opt ->
                            FocusBox(
                                modifier = Modifier.fillMaxWidth(),
                                focusRequester = if (index == 0) panelFocus else null,
                                onClick = {
                                    opt.onSelect()
                                    panel = null
                                },
                            ) { focused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (focused) PanelAlt else Panel)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (opt.selected) "●" else "○",
                                        color = if (opt.selected) Accent else Muted,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(26.dp),
                                    )
                                    Text(
                                        text = if (opt.supported) opt.label else opt.label + "  (sem suporte neste aparelho)",
                                        color = if (opt.supported) TextMain else ErrorColor,
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
    }
}

// =========================================================================
// Painel de fontes do canal
// =========================================================================

@Composable
private fun SourcesPanel(
    modifier: Modifier,
    channelName: String,
    ranked: List<Source>,
    currentId: String?,
    pinnedId: String?,
    testing: Boolean,
    testingId: String?,
    results: Map<String, ProbeResult>,
    user: UserData,
    autoSwitch: Boolean,
    stabilityFirst: Boolean,
    firstFocus: FocusRequester,
    onAuto: () -> Unit,
    onBest: () -> Unit,
    onPick: (Source) -> Unit,
    onTest: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onToggleStability: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF0161E2F))
            .padding(14.dp),
    ) {
        Text("Fontes · " + channelName, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (stabilityFirst) "Ordenadas por estabilidade (a melhor primeiro)" else "Ordenadas por qualidade de imagem",
            color = Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvButton(
                text = if (testing) "Testando..." else "Testar fontes",
                primary = true,
                focusRequester = firstFocus,
            ) { if (!testing) onTest() }
            if (pinnedId != null) {
                TvButton(text = "Voltar ao automático") { if (!testing) onAuto() }
            }
        }
        Spacer(Modifier.height(8.dp))
        TvButton(text = "Melhor fonte (automático)") { if (!testing) onBest() }
        Text(
            "Escolhe a de maior estabilidade e qualidade. Se ainda não mediu alguma, mede antes.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(ranked, key = { _, s -> s.id }) { index, s ->
                val rel = user.reliability(s.id)
                val res = results[s.id]
                val shownR: Float? = if (res != null) res.r else rel
                val detail = when {
                    testing && testingId == s.id -> "testando agora..."
                    res != null -> if (res.ok) res.note else "sem resposta: " + res.note
                    else -> {
                        val ob = user.lastObs(s.id)
                        if (ob != null && ob.headroom > 0f) {
                            String.format(Locale.US, "folga %.1fx na última medição", ob.headroom)
                        } else if (ob != null && ob.stalls > 0) {
                            "travou ${ob.stalls}x na última vez"
                        } else {
                            ""
                        }
                    }
                }
                FocusBox(
                    modifier = Modifier.fillMaxWidth(),
                    focusScale = 1.02f,
                    onClick = { if (!testing) onPick(s) },
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (focused) PanelAlt else Panel)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (s.id == currentId) "▶" else if (index == 0) "★" else "",
                            color = if (s.id == currentId) Accent else Warn,
                            fontSize = 13.sp,
                            modifier = Modifier.width(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.label + (if (s.id == pinnedId) "  (fixada)" else ""),
                                color = TextMain,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (detail.isNotEmpty()) {
                                Text(detail, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            StabilityBars(shownR)
                            Spacer(Modifier.height(2.dp))
                            Text(statusLabel(shownR), color = statusColor(shownR), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Priorizar", color = Muted, fontSize = 12.sp)
            ChipItem(text = "Estabilidade", selected = stabilityFirst) { onToggleStability(true) }
            ChipItem(text = "Qualidade", selected = !stabilityFirst) { onToggleStability(false) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Trocar sozinho se travar", color = Muted, fontSize = 12.sp)
            ChipItem(text = "Sim", selected = autoSwitch) { onToggleAuto(true) }
            ChipItem(text = "Não", selected = !autoSwitch) { onToggleAuto(false) }
        }
    }
}

// =========================================================================
// Painel de praças (Globo São Paulo, Globo Rio...)
// =========================================================================

@Composable
private fun VariantsPanel(
    modifier: Modifier,
    brand: Channel,
    currentKey: String,
    firstFocus: FocusRequester,
    onPick: (Variant) -> Unit,
) {
    // linhas: cabeçalho da região (quando houver) e depois as praças
    val rows = remember(brand) {
        val out = ArrayList<Any>()
        var last: String? = null
        for (v in brand.variants) {
            if (v.region.isNotEmpty() && v.region != last) {
                out.add(v.region)
                last = v.region
            }
            out.add(v)
        }
        out
    }
    val startAt = remember(brand, currentKey) {
        val i = rows.indexOfFirst { it is Variant && it.channel.key == currentKey }
        if (i < 0) 0 else maxOf(0, i - 2)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF0161E2F))
            .padding(14.dp),
    ) {
        Text("Praça · " + brand.name, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text("Escolha a sua região. Fica salva para a próxima vez.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(rows.size) { i ->
                val r = rows[i]
                if (r is String) {
                    Text(
                        r.uppercase(),
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                } else if (r is Variant) {
                    val isCur = r.channel.key == currentKey
                    FocusBox(
                        modifier = Modifier.fillMaxWidth(),
                        focusScale = 1.02f,
                        focusRequester = if (isCur) firstFocus else null,
                        onClick = { onPick(r) },
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (focused) PanelAlt else Panel)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isCur) "▶" else "",
                                color = Accent,
                                fontSize = 13.sp,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                text = r.label,
                                color = TextMain,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${r.channel.sources.size} fontes",
                                color = Muted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Barra de progresso e utilidades de faixas
// =========================================================================

@Composable
private fun ProgressRow(position: Long, duration: Long, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (live) "AO VIVO" else formatTime(position),
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.width(76.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x55FFFFFF)),
        ) {
            if (!live && duration > 0L) {
                val frac = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(Accent),
                )
            }
        }
        Text(
            text = if (live || duration <= 0L) "" else formatTime(duration),
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = if (ms < 0L) 0L else ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun langName(code: String?): String {
    if (code.isNullOrBlank() || code == "und") return "Idioma não informado"
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale("pt"))
    return if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) code else name.replaceFirstChar { it.uppercase() }
}

private fun codecName(mime: String?): String {
    if (mime == null) return "?"
    return when (val sub = mime.substringAfter('/').lowercase()) {
        "mp4a-latm" -> "AAC"
        "ac3" -> "AC3"
        "eac3" -> "EAC3 (Dolby Digital+)"
        "eac3-joc" -> "Dolby Atmos"
        "true-hd" -> "TrueHD"
        "vnd.dts" -> "DTS"
        "vnd.dts.hd" -> "DTS-HD"
        "avc" -> "H.264"
        "hevc" -> "H.265"
        "mpeg" -> "MP3"
        else -> sub.uppercase()
    }
}

private fun trackLabel(f: Format, type: Int): String {
    val parts = ArrayList<String>()
    parts.add(langName(f.language))
    val title = f.label
    if (!title.isNullOrBlank()) parts.add(title)
    if (type == C.TRACK_TYPE_AUDIO) {
        val ch = if (f.channelCount > 0) " ${f.channelCount}ch" else ""
        parts.add(codecName(f.sampleMimeType) + ch)
    }
    return parts.joinToString(" • ")
}

private fun buildOptions(exo: ExoPlayer, tracks: Tracks, type: Int): List<TrackOption> {
    val list = ArrayList<TrackOption>()
    if (type == C.TRACK_TYPE_TEXT) {
        var anySelected = false
        for (g in tracks.groups) {
            if (g.type == C.TRACK_TYPE_TEXT && g.isSelected) anySelected = true
        }
        list.add(
            TrackOption(
                label = "Desligadas",
                selected = !anySelected,
                supported = true,
                onSelect = {
                    exo.trackSelectionParameters = exo.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
            ),
        )
    }
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            val f = g.getTrackFormat(i)
            val index = i
            list.add(
                TrackOption(
                    label = trackLabel(f, type),
                    selected = g.isTrackSelected(i),
                    supported = g.isTrackSupported(i),
                    onSelect = {
                        exo.trackSelectionParameters = exo.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(type, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, index))
                            .build()
                    },
                ),
            )
        }
    }
    return list
}

private fun trackSummary(t: Tracks): String {
    var video = "vídeo: ?"
    var audioSel = "nenhum"
    var audioCount = 0
    var textCount = 0
    var textSel = "desligadas"
    for (g in t.groups) {
        for (i in 0 until g.length) {
            val f = g.getTrackFormat(i)
            when (g.type) {
                C.TRACK_TYPE_VIDEO -> if (g.isTrackSelected(i)) {
                    val size = if (f.width > 0) "${f.width}x${f.height} " else ""
                    video = "vídeo: " + size + codecName(f.sampleMimeType)
                }
                C.TRACK_TYPE_AUDIO -> {
                    audioCount++
                    if (g.isTrackSelected(i)) audioSel = trackLabel(f, C.TRACK_TYPE_AUDIO)
                }
                C.TRACK_TYPE_TEXT -> {
                    textCount++
                    if (g.isTrackSelected(i)) textSel = trackLabel(f, C.TRACK_TYPE_TEXT)
                }
            }
        }
    }
    return "$video  |  áudio: $audioSel ($audioCount faixa(s))  |  legendas: $textSel ($textCount faixa(s))"
}

private fun describeError(e: PlaybackException): String {
    var cause: Throwable? = e.cause
    var http: Int? = null
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            http = cause.responseCode
            break
        }
        cause = cause.cause
    }
    val base = when {
        http != null -> "O servidor respondeu HTTP $http."
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Falha de rede ao conectar."
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Tempo esgotado ao conectar."
        e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Formato de arquivo não suportado."
        e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> "O aparelho não conseguiu decodificar este vídeo."
        e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "O aparelho não tem decodificador para este vídeo."
        else -> "Erro de reprodução."
    }
    return base + "\n(" + e.errorCodeName + ")"
}
