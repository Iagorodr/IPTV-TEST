package app.iptvtest

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

/** Uma entrada da lista M3U já entendida (canal, filme ou episódio). */
class M3uEntry(
    val id: String,
    val kind: Kind,
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
    val year: String,
    val ext: String,
    /** Só em episódio: id e nome da série, temporada e número. */
    val seriesId: String,
    val seriesName: String,
    val season: Int,
    val episode: Int,
)

/** Leitor de lista M3U. Entende #EXTINF com tvg-logo / group-title, separa canais, filmes e séries. */
object M3uParser {

    private val ATTR = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val VOD_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v", "mpg", "mpeg", "webm")
    private val EP_SXE = Regex("""(?i)^(.*?)[\s._\-]*\bs(\d{1,2})[\s._\-]*e(\d{1,3})\b""")
    private val EP_NXN = Regex("""(?i)^(.*?)[\s._\-]*\b(\d{1,2})x(\d{1,3})\b""")
    private val EP_TXE = Regex("""(?i)^(.*?)[\s._\-]*\bt(\d{1,2})[\s._\-]*e(\d{1,3})\b""")
    private val YEAR_END = Regex("""\s*[(\[]((?:19|20)\d{2})[)\]]\s*$""")

    /** Identificador estável de 64 bits (FNV-1a) em hexadecimal. */
    fun hash(s: String): String {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (ch in s) {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(h)
    }

    fun extOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        if (!last.contains('.')) return ""
        val e = last.substringAfterLast('.').lowercase(Locale.ROOT)
        return if (e.length in 1..5) e else ""
    }

    private fun looksLikeSeriesGroup(groupFolded: String): Boolean =
        groupFolded.contains("serie") || groupFolded.contains("novela") || groupFolded.contains("temporada") ||
            groupFolded.contains("season") || groupFolded.contains("tv show")

    /** Monta a entrada a partir de uma linha #EXTINF e a linha do link. */
    fun make(rawName: String, group: String, logo: String?, url: String): M3uEntry {
        var name = rawName.trim()
        val ext = extOf(url)
        val lowUrl = url.lowercase(Locale.ROOT)
        val g = Organizer.fold(group)
        val isVodFile = ext in VOD_EXT || lowUrl.contains("/movie/") || lowUrl.contains("/series/")
        val id = hash(url)

        if (!isVodFile) {
            return M3uEntry(id, Kind.LIVE, name, group, logo, url, "", ext, "", "", 0, 0)
        }

        var series: String? = null
        var season = 0
        var ep = 0
        for (rx in listOf(EP_SXE, EP_NXN, EP_TXE)) {
            val m = rx.find(name)
            if (m != null && m.groupValues[1].isNotBlank()) {
                series = m.groupValues[1].trim().trimEnd('-', '.', '_', ' ')
                season = m.groupValues[2].toIntOrNull() ?: 1
                ep = m.groupValues[3].toIntOrNull() ?: 1
                break
            }
        }
        if (series == null && (lowUrl.contains("/series/") || looksLikeSeriesGroup(g))) {
            series = name
            season = 1
            ep = 1
        }
        if (series != null && series.isNotBlank()) {
            val sid = "s" + hash(Organizer.fold(series))
            val title = "T" + season + " E" + ep
            return M3uEntry(id, Kind.SERIES, title, group, logo, url, "", ext, sid, series, season, ep)
        }

        var year = ""
        val ym = YEAR_END.find(name)
        if (ym != null) {
            year = ym.groupValues[1]
            name = name.substring(0, ym.range.first).trim()
        }
        return M3uEntry(id, Kind.MOVIES, name, group, logo, url, year, ext, "", "", 0, 0)
    }

