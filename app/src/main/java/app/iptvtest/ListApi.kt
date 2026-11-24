package app.iptvtest

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * O que o app precisa de uma lista, seja ela Xtream (servidor, usuário e senha) ou um link M3U.
 * O resto do app só fala com esta interface.
 */
interface ListApi {
    /** Carrega do aparelho o que for preciso para montar os links (a lista M3U guarda um índice). */
    suspend fun prepare() {}

    /** Baixa a lista de novo, quando ela é um arquivo só (M3U). No Xtream o download é feito pelas funções abaixo. */
    suspend fun refresh() {}

    /** Valida a lista e devolve um resumo curto (conta, validade, telas...). */
    suspend fun login(): String

    /** O mesmo resumo, sem baixar nada de novo se possível. */
    suspend fun info(): String = login()

    suspend fun categories(kind: Kind): List<Category>

    suspend fun fetchItems(kind: Kind): List<RawItem>

    fun streamUrl(kind: Kind, id: String, ext: String): String

    /** Links (o primeiro é o mais provável) para tocar um canal a partir de [startMs] no passado. Vazio = sem catch-up. */
    fun timeshiftUrls(id: String, startMs: Long, minutes: Int): List<String> = emptyList()

    suspend fun vodInfo(vodId: String): VodInfo

    suspend fun seriesEpisodes(seriesId: String): List<Episode>
}

/**
 * Várias listas juntas num só conteúdo. Os ids da primeira lista ficam como são (por isso nada que já estava salvo
 * quebra); os das outras ganham o prefixo "~n~". Como tudo vira uma lista só de itens, o organizador já junta
 * os mesmos filmes/séries (dublado, legendado...) e os mesmos canais (fontes diferentes) num cartão único.
 */
