package app.iptvtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL

class ProbeResult(
    val ok: Boolean,
    val r: Float,
    val startupMs: Long,
    val headroom: Float,
    val note: String,
)

/**
 * Teste rápido de uma fonte de canal (poucos segundos): baixa os segmentos mais recentes do HLS e compara
 * o tempo de download com a duração de cada segmento. Folga alta = a conexão aguenta o vídeo sem travar.
 */
object Probe {

    private const val DEADLINE_MS = 16_000L

    suspend fun run(url: String): ProbeResult = withContext(Dispatchers.IO) {
        try {
            probe(url)
        } catch (e: Exception) {
            ProbeResult(false, 0.05f, 0L, 0f, e.message ?: "falhou")
        }
    }

    private fun readBounded(ins: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun resolve(base: String, ref: String): String = URL(URL(base), ref.trim()).toString()

    private fun probe(url: String): ProbeResult {
        val t0 = System.nanoTime()
        fun elapsedMs(): Long = (System.nanoTime() - t0) / 1_000_000L

        var playlistUrl = url
        var text: String? = null
        var firstByteMs = 0L

        for (round in 0 until 2) {
            val conn = Net.open(playlistUrl, readTimeoutMs = 6_000)
            try {
                val ins = BufferedInputStream(conn.inputStream, 64 * 1024)
                ins.mark(16)
                val head = ByteArray(7)
                val n = ins.read(head)
                ins.reset()
                val isHls = n == 7 && String(head, Charsets.ISO_8859_1) == "#EXTM3U"
                if (!isHls) {
                    return probeRaw(ins, elapsedMs())
                }
                if (round == 0) firstByteMs = elapsedMs()
                val body = String(readBounded(ins, 512 * 1024), Charsets.UTF_8)
                val finalUrl = conn.url.toString()
                if (body.contains("#EXT-X-STREAM-INF")) {
                    // lista mestra: segue a variante de maior banda
                    var bestBw = -1L
                    var bestUri: String? = null
                    val lines = body.lines()
                    var i = 0
                    while (i < lines.size) {
                        val l = lines[i].trim()
                        if (l.startsWith("#EXT-X-STREAM-INF")) {
                            val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                            var j = i + 1
                            while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                            if (j < lines.size && bw >= bestBw) {
                                bestBw = bw
                                bestUri = lines[j].trim()
                            }
                        }
                        i++
                    }
                    if (bestUri == null) return ProbeResult(false, 0.05f, firstByteMs, 0f, "lista sem variantes")
                    playlistUrl = resolve(finalUrl, bestUri)
                    continue
                }
                text = body
                playlistUrl = finalUrl
                break
            } finally {
                conn.disconnect()
            }
        }

        val body = text ?: return ProbeResult(false, 0.05f, firstByteMs, 0f, "sem lista de vídeo")

        // segmentos: #EXTINF:<duração>, seguido da linha com o endereço
        val segs = ArrayList<Pair<Double, String>>()
        val lines = body.lines()
        var pendingDur = 0.0
        for (raw in lines) {
            val l = raw.trim()
            if (l.startsWith("#EXTINF:")) {
                pendingDur = l.substring(8).substringBefore(',').trim().toDoubleOrNull() ?: 0.0
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                if (pendingDur > 0.0) segs.add(Pair(pendingDur, resolve(playlistUrl, l)))
                pendingDur = 0.0
            }
        }
        if (segs.isEmpty()) return ProbeResult(false, 0.05f, firstByteMs, 0f, "sem segmentos")

        val pick = if (segs.size > 3) segs.subList(segs.size - 3, segs.size) else segs
        var minHead = Float.MAX_VALUE
        var kbpsSum = 0.0
        var count = 0
        var startup = firstByteMs
        for ((idx, seg) in pick.withIndex()) {
            if (elapsedMs() > DEADLINE_MS) break
            val s0 = System.nanoTime()
            val conn = Net.open(seg.second, readTimeoutMs = 8_000)
            var bytes = 0L
            try {
                val ins = conn.inputStream
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    bytes += n
                    if (elapsedMs() > DEADLINE_MS + 4_000L) break
                }
            } finally {
                conn.disconnect()
            }
            val dlMs = maxOf(1L, (System.nanoTime() - s0) / 1_000_000L)
            if (idx == 0) startup = elapsedMs()
            val head = ((seg.first * 1000.0) / dlMs.toDouble()).toFloat()
            if (head < minHead) minHead = head
            kbpsSum += (bytes * 8.0) / seg.first / 1000.0
            count++
        }
        if (count == 0) return ProbeResult(false, 0.1f, startup, 0f, "tempo esgotado")

        val h = if (minHead == Float.MAX_VALUE) 0f else minHead
        // nota = 1 - exp(-(folga-1)*1.2); folga <= 1 significa que o vídeo não chega a tempo
        var r = if (h <= 1f) 0.05f else (1.0 - Math.exp(-(h - 1.0) * 1.2)).toFloat()
        if (startup > 8_000L) r *= 0.6f else if (startup > 4_000L) r *= 0.8f
        val mbps = kbpsSum / count / 1000.0
        val note = String.format(java.util.Locale.US, "~%.1f Mbps, folga %.1fx", mbps, h)
        return ProbeResult(true, r.coerceIn(0f, 1f), startup, h, note)
    }

    /** Resposta que não é HLS (fluxo contínuo): mede só se os dados chegam sem pausas. */
    private fun probeRaw(ins: InputStream, startupMs: Long): ProbeResult {
        val t0 = System.nanoTime()
        val buf = ByteArray(32 * 1024)
        var total = 0L
        var lastRead = t0
        var maxGapMs = 0L
        while ((System.nanoTime() - t0) / 1_000_000L < 5_000L) {
            val n = ins.read(buf)
            if (n < 0) break
            val now = System.nanoTime()
            val gap = (now - lastRead) / 1_000_000L
            if (gap > maxGapMs) maxGapMs = gap
            lastRead = now
            total += n
        }
        if (total < 50_000L) return ProbeResult(false, 0.1f, startupMs, 0f, "quase sem dados")
        var r = 0.8f
        if (maxGapMs > 2_500L) r = 0.4f else if (maxGapMs > 1_200L) r = 0.6f
        if (startupMs > 8_000L) r *= 0.6f else if (startupMs > 4_000L) r *= 0.8f
        return ProbeResult(true, r, startupMs, 0f, "fluxo contínuo, pausa máx. ${maxGapMs} ms")
    }
}
