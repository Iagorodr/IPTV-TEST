package app.iptvtest

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * "Onde passa": tabelas de transmissão pesquisadas na internet e atualizadas toda semana, uma por país (BR, PT).
 * Duas camadas: [BroadcastComp] diz quem costuma passar cada competição ("costuma"); [BroadcastGame] diz onde passa
 * cada jogo dos próximos dias, marcado como confirmado (fonte específica) ou provável.
 * Aqui só as regras puras; ler o arquivo e baixar a versão nova está em BroadcastStore.kt.
 */

class BroadcastComp(val label: String, val options: List<String>, val note: String)

/** Um jogo da tabela: [date] é a data local no fuso da tabela; [tv] em ordem de probabilidade. */
class BroadcastGame(
    val date: String,
    val comp: String,
    val home: String,
    val away: String,
    val tv: List<String>,
    val confirmed: Boolean,
    val obs: String,
    val src: String,
)

class BroadcastTable(
    val updated: String,
    val comps: Map<String, BroadcastComp>,
    val games: List<BroadcastGame> = emptyList(),
    val country: String = "BR",
    val tz: String = "America/Sao_Paulo",
) {
    fun forKey(key: String): BroadcastComp? = comps[key]

    /** "16/10/2026" a partir de "2026-10-16". */
    fun updatedPretty(): String {
        val p = updated.split("-")
        return if (p.size == 3) p[2] + "/" + p[1] + "/" + p[0] else updated
    }
}

object Broadcasts {

    /** Palavras (sem acento) que identificam cada emissora entre os nomes de canais da lista do provedor. */
    private val HINTS: Map<String, List<String>> = mapOf(
        "Globo" to listOf("globo"),
        "SporTV" to listOf("sportv"),
        "Premiere" to listOf("premiere"),
        "ESPN" to listOf("espn"),
        "Record" to listOf("record"),
        "SBT" to listOf("sbt"),
        "TNT Sports" to listOf("tnt", "tnt sports"),
        "Space" to listOf("space"),
        "RedeTV" to listOf("redetv", "rede tv"),
        "CazéTV" to listOf("caze tv", "cazetv"),
        "Prime Video" to listOf("prime video"),
        "Disney+" to listOf("disney"),
        "Paramount+" to listOf("paramount"),
        "HBO Max" to listOf("hbo max"),
        "GE TV" to listOf("ge tv"),
        "Sportynet" to listOf("sportynet", "sporty net"),
        "Xsports" to listOf("xsports", "x sports"),
        "Jovem Pan" to listOf("jovem pan"),
        "Canal GOAT" to listOf("goat"),
        "Fox Sports" to listOf("fox sports"),
        "Apple TV" to listOf("apple tv"),
        "F1 TV" to listOf("f1 tv"),
        "Globoplay" to listOf("globoplay"),
        "OneFootball" to listOf("onefootball"),
        // Portugal
        "Sport TV" to listOf("sport tv"),
        "DAZN" to listOf("dazn"),
        "Eleven Sports" to listOf("eleven sports", "eleven"),
        "Canal 11" to listOf("canal 11"),
        "Benfica TV" to listOf("benfica tv", "sl benfica tv"),
        "Sporting TV" to listOf("sporting tv"),
        "FC Porto Canal" to listOf("porto canal", "fc porto tv"),
        "RTP1" to listOf("rtp 1", "rtp1"),
        "SIC" to listOf("sic"),
        "TVI" to listOf("tvi"),
        "Eurosport" to listOf("eurosport"),
        // Bélgica
        "Pickx" to listOf("pickx", "proximus"),
        "Telenet" to listOf("telenet", "play sports"),
        "VOO Sport" to listOf("voo sport", "voo"),
        "VRT" to listOf("vrt", "een"),
        "RTBF" to listOf("rtbf", "auvio", "la une"),
        "Streamz" to listOf("streamz"),
        // Espanha
        "Movistar+" to listOf("movistar"),
        "LaLiga TV" to listOf("laliga tv", "la liga tv", "laliga"),
        "Gol" to listOf("gol tv", "gol play"),
        "TVE" to listOf("tve", "la 1"),
        "Teledeporte" to listOf("teledeporte"),
        // Reino Unido
        "Sky Sports" to listOf("sky sports"),
        "TNT Sports UK" to listOf("tnt sports", "tnt"),
        "BBC" to listOf("bbc"),
        "ITV" to listOf("itv"),
        "Amazon Prime" to listOf("prime video", "amazon"),
        "Premier Sports" to listOf("premier sports"),
        // França
        "Canal+" to listOf("canal+", "canal plus"),
        "beIN Sports" to listOf("bein"),
        "Ligue 1+" to listOf("ligue 1"),
        "RMC Sport" to listOf("rmc sport", "rmc"),
        "France TV" to listOf("france tv", "france 2", "france 3"),
        // EUA
        "Peacock" to listOf("peacock"),
        "CBS" to listOf("cbs"),
        "NBC" to listOf("nbc"),
        "Fox" to listOf("fox"),
        "ESPN+" to listOf("espn"),
        "Univision" to listOf("univision"),
    )

