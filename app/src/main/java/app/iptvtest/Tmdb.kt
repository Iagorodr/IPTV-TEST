package app.iptvtest

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Capa, imagem larga e sinopse de um filme/série, vindas do TMDB. */
class TmdbMeta(val poster: String?, val backdrop: String?, val overview: String)

/**
 * Completa o que a lista não traz (capa, imagem larga, sinopse) com o TMDB. Só funciona se o app foi compilado com o
 * token (BuildConfig.TMDB_TOKEN); sem ele, nada muda. Os resultados ficam guardados no aparelho.
 */
object Tmdb {
    private val token: String get() = BuildConfig.TMDB_TOKEN
    val enabled: Boolean get() = token.isNotEmpty()

    private const val POSITIVE_TTL = 30L * 24 * 3_600_000L
    private const val NEGATIVE_TTL = 3L * 24 * 3_600_000L
    private val mem = ConcurrentHashMap<String, Pair<Long, TmdbMeta?>>()
    private val gate = Semaphore(3)
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences("iptv_tmdb", Context.MODE_PRIVATE)
    }

    private fun keyOf(t: Title) = (if (t.series) "s|" else "m|") + t.fold + "|" + t.year

    private fun fromDisk(k: String): Pair<Long, TmdbMeta?>? {
        val raw = prefs?.getString(k, null) ?: return null
        return try {
            val j = JSONObject(raw)
            val at = j.getLong("at")
            if (j.optBoolean("none")) {
                Pair(at, null)
            } else {
                Pair(at, TmdbMeta(j.optString("p").ifEmpty { null }, j.optString("b").ifEmpty { null }, j.optString("o")))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fresh(e: Pair<Long, TmdbMeta?>): Boolean =
        System.currentTimeMillis() - e.first < (if (e.second == null) NEGATIVE_TTL else POSITIVE_TTL)

    /** O que já está guardado (sem rede). */
    fun cached(t: Title): TmdbMeta? {
        val k = keyOf(t)
        val e = mem[k] ?: fromDisk(k)?.also { mem[k] = it }
        return if (e != null && fresh(e)) e.second else null
    }

    private fun save(k: String, m: TmdbMeta?) {
        val now = System.currentTimeMillis()
        mem[k] = Pair(now, m)
        val j = JSONObject().put("at", now)
        if (m == null) j.put("none", true) else j.put("p", m.poster ?: "").put("b", m.backdrop ?: "").put("o", m.overview)
        try {
            prefs?.edit()?.putString(k, j.toString())?.apply()
        } catch (e: Exception) {
            // sem importância
        }
    }

    private fun call(path: String): JSONObject? {
        val c = URL("https://api.themoviedb.org/3/" + path).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        return try {
            if (c.responseCode !in 200..299) null else JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
        } finally {
            c.disconnect()
        }
    }

    /** Busca na internet (se ainda não tem guardado). Devolve null se não achou ou se o recurso está desligado. */
    suspend fun get(t: Title): TmdbMeta? {
        if (!enabled) return null
        val k = keyOf(t)
        val have = mem[k] ?: fromDisk(k)?.also { mem[k] = it }
        if (have != null && fresh(have)) return have.second
        return gate.withPermit {
            val again = mem[k]
            if (again != null && fresh(again)) return@withPermit again.second
            withContext(Dispatchers.IO) {
                try {
                    val q = URLEncoder.encode(t.name, "UTF-8")
                    val yearArg = if (t.year.length == 4) (if (t.series) "&first_air_date_year=" else "&year=") + t.year else ""
                    val kind = if (t.series) "tv" else "movie"
                    val res = call("search/$kind?query=$q$yearArg&language=pt-BR&include_adult=false")
                        ?: return@withContext null // erro de rede: não guarda, tenta de novo depois
                    val arr = res.optJSONArray("results")
                    var found: JSONObject? = null
                    if (arr != null) {
                        val y = t.year.toIntOrNull()
                        for (i in 0 until minOf(arr.length(), 5)) {
                            val o = arr.getJSONObject(i)
                            val d = (if (t.series) o.optString("first_air_date") else o.optString("release_date")).take(4).toIntOrNull()
                            if (y == null || d == null || Math.abs(d - y) <= 1) {
                                found = o
                                break
                            }
                        }
                    }
                    val m = found?.let {
                        val p = it.optString("poster_path").takeIf { s -> s.isNotEmpty() && s != "null" }
                        val b = it.optString("backdrop_path").takeIf { s -> s.isNotEmpty() && s != "null" }
                        TmdbMeta(
                            p?.let { x -> "https://image.tmdb.org/t/p/w342$x" },
                            b?.let { x -> "https://image.tmdb.org/t/p/w1280$x" },
                            it.optString("overview").trim(),
                        )
                    }
                    save(k, m)
                    m
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

/** Dados do TMDB para o título, que chegam aos poucos (começa com o que já estava guardado). */
@Composable
fun rememberTmdb(title: Title, needed: Boolean = true): State<TmdbMeta?> =
    produceState<TmdbMeta?>(initialValue = if (needed) Tmdb.cached(title) else null, title.key, needed) {
        if (needed && value == null) value = Tmdb.get(title)
    }
