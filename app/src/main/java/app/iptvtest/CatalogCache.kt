package app.iptvtest

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Guarda o catálogo já organizado no armazenamento privado do app, para abrir na hora nas próximas vezes.
 * Não grava usuário nem senha: só títulos, ids e capas.
 */
object CatalogCache {

    private const val FORMAT = 5

    fun accKey(acc: Account): String =
        if (acc.m3u.isEmpty()) {
            Integer.toHexString((acc.server + "|" + acc.user).hashCode())
        } else {
            Integer.toHexString(("m3u|" + acc.m3u).hashCode())
        }

    /** Chave do catálogo de várias listas juntas. */
    fun mergedKey(accs: List<Account>): String =
        Integer.toHexString(("merge|" + accs.joinToString("|") { accKey(it) }).hashCode())

    private fun file(ctx: Context, key: String): File = File(ctx.filesDir, "catalog_$key.json")

    fun deleteOthers(ctx: Context, keep: String) = deleteOthers(ctx, setOf(keep))

    fun deleteOthers(ctx: Context, keep: Set<String>) {
        val files = ctx.filesDir.listFiles() ?: return
        val names = keep.map { "catalog_$it.json" }.toSet()
        for (f in files) {
            if (f.name.startsWith("catalog_") && f.name !in names) f.delete()
        }
    }

    fun clear(ctx: Context) {
        val files = ctx.filesDir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith("catalog_")) f.delete()
        }
    }

    fun save(ctx: Context, key: String, c: Catalog) {
        val target = file(ctx, key)
        val tmp = File(ctx.filesDir, "catalog_$key.tmp")
        JsonWriter(OutputStreamWriter(tmp.outputStream().buffered(64 * 1024), Charsets.UTF_8)).use { w ->
            w.beginObject()
            w.name("f").value(FORMAT.toLong())
            w.name("at").value(c.builtAt)
            w.name("movies").beginArray()
            for (t in c.movies) writeTitle(w, t)
            w.endArray()
            w.name("series").beginArray()
            for (t in c.series) writeTitle(w, t)
            w.endArray()
            w.name("channels").beginArray()
            for (ch in c.channels) writeChannel(w, ch)
            w.endArray()
            w.name("cc").beginArray()
            for (n in c.channelCategories) w.value(n)
            w.endArray()
            w.endObject()
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    private fun writeTitle(w: JsonWriter, t: Title) {
        w.beginObject()
        w.name("k").value(t.key)
        w.name("s").value(t.series)
        w.name("n").value(t.name)
        w.name("y").value(t.year)
        if (t.image != null) w.name("i").value(t.image)
        w.name("r").value(t.rating.toDouble())
        w.name("a").value(t.added)
        w.name("g").beginArray()
        for (g in t.genres) w.value(g)
        w.endArray()
        w.name("p").beginArray()
        for (p in t.platforms) w.value(p)
        w.endArray()
        if (t.plot.isNotEmpty()) w.name("pl").value(t.plot)
        if (t.launch) w.name("lc").value(true)
        w.name("v").beginArray()
        for (v in t.versions) {
            w.beginArray()
            w.value(v.id)
            w.value(v.ext)
            w.value(v.leg)
            w.value(v.is4k)
            w.value(v.cam)
            w.value(v.cc)
            w.endArray()
        }
        w.endArray()
        w.endObject()
    }

    private fun writeChannel(w: JsonWriter, c: Channel) {
        w.beginObject()
        w.name("k").value(c.key)
        w.name("n").value(c.name)
        if (c.icon != null) w.name("i").value(c.icon)
        w.name("c").value(c.category)
        w.name("s").beginArray()
        for (s in c.sources) {
            w.beginArray()
            w.value(s.id)
            w.value(s.quality)
            w.value(s.rank.toLong())
            w.value(s.group)
            w.value(s.label)
            w.value(s.arch.toLong())
            w.value(s.cc)
            w.endArray()
        }
        w.endArray()
        w.endObject()
    }

    /** Devolve null se não existe, está velho (formato) ou corrompido. */
    fun load(ctx: Context, key: String): Catalog? {
        val f = file(ctx, key)
        if (!f.exists()) return null
        return try {
            JsonReader(InputStreamReader(f.inputStream().buffered(64 * 1024), Charsets.UTF_8)).use { r -> read(r) }
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    private fun read(r: JsonReader): Catalog? {
        var format = 0
        var at = 0L
        var movies: List<Title> = emptyList()
        var series: List<Title> = emptyList()
        var channels: List<Channel> = emptyList()
        var cc: List<String> = emptyList()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "f" -> format = r.nextInt()
                "at" -> at = r.nextLong()
                "movies" -> movies = readTitles(r)
                "series" -> series = readTitles(r)
                "channels" -> channels = readChannels(r)
                "cc" -> cc = readStrings(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (format != FORMAT) return null
        return Catalog(movies, series, channels, cc, at)
    }

    private fun readStrings(r: JsonReader): List<String> {
        val out = ArrayList<String>()
        r.beginArray()
        while (r.hasNext()) out.add(r.nextString())
        r.endArray()
        return out
    }

    private fun readTitles(r: JsonReader): List<Title> {
        val out = ArrayList<Title>()
        r.beginArray()
        while (r.hasNext()) out.add(readTitle(r))
        r.endArray()
        return out
    }

    private fun readTitle(r: JsonReader): Title {
        var key = ""
        var series = false
        var name = ""
        var year = ""
        var image: String? = null
        var rating = 0f
        var added = 0L
        var genres: List<String> = emptyList()
        var platforms: List<String> = emptyList()
        var plot = ""
        var launch = false
        val versions = ArrayList<Version>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "k" -> key = r.nextString()
                "s" -> series = r.nextBoolean()
                "n" -> name = r.nextString()
                "y" -> year = r.nextString()
                "i" -> image = r.nextString()
                "r" -> rating = r.nextDouble().toFloat()
                "a" -> added = r.nextLong()
                "g" -> genres = readStrings(r)
                "p" -> platforms = readStrings(r)
                "pl" -> plot = r.nextString()
                "lc" -> launch = r.nextBoolean()
                "v" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginArray()
                        val id = r.nextString()
                        val ext = r.nextString()
                        val leg = r.nextBoolean()
                        val k4 = r.nextBoolean()
                        val cam = r.nextBoolean()
                        val vcc = if (r.hasNext()) r.nextString() else ""
                        r.endArray()
                        versions.add(Version(id, ext, leg, k4, cam, vcc))
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Title(key, series, name, year, image, rating, added, genres, platforms, plot, launch, versions)
    }

    private fun readChannels(r: JsonReader): List<Channel> {
        val out = ArrayList<Channel>()
        r.beginArray()
        while (r.hasNext()) {
            var key = ""
            var name = ""
            var icon: String? = null
            var category = ""
            val sources = ArrayList<Source>()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "k" -> key = r.nextString()
                    "n" -> name = r.nextString()
                    "i" -> icon = r.nextString()
                    "c" -> category = r.nextString()
                    "s" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            r.beginArray()
                            val id = r.nextString()
                            val q = r.nextString()
                            val rank = r.nextInt()
                            val group = r.nextString()
                            val label = r.nextString()
                            val arch = if (r.hasNext()) r.nextInt() else 0
                            val scc = if (r.hasNext()) r.nextString() else ""
                            r.endArray()
                            sources.add(Source(id, q, rank, group, label, arch, scc))
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out.add(Channel(key, name, icon, category, sources))
        }
        r.endArray()
        return out
    }
}
