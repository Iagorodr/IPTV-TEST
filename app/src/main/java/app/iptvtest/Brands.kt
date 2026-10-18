package app.iptvtest

/*
 * Canais: marcas com praças (Globo, Record, SBT, Band, RedeTV) e grupos de pastas.
 * Só regras puras (sem Android), para dar para testar fora do aparelho.
 */

object Brands {

    private class Rule(
        val key: String,
        val name: String,
        /** Tira o nome da marca do começo, sobrando só a praça ("Globo São Paulo" → "São Paulo"). */
        val strip: Regex,
        val test: (Channel) -> Boolean,
    )

    private val GLOBO_BAD = Regex("""(^| )(news|play|rural|nordeste news)( |$)""")
    private val SBT_BAD = Regex(
        """(^| )(cinema|crianca|humor|informacao|novelas|pop|saudade|series|silvio|simulcast|tvzyn|vtv|news)( |$)""",
    )
    private val REDETV = Regex("""(^| )rede ?tv( |$)|redetv""")

    // a ordem importa: "Record MTS" (que a lista põe na pasta da Band) fica com a Record
    private val RULES: List<Rule> = listOf(
        Rule("globo", "Globo", Regex("""^(tv\s+)?globo\b[\s\-–:]*""", RegexOption.IGNORE_CASE)) { ch ->
            val f = ch.fold
            val byCat = ch.category.startsWith("Globo ") && !GLOBO_BAD.containsMatchIn(f)
            val stray = f.startsWith("globo ") && !GLOBO_BAD.containsMatchIn(f) && Organizer.fold(ch.category) == "4k"
            byCat || stray
        },
        Rule("record", "Record", Regex("""^(a\s+)?(record\s*tv|recordtv|record)\b[\s\-–:]*""", RegexOption.IGNORE_CASE)) { ch ->
            val f = ch.fold
            !f.contains("news") && !f.startsWith("a ilha") && (f.startsWith("record") || Organizer.fold(ch.category) == "record")
        },
        Rule("sbt", "SBT", Regex("""^(\+\s*)?sbt\b[\s\-–:]*""", RegexOption.IGNORE_CASE)) { ch ->
            val f = ch.fold
            val sbt = Organizer.fold(ch.category) == "sbt" || (f.startsWith("sbt ") && Organizer.fold(ch.category) == "4k")
            sbt && !SBT_BAD.containsMatchIn(f)
        },
        Rule("band", "Band", Regex("""^band\b[\s\-–:]*""", RegexOption.IGNORE_CASE)) { ch ->
            val f = ch.fold
            Organizer.fold(ch.category) == "band" && !f.contains("sports") && !f.contains("news") && !f.startsWith("record")
        },
        Rule("redetv", "RedeTV", Regex("""^(rede\s*tv)\b[\s\-–:]*""", RegexOption.IGNORE_CASE)) { ch ->
            Organizer.fold(ch.category) == "abertos" && REDETV.containsMatchIn(ch.fold)
        },
    )

    private val REGION_ORDER = listOf("Sudeste", "Sul", "Nordeste", "Centro Oeste", "Norte")
    private val PREFERRED = setOf("sao paulo", "sp", "paulista", "nacional")

    private fun regionOf(rule: Rule, ch: Channel): String {
        if (rule.key == "globo") {
            return if (ch.category.startsWith("Globo ")) ch.category.removePrefix("Globo ").trim() else "Outras"
        }
        return ""
    }

    private fun labelOf(rule: Rule, ch: Channel): String {
        var l = rule.strip.replace(ch.name, "").trim().trim('-', '–', ':', ' ')
        if (l.isEmpty()) l = "Nacional"
        if (Organizer.fold(ch.category) == "4k") l += " (4K)"
        return l
    }

    /** A praça que toca quando a pessoa ainda não escolheu nenhuma: São Paulo/Nacional, senão a que tem mais fontes. */
    fun pickDefault(variants: List<Variant>): Variant {
        val pref = variants.filter { PREFERRED.contains(Organizer.fold(it.label)) }
        val pool = if (pref.isNotEmpty()) pref else variants
        return pool.maxByOrNull { it.channel.sources.size } ?: variants.first()
    }

    /** A praça escolhida da última vez (se ainda existir) ou a padrão. */
    fun variantFor(brand: Channel, lastKey: String?): Variant {
        if (lastKey != null) {
            val hit = brand.variants.firstOrNull { it.channel.key == lastKey }
            if (hit != null) return hit
        }
        return pickDefault(brand.variants)
    }

    private fun build(rule: Rule, list: List<Channel>): Channel {
        val vs = list.map { Variant(labelOf(rule, it), regionOf(rule, it), it) }
            .sortedWith(
                compareBy<Variant>(
                    { val i = REGION_ORDER.indexOf(it.region); if (i < 0) 99 else i },
                    { -it.channel.sources.size },
                    { Organizer.fold(it.label) },
                ),
            )
        val def = pickDefault(vs)
        val icon = def.channel.icon ?: vs.firstNotNullOfOrNull { it.channel.icon }
        return Channel("b|" + rule.key, rule.name, icon, rule.name, def.channel.sources, vs)
    }

