package app.iptvtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Uma gravação: um arquivo .ts no próprio aparelho. [state]: "recording", "done" ou "error". */
class Rec(
    val id: String,
    val key: String,
    var title: String,
    val file: String,
    val startedAt: Long,
    var endsAt: Long,
    var state: String,
    var error: String = "",
) {
    val size: Long get() = try { File(file).length() } catch (e: Exception) { 0L }
    val recording: Boolean get() = state == "recording"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("key", key).put("title", title).put("file", file)
        .put("startedAt", startedAt).put("endsAt", endsAt).put("state", state).put("error", error)

    companion object {
        fun fromJson(o: JSONObject) = Rec(
            o.getString("id"), o.optString("key"), o.optString("title"), o.getString("file"),
            o.optLong("startedAt"), o.optLong("endsAt"), o.optString("state", "done"), o.optString("error"),
        )
    }
}

/** Lista de gravações guardada nas preferências (o arquivo de vídeo fica numa pasta do app). */
object RecStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("iptv_rec", Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<Rec> {
        val txt = sp(ctx).getString("list", "[]") ?: "[]"
        val out = ArrayList<Rec>()
        try {
            val a = JSONArray(txt)
            for (i in 0 until a.length()) out.add(Rec.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) {
            // índice quebrado: começa vazio
        }
        // gravação marcada como "em andamento" mas sem serviço rodando (app fechado à força): vira "feita"
        for (r in out) if (r.recording && !RecordService.running(r.id)) r.state = if (r.size > 0L) "done" else "error"
        return out.sortedByDescending { it.startedAt }
    }

    @Synchronized
    fun save(ctx: Context, list: List<Rec>) {
        val a = JSONArray()
        for (r in list) a.put(r.toJson())
        sp(ctx).edit().putString("list", a.toString()).apply()
    }

    @Synchronized
    fun upsert(ctx: Context, r: Rec) {
        val l = all(ctx).filter { it.id != r.id }.toMutableList()
        l.add(r)
        save(ctx, l)
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        val l = all(ctx)
        l.firstOrNull { it.id == id }?.let {
            try { File(it.file).delete() } catch (e: Exception) { /* sem importância */ }
        }
        save(ctx, l.filter { it.id != id })
    }

    fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "recordings")
        d.mkdirs()
        return d
    }
}

object Recorder {
    /** Começa a gravar. Devolve null se deu certo, ou o texto do problema. */
    fun start(ctx: Context, key: String, title: String, url: String, minutes: Int): String? {
        if (url.isBlank()) return "Esse canal não tem endereço para gravar."
        val dir = RecStore.dir(ctx)
        val free = try { dir.usableSpace } catch (e: Exception) { Long.MAX_VALUE }
        if (free < 1_000_000_000L) return "Pouco espaço no aparelho (menos de 1 GB livre)."
        if (RecStore.all(ctx).any { it.key == key && it.recording }) return "Esse canal já está sendo gravado."
        val now = System.currentTimeMillis()
        val id = now.toString() + "-" + (Math.random() * 1000).toInt()
        val safe = title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40).ifEmpty { "gravacao" }
        val file = File(dir, safe + "_" + id + ".ts")
        val rec = Rec(id, key, title, file.absolutePath, now, now + minutes.coerceIn(1, 360) * 60_000L, "recording")
        RecStore.upsert(ctx, rec)
        val i = Intent(ctx, RecordService::class.java)
            .setAction(RecordService.ACTION_START)
            .putExtra("id", id)
            .putExtra("url", url)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (e: Exception) {
            RecStore.remove(ctx, id)
            return "O Android não deixou iniciar a gravação em segundo plano."
        }
        return null
    }

    fun stop(ctx: Context, id: String) {
        val i = Intent(ctx, RecordService::class.java).setAction(RecordService.ACTION_STOP).putExtra("id", id)
        try {
            ctx.startService(i)
        } catch (e: Exception) {
            // serviço já parado
        }
    }
}

