package app.iptvtest

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Idioma da interface. O texto em português (do Brasil) que está no código é a própria chave: tr("Entrar") devolve
 * "Sign in" em inglês, e o mesmo "Entrar" em português. As traduções ficam em assets/i18n/<idioma>.json
 * ({"texto em português": "tradução"}); o que não tiver tradução aparece em português.
 *
 * Trocar o idioma (primeira abertura ou Ajustes > Perfil) muda a tela na hora: [lang] é um estado do Compose.
 */
object I18n {
    /** Idiomas da interface: código curto -> etiqueta guardada no perfil (a mesma de APP_LANGS). */
    val SUPPORTED = listOf("pt", "en", "es", "fr", "it", "de", "nl")

    var lang by mutableStateOf("pt")
        private set
    private var table: Map<String, String> = emptyMap()
    private val cache = HashMap<String, Map<String, String>>()

    /** "pt-BR", "en", "fr-BE"... -> "pt", "en", "fr" (ou "pt" se não for um dos 7). */
    fun short(tag: String): String = tag.lowercase().take(2).let { if (it in SUPPORTED) it else "pt" }

    /** Etiqueta do Android/TMDB para o idioma atual (ex.: "pt-BR", "en-US", "fr-FR"). */
    fun bcp47(): String = when (lang) {
        "pt" -> "pt-BR"
        "en" -> "en-US"
        "es" -> "es-ES"
        "fr" -> "fr-FR"
        "it" -> "it-IT"
        "de" -> "de-DE"
        "nl" -> "nl-NL"
        else -> "pt-BR"
    }

    fun locale(): java.util.Locale = java.util.Locale.forLanguageTag(bcp47())

    fun apply(ctx: Context, tag: String) {
        val l = short(tag)
        table = if (l == "pt") emptyMap() else cache.getOrPut(l) { load(ctx, l) }
        lang = l
    }

    private fun load(ctx: Context, l: String): Map<String, String> = try {
        val txt = ctx.assets.open("i18n/$l.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val o = JSONObject(txt)
        val m = HashMap<String, String>(o.length() * 2)
        for (k in o.keys()) m[k] = o.optString(k)
        m
    } catch (e: Exception) {
        emptyMap()
    }

    fun get(pt: String): String {
        val l = lang // lido sempre, para a tela se redesenhar quando o idioma muda
        if (l == "pt" || pt.isEmpty()) return pt
        return table[pt]?.takeIf { it.isNotEmpty() } ?: pt
    }
}

/** Texto da interface no idioma escolhido (a chave é o texto em português). */
fun tr(pt: String): String = I18n.get(pt)

/** Igual a [tr], com valores no lugar de {0}, {1}...: tr("Faltam {0} minutos", 5). */
fun tr(pt: String, vararg args: Any?): String {
    var s = I18n.get(pt)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
    return s
}
