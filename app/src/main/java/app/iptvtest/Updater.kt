package app.iptvtest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Uma versão nova encontrada: o número do build e o que mudou. */
class UpdateInfo(val code: Int, val notes: String)

/** Atualização dentro do próprio app: lê o version.json do release "latest", baixa o APK e chama o instalador do Android. */
object Updater {
    private const val BASE = "https://github.com/Iagorodr/IPTV-TEST/releases/download/latest/"

    @Suppress("DEPRECATION")
    fun installedCode(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    } catch (e: Exception) {
        0
    }

    private fun open(u: String): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "IagoIPTV-update")
        c.setRequestProperty("Cache-Control", "no-cache")
        return c
    }

    /** Devolve a versão nova, ou null se já está na última (ou se não deu para conferir). */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val c = open(BASE + "version.json?t=" + System.currentTimeMillis())
            try {
                if (c.responseCode !in 200..299) return@withContext null
                val j = JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
                val code = j.optInt("code", 0)
                if (code > installedCode(ctx)) UpdateInfo(code, j.optString("notes")) else null
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun apkFile(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "update")
        d.mkdirs()
        return File(d, "iptv-test.apk")
    }

    /** Baixa o APK. Devolve o arquivo, ou null se falhou. [onProgress] recebe 0..100. */
    suspend fun download(ctx: Context, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val out = apkFile(ctx)
        val part = File(out.parentFile, "iptv-test.apk.part")
        try {
            val c = open(BASE + "iptv-test.apk")
            try {
                if (c.responseCode !in 200..299) return@withContext null
                val total = c.contentLengthLong
                var done = 0L
                var last = -1
                c.inputStream.use { inp ->
                    part.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = inp.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != last) {
                                    last = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                if (total > 0 && done < total) return@withContext null
                if (done < 1_000_000L) return@withContext null
            } finally {
                c.disconnect()
            }
            out.delete()
            if (!part.renameTo(out)) return@withContext null
            out
        } catch (e: Exception) {
            try { part.delete() } catch (x: Exception) { /* sem importância */ }
            null
        }
    }

    /** Abre o instalador do Android. Devolve null se abriu, ou o texto do que a pessoa precisa fazer. */
    fun install(ctx: Context, f: File): String? {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            return try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                "Permita \"instalar apps desconhecidos\" para este app, volte e toque em Atualizar de novo."
            } catch (e: Exception) {
                "Nas configurações da TV, permita instalar apps de fontes desconhecidas para este app e tente de novo."
            }
        }
        return try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            null
        } catch (e: Exception) {
            "Não consegui abrir o instalador. Baixe pelo link de sempre (Downloader)."
        }
    }
}
