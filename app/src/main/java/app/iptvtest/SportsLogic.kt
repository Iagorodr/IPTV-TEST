package app.iptvtest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * Esportes: modelos e regras puras (sem Android), para dar para testar fora do aparelho.
 *  - de onde vêm os jogos (ESPN) está em SportsApi.kt;
 *  - a tela está em SportsUi.kt.
 */

enum class SportKind(val label: String) {
    F1("Fórmula 1"),
    UFC("UFC"),
    TENNIS("Tênis"),
}

/** Time escolhido pela pessoa. [leagues] são as competições onde procuramos os jogos dele. */
class FavTeam(
    val id: String,
    val name: String,
    val short: String,
    val logo: String?,
    val leagues: List<String>,
    val national: Boolean,
)

/** Um lado de um jogo. [names] são os nomes (sem acento, minúsculos) usados para casar com os canais da lista. */
class Side(
    val id: String,
    val name: String,
    val short: String,
    val logo: String?,
    val score: String?,
    val names: List<String>,
)

class Game(
    val id: String,
    val leagueSlug: String,
    val leagueName: String,
    val startMs: Long,
    /** 0 = ainda vai começar, 1 = ao vivo, 2 = terminou */
    val state: Int,
    val detail: String,
    val home: Side,
    val away: Side,
) {
    val key: String get() = leagueSlug + "|" + id
}

class SportEvent(
    val kind: SportKind,
    val id: String,
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val state: Int,
)

class LeagueDef(val slug: String, val label: String)

class Country(
    val code: String,
    val name: String,
    /** Na ordem: divisão principal primeiro (no Brasil: Série A, Série B). */
    val leagues: List<LeagueDef>,
    /** Copas e competições continentais da divisão principal. */
    val topExtras: List<String>,
    /** Copas das divisões de baixo (ex.: Série B joga a Copa do Brasil). */
    val lowerExtras: List<String>,
    /** Nome da seleção como a ESPN escreve (em inglês). */
    val national: String,
    val nationalLabel: String,
    /** 0 = Europa, 1 = América do Sul, 2 = América do Norte/Central */
    val confed: Int,
)

object SportsData {

    private val UEFA3 = listOf("uefa.champions", "uefa.europa", "uefa.europa.conf")
    private val SULAM = listOf("conmebol.libertadores", "conmebol.sudamericana")

    val countries: List<Country> = listOf(
        Country(
            "br", "Brasil",
            listOf(LeagueDef("bra.1", "Série A"), LeagueDef("bra.2", "Série B")),
            listOf("bra.copa_do_brazil") + SULAM, listOf("bra.copa_do_brazil"),
            "Brazil", "Seleção Brasileira", 1,
        ),
        Country(
            "en", "Inglaterra", listOf(LeagueDef("eng.1", "Premier League")),
            listOf("eng.fa", "eng.league_cup") + UEFA3, emptyList(), "England", "Seleção Inglesa", 0,
        ),
        Country(
            "es", "Espanha", listOf(LeagueDef("esp.1", "LaLiga")),
            listOf("esp.copa_del_rey") + UEFA3, emptyList(), "Spain", "Seleção Espanhola", 0,
        ),
        Country(
            "it", "Itália", listOf(LeagueDef("ita.1", "Serie A")),
            listOf("ita.coppa_italia") + UEFA3, emptyList(), "Italy", "Seleção Italiana", 0,
        ),
        Country(
            "de", "Alemanha", listOf(LeagueDef("ger.1", "Bundesliga")),
            listOf("ger.dfb_pokal") + UEFA3, emptyList(), "Germany", "Seleção Alemã", 0,
        ),
        Country(
            "fr", "França", listOf(LeagueDef("fra.1", "Ligue 1")),
            listOf("fra.coupe_de_france") + UEFA3, emptyList(), "France", "Seleção Francesa", 0,
        ),
        Country(
            "pt", "Portugal", listOf(LeagueDef("por.1", "Liga Portugal")),
            UEFA3, emptyList(), "Portugal", "Seleção Portuguesa", 0,
        ),
        Country(
            "nl", "Holanda", listOf(LeagueDef("ned.1", "Eredivisie")),
            UEFA3, emptyList(), "Netherlands", "Seleção Holandesa", 0,
        ),
        Country(
            "be", "Bélgica", listOf(LeagueDef("bel.1", "Pro League")),
            UEFA3, emptyList(), "Belgium", "Seleção Belga", 0,
        ),
        Country(
            "tr", "Turquia", listOf(LeagueDef("tur.1", "Süper Lig")),
            UEFA3, emptyList(), "Turkiye", "Seleção Turca", 0,
        ),
        Country(
            "sc", "Escócia", listOf(LeagueDef("sco.1", "Premiership")),
            UEFA3, emptyList(), "Scotland", "Seleção Escocesa", 0,
        ),
        Country(
            "ar", "Argentina", listOf(LeagueDef("arg.1", "Liga Profesional")),
            SULAM, emptyList(), "Argentina", "Seleção Argentina", 1,
        ),
        Country(
            "mx", "México", listOf(LeagueDef("mex.1", "Liga MX")),
            emptyList(), emptyList(), "Mexico", "Seleção Mexicana", 2,
        ),
        Country(
            "us", "Estados Unidos", listOf(LeagueDef("usa.1", "MLS")),
            emptyList(), emptyList(), "United States", "Seleção dos EUA", 2,
        ),
    )

