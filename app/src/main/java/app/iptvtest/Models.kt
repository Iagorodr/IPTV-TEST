package app.iptvtest

enum class Kind(val label: String) {
    MOVIES("Filmes"),
    SERIES("Séries"),
    LIVE("Canais"),
}

enum class Tab { HOME, MOVIES, SERIES, CHANNELS, LIST, SEARCH, SETTINGS }

data class Account(val server: String, val user: String, val pass: String)

data class Category(val id: String, val name: String)

/** Uma cópia concreta de um título no provedor (um stream_id ou series_id). */
data class Version(
    val id: String,
    val ext: String,
    val leg: Boolean,
    val is4k: Boolean,
    val cam: Boolean,
)

/** Um filme ou série já agrupado: dublado, legendado e 4K ficam todos dentro do mesmo card. */
class Title(
    val key: String,
    val series: Boolean,
    val name: String,
    val year: String,
    val image: String?,
    val rating: Float,
    val added: Long,
    val genres: List<String>,
    val platforms: List<String>,
    val plot: String,
    val launch: Boolean,
    val versions: List<Version>,
) {
    val fold: String by lazy(LazyThreadSafetyMode.NONE) { Organizer.fold(name) }
    val national: Boolean get() = genres.contains("Nacionais")
    val onlyCam: Boolean get() = versions.isNotEmpty() && versions.all { it.cam }
    val has4k: Boolean get() = versions.any { it.is4k }

    /** Pontuação simples para ordenar prateleiras: nota + um bônus para títulos recentes. */
    fun score(): Float {
        val y = year.toIntOrNull() ?: 0
        val recency = if (y > 1980) (minOf(y - 1980, 46) / 46f) * 30f else 0f
        return rating * 10f + recency - (if (onlyCam) 40f else 0f)
    }
}

/** Uma das várias versões de um canal (SD, HD, FHD, 4K, H265...). */
data class Source(
    val id: String,
    val quality: String,
    val rank: Int,
    val group: String,
    val label: String,
)

/** Uma "praça" (versão regional) de uma marca de TV, como Globo São Paulo ou SBT Rio. */
class Variant(val label: String, val region: String, val channel: Channel)

class Channel(
    val key: String,
    val name: String,
    val icon: String?,
    val category: String,
    val sources: List<Source>,
    /** Só nos cartões de marca (Globo, Record, SBT, Band, RedeTV): as praças que a pessoa pode escolher. */
    val variants: List<Variant> = emptyList(),
) {
    val fold: String by lazy(LazyThreadSafetyMode.NONE) { Organizer.fold(name) }
    val isBrand: Boolean get() = variants.isNotEmpty()

    /** Nome do cartão + nome de cada praça, para a busca achar "Anhanguera" dentro da Globo. */
    val searchFold: String by lazy(LazyThreadSafetyMode.NONE) {
        if (variants.isEmpty()) fold else fold + " " + variants.joinToString(" ") { it.channel.fold }
    }
}

class Catalog(
    val movies: List<Title>,
    val series: List<Title>,
    val channels: List<Channel>,
    val channelCategories: List<String>,
    val builtAt: Long,
)

class Shelf(val name: String, val all: List<Title>) {
    val row: List<Title> = if (all.size > 40) all.subList(0, 40) else all
}

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
)

class EpisodesState(val loading: Boolean, val error: String?, val episodes: List<Episode>)

data class VodInfo(
    val plot: String,
    val genre: String,
    val durationSecs: Int,
    val cast: String,
    val director: String,
    val rating: Float,
)

/** Ponto onde a pessoa parou de assistir. Uma entrada por título. */
data class ResumeEntry(
    val titleKey: String,
    val series: Boolean,
    val versionId: String,
    val epId: String,
    val ext: String,
    val label: String,
    val posMs: Long,
    val durMs: Long,
    val updated: Long,
    val zoom: Boolean,
)

class LiveInfo(
    val channel: Channel,
    val urlOf: (Source) -> String,
    /** Se for uma praça de uma marca (Globo São Paulo...), o cartão da marca, para trocar de praça dentro do player. */
    val brand: Channel? = null,
)

class PlaybackRequest(
    val title: String,
    val url: String,
    val isLive: Boolean,
    val startMs: Long = 0L,
    val zoom: Boolean = false,
    val live: LiveInfo? = null,
    val onProgress: ((Long, Long) -> Unit)? = null,
)
