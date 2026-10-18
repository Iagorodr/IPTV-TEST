package app.iptvtest

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

    suspend fun vodInfo(vodId: String): VodInfo

    suspend fun seriesEpisodes(seriesId: String): List<Episode>
}

/**
 * Várias listas juntas num só conteúdo. Os ids da primeira lista ficam como são (por isso nada que já estava salvo
 * quebra); os das outras ganham o prefixo "~n~". Como tudo vira uma lista só de itens, o organizador já junta
 * os mesmos filmes/séries (dublado, legendado...) e os mesmos canais (fontes diferentes) num cartão único.
 */
class MergedApi(private val apis: List<ListApi>) : ListApi {

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
                out.add("Lista ${i + 1}: " + f(a))
                ok++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                out.add("Lista ${i + 1}: não respondeu")
            }
        }
        if (ok == 0 && last != null) throw last
        return out.joinToString("\n")
    }

    override suspend fun categories(kind: Kind): List<Category> {
        val out = ArrayList<Category>()
        for ((i, a) in apis.withIndex()) {
            try {
                for (c in a.categories(kind)) out.add(Category(tag(i, c.id), c.name))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (apis.size == 1) throw e
            }
        }
        return out
    }

    override suspend fun fetchItems(kind: Kind): List<RawItem> {
        val out = ArrayList<RawItem>()
        var ok = 0
        var last: Exception? = null
        for ((i, a) in apis.withIndex()) {
            try {
                val l = a.fetchItems(kind)
                ok++
                if (i == 0) {
                    out.addAll(l)
                } else {
                    for (r in l) {
                        out.add(
                            RawItem(
                                id = tag(i, r.id), name = r.name, image = r.image, year = r.year, rating = r.rating,
                                added = r.added, ext = r.ext, cats = r.cats.map { tag(i, it) }, plot = r.plot, genre = r.genre,
                            ),
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        if (ok == 0 && last != null) throw last
        return out
    }

    override fun streamUrl(kind: Kind, id: String, ext: String): String {
        val (i, raw) = split(id)
        return apis[i].streamUrl(kind, raw, ext)
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
