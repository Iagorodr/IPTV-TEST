@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.iptvtest

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

private fun Context.findOwner(): LifecycleOwner? {
    var c: Context? = this
    while (c != null) {
        if (c is LifecycleOwner) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

/**
 * A mini tela da aba Canais. Só toca quando a pessoa aperta OK num canal (quem decide isso é a tela de fora: [live]
 * fica nulo até lá); andar pela lista não mexe nela. Tem som. Usa uma conexão do provedor, e a conta só permite 2 telas,
 * por isso o player é liberado assim que a aba sai da tela (ex.: ao abrir em tela cheia) e pausa quando o app vai para
 * segundo plano.
 *
 * [wantedSource] é a fonte que a pessoa escolheu nos botões. Sem ela abre a fixada; sem fixada, a última que tocou
 * de verdade naquele canal; e se nunca tocou, uma das melhores, sorteada. Não fica varrendo as outras fontes: se a
 * fonte não abrir, avisa e deixa a pessoa escolher outra (ou abrir em tela cheia, que tem a troca automática).
 * [onCurrent] avisa qual fonte está tocando.
 */
@Composable
fun ChannelPreview(
    live: LiveInfo?,
    wantedSource: Source?,
    user: UserData,
    modifier: Modifier = Modifier,
    onCurrent: (Source?) -> Unit,
) {
    val context = LocalContext.current
    val liveNow by rememberUpdatedState(live)
    val wantedNow by rememberUpdatedState(wantedSource)
    var current by remember { mutableStateOf<Source?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(12_000)
        // reserva menor que a do player: é só uma espiada, e abre mais rápido
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(6_000, 24_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .setLoadControl(load)
            .build()
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguages("pt", "por")
            .build()
        p
    }

    fun play(s: Source) {
        val l = liveNow ?: return
        current = s
        error = null
        ready = false
        buffering = true
        exo.setMediaItem(
            MediaItem.Builder()
                .setUri(l.urlOf(s))
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.0f).build(),
                )
                .build(),
        )
        exo.prepare()
        exo.playWhenReady = true
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    ready = true
                    error = null
                    // guarda a fonte que tocou: é por ela que abre da próxima vez
                    val k = liveNow?.channel?.key
                    val cur = current
                    if (k != null && cur != null) user.setLastOk(k, cur.id)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                // sem varrer as outras fontes: avisa, e a pessoa escolhe outra ou abre em tela cheia
                buffering = false
                ready = false
                error = "Esta fonte não abriu.\nEscolha outra em FONTES ou aperte OK para abrir em tela cheia."
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // pausa quando o app vai para segundo plano; ao voltar, pula para o ao vivo
    val owner = remember(context) { context.findOwner() }
    DisposableEffect(owner, exo) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) {
                exo.pause()
            } else if (ev == Lifecycle.Event.ON_START && exo.mediaItemCount > 0) {
                exo.seekToDefaultPosition()
                exo.prepare()
                exo.playWhenReady = true
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    // trocou de canal (ou a pessoa escolheu outra fonte): carrega
    val channelKey = live?.channel?.key
    val wantedId = wantedSource?.id
    LaunchedEffect(channelKey, wantedId) {
        error = null
        ready = false
        val l = liveNow
        if (l == null) {
            exo.stop()
            exo.clearMediaItems()
            buffering = false
            current = null
            return@LaunchedEffect
        }
        val key = l.channel.key
        val first = wantedNow
            ?: l.channel.sources.firstOrNull { it.id == user.pinnedSource(key) }
            ?: l.channel.sources.firstOrNull { it.id == user.lastOkSource(key) }
            // nunca tocou: sorteia entre as 3 melhores (não varre todas, e as piores nem entram)
            ?: user.rank(l.channel).take(3).randomOrNull()
        if (first == null) {
            exo.stop()
            exo.clearMediaItems()
            buffering = false
            current = null
            error = "Este canal não tem fontes."
            return@LaunchedEffect
        }
        play(first)
    }
    LaunchedEffect(current) { onCurrent(current) }

    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0E1422))) {
        if (live == null) {
            SunBackdrop(modifier = Modifier.fillMaxSize(), sunX = 0.5f, sunY = 0.42f, sunR = 0.16f)
            Text(
                "Aperte OK em um canal\npara ver aqui",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
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
                modifier = Modifier.fillMaxSize(),
            )
            if (buffering && error == null) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.Center).size(34.dp),
                )
            }
            val err = error
            if (err != null) {
                Text(
                    err,
                    color = TextMain,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                )
            }
            if (ready && err == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Red)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(6.dp))
                    Text("AO VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            val q = current?.quality
            if (!q.isNullOrEmpty() && err == null) {
                Text(
                    q,
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC161E2F))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