    /** Lê a lista inteira, linha a linha. Devolve só links http/https. */
    fun parse(reader: BufferedReader, sink: (M3uEntry) -> Unit) {
        var name: String? = null
        var group = ""
        var logo: String? = null
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                var comma = -1
                var quoted = false
                for ((i, ch) in line.withIndex()) {
                    if (ch == '"') quoted = !quoted
                    if (ch == ',' && !quoted) {
                        comma = i
                        break
                    }
                }
                val head = if (comma >= 0) line.substring(0, comma) else line
                val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                var tvgName = ""
                group = ""
                logo = null
                for (m in ATTR.findAll(head)) {
                    when (m.groupValues[1].lowercase(Locale.ROOT)) {
                        "group-title" -> group = m.groupValues[2].trim()
                        "tvg-logo" -> logo = m.groupValues[2].trim().ifEmpty { null }
                        "tvg-name" -> tvgName = m.groupValues[2].trim()
                    }
                }
                name = title.ifEmpty { tvgName }
            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                if (group.isEmpty()) group = line.substring(8).trim()
            } else if (line.startsWith("#")) {
                continue
            } else {
                val n = name
                if (n != null && n.isNotEmpty() && (line.startsWith("http://", true) || line.startsWith("https://", true))) {
                    sink(make(n, group, logo, line))
                }
                name = null
                group = ""
                logo = null
            }
        }
    }
}

/** Lista M3U (um link). Baixa o arquivo, separa canais/filmes/séries e guarda um índice no aparelho para montar os links. */
class M3uApi(ctx: Context, private val link: String) : ListApi {

    private val indexFile = File(ctx.applicationContext.filesDir, "m3u_index.tsv")

    @Volatile private var entries: List<M3uEntry> = emptyList()
    @Volatile private var urls: Map<String, String> = emptyMap()
    @Volatile private var episodes: Map<String, List<Episode>> = emptyMap()
    @Volatile private var lastRefresh = 0L
    @Volatile private var summary = ""

    // ------------------------------------------------------------------ download

    private val lock = Mutex()

    override suspend fun prepare() {
        lock.withLock {
            if (urls.isNotEmpty()) return
            val loaded = withContext(Dispatchers.IO) { loadIndex() }
            if (!loaded) refreshLocked()
        }
    }

    override suspend fun refresh() {
        lock.withLock { refreshLocked() }
    }

    private suspend fun refreshLocked() {
        val now = System.currentTimeMillis()
        if (entries.isNotEmpty() && now - lastRefresh < 90_000L) return
        val list = withContext(Dispatchers.IO) { download() }
        val u = HashMap<String, String>(list.size * 2)
        for (e in list) u[e.id] = e.url
        entries = list
        urls = u
        episodes = buildEpisodes(list)
        summary = makeSummary(list)
        lastRefresh = System.currentTimeMillis()
        withContext(Dispatchers.IO) { saveIndex(list) }
    }

