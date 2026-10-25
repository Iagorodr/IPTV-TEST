package app.iptvtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Confere se o relógio do aparelho está certo. Com a data errada, a internet segura (HTTPS) falha: jogos, capas,
 * atualização e gravações agendadas não funcionam. A conferência usa uma página comum (sem HTTPS), que devolve a hora certa.
 */
object ClockCheck {
    private val URLS = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.msftconnecttest.com/connecttest.txt",
        "http://detectportal.firefox.com/success.txt",
    )

    /** Hora certa (ms) segundo a internet, ou null se não deu para conferir. */
    suspend fun realTime(): Long? = withContext(Dispatchers.IO) {
        for (u in URLS) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 5_000
                c.readTimeout = 5_000
                c.requestMethod = "HEAD"
                c.instanceFollowRedirects = false
                try {
                    c.responseCode
                    val d = c.date
                    if (d > 0L) return@withContext d
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                // tenta o próximo endereço
            }
        }
        null
    }
}
