package app.iptvtest

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * Controle básico de quem usa o app (para quem desenvolve): cada instalação tem um código anônimo e avisa que está
 * em uso. O servidor devolve se o acesso está liberado e, se houver, um aviso para mostrar na tela.
 * Não sai daqui o link da lista, usuário, senha nem o que a pessoa assiste. Só: código, versão, modelo e país do aparelho.
 */
object Control {
    private const val URL_PING = "https://zjbajnhnexsxthigokhg.supabase.co/rest/v1/rpc/ping"

    // chave pública (publishable) do projeto: só deixa chamar a função "ping", não lê nem altera tabelas
    private const val KEY = "sb_publishable_REBiG8eo1E96bSyY1ih1aQ_QxbXS2Li"

    /** [status]: "active" (liberado), "pending" (esperando liberação) ou "blocked" (suspenso). */
    class Reply(val status: String, val notice: String?, val noticeId: Int)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("iptv_control", Context.MODE_PRIVATE)

    fun installId(ctx: Context): String {
        val p = prefs(ctx)
        var id = p.getString("id", null)
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString()
            p.edit().putString("id", id).apply()
        }
        return id
    }

    /** Os 6 primeiros caracteres do código: é o que a pessoa lê para o desenvolvedor. */
    fun shortCode(ctx: Context): String = installId(ctx).take(6).uppercase(Locale.ROOT)

    /** Último estado conhecido. Aparelho que nunca falou com o servidor começa como "pending": só vale depois da liberação. */
    fun cachedStatus(ctx: Context): String = prefs(ctx).getString("status", "pending") ?: "pending"

    fun lastSeenNotice(ctx: Context): Int = prefs(ctx).getInt("notice_seen", 0)

    fun markNoticeSeen(ctx: Context, id: Int) {
        prefs(ctx).edit().putInt("notice_seen", id).apply()
    }

    private fun versionText(ctx: Context): String = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        (pi.versionName ?: "?") + "." + pi.versionCode
    } catch (e: Exception) {
        "?"
    }

    /** Avisa que o app está em uso e pega o estado. Devolve null se não deu para falar com o servidor. */
    suspend fun ping(ctx: Context, newSession: Boolean): Reply? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("p_id", installId(ctx))
                .put("p_version", versionText(ctx))
                .put("p_model", (Build.MANUFACTURER + " " + Build.MODEL).trim())
                .put("p_country", Locale.getDefault().country)
                .put("p_new_session", newSession)
            val conn = URL(URL_PING).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 10_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("apikey", KEY)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) return@withContext null
                val txt = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val o = JSONObject(txt)
                val status = o.optString("status").ifEmpty { "pending" }
                val notice = if (o.isNull("notice")) null else o.optString("notice").trim().ifEmpty { null }
                prefs(ctx).edit().putString("status", status).apply()
                Reply(status, notice, o.optInt("notice_id", 0))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