class MergedApi(
    private val apis: List<ListApi>,
    /** Uma chave estável por lista e a pasta onde guardar a última cópia boa de cada uma (opcional). */
    private val keys: List<String> = emptyList(),
    private val dir: File? = null,
) : ListApi {

    /** Listas (posição → motivo) cujo download falhou ou veio vazio e que foram completadas com a última cópia boa dela. */
    val stale = java.util.concurrent.ConcurrentHashMap<Int, String>()


    private fun tag(i: Int, id: String): String = if (i == 0) id else "~$i~$id"

    /** Separa o prefixo: devolve (posição da lista, id original). */
    private fun split(id: String): Pair<Int, String> {
        if (id.startsWith("~")) {
            val end = id.indexOf('~', 1)
            val n = if (end > 1) id.substring(1, end).toIntOrNull() else null
            if (n != null && n in apis.indices) return Pair(n, id.substring(end + 1))
        }
        return Pair(0, id)
    }

    override suspend fun prepare() {
        failures.clear()
        stale.clear()
        for (a in apis) {
            try {
                a.prepare()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // uma lista fora do ar não derruba as outras
            }
        }
    }

    override suspend fun refresh() {
        for (a in apis) {
            try {
                a.refresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // idem
            }
        }
    }

    override suspend fun login(): String = collect { it.login() }

    override suspend fun info(): String = collect { it.info() }

    private suspend fun collect(f: suspend (ListApi) -> String): String {
        val out = ArrayList<String>()
        var ok = 0
        var last: Exception? = null
        for ((i, a) in apis.withIndex()) {
            try {
                out.add(tr("Lista {0}: ", i + 1) + f(a))
                ok++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                out.add(tr("Lista {0}: não respondeu", i + 1))
            }
        }
        if (ok == 0 && last != null) throw last
        return out.joinToString("\n")
    }

    override suspend fun categories(kind: Kind): List<Category> {
        val out = ArrayList<Category>()
        for ((i, a) in apis.withIndex()) {
            try {
                val cs = a.categories(kind)
                if (cs.isNotEmpty()) saveCopy(i, "cat_" + kind.name.lowercase(), cs.map { RawItem(it.id, it.name, null, "", 0f, 0L, "", emptyList(), "", "") })
                for (c in cs) out.add(Category(tag(i, c.id), c.name))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (apis.size == 1) throw e
                val saved = loadSaved(i, "cat_" + kind.name.lowercase())
                if (saved.isNotEmpty()) {
                    stale[i] = e.message ?: e.javaClass.simpleName
                    for (c in saved) out.add(Category(tag(i, c.id), c.name))
                } else {
                    failures[i] = e
                }
            }
        }
        return out
    }

    /** Listas (posição → erro) que não responderam no último download; vazio = tudo veio. */
    val failures = java.util.concurrent.ConcurrentHashMap<Int, Exception>()

    /** Verdadeiro se, no último download, alguma das listas juntas não respondeu (o catálogo veio incompleto). */
    val partial: Boolean get() = failures.isNotEmpty()

    override suspend fun fetchItems(kind: Kind): List<RawItem> {
        val out = ArrayList<RawItem>()
        var ok = 0
        var last: Exception? = null
        for ((i, a) in apis.withIndex()) {
            var l: List<RawItem>? = null
            var err: Exception? = null
            try {
                l = a.fetchItems(kind)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                err = e
            }
            val before = lastCount(i, kind.name.lowercase())
            // vazio ou muito menor que da última vez (o provedor às vezes responde "[]" quando está sobrecarregado)
            val suspicious = err == null && l != null && (l.isEmpty() && before > 0 || before >= 20 && l.size * 2 < before)
            if (err != null || suspicious) {
                val saved = loadSaved(i, kind.name.lowercase())
                if (saved.isNotEmpty()) {
                    stale[i] = err?.let { it.message ?: it.javaClass.simpleName } ?: tr("veio vazia")
                    l = saved
                    err = null
                }
            } else if (l != null && l.isNotEmpty()) {
                saveCopy(i, kind.name.lowercase(), l)
            }
            if (err != null || l == null) {
                last = err
                failures[i] = err ?: java.io.IOException(tr("veio vazia"))
                continue
            }
            ok++
            if (i == 0) {
                out.addAll(l)
            } else {
                for (r in l) {
                    out.add(
                        RawItem(
                            id = tag(i, r.id), name = r.name, image = r.image, year = r.year, rating = r.rating,
                            added = r.added, ext = r.ext, cats = r.cats.map { tag(i, it) }, plot = r.plot, genre = r.genre, arch = r.arch, tmdb = r.tmdb,
                        ),
                    )
                }
            }
        }
        if (ok == 0 && last != null) throw last
        return out
    }

    // ---- última cópia boa de cada lista (para o provedor falhar sem sumirem os canais) ----

    private fun copyFile(i: Int, kind: String): File? {
        val d = dir ?: return null
        val k = keys.getOrNull(i) ?: return null
        return File(d, "rawlist_${k}_$kind.gz")
    }

    private fun lastCount(i: Int, kind: String): Int {
        val f = copyFile(i, kind) ?: return 0
        if (!f.exists()) return 0
        return try {
            JsonReader(java.io.InputStreamReader(GZIPInputStream(f.inputStream().buffered()), Charsets.UTF_8)).use { r ->
                var n = 0
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() == "n") n = r.nextInt() else r.skipValue()
                    if (n > 0) break
                }
                n
            }
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun saveCopy(i: Int, kind: String, l: List<RawItem>) {
        val f = copyFile(i, kind) ?: return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tmp = File(f.parentFile, f.name + ".tmp")
                JsonWriter(java.io.OutputStreamWriter(GZIPOutputStream(tmp.outputStream().buffered(64 * 1024)), Charsets.UTF_8)).use { w ->
                    w.beginObject()
                    w.name("n").value(l.size.toLong())
                    w.name("a").beginArray()
                    for (r in l) {
                        w.beginObject()
                        w.name("i").value(r.id)
                        w.name("m").value(r.name)
                        if (r.image != null) w.name("g").value(r.image)
                        w.name("y").value(r.year)
                        w.name("r").value(r.rating.toDouble())
                        w.name("d").value(r.added)
                        w.name("e").value(r.ext)
                        w.name("c").beginArray()
                        for (c in r.cats) w.value(c)
                        w.endArray()
                        w.name("p").value(r.plot)
                        w.name("n").value(r.genre)
                        w.name("h").value(r.arch.toLong())
                        w.name("t").value(r.tmdb)
                        w.endObject()
                    }
                    w.endArray()
                    w.endObject()
                }
                if (!tmp.renameTo(f)) {
                    f.delete()
                    tmp.renameTo(f)
                }
            } catch (e: Exception) {
                // sem cópia: só perde a rede de segurança
            }
        }
    }

    private suspend fun loadSaved(i: Int, kind: String): List<RawItem> {
        val f = copyFile(i, kind) ?: return emptyList()
        if (!f.exists()) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                JsonReader(java.io.InputStreamReader(GZIPInputStream(f.inputStream().buffered(64 * 1024)), Charsets.UTF_8)).use { r ->
                    val out = ArrayList<RawItem>()
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() != "a") {
                            r.skipValue()
                            continue
                        }
                        r.beginArray()
                        while (r.hasNext()) {
                            var id = ""
                            var name = ""
                            var img: String? = null
                            var year = ""
                            var rating = 0f
                            var added = 0L
                            var ext = ""
                            val cats = ArrayList<String>()
                            var plot = ""
                            var genre = ""
                            var arch = 0
                            var tmdb = ""
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "i" -> id = r.nextString()
                                    "m" -> name = r.nextString()
                                    "g" -> img = r.nextString()
                                    "y" -> year = r.nextString()
                                    "r" -> rating = r.nextDouble().toFloat()
                                    "d" -> added = r.nextLong()
                                    "e" -> ext = r.nextString()
                                    "c" -> {
                                        r.beginArray()
                                        while (r.hasNext()) cats.add(r.nextString())
                                        r.endArray()
                                    }
                                    "p" -> plot = r.nextString()
                                    "n" -> genre = r.nextString()
                                    "h" -> arch = r.nextInt()
                                    "t" -> tmdb = r.nextString()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            out.add(RawItem(id, name, img, year, rating, added, ext, cats, plot, genre, arch, tmdb))
                        }
                        r.endArray()
                    }
                    out
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun streamUrl(kind: Kind, id: String, ext: String): String {
        val (i, raw) = split(id)
        return apis[i].streamUrl(kind, raw, ext)
    }

    override fun timeshiftUrls(id: String, startMs: Long, minutes: Int): List<String> {
        val (i, raw) = split(id)
        return apis[i].timeshiftUrls(raw, startMs, minutes)
    }

    override suspend fun vodInfo(vodId: String): VodInfo {
        val (i, raw) = split(vodId)
        return apis[i].vodInfo(raw)
    }

    override suspend fun seriesEpisodes(seriesId: String): List<Episode> {
        val (i, raw) = split(seriesId)
        val l = apis[i].seriesEpisodes(raw)
        return if (i == 0) l else l.map { it.copy(id = tag(i, it.id)) }
    }
}