    /**
     * Junta as praças de cada marca num cartão só. Devolve os cartões de marca (na ordem das regras)
     * e depois todos os outros canais como estavam.
     */
    fun group(channels: List<Channel>): List<Channel> {
        val buckets = LinkedHashMap<String, ArrayList<Channel>>()
        val rest = ArrayList<Channel>()
        for (ch in channels) {
            val rule = RULES.firstOrNull { it.test(ch) }
            if (rule == null) rest.add(ch) else buckets.getOrPut(rule.key) { ArrayList() }.add(ch)
        }
        val cards = ArrayList<Channel>()
        val loose = ArrayList<Channel>()
        for (rule in RULES) {
            val l = buckets[rule.key] ?: continue
            if (l.size < 2) loose.addAll(l) else cards.add(build(rule, l))
        }
        val out = ArrayList<Channel>(channels.size)
        out.addAll(cards)
        // quem ficou solto volta para o lugar de origem
        val looseSet = HashSet<String>()
        for (c in loose) looseSet.add(c.key)
        for (ch in channels) {
            if (looseSet.contains(ch.key) || (RULES.none { it.test(ch) })) out.add(ch)
        }
        return out
    }
}

/** As ~9 pastas de canais (no lugar das 47 do provedor). */
object ChannelGroups {

    /** O que é do próprio provedor do sinal (lista de jogos do dia, avisos, área do cliente), separado do resto. */
    const val PROVIDER = "Do provedor"

    /** Jogo avulso já com a hora local limpa pelo app ("17:45 · Lyon x Crystal Palace"). */
    private val CLEAN_EVENT = Regex("""^\d{1,2}:\d{2} · .+""")

    private val PROVIDER_CATS = listOf("jogos em destaque", "eventos em destaque", "jogos do dia", "jogos de hoje", "cliente")

    val ORDER = listOf(
        "Esportes",
        "TV aberta",
        "Notícias",
        "Filmes e séries",
        "Infantil e anime",
        "Variedades e reality",
        "Documentários",
        "Música e rádios",
        "Religiosos",
        "Outros",
        PROVIDER,
    )

    private val ESPORTES = listOf("jogo", "evento", "f1", "premiere", "sportv", "espn", "esporte", "copa", "campeonato", "ppv", "tnt", "combate", "ufc", "sport", "deporte", "desporto", "futebol", "football", "soccer", "calcio", "fussball", "futbol")
    private val ABERTA = listOf("globo", "record", "sbt", "band", "aberto", "geral", "general", "generalist", "allgemein", "ulusal", "entertainment")
    private val FILMES = listOf("disney", "paramount", "prime video", "film", "serie", "telecine", "hbo", "hallo", "runtime", "cine", "dorama", "novela", "movie", "pelicula", "kino")
    private val INFANTIL = listOf("infantil", "anime", "kids", "kinder", "enfants", "bambini", "ninos", "cartoon", "jeunesse")
    private val VARIEDADES = listOf("variedade", "entretenimento", "fazenda", "comedia", "reality", "lives", "youtube channels", "shows")
    private val MUSICA = listOf("radio", "urban", "music", "musica", "musik", "musique")
    private val MUSICA_NOME = listOf("zapping music", "mtv", "music box", "trace ", "radio")

    private fun has(f: String, keys: List<String>): Boolean {
        for (k in keys) if (f.contains(k)) return true
        return false
    }

    /** Nome da pasta do canal, ou null se ele deve ficar escondido. */
    fun groupOf(ch: Channel): String? {
        if (ch.isBrand) return "TV aberta"
        val c = Organizer.fold(ch.category)
        val n = ch.fold
        // lista de jogos do dia do provedor: pela pasta, ou pelo nome ("17:45 · A x B") se ela vier numa pasta nova
        if (has(c, PROVIDER_CATS) || CLEAN_EVENT.containsMatchIn(ch.name)) return PROVIDER
        if (c == "4k") {
            // o que sobrou da pasta 4K vai pelo nome
            return when {
                has(n, listOf("sportv", "espn", "premiere", "tnt", "f1", "esporte")) -> "Esportes"
                has(n, listOf("hbo", "telecine", "max", "disney", "cine", "filme", "serie")) -> "Filmes e séries"
                has(n, listOf("globo", "record", "sbt", "band", "redetv", "rbs")) -> "TV aberta"
                has(n, listOf("news", "noticia", "cnn")) -> "Notícias"
                else -> "Outros"
            }
        }
        if (has(c, ESPORTES)) return "Esportes"
        if (has(c, ABERTA)) return "TV aberta"
        if (has(c, listOf("noticia", "news", "nachrichten", "actualite", "notizie", "haber", "informacion"))) return "Notícias"
        if (has(c, INFANTIL)) return "Infantil e anime"
        if (has(c, listOf("document", "dokument"))) return "Documentários"
        if (has(c, listOf("religios"))) return "Religiosos"
        if (has(c, MUSICA)) return "Música e rádios"
        if (has(c, VARIEDADES) || has(c, listOf("variedades"))) {
            return if (has(n, MUSICA_NOME)) "Música e rádios" else "Variedades e reality"
        }
        if (has(c, FILMES)) return "Filmes e séries"
        return "Outros"
    }

    private val ESPORTES_FIRST = listOf("jogo", "evento", "sportv", "espn", "premiere", "tnt", "copa", "campeonato", "estaduais", "youtube", "f1")

    /** Dentro da pasta Esportes, o que mais interessa vem primeiro (jogos do dia, SporTV, ESPN, Premiere...). */
    fun order(ch: Channel, group: String): Int {
        if (group != "Esportes") return 0
        val c = Organizer.fold(ch.category)
        for ((i, k) in ESPORTES_FIRST.withIndex()) if (c.contains(k)) return i
        return ESPORTES_FIRST.size
    }

    fun isYoutube(ch: Channel): Boolean {
        val c = Organizer.fold(ch.category)
        return c.contains("youtube") || ch.fold.contains("youtube")
    }

    fun has4k(ch: Channel): Boolean = ch.sources.any { it.quality == "4K" }
}
