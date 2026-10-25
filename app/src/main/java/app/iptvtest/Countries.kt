package app.iptvtest

import java.util.Locale

/**
 * País/idioma de cada categoria, lido do nome que o provedor deu ("PT | FILMES", "|BR| SÉRIES", "[UK] Sports"...).
 * Serve para listas com conteúdo de vários países: dá para escolher quais mostrar. O que não tem marca de país
 * fica sempre visível (listas de um país só, como a brasileira, não mudam em nada).
 */
object Countries {

    /** Código -> nome mostrado. A ordem é a da lista nos Ajustes. */
    val NAMES: LinkedHashMap<String, String> = linkedMapOf(
        "BR" to "Brasil", "PT" to "Portugal", "ES" to "Espanha", "FR" to "França", "IT" to "Itália", "DE" to "Alemanha",
        "UK" to "Reino Unido", "US" to "EUA", "EN" to "Inglês", "LAT" to "América Latina", "MX" to "México",
        "AR" to "Árabe", "TR" to "Turquia", "NL" to "Holanda", "BE" to "Bélgica", "PL" to "Polônia", "RU" to "Rússia",
        "GR" to "Grécia", "RO" to "Romênia", "IN" to "Índia", "CA" to "Canadá", "CH" to "Suíça", "SE" to "Suécia",
        "NO" to "Noruega", "DK" to "Dinamarca", "FI" to "Finlândia", "AL" to "Albânia", "RS" to "Sérvia", "HR" to "Croácia",
        "CN" to "China", "JP" to "Japão", "KR" to "Coreia", "AO" to "Angola", "MZ" to "Moçambique",
    )

    private val ALIAS: Map<String, String> = mapOf(
        "GB" to "UK", "ENG" to "EN", "USA" to "US", "POR" to "PT", "BRA" to "BR", "BRZ" to "BR", "BRASIL" to "BR",
        "SP" to "ES", "ESP" to "ES", "SPA" to "ES", "FRA" to "FR", "FRE" to "FR", "ITA" to "IT", "GER" to "DE", "DEU" to "DE",
        "ARA" to "AR", "ARB" to "AR", "TUR" to "TR", "NED" to "NL", "POL" to "PL", "RUS" to "RU", "LATAM" to "LAT",
        "LA" to "LAT", "MEX" to "MX", "GRE" to "GR", "ROM" to "RO", "IND" to "IN", "CAN" to "CA", "SWE" to "SE",
    )

    /** Palavras (sem acento, minúsculas) que também denunciam o país no meio do nome. */
    private val WORDS: Map<String, String> = mapOf(
        "portugal" to "PT", "portugues" to "PT", "brasil" to "BR", "brazil" to "BR", "brasileiro" to "BR",
        "espanha" to "ES", "spain" to "ES", "espana" to "ES", "espanol" to "ES", "franca" to "FR", "france" to "FR",
        "francais" to "FR", "italia" to "IT", "italy" to "IT", "italiano" to "IT", "alemanha" to "DE", "germany" to "DE",
        "deutsch" to "DE", "deutschland" to "DE", "england" to "UK", "british" to "UK", "reino unido" to "UK",
        "usa" to "US", "america" to "US", "arabic" to "AR", "arabe" to "AR", "turkey" to "TR", "turquia" to "TR",
        "turkish" to "TR", "india" to "IN", "latino" to "LAT", "latin" to "LAT", "mexico" to "MX",
    )

    private val PREFIX = Regex("""^[\s|\[({*•#>·\-]*([A-Za-z]{2,5})(?=[\s|\])}:\-–—_.,/]|$)""")

    fun label(code: String): String = NAMES[code] ?: EXTRA[code] ?: code

    private val EXTRA = mapOf("AF" to "África", "EXYU" to "Ex-Iugoslávia", "AFR" to "África", "ASIA" to "Ásia", "LA" to "América Latina", "CAR" to "Caribe", "IR" to "Irã", "PK" to "Paquistão", "BD" to "Bangladesh", "SO" to "Somália", "ET" to "Etiópia", "NG" to "Nigéria", "MA" to "Marrocos", "DZ" to "Argélia", "TN" to "Tunísia", "EG" to "Egito", "SA" to "Arábia Saudita", "IL" to "Israel", "UA" to "Ucrânia", "BG" to "Bulgária", "HU" to "Hungria", "CZ" to "Tchéquia", "SK" to "Eslováquia", "SI" to "Eslovênia", "BA" to "Bósnia", "MK" to "Macedônia", "LT" to "Lituânia", "LV" to "Letônia", "EE" to "Estônia", "AT" to "Áustria", "IE" to "Irlanda", "AU" to "Austrália", "NZ" to "Nova Zelândia", "PH" to "Filipinas", "TH" to "Tailândia", "VN" to "Vietnã", "ID" to "Indonésia", "MY" to "Malásia", "CL" to "Chile", "CO" to "Colômbia", "PE" to "Peru", "VE" to "Venezuela", "UY" to "Uruguai", "PY" to "Paraguai", "BO" to "Bolívia", "EC" to "Equador", "DO" to "Rep. Dominicana", "PR" to "Porto Rico", "CU" to "Cuba", "KU" to "Curdistão", "AM" to "Armênia", "AZ" to "Azerbaijão", "GE" to "Geórgia", "KZ" to "Cazaquistão")

