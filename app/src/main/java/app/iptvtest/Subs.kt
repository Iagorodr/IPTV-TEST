package app.iptvtest

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** O que procurar: nome e ano do título; em séries, temporada e episódio. */
class SubQuery(val name: String, val year: String, val season: Int = 0, val episode: Int = 0)

class SubResult(val fileId: Long, val lang: String, val release: String, val downloads: Int, val hearing: Boolean)

/**
 * Legendas baixadas do OpenSubtitles (chave e conta vêm do GitHub na compilação). Cada legenda fica guardada
 * no aparelho por 7 dias e depois é apagada sozinha.
 */
object Subs {
    private const val API = "https://api.opensubtitles.com/api/v1"
    private const val UA = "IAGO IPTV v1.0"
    private const val WEEK = 7L * 86_400_000L

    val enabled: Boolean get() = BuildConfig.OS_KEY.isNotEmpty()

    /** (código OpenSubtitles, nome na tela). */
    val LANGS = listOf(
        "pt-br" to "Português (Brasil)", "pt-pt" to "Português (Portugal)", "en" to "Inglês", "es" to "Espanhol",
        "fr" to "Francês", "it" to "Italiano", "de" to "Alemão", "nl" to "Holandês",
    )

    fun langName(code: String): String = LANGS.firstOrNull { it.first == code }?.second ?: code

    private var token: String? = null
    private var tokenAt = 0L

    private fun conn(path: String, method: String, body: String?, auth: Boolean): HttpURLConnection {
        val c = URL(API + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Api-Key", BuildConfig.OS_KEY)
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        val t = token
        if (auth && t != null) c.setRequestProperty("Authorization", "Bearer $t")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return c
    }

    private fun read(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
        c.disconnect()
        if (code !in 200..299) {
            val msg = try {
                JSONObject(s).optString("message")
            } catch (e: Exception) {
                ""
            }
            throw RuntimeException(if (code == 406 || code == 429) "Limite de legendas do dia atingido. Tente amanhã." else msg.ifEmpty { "Erro $code no OpenSubtitles." })
        }
        return JSONObject(s)
    }

    private fun ensureLogin() {
        if (BuildConfig.OS_USER.isEmpty()) return
        if (token != null && System.currentTimeMillis() - tokenAt < 20L * 3_600_000L) return
        val body = JSONObject().put("username", BuildConfig.OS_USER).put("password", BuildConfig.OS_PASS).toString()
        try {
            val r = read(conn("/login", "POST", body, false))
            token = r.optString("token").ifEmpty { null }
            tokenAt = System.currentTimeMillis()
        } catch (e: Exception) {
            token = null // sem login ainda dá para baixar algumas por dia
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun search(q: SubQuery, lang: String): List<SubResult> = withContext(Dispatchers.IO) {
        var p = "/subtitles?languages=" + enc(lang) + "&query=" + enc(q.name.lowercase())
        if (q.season > 0) p += "&season_number=" + q.season + "&episode_number=" + q.episode
        else if (q.year.length == 4) p += "&year=" + q.year
        val r = read(conn(p, "GET", null, false))
        val arr = r.optJSONArray("data") ?: return@withContext emptyList()
        val out = ArrayList<SubResult>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val files = a.optJSONArray("files") ?: continue
            val f = files.optJSONObject(0) ?: continue
            out.add(SubResult(f.optLong("file_id"), a.optString("language"), a.optString("release"), a.optInt("download_count"), a.optBoolean("hearing_impaired")))
        }
        out.sortedByDescending { it.downloads }.take(10)
    }

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "subs").also { it.mkdirs() }

    /** Apaga as legendas guardadas há mais de 7 dias. */
    fun cleanup(ctx: Context) {
        val now = System.currentTimeMillis()
        dir(ctx).listFiles()?.forEach { if (now - it.lastModified() > WEEK) it.delete() }
    }

    /** Baixa (ou reaproveita, se já baixada nesta semana) e devolve o arquivo .srt. */
    suspend fun download(ctx: Context, r: SubResult): File = withContext(Dispatchers.IO) {
        cleanup(ctx)
        val f = File(dir(ctx), r.fileId.toString() + ".srt")
        if (f.exists() && f.length() > 0) return@withContext f
        ensureLogin()
        val link = read(conn("/download", "POST", JSONObject().put("file_id", r.fileId).put("sub_format", "srt").toString(), true)).optString("link")
        if (link.isEmpty()) throw RuntimeException("O OpenSubtitles não mandou o arquivo.")
        val c = URL(link).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.inputStream.use { inp -> f.outputStream().use { inp.copyTo(it) } }
        c.disconnect()
        f
    }
}
