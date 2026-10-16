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
