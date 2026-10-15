package app.iptvtest

enum class Kind(val label: String) {
    MOVIES("Filmes"),
    SERIES("Séries"),
    LIVE("Canais"),
}

data class Account(val server: String, val user: String, val pass: String)

data class Category(val id: String, val name: String)

/** Um item da grade: filme, série ou canal (o significado depende da aba). */
data class MediaEntry(
    val id: String,
    val title: String,
    val image: String?,
    val ext: String,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
)

data class SeriesDetail(
    val entry: MediaEntry,
    val loading: Boolean,
    val error: String?,
    val episodes: List<Episode>,
)

data class PlaybackRequest(
    val title: String,
    val url: String,
    val isLive: Boolean,
)