    fun country(code: String): Country? = countries.firstOrNull { it.code == code }

    /** Competições onde procurar os jogos de um clube escolhido na liga [slug] do país. */
    fun clubLeagues(c: Country, slug: String): List<String> {
        val top = c.leagues.firstOrNull()?.slug == slug
        return listOf(slug) + (if (top) c.topExtras else c.lowerExtras)
    }

    fun nationalLeagues(c: Country): List<String> = when (c.confed) {
        0 -> listOf("fifa.friendly", "uefa.nations", "uefa.euro", "fifa.world")
        1 -> listOf("fifa.friendly", "conmebol.america", "fifa.world")
        else -> listOf("fifa.friendly", "concacaf.gold", "fifa.world")
    }

    private val LABELS: Map<String, String> = mapOf(
        "bra.1" to "Brasileirão",
        "bra.2" to "Série B",
        "bra.copa_do_brazil" to "Copa do Brasil",
        "conmebol.libertadores" to "Libertadores",
        "conmebol.sudamericana" to "Sul-Americana",
        "eng.1" to "Premier League",
        "eng.fa" to "FA Cup",
        "eng.league_cup" to "Copa da Liga",
        "esp.1" to "LaLiga",
        "esp.copa_del_rey" to "Copa del Rey",
        "ita.1" to "Serie A",
        "ita.coppa_italia" to "Coppa Italia",
        "ger.1" to "Bundesliga",
        "ger.dfb_pokal" to "Copa da Alemanha",
        "fra.1" to "Ligue 1",
        "fra.coupe_de_france" to "Copa da França",
        "por.1" to "Liga Portugal",
        "ned.1" to "Eredivisie",
        "bel.1" to "Pro League",
        "tur.1" to "Süper Lig",
        "sco.1" to "Premiership",
        "arg.1" to "Liga Argentina",
        "mex.1" to "Liga MX",
        "usa.1" to "MLS",
        "uefa.champions" to "Champions League",
        "uefa.europa" to "Europa League",
        "uefa.europa.conf" to "Conference League",
        "fifa.friendly" to "Amistoso",
        "fifa.world" to "Copa do Mundo",
        "uefa.nations" to "Liga das Nações",
        "uefa.euro" to "Eurocopa",
        "conmebol.america" to "Copa América",
        "concacaf.gold" to "Copa Ouro",
    )

    fun leagueLabel(slug: String, fallback: String): String = LABELS[slug] ?: fallback.ifEmpty { slug }
}

// =========================================================================
// Datas e horários
// =========================================================================

object SportsTime {
    const val DAY = 86_400_000L
    private val WEEK = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

