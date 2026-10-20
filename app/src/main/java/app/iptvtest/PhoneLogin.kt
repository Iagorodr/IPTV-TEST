package app.iptvtest

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Servidor minúsculo que roda na própria TV, só enquanto a tela do QR code está aberta.
 * O celular (na mesma rede Wi-Fi) abre o endereço do QR, digita os dados da lista e envia: tudo fica dentro da rede
 * de casa, nada passa por servidor nosso. O endereço leva um código aleatório; sem ele o pedido é recusado.
 */
class PhoneLoginServer(
    private val token: String,
    private val appName: String,
    private val page: Page = Page.LOGIN,
    private val onSubmit: (mode: String, fields: Map<String, String>) -> Unit,
) {
    /** LOGIN: dados da lista. ACCESS: pedido de liberação (nome, e-mail e telefone). */
    enum class Page { LOGIN, ACCESS }

    private var server: ServerSocket? = null

    @Volatile
    private var running = false

    var port: Int = 0
        private set

    fun start(): Boolean {
        return try {
            val s = ServerSocket(0, 8)
            server = s
            port = s.localPort
            running = true
            thread(name = "phone-login", isDaemon = true) { acceptLoop(s) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: Exception) {
            // já fechado
        }
        server = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running) {
            val c = try {
                s.accept()
            } catch (e: Exception) {
                break
            }
            thread(name = "phone-login-conn", isDaemon = true) { handle(c) }
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 8_000
            val addr = sock.inetAddress
            // só aceita aparelhos da rede local
            if (!(addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress)) return
            val input = BufferedInputStream(sock.getInputStream())
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val first = lines[0].split(" ")
            if (first.size < 2) return
            val method = first[0]
            val target = first[1]
            var len = 0
            for (l in lines.drop(1)) {
                val i = l.indexOf(':')
                if (i > 0 && l.substring(0, i).trim().equals("content-length", ignoreCase = true)) {
                    len = l.substring(i + 1).trim().toIntOrNull() ?: 0
                }
            }
            val path = target.substringBefore('?')
            val query = parseForm(target.substringAfter('?', ""))
            if (method == "POST" && path == "/enviar") {
                if (len <= 0 || len > 16_384) {
                    send(sock, 400, simplePage("Pedido inválido."))
                    return
                }
                val body = readBody(input, len) ?: return
                val f = parseForm(String(body, Charsets.UTF_8))
                if (f["k"] != token) {
                    send(sock, 403, simplePage("Esta página expirou. Aponte a câmera para o QR code da TV de novo."))
                    return
                }
                onSubmit(f["modo"] ?: "", f)
                send(sock, 200, simplePage(if (page == Page.ACCESS) "Pedido enviado! Olhe a TV." else "Pronto! Olhe a TV. Se der algum erro, a TV mostra o motivo e você pode enviar de novo.", backLink = page != Page.ACCESS))
            } else if (method == "GET" && path == "/") {
                if (query["k"] != token) {
                    send(sock, 403, simplePage("Aponte a câmera do celular para o QR code que aparece na TV."))
                    return
                }
                send(sock, 200, if (page == Page.ACCESS) accessPage() else formPage())
            } else {
                send(sock, 404, simplePage("Página não encontrada."))
            }
        } catch (e: Exception) {
            // conexão quebrada ou lenta: ignora
        } finally {
            try {
                sock.close()
            } catch (e: Exception) {
                // já fechado
            }
        }
    }

    private fun readHead(input: InputStream): String? {
        val sb = ByteArrayOutputStream()
        var state = 0
        while (sb.size() < 8_192) {
            val b = input.read()
            if (b < 0) return null
            sb.write(b)
            state = when {
                b == 13 && (state == 0 || state == 2) -> state + 1
                b == 10 && (state == 1 || state == 3) -> state + 1
                b == 13 -> 1
                else -> 0
            }
            if (state == 4) return String(sb.toByteArray(), Charsets.ISO_8859_1)
        }
        return null
    }

    private fun readBody(input: InputStream, len: Int): ByteArray? {
        val buf = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = input.read(buf, got, len - got)
            if (n < 0) return null
            got += n
        }
        return buf
    }

    private fun parseForm(s: String): Map<String, String> {
        val out = HashMap<String, String>()
        if (s.isEmpty()) return out
        for (part in s.split('&')) {
            val i = part.indexOf('=')
            if (i <= 0) continue
            val k = decode(part.substring(0, i))
            val v = decode(part.substring(i + 1))
            out[k] = v
        }
        return out
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun send(sock: Socket, code: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "200 OK"
            400 -> "400 Bad Request"
            403 -> "403 Forbidden"
            else -> "404 Not Found"
        }
        val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        val out = sock.getOutputStream()
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private val style = "body{margin:0;padding:20px;background:#161E2F;color:#FFF3EC;font-family:system-ui,sans-serif}" +
        "h1{font-size:22px;color:#FFA586;margin:0 0 6px}h2{font-size:17px;margin:0}" +
        "p{color:#9AA3B8;font-size:14px;line-height:1.45}a{color:#FFA586}" +
        ".card{background:#242F49;border-radius:14px;padding:16px;margin:16px 0}" +
        "label{display:block;font-size:13px;color:#9AA3B8;margin:12px 0 4px}" +
        "input{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #3A4766;" +
        "background:#161E2F;color:#FFF3EC;font-size:16px}" +
        "button{width:100%;margin-top:18px;padding:14px;border:0;border-radius:24px;background:#B51A2B;" +
        "color:#FFF3EC;font-size:17px;font-weight:700}"

    private val safeName = appName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun head(): String =
        "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + safeName + "</title>" +
            "<style>" + style + "</style></head><body><h1>" + safeName + "</h1>"

    private fun simplePage(msg: String, backLink: Boolean = false): String =
        head() + "<p>" + msg + "</p>" +
            (if (backLink) "<p><a href=\"/?k=$token\">Enviar outra lista</a></p>" else "") + "</body></html>"

    private fun accessPage(): String =
        head() +
            "<p>Peça a liberação do app. Seus dados vão para quem desenvolve o app, só para ele entrar em contato com você.</p>" +
            "<form class=\"card\" method=\"post\" action=\"/enviar\"><h2>Pedir liberação</h2>" +
            "<input type=\"hidden\" name=\"k\" value=\"$token\"><input type=\"hidden\" name=\"modo\" value=\"access\">" +
            "<label>Nome</label><input name=\"nome\" autocomplete=\"name\">" +
            "<label>E-mail</label><input name=\"email\" type=\"email\" inputmode=\"email\" autocapitalize=\"off\" autocomplete=\"email\">" +
            "<label>Telefone</label><input name=\"tel\" type=\"tel\" inputmode=\"tel\" autocomplete=\"tel\">" +
            "<button type=\"submit\">Enviar pedido</button></form></body></html>"

    private fun formPage(): String =
        head() +
            "<p>Digite aqui os dados da sua lista. Eles vão direto para a TV, pela sua rede Wi-Fi, e não passam por nenhum servidor.</p>" +
            "<form class=\"card\" method=\"post\" action=\"/enviar\"><h2>Servidor, usuário e senha (Xtream)</h2>" +
            "<input type=\"hidden\" name=\"k\" value=\"$token\"><input type=\"hidden\" name=\"modo\" value=\"xtream\">" +
            "<label>Servidor (ex.: http://exemplo.com:8080)</label>" +
            "<input name=\"server\" inputmode=\"url\" autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\">" +
            "<label>Usuário</label><input name=\"user\" autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\">" +
            "<label>Senha</label><input name=\"pass\" type=\"password\" autocapitalize=\"off\" autocorrect=\"off\">" +
            "<button type=\"submit\">Enviar para a TV</button></form>" +
            "<form class=\"card\" method=\"post\" action=\"/enviar\"><h2>Link da lista (M3U)</h2>" +
            "<input type=\"hidden\" name=\"k\" value=\"$token\"><input type=\"hidden\" name=\"modo\" value=\"m3u\">" +
            "<label>Cole o link</label>" +
            "<input name=\"link\" inputmode=\"url\" autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\">" +
            "<button type=\"submit\">Enviar para a TV</button></form></body></html>"

    companion object {
        fun newToken(): String {
            val r = SecureRandom()
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            val sb = StringBuilder()
            for (i in 0 until 8) sb.append(chars[r.nextInt(chars.length)])
            return sb.toString()
        }

        /** O endereço da TV na rede de casa (192.168.x.x, 10.x.x.x...), ou null se ela não estiver numa rede. */
        fun localIp(): String? {
            return try {
                val found = ArrayList<String>()
                val ifs = NetworkInterface.getNetworkInterfaces() ?: return null
                for (ni in ifs) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in ni.inetAddresses) {
                        if (a is Inet4Address && a.isSiteLocalAddress) {
                            val h = a.hostAddress
                            if (h != null) found.add(h)
                        }
                    }
                }
                found.firstOrNull { it.startsWith("192.168.") } ?: found.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}
