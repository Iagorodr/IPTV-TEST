package app.iptvtest

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Android TV) IPTV-TEST/0.7.0"

/** Categoria adulta é detectada só pelo NOME DA CATEGORIA do provedor. */
private val ADULT_CATEGORY = Regex("""\+\s?18|18\s?\+|18\s?plus|adult|xxx|er[oó]tic|porn|erwachsen|nsfw|hardcore""")

fun isAdultCategory(name: String): Boolean = ADULT_CATEGORY.containsMatchIn(name.lowercase())

private fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val s = optString(key, "").trim()
    return if (s.isEmpty() || s == "null") null else s
}

/** Um item cru, como o provedor entrega (filme, série ou canal). */
class RawItem(
    val id: String,
    val name: String,
    val image: String?,
    val year: String,
    val rating: Float,
    val added: Long,
    val ext: String,
    val cats: List<String>,
    val plot: String,
    val genre: String,
    /** Dias de catch-up do canal (só canais ao vivo de servidores Xtream). */
    val arch: Int = 0,
)

/** Leitura em fluxo (sem montar a árvore inteira na memória): o catálogo de filmes passa de 13 MB. */
private object RawParser {

    private fun JsonReader.loose(): String? {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                null
            }
            JsonToken.STRING, JsonToken.NUMBER -> {
                val s = nextString().trim()
                if (s.isEmpty() || s == "null") null else s
            }
            JsonToken.BOOLEAN -> nextBoolean().toString()
            else -> {
                skipValue()
                null
            }
        }
    }

    private fun JsonReader.looseList(): List<String> {
        return when (peek()) {
            JsonToken.BEGIN_ARRAY -> {
                val l = ArrayList<String>()
                beginArray()
                while (hasNext()) {
                    val v = loose()
                    if (v != null) l.add(v)
                }
                endArray()
                l
            }
            JsonToken.STRING -> {
                val s = nextString()
                Regex("\\d+").findAll(s).map { it.value }.toList()
            }
            JsonToken.NUMBER -> listOf(nextString())
            else -> {
                skipValue()
                emptyList()
            }
        }
    }

    fun readItems(r: JsonReader): List<RawItem> {
        val out = ArrayList<RawItem>()
        val seen = HashSet<String>()
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue()
            return out
        }
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            val item = readItem(r)
            if (item != null && seen.add(item.id)) out.add(item)
        }
        r.endArray()
        return out
    }

    private fun readItem(r: JsonReader): RawItem? {
        var id: String? = null
        var name: String? = null
        var title: String? = null
        var image: String? = null
        var year = ""
        var rating = 0f
        var added = 0L
        var ext = ""
        var catId: String? = null
        var cats: List<String> = emptyList()
        var plot = ""
        var genre = ""
        var archOn = false
        var archDays = 0
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "tv_archive" -> archOn = (r.loose() ?: "") == "1"
                "tv_archive_duration" -> archDays = (r.loose() ?: "").toIntOrNull() ?: 0
                "stream_id", "series_id" -> id = r.loose()
                "name" -> name = r.loose()
                "title" -> title = r.loose()
                "stream_icon", "cover" -> image = r.loose()
                "year" -> year = r.loose() ?: ""
                "rating" -> rating = (r.loose() ?: "").toFloatOrNull() ?: 0f
                "added", "last_modified" -> added = (r.loose() ?: "").toLongOrNull() ?: 0L
                "container_extension" -> ext = r.loose() ?: ""
                "category_id" -> catId = r.loose()
                "category_ids" -> cats = r.looseList()
                "plot" -> plot = r.loose() ?: ""
                "genre" -> genre = r.loose() ?: ""
                else -> r.skipValue()
            }
        }
        r.endObject()
        val realId = id ?: return null
        val allCats = if (cats.isNotEmpty()) cats else if (catId != null) listOf(catId) else emptyList()
        return RawItem(
            id = realId,
            name = name ?: title ?: "",
            image = image,
            year = year,
            rating = rating,
            added = added,
            ext = ext,
            cats = allCats,
            plot = plot,
            genre = genre,
            arch = if (archOn) (if (archDays > 0) archDays else 1) else 0,
        )
    }
}

/** Cliente mínimo da API Xtream Codes (player_api.php). Nada daqui sai do aparelho além das chamadas ao próprio provedor. */
class XtreamApi(private val account: Account) : ListApi {

    private val base = account.server.trimEnd('/')

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun apiUrl(action: String? = null, extra: String = ""): String {
        val sb = StringBuilder("$base/player_api.php?username=${enc(account.user)}&password=${enc(account.pass)}")
        if (action != null) sb.append("&action=").append(action)
        sb.append(extra)
        return sb.toString()
    }

    override fun streamUrl(kind: Kind, id: String, ext: String): String {
        val folder = when (kind) {
            Kind.MOVIES -> "movie"
            Kind.SERIES -> "series"
            Kind.LIVE -> "live"
        }
        return "$base/$folder/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/$id.$ext"
    }

    // ---- voltar no tempo (catch-up): o horário do link é o do servidor do provedor
    @Volatile private var serverOffsetMs: Long? = null

