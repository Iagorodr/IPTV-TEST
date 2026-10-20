package app.iptvtest

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Entra numa lista do jeito que der: tenta o servidor como veio, com http/https trocados, com outras
 * identificações (alguns painéis só aceitam certos apps) e, se a API Xtream não responder, cai para a lista M3U
 * do mesmo provedor. Só desiste depois de esgotar as maneiras, e conta o que tentou.
 */
object Connector {

    class Result(val acc: Account, val api: ListApi, val info: String, val agent: String)

    private val AGENTS = listOf(
        DEFAULT_USER_AGENT,
        "IPTVSmartersPro/3.1.5",
        "VLC/3.0.20 LibVLC/3.0.20",
        "okhttp/4.12.0",
        "Lavf/60.16.100",
    )

    private const val BAD_AUTH = "Usuário ou senha inválidos."

    /** Erro de rede de verdade (outra identificação não ajuda). */
    private fun isNetwork(e: Throwable) =
        e is UnknownHostException || e is ConnectException || e is SocketTimeoutException

    private fun flip(server: String): String? = when {
        server.startsWith("https://", true) -> "http://" + server.substring(8)
        server.startsWith("http://", true) -> "https://" + server.substring(7)
        else -> null
    }

    /** Xtream com usuário e senha. [m3uLink] é o que se usa se a API não responder de jeito nenhum. */
    suspend fun xtream(
        ctx: Context,
        server: String,
        user: String,
        pass: String,
        status: (String) -> Unit,
    ): Result {
        val servers = listOfNotNull(server, flip(server))
        var last: Throwable? = null
        var tries = 0
        for (srv in servers) {
            var netFail = false
            for (ua in AGENTS) {
                if (netFail) break
                USER_AGENT = ua
                tries++
                if (tries > 1) status("Tentando outro jeito de conectar ($tries)…")
                val acc = Account(srv, user, pass)
                try {
                    val x = XtreamApi(acc)
                    val info = x.login()
                    return Result(acc, x, info, ua)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.message == BAD_AUTH) throw e
                    last = e
                    if (isNetwork(e) || e is SSLException) netFail = true
                }
            }
        }
        // a API não respondeu: tenta a lista M3U do mesmo provedor
        for (srv in servers) {
            for (out in listOf("ts", "m3u8")) {
                USER_AGENT = DEFAULT_USER_AGENT
                val link = srv.trimEnd('/') + "/get.php?username=" + android.net.Uri.encode(user) +
                    "&password=" + android.net.Uri.encode(pass) + "&type=m3u_plus&output=" + out
                status("Tentando a lista M3U do provedor…")
                try {
                    return m3u(ctx, link, status, allowXtream = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = last ?: e
                }
            }
        }
        throw explain(last, tries)
    }

    /** Link de lista M3U (ou get.php de Xtream). */
    suspend fun m3u(ctx: Context, link: String, status: (String) -> Unit, allowXtream: Boolean = true): Result {
        if (allowXtream) {
            val xt = ListLink.xtreamFrom(link)
            if (xt != null) {
                try {
                    return xtream(ctx, xt.server, xt.user, xt.pass, status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.message == BAD_AUTH) throw e
                    // não é Xtream de verdade: segue como lista comum
                }
            }
        }
        val links = listOfNotNull(link, flip(link))
        var last: Throwable? = null
        var tries = 0
        for (l in links) {
            var netFail = false
            for (ua in AGENTS) {
                if (netFail) break
                USER_AGENT = ua
                tries++
                if (tries > 1) status("Tentando outro jeito de conectar ($tries)…")
                try {
                    val m = M3uApi(ctx, l)
                    val info = m.login()
                    return Result(Account("", "", "", l), m, info, ua)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                    if (isNetwork(e) || e is SSLException) netFail = true
                }
            }
        }
        throw explain(last, tries)
    }

    private fun explain(e: Throwable?, tries: Int): Exception {
        val base = when (e) {
            null -> "Não consegui entrar."
            is UnknownHostException -> "Servidor não encontrado. Confira o endereço e a internet."
            is SocketTimeoutException -> "O servidor demorou demais para responder."
            is ConnectException -> "Não foi possível conectar ao servidor."
            is OutOfMemoryError -> "A TV ficou sem memória ao organizar o catálogo."
            else -> e.message ?: "Erro desconhecido."
        }
        return IOException(if (tries > 1) "$base (tentei $tries jeitos de conectar)" else base)
    }
}