    private fun download(): List<M3uEntry> {
        val conn = Net.open(link, readTimeoutMs = 180_000)
        val out = ArrayList<M3uEntry>()
        val seen = HashSet<String>()
        try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8), 64 * 1024)
            reader.use { r -> M3uParser.parse(r) { e -> if (seen.add(e.id)) out.add(e) } }
        } finally {
            conn.disconnect()
        }
        if (out.isEmpty()) {
            throw IOException("Não encontrei canais, filmes ou séries nesse link. Confira se é mesmo uma lista M3U.")
        }
        return out
    }

    private fun buildEpisodes(list: List<M3uEntry>): Map<String, List<Episode>> {
        val m = HashMap<String, ArrayList<Episode>>()
        for (e in list) {
            if (e.seriesId.isEmpty()) continue
            val ext = e.ext.ifEmpty { "mp4" }
            m.getOrPut(e.seriesId) { ArrayList() }.add(Episode(e.id, e.season, e.episode, "Episódio " + e.episode, ext))
        }
        val out = HashMap<String, List<Episode>>(m.size * 2)
        for ((k, v) in m) out[k] = v.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
        return out
    }

    private fun makeSummary(list: List<M3uEntry>): String {
        var live = 0
        var movies = 0
        val series = HashSet<String>()
        for (e in list) {
            when (e.kind) {
                Kind.LIVE -> live++
                Kind.MOVIES -> movies++
                Kind.SERIES -> series.add(e.seriesId)
            }
        }
        return "Lista M3U • $live canais • $movies filmes • ${series.size} séries"
    }

    // ------------------------------------------------------------------ índice no aparelho (id → link)

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun saveIndex(list: List<M3uEntry>) {
        try {
            val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                for (e in list) {
                    w.write(e.id)
                    w.write("\t")
                    w.write(clean(e.url))
                    w.write("\t")
                    w.write(e.seriesId)
                    w.write("\t")
                    w.write(e.season.toString())
                    w.write("\t")
                    w.write(e.episode.toString())
                    w.write("\t")
                    w.write(e.ext)
                    w.write("\n")
                }
            }
            if (indexFile.exists()) indexFile.delete()
            tmp.renameTo(indexFile)
        } catch (e: Exception) {
            // sem o índice o app baixa a lista de novo na próxima abertura
        }
    }

    private fun loadIndex(): Boolean {
        if (!indexFile.exists()) return false
        return try {
            val u = HashMap<String, String>()
            val eps = HashMap<String, ArrayList<Episode>>()
            indexFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.size < 6) continue
                    val id = p[0]
                    u[id] = p[1]
                    if (p[2].isNotEmpty()) {
                        val season = p[3].toIntOrNull() ?: 1
                        val num = p[4].toIntOrNull() ?: 1
                        eps.getOrPut(p[2]) { ArrayList() }.add(Episode(id, season, num, "Episódio $num", p[5].ifEmpty { "mp4" }))
                    }
                }
            }
            if (u.isEmpty()) return false
            urls = u
            val out = HashMap<String, List<Episode>>(eps.size * 2)
            for ((k, v) in eps) out[k] = v.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
            episodes = out
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ ListApi

    override suspend fun login(): String {
        refresh()
        return summary
    }

    override suspend fun info(): String = summary.ifEmpty { "Lista M3U" }

    /** "Filmes | Ação" vira "Ação" (o app reconhece gêneros e plataformas pelo nome da categoria). */
    private fun cleanGroup(g: String): String {
        val parts = g.split(Regex("""\s*[|:>»]\s*|\s+[-–—]\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts.last() else g.trim()
    }

    private fun catId(group: String): String = "g" + M3uParser.hash(group)

    override suspend fun categories(kind: Kind): List<Category> {
        val seen = LinkedHashMap<String, Category>()
        for (e in entries) {
            if (e.kind != kind) continue
            val id = catId(e.group)
            if (!seen.containsKey(id)) seen[id] = Category(id, cleanGroup(e.group).ifEmpty { "Sem categoria" })
        }
        return seen.values.toList()
    }

    override suspend fun fetchItems(kind: Kind): List<RawItem> {
        val out = ArrayList<RawItem>()
        val seenSeries = HashSet<String>()
        for (e in entries) {
            if (e.kind != kind) continue
            if (kind == Kind.SERIES) {
                if (!seenSeries.add(e.seriesId)) continue
                out.add(
                    RawItem(
                        id = e.seriesId, name = e.seriesName, image = e.logo, year = "", rating = 0f, added = 0L,
                        ext = "", cats = listOf(catId(e.group)), plot = "", genre = "",
                    ),
                )
            } else {
                out.add(
                    RawItem(
                        id = e.id, name = e.name, image = e.logo, year = e.year, rating = 0f, added = 0L,
                        ext = if (kind == Kind.MOVIES) e.ext.ifEmpty { "mp4" } else "", cats = listOf(catId(e.group)),
                        plot = "", genre = "",
                    ),
                )
            }
        }
        return out
    }

    override fun streamUrl(kind: Kind, id: String, ext: String): String = urls[id] ?: ""

    override suspend fun vodInfo(vodId: String): VodInfo = VodInfo("", "", 0, "", "", 0f)

    override suspend fun seriesEpisodes(seriesId: String): List<Episode> = episodes[seriesId] ?: emptyList()
}

/** Reconhece links de lista M3U que na verdade são de um servidor Xtream (get.php?username=...&password=...). */
object ListLink {
    fun xtreamFrom(link: String): Account? {
        return try {
            val uri = Uri.parse(link.trim())
            val u = uri.getQueryParameter("username")
            val p = uri.getQueryParameter("password")
            val host = uri.host
            if (u.isNullOrEmpty() || p.isNullOrEmpty() || host.isNullOrEmpty()) {
                null
            } else {
                val port = if (uri.port != -1) ":" + uri.port else ""
                Account((uri.scheme ?: "http") + "://" + host + port, u, p)
            }
        } catch (e: Exception) {
            null
        }
    }
}
