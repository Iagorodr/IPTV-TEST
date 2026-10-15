package app.iptvtest

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Android TV) IPTV-TEST/0.1"

/** Mesma regra do organizador: categoria adulta é detectada só pelo NOME DA CATEGORIA do provedor. */
private val ADULT_CATEGORY = Regex("""\+\s?18|18\s?\+|adult|xxx|er[oó]tic""")

fun isAdultCategory(name: String): Boolean = ADULT_CATEGORY.containsMatchIn(name.lowercase())

private fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val s = optString(key, "").trim()
    return if (s.isEmpty() || s == "null") null else s
}

/** Cliente mínimo da API Xtream Codes (player_api.php). Nada daqui sai do aparelho além das chamadas ao próprio provedor. */
class XtreamApi(private val account: Account) {

    private val base = account.server.trimEnd('/')

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun apiUrl(action: String? = null, extra: String = ""): String {
        val sb = StringBuilder("$base/player_api.php?username=${enc(account.user)}&password=${enc(account.pass)}")
        if (action != null) sb.append("&action=").append(action)
        sb.append(extra)
        return sb.toString()
    }

    fun streamUrl(kind: Kind, id: String, ext: String): String {
        val folder = when (kind) {
            Kind.MOVIES -> "movie"
            Kind.SERIES -> "series"
            Kind.LIVE -> "live"
        }
        return "$base/$folder/${Uri.encode(account.user)}/${Uri.encode(account.pass)}/$id.$ext"
    }

    private suspend fun get(startUrl: String): String = withContext(Dispatchers.IO) {
        var url = startUrl
        for (attempt in 0 until 6) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Redirecionamento inválido do servidor.")
                    url = URL(URL(url), loc).toString()
                    continue
                }
                if (code !in 200..299) {
                    throw IOException(
                        when (code) {
                            401, 403 -> "O servidor recusou o acesso (HTTP $code). Confira usuário e senha."
                            404 -> "Endereço não encontrado (HTTP 404). Confira o servidor."
                            else -> "O servidor respondeu HTTP $code."
                        },
                    )
                }
                return@withContext conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Redirecionamentos demais.")
    }

    private fun parseArray(text: String): JSONArray {
        val t = text.trim()
        if (t.startsWith("[")) return JSONArray(t)
        if (t.isEmpty() || t == "null" || t == "{}") return JSONArray()
        throw IOException("Resposta inesperada do servidor.")
    }

    /** Valida usuário/senha e devolve um resumo da conta (sem dados sensíveis). */
    suspend fun login(): String {
        val root = JSONObject(get(apiUrl()))
        val ui = root.optJSONObject("user_info") ?: throw IOException("Resposta inesperada do servidor.")
        if (ui.optInt("auth", 0) != 1) throw IOException("Usuário ou senha inválidos.")
        val status = ui.str("status") ?: "?"
        val exp = ui.str("exp_date")?.toLongOrNull()?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it * 1000))
        } ?: "sem data"
        val max = ui.str("max_connections") ?: "?"
        return "Conta $status • vence $exp • telas: $max"
    }

    suspend fun categories(kind: Kind): List<Category> {
        val action = when (kind) {
            Kind.MOVIES -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
            Kind.LIVE -> "get_live_categories"
        }
        val arr = parseArray(get(apiUrl(action)))
        val out = ArrayList<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("category_id") ?: continue
            out.add(Category(id, o.str("category_name") ?: "Categoria $id"))
        }
        return out.distinctBy { it.id }
    }

    suspend fun items(kind: Kind, categoryId: String): List<MediaEntry> {
        val action = when (kind) {
            Kind.MOVIES -> "get_vod_streams"
            Kind.SERIES -> "get_series"
            Kind.LIVE -> "get_live_streams"
        }
        val text = get(apiUrl(action, "&category_id=${enc(categoryId)}"))
        return withContext(Dispatchers.Default) { parseItems(kind, categoryId, text) }
    }

    private fun belongs(o: JSONObject, categoryId: String): Boolean {
        val cid = o.str("category_id")
        val cids = o.optJSONArray("category_ids")
        if (cid == null && cids == null) return true
        if (cid == categoryId) return true
        if (cids != null) {
            for (i in 0 until cids.length()) {
                if (cids.optString(i) == categoryId) return true
            }
        }
        return false
    }

    private fun parseItems(kind: Kind, categoryId: String, text: String): List<MediaEntry> {
        val arr = parseArray(text)
        val all = ArrayList<MediaEntry>(arr.length())
        val mine = ArrayList<MediaEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val entry = when (kind) {
                Kind.MOVIES -> {
                    val id = o.str("stream_id") ?: continue
                    MediaEntry(id, o.str("name") ?: "(sem título)", o.str("stream_icon"), o.str("container_extension") ?: "mp4")
                }
                Kind.SERIES -> {
                    val id = o.str("series_id") ?: continue
                    MediaEntry(id, o.str("name") ?: "(sem título)", o.str("cover"), "")
                }
                Kind.LIVE -> {
                    val id = o.str("stream_id") ?: continue
                    MediaEntry(id, o.str("name") ?: "(sem nome)", o.str("stream_icon"), "m3u8")
                }
            }
            all.add(entry)
            if (belongs(o, categoryId)) mine.add(entry)
        }
        // Se o filtro local não casar com nada, o provedor já filtrou do lado dele: usa tudo.
        val chosen = if (mine.isEmpty()) all else mine
        return chosen.distinctBy { it.id }
    }

    suspend fun seriesEpisodes(seriesId: String): List<Episode> {
        val root = JSONObject(get(apiUrl("get_series_info", "&series_id=${enc(seriesId)}")))
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
