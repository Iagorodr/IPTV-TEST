package app.iptvtest

import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.launch
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
                    val loc = conn.getHeaderField("Location") ?: throw IOException(tr("Redirecionamento inválido do servidor."))
                    url = URL(URL(url), loc).toString()
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) {
                    throw IOException(
                        when (code) {
                            401, 403 -> tr("O servidor recusou o acesso (HTTP {0}). Confira usuário e senha.", code)
                            404 -> tr("Endereço não encontrado (HTTP 404).")
                            else -> tr("O servidor respondeu HTTP {0}.", code)
                        },
                    )
                }
                return conn
            } catch (e: Exception) {
                conn.disconnect()
                throw e
            }
        }
        throw IOException(tr("Redirecionamentos demais."))
    }

    /**
     * Abre [url] e roda [body] numa thread de rede; se a tarefa for cancelada (trocou de lista, saiu da tela), a conexão é
     * derrubada na hora. Antes a leitura em andamento seguia até o fim em segundo plano, e baixar a lista velha e a nova
     * ao mesmo tempo fazia o provedor recusar ou devolver resposta vazia.
     */
    suspend fun <T> call(url: String, readTimeoutMs: Int, accept: String, body: (HttpURLConnection) -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.coroutineScope {
                var opened: HttpURLConnection? = null
                val watcher = launch {
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        try { opened?.disconnect() } catch (e: Exception) { }
                    }
                }
                try {
                    val conn = open(url, readTimeoutMs = readTimeoutMs, accept = accept)
                    opened = conn
                    try {
                        body(conn)
                    } finally {
                        conn.disconnect()
                    }
                } finally {
                    watcher.cancel()
                }
            }
        }
}