    /** A ESPN manda "2026-10-17T18:00Z" (ou com segundos). Devolve 0 se não entender. */
    fun parseIso(s: String?): Long {
        if (s.isNullOrEmpty()) return 0L
        val patterns = arrayOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        for (p in patterns) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                f.isLenient = false
                return f.parse(s)?.time ?: continue
            } catch (e: Exception) {
                // tenta o próximo formato
            }
        }
        return 0L
    }

    /** O dia (número inteiro) em que [ms] cai no fuso [tz]. */
    fun dayNumber(ms: Long, tz: TimeZone): Long = Math.floorDiv(ms + tz.getOffset(ms), DAY)

    fun clock(ms: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz, Locale.US)
        c.timeInMillis = ms
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun shortDate(ms: Long, tz: TimeZone): String {
        val c = Calendar.getInstance(tz, Locale.US)
        c.timeInMillis = ms
        return String.format(Locale.US, "%02d/%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1)
    }

    /** "Hoje", "Amanhã" ou "Sáb 17/10". */
    fun dayLabel(ms: Long, now: Long, tz: TimeZone): String {
        val d = dayNumber(ms, tz) - dayNumber(now, tz)
        if (d == 0L) return "Hoje"
        if (d == 1L) return "Amanhã"
        if (d == -1L) return "Ontem"
        val c = Calendar.getInstance(tz, Locale.US)
        c.timeInMillis = ms
        return WEEK[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + shortDate(ms, tz)
    }

    /** Data para a ESPN (ela agrupa os jogos pelo dia no horário da costa leste dos EUA). */
    fun espnDay(ms: Long): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        f.timeZone = TimeZone.getTimeZone("America/New_York")
        return f.format(java.util.Date(ms))
    }

    /** Minutos desde a meia-noite em Brasília (os horários da lista do provedor são de Brasília). */
    fun brtMinutes(ms: Long): Int {
        val brt = ms - 3L * 3_600_000L
        return Math.floorMod(brt / 60_000L, 1440L).toInt()
    }
}

// =========================================================================
// Quais jogos aparecem no início
// =========================================================================

object SportsPick {

    /**
     * Regra combinada: jogos de hoje (inclusive ao vivo) e de amanhã dos times escolhidos;
     * time sem jogo nesses dias mostra o próximo jogo dele.
     */
    fun games(all: List<Game>, favs: List<FavTeam>, now: Long, tz: TimeZone): List<Game> {
        if (favs.isEmpty()) return emptyList()
        val favIds = HashSet<String>()
        for (f in favs) favIds.add(f.id)
        val mine = all.filter { favIds.contains(it.home.id) || favIds.contains(it.away.id) }
        val today = SportsTime.dayNumber(now, tz)
        val shown = LinkedHashMap<String, Game>()
        for (g in mine) {
            val stale = g.state == 0 && g.startMs < now - 3L * 3_600_000L
            if (stale) continue
            val d = SportsTime.dayNumber(g.startMs, tz) - today
            if (g.state == 1 || d == 0L || d == 1L) shown[g.key] = g
        }
        for (f in favs) {
            val has = shown.values.any { it.home.id == f.id || it.away.id == f.id }
            if (has) continue
            val next = mine
                .filter { (it.home.id == f.id || it.away.id == f.id) && it.state == 0 && it.startMs >= now - 3L * 3_600_000L }
                .minByOrNull { it.startMs }
            if (next != null) shown[next.key] = next
        }
        return shown.values.sortedWith(
            compareBy<Game>(
                { if (it.state == 1) 0 else if (it.state == 0) 1 else 2 },
                { if (it.state == 2) -it.startMs else it.startMs },
            ),
        )
    }
}

// =========================================================================
// Ligar jogos e esportes aos canais da lista do provedor
// =========================================================================

class ParsedEvent(val timeMin: Int?, val a: String, val b: String)

class EventChan(val channel: Channel, val ev: ParsedEvent, val a: String, val b: String)

object EventNames {
    private val TIME = Regex("""\b(\d{1,2})\s*[:h]\s*(\d{2})\s*h?\b""", RegexOption.IGNORE_CASE)
    private val SEP = Regex("""\s+(?:vs\.?|v|x|×)\s+""", RegexOption.IGNORE_CASE)
    private val LEAD = Regex("""^\s*\(\d+\)\s*""")
    private val EMOJI = Regex("[⏰⚽]")
    private val SPACES = Regex("""\s+""")

    /** "13:45h | Lask Linz vs Liverpool" ou "(01) ⏰ 13:45 • LASK Linz x Liverpool •" → hora + dois times. */
    fun parse(raw: String): ParsedEvent? {
        val s = EMOJI.replace(raw, " ")
        val tm = TIME.find(s)
        var timeMin: Int? = null
        if (tm != null) {
            val h = tm.groupValues[1].toInt()
            val m = tm.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) timeMin = h * 60 + m
        }
        for (seg in s.split('|', '•', '·')) {
            val t = seg.trim()
            val m = SEP.find(t) ?: continue
            val left = tidy(t.substring(0, m.range.first))
            val right = tidy(t.substring(m.range.last + 1))
            if (left.length < 2 || right.length < 2) continue
            return ParsedEvent(timeMin, left, right)
        }
        return null
    }

    private fun tidy(x: String): String {
        var r = LEAD.replace(x, "")
        r = TIME.replace(r, " ")
        r = SPACES.replace(r, " ").trim(' ', '-', ':', '|', '.')
        return r
    }
}

