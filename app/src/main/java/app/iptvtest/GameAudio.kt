package app.iptvtest

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.media3.common.C

/**
 * Som do modo jogo: "voz clara" + volume mais parelho, usando os efeitos de áudio do próprio Android (leves, rodam no
 * sistema de som, não mexem no vídeo). Cada efeito é opcional: o aparelho que não aceitar um deles só fica sem ele.
 * Não faz efeito quando o som sai em passagem direta (Dolby/surround pelo HDMI): aí o app nem decodifica o áudio.
 */
class GameAudio {
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var dyn: DynamicsProcessing? = null

    fun attach(session: Int) {
        release()
        if (session == C.AUDIO_SESSION_ID_UNSET || session <= 0) return
        // voz clara: realça a faixa da fala (1–4,5 kHz) e tira um pouco do ronco grave da torcida
        try {
            val e = Equalizer(0, session)
            val range = e.bandLevelRange
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            for (i in 0 until e.numberOfBands) {
                val center = e.getCenterFreq(i.toShort()) // em milihertz
                val level = when {
                    center in 1_000_000..4_500_000 -> 350
                    center < 250_000 -> -150
                    else -> 0
                }
                e.setBandLevel(i.toShort(), level.coerceIn(lo, hi).toShort())
            }
            e.enabled = true
            eq = e
        } catch (t: Throwable) {
            eq = null
        }
        // volume mais parelho (1/2): levanta os trechos baixos (+3 dB)
        try {
            val l = LoudnessEnhancer(session)
            l.setTargetGain(300)
            l.enabled = true
            loud = l
        } catch (t: Throwable) {
            loud = null
        }
        // volume mais parelho (2/2): segura os picos (gol, apito, torcida) para não estourar
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val cfg = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, false, 0, false, 0, true,
                ).build()
                val d = DynamicsProcessing(0, session, cfg)
                val lim = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 6f, -8f, 0f)
                d.setLimiterAllChannelsTo(lim)
                d.enabled = true
                dyn = d
            } catch (t: Throwable) {
                dyn = null
            }
        }
    }

    fun release() {
        try { eq?.release() } catch (t: Throwable) { }
        try { loud?.release() } catch (t: Throwable) { }
        try { dyn?.release() } catch (t: Throwable) { }
        eq = null
        loud = null
        dyn = null
    }
}