    private fun hasWords(hay: String, words: String): Boolean = (" $hay ").contains(" $words ")

    private fun channelMatches(option: String, ch: Channel): Boolean {
        val hints = HINTS[option] ?: listOf(Organizer.fold(option))
        val hay = ch.searchFold
        for (h in hints) if (hasWords(hay, h)) return true
        return false
    }

    /** Quais emissoras da lista [options] aparecem na lista de canais do provedor. */
    fun inList(options: List<String>, channels: List<Channel>): Set<String> {
        val out = LinkedHashSet<String>()
        for (o in options) {
            for (ch in channels) {
                if (channelMatches(o, ch)) {
                    out.add(o)
                    break
                }
            }
        }
        return out
    }

    /**
     * Canais da lista que são dessas emissoras (na ordem das opções, até [perOption] de cada e [max] no total),
     * para a pessoa tentar mesmo quando o jogo não está em "Jogos em Destaque".
     */
    fun channelsFor(options: List<String>, channels: List<Channel>, perOption: Int = 2, max: Int = 10): List<Channel> {
        val out = ArrayList<Channel>()
        val used = HashSet<String>()
        for (o in options) {
            var n = 0
            for (ch in channels) {
                if (n >= perOption || out.size >= max) break
                if (used.contains(ch.key)) continue
                // só canais de verdade da emissora, não os "jogos avulsos" ("13:45 · A x B")
                if (EventNames.parse(ch.name) != null) continue
                if (channelMatches(o, ch)) {
                    out.add(ch)
                    used.add(ch.key)
                    n++
                }
            }
            if (out.size >= max) break
        }
        return out
    }

    private const val DAY_MS = 86_400_000L

    /** Quão bem o nome da tabela casa com o lado do jogo: 2 = igual ou contido (por palavras), 1 = divide uma palavra forte, 0 = não. */
    private fun teamLevel(want: String, side: Side): Int {
        val w = Organizer.fold(want)
        if (w.length < 3) return 0
        var best = 0
        for (n in side.names) {
            if (n == w) return 2
            if (n.length >= 3 && (hasWords(n, w) || hasWords(w, n))) best = 2
        }
        if (best == 0) {
            // divide uma palavra forte (5+ letras), igual ou o começo dela: "Hamburgo" ~ "Hamburg", "Atlético-MG" ~ "Atlético Mineiro"
            val wt = w.split(' ').filter { it.length >= 5 }
            for (n in side.names) {
                val nt = n.split(' ').filter { it.length >= 5 }
                if (wt.any { a -> nt.any { b -> a == b || (a.length <= b.length && b.startsWith(a)) || (b.length <= a.length && a.startsWith(b)) } }) {
                    best = 1
                    break
                }
            }
        }
        return best
    }

    /**
     * O registro do jogo na tabela, se houver: mesma data (ou um dia de diferença, por causa do fuso) e os dois times
     * casando. Prefere data igual e mesma competição.
     */
    fun gameFor(table: BroadcastTable, g: Game): BroadcastGame? {
        if (table.games.isEmpty()) return null
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = try {
            TimeZone.getTimeZone(table.tz)
        } catch (e: Exception) {
            TimeZone.getDefault()
        }
        val exact = fmt.format(Date(g.startMs))
        val near = setOf(exact, fmt.format(Date(g.startMs - DAY_MS)), fmt.format(Date(g.startMs + DAY_MS)))
        var best: BroadcastGame? = null
        var bestScore = 0
        for (bg in table.games) {
            if (bg.date !in near) continue
            val h = teamLevel(bg.home, g.home)
            if (h == 0) continue
            val a = teamLevel(bg.away, g.away)
            if (a == 0) continue
            val score = h + a + (if (bg.date == exact) 4 else 0) + (if (bg.comp == g.leagueSlug) 2 else 0)
            if (score > bestScore) {
                best = bg
                bestScore = score
            }
        }
        return best
    }

    /** "Globo · SporTV · Premiere" para mostrar na tela. */
    fun line(options: List<String>, inList: Set<String>): String =
        options.joinToString(" · ") { if (inList.contains(it)) "$it ✓" else it }
}