object SportsChannels {

    private val CAT_KEYS = listOf(
        "premiere", "sportv", "espn", "esporte", "campeonato", "copa", "futebol", "jogo", "evento",
        "tnt", "dazn", "bein", "eleven", "sky sport", "paramount", "prime video", "disney",
        "f1", "ufc", "combate", "tenis", "tennis",
    )

    private val F1 = Regex("""(^| )(f1|formula (1|one|um))( |$)""")
    private val UFC = Regex("""(^| )(ufc|combate|fight pass|mma|bellator|pfl)( |$)""")
    private val TENNIS = Regex("""(^| )(tenis|tennis|atp|wta|roland garros|wimbledon|us open|australian open)( |$)""")

    fun isSportCategory(cat: String): Boolean {
        val f = Organizer.fold(cat)
        for (k in CAT_KEYS) if (f.contains(k)) return true
        return false
    }

    /** Categorias da lista que têm cara de esporte, na ordem em que o provedor as mostra. */
    fun sportCategories(categories: List<String>): List<String> = categories.filter { isSportCategory(it) }

    /** Canais "jogo avulso" da lista (os de "Jogos em Destaque" e afins) já lidos: hora + dois times. */
    fun indexEvents(channels: List<Channel>): List<EventChan> {
        val out = ArrayList<EventChan>()
        val catOk = HashMap<String, Boolean>()
        for (ch in channels) {
            val ok = catOk.getOrPut(ch.category) { isSportCategory(ch.category) }
            if (!ok) continue
            val ev = EventNames.parse(ch.name) ?: continue
            out.add(EventChan(ch, ev, Organizer.fold(ev.a), Organizer.fold(ev.b)))
        }
        return out
    }

    fun nameMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val s = if (a.length <= b.length) a else b
        val l = if (a.length <= b.length) b else a
        if (s.length < 4) return false
        if (l.startsWith("$s ") || l.endsWith(" $s") || l.contains(" $s ")) return true
        if (s.length >= 5 && l.startsWith(s)) return true
        return false
    }

    private fun sideMatches(provider: String, side: Side): Boolean {
        for (n in side.names) if (nameMatch(provider, n)) return true
        return false
    }

    private fun timeClose(providerMin: Int?, gameBrtMin: Int): Boolean {
        if (providerMin == null) return true
        val d = Math.abs(providerMin - gameBrtMin)
        return minOf(d, 1440 - d) <= 150
    }

    fun matchGame(g: Game, idx: List<EventChan>): List<EventChan> {
        val brt = SportsTime.brtMinutes(g.startMs)
        return idx.filter { e ->
            val direct = sideMatches(e.a, g.home) && sideMatches(e.b, g.away)
            val swapped = sideMatches(e.a, g.away) && sideMatches(e.b, g.home)
            (direct || swapped) && timeClose(e.ev.timeMin, brt)
        }
    }

    /** O canal com um nome que a pessoa entende ("18:45 · LASK Linz x Liverpool"), na hora do aparelho. */
    fun display(e: EventChan, now: Long, tz: TimeZone): Channel {
        val ch = e.channel
        val t = e.ev.timeMin
        val name = if (t == null) {
            e.ev.a + " x " + e.ev.b
        } else {
            val deltaMin = tz.getOffset(now) / 60_000 + 180
            val local = Math.floorMod(t + deltaMin, 1440)
            String.format(Locale.US, "%02d:%02d", local / 60, local % 60) + " · " + e.ev.a + " x " + e.ev.b
        }
        return Channel(ch.key, name, ch.icon, ch.category, ch.sources)
    }

    fun forKind(kind: SportKind, channels: List<Channel>): List<Channel> {
        val re = when (kind) {
            SportKind.F1 -> F1
            SportKind.UFC -> UFC
            SportKind.TENNIS -> TENNIS
        }
        val out = ArrayList<Channel>()
        for (ch in channels) {
            val cat = Organizer.fold(ch.category)
            val byName = re.containsMatchIn(ch.fold)
            val byCat = kind == SportKind.F1 && (cat == "f1" || cat.startsWith("f1 "))
            if (byCat || (byName && isSportCategory(ch.category))) out.add(ch)
        }
        return out
    }
}
