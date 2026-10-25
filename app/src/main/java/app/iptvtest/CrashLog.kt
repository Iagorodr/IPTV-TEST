package app.iptvtest

import android.content.Context
import java.io.File

/** Guarda o motivo quando o app fecha sozinho, para mostrar na próxima abertura (e eu poder corrigir). */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val rt = Runtime.getRuntime()
                val mem = "memória: usada " + ((rt.totalMemory() - rt.freeMemory()) / 1048576) + " MB de " + (rt.maxMemory() / 1048576) + " MB"
                val sb = StringBuilder()
                sb.append(java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(java.util.Date())).append(" · build ")
                    .append(BuildConfig.VERSION_CODE).append(" · ").append(mem).append("\n")
                var c: Throwable? = e
                var depth = 0
                while (c != null && depth < 3) {
                    sb.append(c.javaClass.simpleName).append(": ").append(c.message ?: "").append("\n")
                    for (el in c.stackTrace.take(8)) sb.append("  ").append(el.className.substringAfterLast('.')).append('.').append(el.methodName).append(':').append(el.lineNumber).append("\n")
                    c = c.cause
                    depth++
                }
                file(app).writeText(sb.toString())
            } catch (x: Throwable) {
                // não atrapalha o fechamento
            }
            prev?.uncaughtException(t, e)
        }
    }

    /** O último motivo guardado (e apaga), ou null. */
    fun take(ctx: Context): String? {
        val f = file(ctx)
        if (!f.exists()) return null
        val s = try { f.readText() } catch (e: Exception) { null }
        f.delete()
        return s
    }
}
