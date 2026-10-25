package app.iptvtest

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Quantos itens já foram lidos no download em andamento (a tela de carregamento mostra). */
object LoadCounter {
    @Volatile
    var items: Int = 0
}

object Net {
    /**
     * Abre a conexão seguindo redirecionamentos à mão (alguns provedores redirecionam de http para outro host).
     * Quem chamar precisa fechar com disconnect().
     */
    fun open(startUrl: String, readTimeoutMs: Int = 60_000, accept: String = "*/*"): HttpURLConnection {
        var url = startUrl
        for (attempt in 0 until 6) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = readTimeoutMs
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", accept)
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("Redirecionamento inválido do servidor.")
                    url = URL(URL(url), loc).toString()
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) {
                    throw IOException(
                        when (code) {
                            401, 403 -> "O servidor recusou o acesso (HTTP $code). Confira usuário e senha."
                            404 -> "Endereço não encontrado (HTTP 404)."
                            else -> "O servidor respondeu HTTP $code."
                        },
                    )
                }
                return conn
            } catch (e: Exception) {
                conn.disconnect()
                throw e
            }
        }
        throw IOException("Redirecionamentos demais.")
    }
}
