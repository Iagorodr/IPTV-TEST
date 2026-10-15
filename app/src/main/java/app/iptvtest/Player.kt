@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.iptvtest

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

private class TrackOption(
    val label: String,
    val selected: Boolean,
    val supported: Boolean,
    val onSelect: () -> Unit,
)

@Composable
fun PlayerScreen(req: PlaybackRequest, onExit: () -> Unit) {
    val context = LocalContext.current

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
    }

    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks2: Tracks) {
                tracks = tracks2
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(e: PlaybackException) {
                error = describeError(e)
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(req.url) {
        error = null
        exo.setMediaItem(MediaItem.fromUri(req.url))
        exo.prepare()
        exo.playWhenReady = true
    }

    // atualiza posição/duração a cada meio segundo
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition
            val d = exo.duration
            duration = if (d == C.TIME_UNSET) 0L else d
            delay(500)
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
        if (panel != null) panel = null else onExit()
    }

    fun seekBy(ms: Long) {
        if (exo.isCurrentMediaItemSeekable) {
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
                    else -> {
                        if (controls || panel != null) {
                            false
                        } else {
                            when (ev.key) {
                                Key.DirectionLeft -> {
                                    seekBy(-10_000)
                                    true
                                }
                                Key.DirectionRight -> {
                                    seekBy(10_000)
                                    true
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

        if (buffering && error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
        }

        val err = error
        if (err != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6101018))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Não foi possível reproduzir", color = ErrorColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(err, color = TextMain, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Aperte Voltar para sair.", color = Muted, fontSize = 13.sp)
            }
        }

        if (controls || err != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                Text(req.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(trackSummary(tracks), color = Color(0xFFC9C9D8), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        if (controls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                ProgressRow(position, duration, req.isLive)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvButton(text = if (isPlaying) "Pausar" else "Reproduzir", focusRequester = playFocus) { togglePlay() }
                    TvButton(text = "-10s") { seekBy(-10_000) }
                    TvButton(text = "+10s") { seekBy(10_000) }
                    TvButton(text = "Áudio") {
                        touch++
                        panel = "audio"
                    }
                    TvButton(text = "Legendas") {
                        touch++
                        panel = "text"
                    }
                    TvButton(text = if (zoom) "Tela: preencher" else "Tela: ajustar") {
                        touch++
                        zoom = !zoom
                    }
                }
            }
        }

        val which = panel
        if (which != null) {
            val type = if (which == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            val options = buildOptions(exo, tracks, type)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(380.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF0101018))
                    .padding(14.dp),
            ) {
                Text(
                    text = if (which == "audio") "Áudio" else "Legendas",
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