    /** A sigla é de um país/idioma conhecido (inclui apelidos como GB, USA, BRA)? */
    fun isCodeToken(t: String): Boolean {
        val u = t.uppercase(Locale.ROOT)
        return NAMES.containsKey(u) || ALIAS.containsKey(u)
    }

    private val NAME_PREFIX = Regex("""^[\s|\[({*•#>·]*([A-Za-z]{2,4})\s*(?:[:|\])\-])""")

    /** País indicado só pelo começo do nome de um canal ("FR: Eurosport", "[PT] Sport TV", "BR| Globo"), ou null. */
    fun prefixTag(channelName: String): String? {
        val m = NAME_PREFIX.find(channelName) ?: return null
        val c = m.groupValues[1].uppercase(Locale.ROOT)
        val canon = ALIAS[c] ?: c
        if (NAMES.containsKey(canon)) return canon
        // sigla desconhecida mas com cara de país/região ("AF:", "EXYU|"): vale como etiqueta própria
        return if (canon.length in 2..4 && canon !in NOT_CODES) canon else null
    }

    private val NOT_CODES = setOf("TV", "HD", "FHD", "UHD", "SD", "VIP", "PPV", "LIVE", "NEW", "THE", "BEIN", "ESPN", "SKY", "BBC", "CNN", "FOX", "NBC", "CBS", "RTP", "SIC", "TVI", "MAX", "HBO", "RAW", "BOX", "VOD", "SUB", "DUB")

    /** O país/idioma que o nome da categoria indica, ou null se não indicar nenhum. */
    fun tagOf(rawName: String): String? {
        val m = PREFIX.find(rawName)
        if (m != null) {
            val c = m.groupValues[1].uppercase(Locale.ROOT)
            val canon = ALIAS[c] ?: c
            if (NAMES.containsKey(canon)) return canon
        }
        val f = Organizer.fold(rawName)
        for ((w, code) in WORDS) {
            if (f == w || f.startsWith("$w ") || f.endsWith(" $w") || f.contains(" $w ")) return code
        }
        return null
    }

    /** O que o app deve mostrar com o filtro [sel]. null ou vazio = tudo. O que não tem marca de país sempre aparece. */
    fun filter(c: Catalog, sel: Set<String>?): Catalog {
        if (sel == null || sel.isEmpty()) return c
        fun ok(cc: String) = cc.isEmpty() || sel.contains(cc)
        val movies = ArrayList<Title>(c.movies.size)
        for (t in c.movies) {
            val v = t.versions.filter { ok(it.cc) }
            if (v.isNotEmpty()) movies.add(if (v.size == t.versions.size) t else t.withVersions(v))
        }
        val series = ArrayList<Title>(c.series.size)
        for (t in c.series) {
            val v = t.versions.filter { ok(it.cc) }
            if (v.isNotEmpty()) series.add(if (v.size == t.versions.size) t else t.withVersions(v))
        }
        val ch = ArrayList<Channel>(c.channels.size)
        for (x in c.channels) {
            val s = x.sources.filter { ok(it.cc) }
            if (s.isNotEmpty()) ch.add(if (s.size == x.sources.size) x else x.withSources(s))
        }
        return Catalog(movies, series, ch, c.channelCategories, c.builtAt)
    }

    /** Países encontrados no catálogo: código -> (títulos, canais). */
    fun stats(c: Catalog): LinkedHashMap<String, Pair<Int, Int>> {
        val titles = HashMap<String, Int>()
        val chans = HashMap<String, Int>()
        for (t in c.movies) for (cc in t.versions.map { it.cc }.toSet()) if (cc.isNotEmpty()) titles[cc] = (titles[cc] ?: 0) + 1
        for (t in c.series) for (cc in t.versions.map { it.cc }.toSet()) if (cc.isNotEmpty()) titles[cc] = (titles[cc] ?: 0) + 1
        for (x in c.channels) for (cc in x.sources.map { it.cc }.toSet()) if (cc.isNotEmpty()) chans[cc] = (chans[cc] ?: 0) + 1
        val out = LinkedHashMap<String, Pair<Int, Int>>()
        val order = ArrayList<String>(NAMES.keys)
        for (k in titles.keys + chans.keys) if (!order.contains(k)) order.add(k)
        for (k in order) {
            val a = titles[k] ?: 0
            val b = chans[k] ?: 0
            if (a + b > 0) out[k] = Pair(a, b)
        }
        return out
    }
}
