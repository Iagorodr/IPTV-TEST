package app.iptvtest

/**
 * Modo infantil: só o que a lista marca como infantil, animação ou família, sem os gêneros pesados.
 * Conteúdo adulto já fica fora sempre (pelo nome da categoria). Sem PIN, como combinado.
 */
object Kids {
    private val KID_GENRES = setOf("Infantil", "Animação", "Família")
    private val HEAVY = setOf("Terror", "Crime", "Guerra", "Suspense", "Policial", "Reality", "Faroeste")

    fun okTitle(t: Title): Boolean {
        val g = t.genres
        if (g.none { it in KID_GENRES }) return false
        // animação adulta e filmes de família com gênero pesado ficam de fora, salvo se a lista os chama de infantil
        return !g.any { it in HEAVY } || g.contains("Infantil")
    }

    fun filter(c: Catalog): Catalog {
        val movies = c.movies.filter { okTitle(it) }
        val series = c.series.filter { okTitle(it) }
        val ch = c.channels.filter { ChannelGroups.groupOf(it) == "Infantil e anime" }
        return Catalog(movies, series, ch, c.channelCategories, c.builtAt)
    }
}