    private fun readServerClock(root: JSONObject) {
        val si = root.optJSONObject("server_info") ?: return
        try {
            val ts = si.str("timestamp_now")?.toLongOrNull()
            val tn = si.str("time_now")
            if (ts != null && tn != null) {
                val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val naive = f.parse(tn)?.time
                if (naive != null) {
                    val raw = naive - ts * 1000L
                    // arredonda para 15 min (o relógio do servidor anda alguns segundos de diferença)
                    serverOffsetMs = Math.round(raw / 900_000.0) * 900_000L
                    return
                }
            }
            val name = si.str("timezone")
            if (name != null) serverOffsetMs = java.util.TimeZone.getTimeZone(name).getOffset(System.currentTimeMillis()).toLong()
        } catch (e: Exception) {
            // sem o fuso do servidor, usa o do aparelho
        }
    }

    override suspend fun prepare() {
        if (serverOffsetMs != null) return
        try {
            readServerClock(JSONObject(getText(apiUrl())))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // vale o fuso do aparelho
        }
    }

    override fun timeshiftUrls(id: String, startMs: Long, minutes: Int): List<String> {
        val off = serverOffsetMs ?: java.util.TimeZone.getDefault().getOffset(startMs).toLong()
        val f = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        f.timeZone = java.util.SimpleTimeZone(off.toInt(), "srv")
        val stamp = f.format(Date(startMs))
        val u = Uri.encode(account.user)
        val p = Uri.encode(account.pass)
        return listOf(
            "$base/timeshift/$u/$p/$minutes/$stamp/$id.ts",
            "$base/streaming/timeshift.php?username=$u&password=$p&stream=$id&start=$stamp&duration=$minutes",
        )
    }

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val conn = Net.open(url, readTimeoutMs = 60_000, accept = "application/json")
        try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseArray(text: String): JSONArray {
        val t = text.trim()
        if (t.startsWith("[")) return JSONArray(t)
        if (t.isEmpty() || t == "null" || t == "{}") return JSONArray()
        throw IOException("Resposta inesperada do servidor.")
    }

    /** Valida usuário/senha e devolve um resumo da conta (sem dados sensíveis). */
    override suspend fun login(): String {
        val root = JSONObject(getText(apiUrl()))
        readServerClock(root)
        val ui = root.optJSONObject("user_info") ?: throw IOException("Resposta inesperada do servidor.")
        if (ui.optInt("auth", 0) != 1) throw IOException("Usuário ou senha inválidos.")
        val status = ui.str("status") ?: "?"
        val exp = ui.str("exp_date")?.toLongOrNull()?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it * 1000))
        } ?: "sem data"
        val max = ui.str("max_connections") ?: "?"
        return "Conta $status • vence $exp • telas: $max"
    }

    override suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.MOVIES -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
            Kind.LIVE -> "get_live_categories"
        }
        val arr = parseArray(getText(apiUrl(action)))
        val out = ArrayList<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("category_id") ?: continue
            out.add(Category(id, o.str("category_name") ?: "Categoria $id"))
        }
        return out.distinctBy { it.id }
    }

    /** Baixa o catálogo inteiro de um tipo, lendo em fluxo. Repetidos (mesmo id) são descartados. */
    override suspend fun fetchItems(kind: Kind): List<RawItem> = withContext(Dispatchers.IO) {
        val action = when (kind) {
            Kind.MOVIES -> "get_vod_streams"
            Kind.SERIES -> "get_series"
            Kind.LIVE -> "get_live_streams"
        }
        val conn = Net.open(apiUrl(action), readTimeoutMs = 120_000, accept = "application/json")
        try {
            val reader = JsonReader(InputStreamReader(conn.inputStream, Charsets.UTF_8).buffered(64 * 1024))
            reader.use { RawParser.readItems(it) }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun vodInfo(vodId: String): VodInfo {
        val root = JSONObject(getText(apiUrl("get_vod_info", "&vod_id=${enc(vodId)}")))
        val i = root.optJSONObject("info") ?: JSONObject()
        return VodInfo(
            plot = i.str("plot") ?: i.str("description") ?: "",
            genre = i.str("genre") ?: "",
            durationSecs = i.optInt("duration_secs", 0),
            cast = i.str("cast") ?: i.str("actors") ?: "",
            director = i.str("director") ?: "",
            rating = i.str("rating")?.toFloatOrNull() ?: 0f,
        )
    }

    override suspend fun seriesEpisodes(seriesId: String): List<Episode> {
        val root = JSONObject(getText(apiUrl("get_series_info", "&series_id=${enc(seriesId)}")))
        val eps = root.opt("episodes")
        val out = ArrayList<Episode>()

        fun add(o: JSONObject, seasonFallback: Int) {
            val id = o.str("id") ?: return
            val number = o.optInt("episode_num", 0)
            out.add(
                Episode(
                    id = id,
                    season = o.optInt("season", seasonFallback),
                    number = number,
                    title = o.str("title") ?: "Episódio $number",
                    ext = o.str("container_extension") ?: "mp4",
                ),
            )
        }

        if (eps is JSONObject) {
            val keys = eps.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val season = k.toIntOrNull() ?: 0
                val list = eps.optJSONArray(k) ?: continue
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    add(o, season)
                }
            }
        } else if (eps is JSONArray) {
            for (i in 0 until eps.length()) {
                val x = eps.opt(i)
                if (x is JSONObject) {
                    add(x, 1)
                } else if (x is JSONArray) {
                    for (j in 0 until x.length()) {
                        val o = x.optJSONObject(j) ?: continue
                        add(o, i + 1)
                    }
                }
            }
        }
        return out.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
    }
}