/**
 * Serviço em primeiro plano (com notificação fixa): baixa o sinal do canal para um arquivo, mesmo com a tela apagada
 * ou o app em segundo plano. Aceita link direto (.ts) e lista HLS (.m3u8).
 */
class RecordService : Service() {
    companion object {
        const val ACTION_START = "app.iptvtest.REC_START"
        const val ACTION_STOP = "app.iptvtest.REC_STOP"
        private const val CHANNEL = "rec"
        private const val NOTE_ID = 4711
        private val jobs = ConcurrentHashMap<String, Job>()
        fun running(id: String) = jobs.containsKey(id)
    }

    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTE_ID, note())
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra("id")
                val url = intent.getStringExtra("url")
                val rec = RecStore.all(this).firstOrNull { it.id == id }
                if (id != null && url != null && rec != null && !jobs.containsKey(id)) {
                    acquireWake()
                    val j = Job(applicationContext, rec, url) { finished(id) }
                    jobs[id] = j
                    j.start()
                }
            }
            ACTION_STOP -> intent.getStringExtra("id")?.let { jobs[it]?.cancel() }
        }
        if (jobs.isEmpty()) endService() else refreshNote()
        return START_NOT_STICKY
    }

    private fun finished(id: String) {
        jobs.remove(id)
        if (jobs.isEmpty()) endService() else refreshNote()
    }

    private fun endService() {
        releaseWake()
        try {
            stopForeground(true)
        } catch (e: Exception) {
            // sem importância
        }
        stopSelf()
    }

    override fun onDestroy() {
        for (j in jobs.values) j.cancel()
        releaseWake()
        super.onDestroy()
    }

    private fun acquireWake() {
        if (wake?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iptv:rec").apply {
                setReferenceCounted(false)
                acquire(7 * 60 * 60_000L)
            }
        } catch (e: Exception) {
            // segue sem o bloqueio de energia
        }
    }

    private fun releaseWake() {
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
            // sem importância
        }
    }

    private fun refreshNote() {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, note())
        } catch (e: Exception) {
            // sem importância
        }
    }

    @Suppress("DEPRECATION")
    private fun note(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gravações", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = jobs.size
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (n <= 1) "Gravando" else "Gravando $n canais")
            .setContentText("Toque para abrir o app e ver as gravações")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    /** Uma gravação: uma linha de execução própria. */
    private class Job(
        val ctx: Context,
        val rec: Rec,
        val url: String,
        val onEnd: () -> Unit,
    ) {
        @Volatile private var cancelled = false
        private var thread: Thread? = null

        fun start() {
            thread = Thread({ run() }, "rec-" + rec.id).apply { isDaemon = true; start() }
        }

        fun cancel() {
            cancelled = true
            thread?.interrupt()
        }

        private fun over() = cancelled || System.currentTimeMillis() >= rec.endsAt

        private fun run() {
            var failure = ""
            try {
                FileOutputStream(File(rec.file), true).use { out -> failure = record(out) }
            } catch (e: Exception) {
                failure = "Erro ao gravar: " + (e.message ?: "desconhecido")
            }
            rec.state = if (rec.size > 0L) "done" else "error"
            rec.error = if (rec.size > 0L) "" else failure.ifEmpty { "Não consegui gravar esse canal." }
            RecStore.upsert(ctx, rec)
            onEnd()
        }

        private fun open(u: String): Pair<HttpURLConnection, String> {
            var cur = u
            for (hop in 0 until 6) {
                val c = URL(cur).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", USER_AGENT)
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw java.io.IOException("redirecionamento sem destino")
                    c.disconnect()
                    cur = URL(URL(cur), loc).toString()
                    continue
                }
                if (code !in 200..299) {
                    c.disconnect()
                    throw java.io.IOException("o servidor respondeu $code")
                }
                return Pair(c, cur)
            }
            throw java.io.IOException("redirecionamentos demais")
        }

        /** Grava até o fim do tempo. Devolve o texto do problema se não conseguiu nada. */
        private fun record(out: FileOutputStream): String {
            var lastError = ""
            var fails = 0
            while (!over() && fails < 20) {
                try {
                    val (conn, finalUrl) = open(url)
                    try {
                        val inp = BufferedInputStream(conn.inputStream, 64 * 1024)
                        inp.mark(16)
                        val head = ByteArray(7)
                        val n = inp.read(head)
                        inp.reset()
                        if (n >= 7 && String(head, Charsets.US_ASCII) == "#EXTM3U") {
                            val text = inp.readBytes().toString(Charsets.UTF_8)
                            conn.disconnect()
                            hls(out, finalUrl, text)
                        } else {
                            raw(out, inp)
                        }
                        if (rec.size > 0L) fails = 0
                    } finally {
                        conn.disconnect()
                    }
                    if (over()) break
                    fails++
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (cancelled) break
                    lastError = "Falha de conexão: " + (e.message ?: "desconhecida")
                    fails++
                    try {
                        Thread.sleep(2_000L)
                    } catch (x: InterruptedException) {
                        break
                    }
                }
            }
            return lastError
        }

        private fun raw(out: FileOutputStream, inp: InputStream) {
            val buf = ByteArray(64 * 1024)
            while (!over()) {
                val n = inp.read(buf)
                if (n < 0) return
                out.write(buf, 0, n)
            }
        }

        private fun resolve(base: String, ref: String): String = URL(URL(base), ref).toString()

        private fun fetchText(u: String): String {
            val (c, _) = open(u)
            try {
                return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            } finally {
                c.disconnect()
            }
        }

        private fun hls(out: FileOutputStream, startUrl: String, firstText: String) {
            var plUrl = startUrl
            var text = firstText
            var lastSeq = Long.MIN_VALUE
            var idle = 0
            while (!over()) {
                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
                    // lista mestre: escolhe a versão de maior qualidade
                    var best = -1L
                    var bestUrl: String? = null
                    for (i in lines.indices) {
                        if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
                        val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        val next = lines.drop(i + 1).firstOrNull { !it.startsWith("#") } ?: continue
                        if (bw > best) {
                            best = bw
                            bestUrl = resolve(plUrl, next)
                        }
                    }
                    plUrl = bestUrl ?: throw java.io.IOException("lista sem versões")
                    text = fetchText(plUrl)
                    continue
                }
                if (lines.any { it.startsWith("#EXT-X-KEY") && !it.contains("METHOD=NONE") }) {
                    throw java.io.IOException("Esse canal é protegido e não dá para gravar.")
                }
                var seq = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
                    ?.substringAfter(':')?.toLongOrNull() ?: 0L
                if (lastSeq != Long.MIN_VALUE && seq + 50 < lastSeq) lastSeq = seq - 1 // o servidor recomeçou a contagem
                var target = lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION:") }
                    ?.substringAfter(':')?.toLongOrNull() ?: 6L
                var wrote = 0
                for (l in lines) {
                    if (l.startsWith("#")) continue
                    if (over()) break
                    if (seq > lastSeq) {
                        var ok = false
                        for (attempt in 0 until 2) {
                            try {
                                val (c, _) = open(resolve(plUrl, l))
                                try {
                                    c.inputStream.use { inp ->
                                        val buf = ByteArray(64 * 1024)
                                        while (!over()) {
                                            val n = inp.read(buf)
                                            if (n < 0) break
                                            out.write(buf, 0, n)
                                        }
                                    }
                                } finally {
                                    c.disconnect()
                                }
                                ok = true
                                break
                            } catch (e: java.io.IOException) {
                                if (cancelled) return
                            }
                        }
                        if (ok) wrote++
                        lastSeq = seq
                    }
                    seq++
                }
                if (lines.any { it == "#EXT-X-ENDLIST" }) return
                idle = if (wrote == 0) idle + 1 else 0
                if (idle > 60) throw java.io.IOException("o canal parou de enviar")
                val wait = (target * 500L).coerceIn(800L, 4_000L)
                Thread.sleep(wait)
                text = fetchText(plUrl)
            }
        }
    }
}
