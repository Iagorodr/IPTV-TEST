package app.iptvtest

import java.text.Normalizer
import java.util.Locale

/**
 * Pega o catálogo cru do provedor e organiza:
 *  - um card por título (dublado, legendado, 4K e cópias ficam dentro dele);
 *  - gêneros e plataformas a partir do NOME das categorias do provedor;
 *  - um card por canal (SD/HD/FHD/4K/H265 e servidores viram "fontes");
 *  - conteúdo adulto fora (só pelo nome da categoria, como combinado).
 */
object Organizer {

    private val WS = Regex("\\s+")

    /** Minúsculas, sem acento, só letras e números separados por um espaço. */
    fun fold(s: String): String {
        val d = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val sb = StringBuilder(d.length)
        var lastSpace = true
        for (ch in d) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            if (ch.isLetterOrDigit()) {
                sb.append(ch)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }

    private fun cleanName(raw: String?): String {
        if (raw == null) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch == '‎' || ch == '‏' || ch == '​' || ch == '﻿') continue
            if (ch.isWhitespace() || ch == ' ') sb.append(' ') else sb.append(ch)
        }
        return WS.replace(sb.toString(), " ").trim()
    }

    // ------------------------------------------------------------------
    // Nome do título: tags (L) legendado, (4K), (CAM)
    // ------------------------------------------------------------------

    private class Parsed(val base: String, val leg: Boolean, val k4: Boolean, val cam: Boolean, val year: String = "")

    private val TAG_PRE = Regex("^\\((L|4K|CAM)\\)\\s*", RegexOption.IGNORE_CASE)
    private val TAG_SUF = Regex("\\s*\\((L|4K|CAM)\\)\\s*$", RegexOption.IGNORE_CASE)

    private fun parseName(raw: String?): Parsed {
        var n = cleanName(raw)
        var leg = false
        var k4 = false
        var cam = false
        var changed = true
        while (changed) {
            changed = false
            val m1 = TAG_PRE.find(n)
            if (m1 != null) {
                val tag = m1.groupValues[1].uppercase(Locale.ROOT)
                if (tag == "L") leg = true else if (tag == "4K") k4 = true else cam = true
                n = n.substring(m1.range.last + 1).trim()
                changed = true
                continue
            }
            val m2 = TAG_SUF.find(n)
            if (m2 != null) {
                val tag = m2.groupValues[1].uppercase(Locale.ROOT)
                if (tag == "L") leg = true else if (tag == "4K") k4 = true else cam = true
                n = n.substring(0, m2.range.first).trim()
                changed = true
            }
        }
        // listas de vários países: "FR - Título (2026) (SE)" vira "Título"; a sigla do país já vem da categoria
        var yr = ""
        for (round in 0 until 3) {
            val before = n
            val mp = LEAD_PREFIX.find(n)
            if (mp != null) {
                val tok = mp.groupValues[1]
                if (Countries.isCodeToken(tok) || PLATFORM_CODES.contains(tok.uppercase(Locale.ROOT))) {
                    val rest = n.substring(mp.range.last + 1).trim()
                    if (rest.length >= 2) n = rest
                }
            }
            val my = TRAIL_YEAR.find(n)
            if (my != null) {
                val rest = n.substring(0, my.range.first).trim()
                if (rest.length >= 2) {
                    if (yr.isEmpty()) yr = my.groupValues[1]
                    n = rest
                }
            }
            val mc = TRAIL_CODE.find(n)
            if (mc != null) {
                val rest = n.substring(0, mc.range.first).trim()
                if (rest.length >= 2) n = rest
            }
            if (n == before) break
        }
        return Parsed(n, leg, k4, cam, yr)
    }

    private val LEAD_PREFIX = Regex("^([A-Z][A-Z0-9+]{1,3})\\s*[-|:]\\s+")
    private val TRAIL_YEAR = Regex("\\s*[(\\[]((?:19|20)\\d\\d)[)\\]]\\s*$")
    private val TRAIL_CODE = Regex("\\s*[(\\[]([A-Z]{2,3})[)\\]]\\s*$")
    private val PLATFORM_CODES = setOf(
        "NF", "NFX", "AMZ", "APV", "ATV", "ATP", "DSNP", "DNP", "DSN", "HBO", "HMX", "MAX", "PMT", "PRM", "HLU", "HULU",
        "STZ", "SKY", "CRU", "GPL", "CNP", "TVP", "PCK", "UNI", "DIS", "YT", "SHO", "ROK", "TUB", "PLT", "CRA",
    )

    // ------------------------------------------------------------------
    // Categorias do provedor -> gênero / plataforma
    // ------------------------------------------------------------------

    private val GENRES: Map<String, String> = mapOf(
        "acao" to "Ação", "action" to "Ação",
        "aventura" to "Aventura", "adventure" to "Aventura",
        "animacao" to "Animação", "animation" to "Animação",
        "anime" to "Anime",
        "biografia" to "Biografia",
        "classicos" to "Clássicos",
        "comedia" to "Comédia", "comedy" to "Comédia",
        "crime" to "Crime",
        "documentarios" to "Documentários", "documentario" to "Documentários", "documentary" to "Documentários",
        "drama" to "Drama",
        "familia" to "Família", "family" to "Família",
        "fantasia" to "Fantasia", "fantasy" to "Fantasia",
        "faroeste" to "Faroeste", "western" to "Faroeste",
        "ficcao cientifica" to "Ficção Científica", "sci fi" to "Ficção Científica", "science fiction" to "Ficção Científica",
        "guerra" to "Guerra", "war" to "Guerra",
        "historia" to "História", "history" to "História",
        "infantil" to "Infantil", "kids" to "Infantil",
        "misterio" to "Mistério", "mystery" to "Mistério",
        "musica" to "Música", "music" to "Música",
        "policial" to "Policial",
        "romance" to "Romance",
        "suspense" to "Suspense", "thriller" to "Suspense",
        "terror" to "Terror", "horror" to "Terror",
        "nacionais" to "Nacionais",
        "religiosos" to "Religiosos",
        "dorama" to "Dorama",
        "reality" to "Reality",
        "shows" to "Shows",
        "tokusatsu" to "Tokusatsu",
        "turcas" to "Turcas",
        // outras línguas (listas de vários países)
        "accion" to "Ação", "aktion" to "Ação", "azione" to "Ação",
        "aventuras" to "Aventura", "abenteuer" to "Aventura", "avventura" to "Aventura",
        "animacion" to "Animação", "animazione" to "Animação", "dessin anime" to "Animação", "dessins animes" to "Animação", "trickfilm" to "Animação",
        "comedie" to "Comédia", "comedias" to "Comédia", "commedia" to "Comédia", "komodie" to "Comédia",
        "documentales" to "Documentários", "documentaires" to "Documentários", "documentaire" to "Documentários", "dokumentation" to "Documentários", "dokumentationen" to "Documentários", "documentari" to "Documentários",
        "drame" to "Drama", "drammatico" to "Drama", "dramma" to "Drama",
        "terreur" to "Terror", "horreur" to "Terror", "epouvante" to "Terror", "miedo" to "Terror", "paura" to "Terror", "grusel" to "Terror",
        "ciencia ficcion" to "Ficção Científica", "fantascienza" to "Ficção Científica", "science fiction" to "Ficção Científica",
        "fantastique" to "Fantasia", "fantastico" to "Fantasia",
        "romantique" to "Romance", "romantico" to "Romance", "romantik" to "Romance", "romantica" to "Romance",
        "krimi" to "Crime", "policier" to "Policial", "policiaco" to "Policial",
        "guerre" to "Guerra", "krieg" to "Guerra", "guerra" to "Guerra",
        "familial" to "Família", "familie" to "Família", "famiglia" to "Família", "familiar" to "Família",
        "enfants" to "Infantil", "ninos" to "Infantil", "kinder" to "Infantil", "bambini" to "Infantil", "jeunesse" to "Infantil",
        "mysterio" to "Mistério", "misterio" to "Mistério", "mystere" to "Mistério",
        "biography" to "Biografia", "biographie" to "Biografia", "biografia" to "Biografia",
        "historique" to "História", "geschichte" to "História", "storia" to "História",
    )

    private val GENERIC_PREFIX = listOf("filmes ", "filme ", "movies ", "movie ", "films ", "film ", "peliculas ", "pelicula ", "series ", "serie ", "serien ", "tv shows ", "shows ", "vod ")

    /** O gênero que o nome de uma categoria indica, mesmo com prefixo de país ("BR | FILMES | AÇÃO") ou palavra genérica. */
    private fun genreOfName(cleaned: String): String? {
        for (seg in cleaned.split('|', '/', '·', ':', '-', '–', '—', '(', ')', '[', ']')) {
            var f = fold(seg)
            if (f.isEmpty()) continue
            for (p in GENERIC_PREFIX) if (f.startsWith(p)) { f = f.removePrefix(p).trim(); break }
            val g = GENRES[f]
            if (g != null) return g
        }
        return null
    }

    private val GENRE_EXTRA: Map<String, List<String>> = mapOf(
        "acao e aventura" to listOf("Ação", "Aventura"),
        "action adventure" to listOf("Ação", "Aventura"),
        "sci fi fantasy" to listOf("Ficção Científica", "Fantasia"),
        "ficcao cientifica e fantasia" to listOf("Ficção Científica", "Fantasia"),
        "guerra e politica" to listOf("Guerra"),
        "war politics" to listOf("Guerra"),
        "soap" to listOf("Novelas"),
        "novela" to listOf("Novelas"),
    )

    private val PLATFORMS: List<Pair<String, String>> = listOf(
        "netflix" to "Netflix",
        "prime" to "Prime Video",
        "amazon" to "Prime Video",
        "disney" to "Disney+",
        "hbo" to "HBO Max",
        "apple" to "Apple TV+",
        "globo play" to "Globoplay",
        "globoplay" to "Globoplay",
        "paramount" to "Paramount+",
        "telecine" to "Telecine",
        "looke" to "Looke",
        "universal" to "Universal+",
        "crunchyroll" to "Crunchyroll",
        "star+" to "Star+",
        "starz" to "Starz",
        "discovery" to "Discovery+",
        "brasil paralelo" to "Brasil Paralelo",
    )

    private fun genreFromText(text: String): List<String> {
        val out = ArrayList<String>()
        for (part in text.split(',', '/', '|')) {
            val f = fold(part)
            if (f.isEmpty()) continue
            val extra = GENRE_EXTRA[f]
            if (extra != null) {
                out.addAll(extra)
                continue
            }
            val g = GENRES[f]
            if (g != null) out.add(g)
        }
        return out
    }

    private class CatInfo(
        val adult: Boolean,
        val genre: String?,
        val platform: String?,
        val leg: Boolean,
        val launch: Boolean,
        val k4: Boolean,
    )

    private fun classify(name: String): CatInfo {
        val cleaned = cleanName(name)
        val adult = isAdultCategory(cleaned)
        val low = cleaned.lowercase(Locale.ROOT)
        var f = fold(cleaned)
        val leg = low.startsWith("(l)") || f == "legendados"
        if (leg && f.startsWith("l ")) f = f.substring(2)
        val core = f.removePrefix("series ").removePrefix("serie ").trim()
        var genre: String? = GENRES[core] ?: genreOfName(cleaned)
        if (genre == null && core.startsWith("novelas")) genre = "Novelas"
        var platform: String? = null
        for (p in PLATFORMS) {
            if (low.contains(p.first)) {
                platform = p.second
                break
            }
        }
        return CatInfo(
            adult = adult,
            genre = genre,
            platform = platform,
            leg = leg,
            launch = core.contains("lancamento"),
            k4 = core.contains("4k"),
        )
    }

    // ------------------------------------------------------------------
    // Filmes e séries
    // ------------------------------------------------------------------

    private class Acc(
        val key: String,
        val series: Boolean,
        var name: String,
        var nameFromLeg: Boolean,
        var year: String,
        var image: String?,
        var rating: Float,
        var added: Long,
        val genres: LinkedHashSet<String>,
        val platforms: LinkedHashSet<String>,
        var plot: String,
        var launch: Boolean,
        val versions: ArrayList<Version>,
        var nameCc: String = "",
    )

    private fun buildTitles(cats: List<Category>, items: List<RawItem>, series: Boolean): List<Title> {
        val info = HashMap<String, CatInfo>()
        val ccOf = HashMap<String, String>()
        for (c in cats) {
            info[c.id] = classify(c.name)
            val t = Countries.tagOf(c.name)
            if (t != null) ccOf[c.id] = t
        }
        val map = LinkedHashMap<String, Acc>()
        // mesmo código TMDB = mesmo filme/série, mesmo que o nome venha em outra língua
        val byTmdb = HashMap<String, String>()
        var nDone = 0
        for (raw in items) {
            if (++nDone % 500 == 0) LoadCounter.items = nDone
            var adult = false
            var cc = ""
            for (cid in raw.cats) {
                val t = ccOf[cid]
                if (t != null) {
                    cc = t
                    break
                }
            }
            var catLeg = false
            var cat4k = false
            var launch = false
            val genres = ArrayList<String>()
            val platforms = ArrayList<String>()
            for (cid in raw.cats) {
                val ci = info[cid] ?: continue
                if (ci.adult) adult = true
                if (ci.leg) catLeg = true
                if (ci.k4) cat4k = true
                if (ci.launch) launch = true
                val g = ci.genre
                if (g != null) genres.add(g)
                val p = ci.platform
                if (p != null) platforms.add(p)
            }
            if (adult) continue
            val parsed = parseName(raw.name)
            val keyText = fold(parsed.base)
            if (keyText.isEmpty()) continue
            val y = raw.year.trim().ifEmpty { parsed.year }
            val year = if (y.length == 4 && y.all { c -> c.isDigit() }) y else ""
            val key = (if (series) "s|" else "m|") + keyText + "|" + year
            if (series && raw.genre.isNotEmpty()) genres.addAll(genreFromText(raw.genre))
            val leg = parsed.leg || catLeg
            val ext = if (raw.ext.isEmpty() && !series) "mp4" else raw.ext
            val ver = Version(raw.id, ext, leg, parsed.k4 || cat4k, parsed.cam, cc)

            val tm = raw.tmdb
            var acc = map[key]
            if (acc == null && tm.isNotEmpty()) {
                val k2 = byTmdb[tm]
                if (k2 != null) acc = map[k2]
            }
            if (acc == null) {
                if (tm.isNotEmpty()) byTmdb.putIfAbsent(tm, key)
                map[key] = Acc(
                    key = key,
                    series = series,
                    name = parsed.base,
                    nameFromLeg = leg,
                    year = year,
                    image = raw.image,
                    rating = raw.rating,
                    added = raw.added,
                    genres = LinkedHashSet(genres),
                    platforms = LinkedHashSet(platforms),
                    plot = raw.plot,
                    launch = launch,
                    versions = arrayListOf(ver),
                    nameCc = cc,
                )
            } else {
                if (tm.isNotEmpty()) byTmdb.putIfAbsent(tm, acc.key)
                // o nome mostrado vem, de preferência, de uma versão em português
                if (acc.nameCc != "BR" && acc.nameCc != "PT" && (cc == "BR" || cc == "PT")) {
                    acc.name = parsed.base
                    acc.nameCc = cc
                    if (raw.image != null) acc.image = raw.image
                    acc.nameFromLeg = leg
                }
                if (acc.nameFromLeg && !leg) {
                    acc.name = parsed.base
                    acc.nameFromLeg = false
                    if (raw.image != null) acc.image = raw.image
                }
                if (acc.image == null) acc.image = raw.image
                if (raw.rating > acc.rating) acc.rating = raw.rating
                if (raw.added > acc.added) acc.added = raw.added
                acc.genres.addAll(genres)
                acc.platforms.addAll(platforms)
                if (acc.plot.isEmpty()) acc.plot = raw.plot
                if (launch) acc.launch = true
                acc.versions.add(ver)
            }
        }
        val out = ArrayList<Title>(map.size)
        for (a in map.values) {
            out.add(
                Title(
                    key = a.key,
                    series = a.series,
                    name = a.name,
                    year = a.year,
                    image = a.image,
                    rating = a.rating,
                    added = a.added,
                    genres = a.genres.toList(),
                    platforms = a.platforms.toList(),
                    plot = a.plot,
                    launch = a.launch,
                    versions = a.versions.toList(),
                ),
            )
        }
        return out
    }

    // ------------------------------------------------------------------
    // Canais
    // ------------------------------------------------------------------

    private val CH_TAG = Regex(
        "\\b(4K|UHD|FHD\\d*|FULL\\s?HD|HD\\d*|SD\\d*|H\\.?265|HEVC|RAW|50FPS|60FPS|BACKUP|ALT\\d*)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CH_CODE = Regex("\\(([A-Za-z]{2})\\)")
    private val Q_4K = Regex("\\b(4K|UHD)\\b", RegexOption.IGNORE_CASE)
    private val Q_FHD = Regex("\\b(FHD\\d*|FULL\\s?HD)\\b", RegexOption.IGNORE_CASE)
    private val Q_265 = Regex("\\b(H\\.?265|HEVC)\\b", RegexOption.IGNORE_CASE)
    private val Q_HD = Regex("\\bHD\\d*\\b", RegexOption.IGNORE_CASE)
    private val Q_SD = Regex("\\bSD\\d*\\b", RegexOption.IGNORE_CASE)
    private val IDX = Regex("\\b(?:FHD|HD|SD)(\\d+)\\b", RegexOption.IGNORE_CASE)
    private val TRAIL = Regex("[\\s\\-|:]+$")

    private fun stripChannel(raw: String): String {
        var n = cleanName(raw).replace("*", " ")
        n = CH_TAG.replace(n, " ")
        n = CH_CODE.replace(n, " ")
        return n
    }

    private fun channelKey(raw: String): String {
        return fold(stripChannel(raw).replace("+", " plus ").replace("&", " e "))
    }

    private fun displayName(raw: String): String {
        return WS.replace(TRAIL.replace(stripChannel(raw), ""), " ").trim()
    }

    private fun prettyName(cands: List<String>): String {
        val mixed = cands.firstOrNull { c -> c.any { it.isLowerCase() } && c.any { it.isUpperCase() } }
        if (mixed != null) return mixed
        val c = cands.firstOrNull() ?: return ""
        return c.split(' ').joinToString(" ") { w ->
            if (w.length <= 4) w.uppercase(Locale.ROOT) else w.lowercase(Locale.ROOT).replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    private fun qualityOf(name: String): String = when {
        Q_4K.containsMatchIn(name) -> "4K"
        Q_FHD.containsMatchIn(name) -> "FHD"
        Q_265.containsMatchIn(name) -> "H265"
        Q_HD.containsMatchIn(name) -> "HD"
        Q_SD.containsMatchIn(name) -> "SD"
        else -> "HD"
    }

    private fun rankOf(q: String): Int = when (q) {
        "4K" -> 4
        "FHD" -> 3
        "H265" -> 3
        "HD" -> 2
        else -> 1
    }

    private fun buildChannels(cats: List<Category>, items: List<RawItem>): Pair<List<Channel>, List<String>> {
        val catName = HashMap<String, String>()
        for (c in cats) catName[c.id] = c.name.replace("*", "").trim()
        val catCc = HashMap<String, String>()
        for (c in cats) {
            val t = Countries.tagOf(c.name)
            if (t != null) catCc[c.id] = t
        }

        val groups = LinkedHashMap<String, ArrayList<RawItem>>()
        var nDone = 0
        for (raw in items) {
            if (++nDone % 500 == 0) LoadCounter.items = nDone
            val cn = raw.cats.firstOrNull()?.let { catName[it] } ?: ""
            if (isAdultCategory(cn)) continue
            val key = channelKey(raw.name)
            if (key.isEmpty()) continue
            groups.getOrPut(key) { ArrayList() }.add(raw)
        }

        val channels = ArrayList<Channel>(groups.size)
        for ((key, list) in groups) {
            val counts = LinkedHashMap<String, Int>()
            for (raw in list) {
                val cn = raw.cats.firstOrNull()?.let { catName[it] } ?: "Outros"
                if (cn.lowercase(Locale.ROOT).contains("4k")) continue
                counts[cn] = (counts[cn] ?: 0) + 1
            }
            var best: String? = null
            var bestN = 0
            for ((cn, n) in counts) {
                if (n > bestN) {
                    best = cn
                    bestN = n
                }
            }
            val fallback = list.firstNotNullOfOrNull { r -> r.cats.firstOrNull()?.let { catName[it] } } ?: "Outros"
            val category = best ?: fallback

            val sources = ArrayList<Source>()
            for (raw in list) {
                val q = qualityOf(raw.name)
                val idx = IDX.find(raw.name)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val grp = if (raw.name.contains("**")) "B" else "A"
                val label = q + (if (idx > 1) " $idx" else "") + " · Fonte " + grp
                sources.add(Source(raw.id, q, rankOf(q), grp, label, raw.arch, raw.cats.firstNotNullOfOrNull { catCc[it] } ?: Countries.prefixTag(raw.name) ?: ""))
            }
            val seen = HashMap<String, Int>()
            val fixed = ArrayList<Source>()
            val ordered = sources.sortedWith(
                compareByDescending<Source> { it.rank }.thenBy { it.group }.thenBy { it.label },
            )
            for (s in ordered) {
                val n = (seen[s.label] ?: 0) + 1
                seen[s.label] = n
                fixed.add(if (n == 1) s else s.copy(label = s.label + " (" + n + ")"))
            }

            val name = prettyName(list.map { displayName(it.name) }.filter { it.isNotEmpty() })
            if (name.isEmpty()) continue
            val icon = list.firstNotNullOfOrNull { it.image }
            channels.add(Channel("c|$key", name, icon, category, fixed))
        }

        val used = LinkedHashSet<String>()
        for (ch in channels) used.add(ch.category)
        val order = ArrayList<String>()
        for (c in cats) {
            val n = catName[c.id] ?: continue
            if (used.contains(n) && !order.contains(n)) order.add(n)
        }
        for (n in used) if (!order.contains(n)) order.add(n)
        val sortedOrder = order.filterNot { it.lowercase(Locale.ROOT).contains("cliente") } +
            order.filter { it.lowercase(Locale.ROOT).contains("cliente") }
        val rankOfCat = HashMap<String, Int>()
        for ((i, n) in sortedOrder.withIndex()) rankOfCat[n] = i
        val sortedChannels = channels.sortedWith(
            compareBy<Channel>({ rankOfCat[it.category] ?: 9999 }, { it.fold }),
        )
        return Pair(sortedChannels, sortedOrder)
    }

    // ------------------------------------------------------------------

    /** Avisa a tela de carregamento em que parte está. */
    @Volatile
    var phase: (String) -> Unit = {}

    fun build(
        vodCats: List<Category>,
        serCats: List<Category>,
        liveCats: List<Category>,
        vod: List<RawItem>,
        ser: List<RawItem>,
        live: List<RawItem>,
    ): Catalog {
        phase("Organizando filmes")
        val movies = buildTitles(vodCats, vod, series = false)
        phase("Organizando séries")
        val series = buildTitles(serCats, ser, series = true)
        phase("Organizando canais")
        val ch = buildChannels(liveCats, live)
        phase("Salvando na TV")
        return Catalog(movies, series, ch.first, ch.second, System.currentTimeMillis())
    }
}

/** Monta as prateleiras (listas horizontais) a partir dos títulos já organizados. */
/** Sorteio das prateleiras: o que aparece na frente muda a cada vez, mas títulos bons continuam com vantagem. */
object Mix {
    /** [spread] < 0: embaralha de verdade; > 0: nota + recência + sorteio de 0 até [spread] pontos. */
    fun arrange(list: List<Title>, rnd: java.util.Random, spread: Float): List<Title> {
        if (list.size < 2) return list
        if (spread < 0f) {
            val l = ArrayList(list)
            java.util.Collections.shuffle(l, rnd)
            return l
        }
        val keyed = ArrayList<Pair<Title, Float>>(list.size)
        for (t in list) keyed.add(Pair(t, t.score() + rnd.nextFloat() * spread))
        keyed.sortByDescending { it.second }
        val out = ArrayList<Title>(list.size)
        for (k in keyed) out.add(k.first)
        return out
    }

    const val GENRE = 55f
    const val SHUFFLE = -1f
}

object Shelves {

    class Built(
        val home: List<Shelf>,
        val movies: List<Shelf>,
        val series: List<Shelf>,
        /** Destaques do Início: filmes e séries misturados. */
        val featured: List<Title>,
        /** Destaques da página Filmes e da página Séries. */
        val featuredMovies: List<Title>,
        val featuredSeries: List<Title>,
        val index: HashMap<String, Title>,
        val all: List<Title>,
    )

    fun forTitles(titles: List<Title>, maxGenres: Int, rnd: java.util.Random): List<Shelf> {
        val out = ArrayList<Shelf>()
        val launches = titles.filter { it.launch && !it.onlyCam }
        if (launches.size >= 6) out.add(Shelf("Lançamentos", Mix.arrange(launches, rnd, Mix.SHUFFLE), Mix.SHUFFLE))
        val recent = titles.filter { !it.onlyCam }.sortedByDescending { it.added }.take(80)
        if (recent.size >= 6) out.add(Shelf("Adicionados recentemente", Mix.arrange(recent, rnd, Mix.SHUFFLE), Mix.SHUFFLE))

        val byGenre = HashMap<String, ArrayList<Title>>()
        val byPlat = HashMap<String, ArrayList<Title>>()
        for (t in titles) {
            for (g in t.genres) byGenre.getOrPut(g) { ArrayList() }.add(t)
            for (p in t.platforms) byPlat.getOrPut(p) { ArrayList() }.add(t)
        }
        val genres = byGenre.entries.filter { it.value.size >= 12 }.sortedByDescending { it.value.size }
        for ((i, e) in genres.withIndex()) {
            if (i >= maxGenres) break
            out.add(Shelf(e.key, Mix.arrange(e.value, rnd, Mix.GENRE), Mix.GENRE))
        }
        val plats = byPlat.entries.filter { it.value.size >= 12 }.sortedByDescending { it.value.size }
        for (e in plats) out.add(Shelf(e.key, Mix.arrange(e.value, rnd, Mix.GENRE), Mix.GENRE))
        return out
    }

    fun buildAll(c: Catalog, seed: Long): Built {
        val rnd = java.util.Random(seed)
        val all = ArrayList<Title>(c.movies.size + c.series.size)
        all.addAll(c.movies)
        all.addAll(c.series)
        val index = HashMap<String, Title>(all.size * 2)
        for (t in all) index[t.key] = t
        // destaque: lançamentos de plataformas conhecidas, com capa e nota decente (evita títulos obscuros)
        // filmes e séries se alternam, para o início não parecer "só filmes"
        fun best(list: List<Title>): List<Title> =
            list.filter { it.launch && !it.onlyCam && it.image != null && it.platforms.isNotEmpty() && it.rating >= 6f }
                .sortedByDescending { it.score() }
                .take(12)
        var pm = best(c.movies)
        // série quase nunca vem marcada como lançamento: usa as adicionadas há pouco, de plataforma conhecida e bem avaliadas
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        var ps = c.series
            .filter { it.image != null && !it.onlyCam && it.platforms.isNotEmpty() && it.rating >= 7f && (it.year.toIntOrNull() ?: 0) >= year - 2 }
            .sortedByDescending { it.added }
            .take(12)
        // se faltar gente para girar, completa com os mais bem avaliados (com capa) de cada tipo
        if (pm.size < 4) pm = c.movies.filter { it.image != null && !it.onlyCam && it.rating >= 6f }.sortedByDescending { it.score() }.take(12)
        if (ps.size < 4) ps = c.series.filter { it.image != null && !it.onlyCam && it.rating >= 6f }.sortedByDescending { it.score() }.take(12)
        val picks = ArrayList<Title>()
        for (i in 0 until maxOf(pm.size, ps.size)) {
            if (i < pm.size) picks.add(pm[i])
            if (i < ps.size) picks.add(ps[i])
        }
        val featured = if (picks.size >= 4) {
            picks
        } else {
            all.filter { it.launch && !it.onlyCam && it.image != null }.sortedByDescending { it.score() }.take(10)
        }
        return Built(
            home = forTitles(all, 8, rnd),
            movies = forTitles(c.movies, 40, rnd),
            series = forTitles(c.series, 40, rnd),
            featured = featured,
            featuredMovies = pm,
            featuredSeries = ps,
            index = index,
            all = all,
        )
    }
}
