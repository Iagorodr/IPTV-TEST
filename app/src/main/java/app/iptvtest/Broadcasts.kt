package app.iptvtest

/*
 * "Onde costuma passar": tabela (competição → emissoras do Brasil) pesquisada à mão e atualizada todo mês.
 * Pode estar desatualizada, por isso sempre mostramos mais de uma opção e chamamos de "costuma".
 * Aqui só as regras puras; ler o arquivo e baixar a versão nova está em BroadcastStore.kt.
 */

class BroadcastComp(val label: String, val options: List<String>, val note: String)

class BroadcastTable(val updated: String, val comps: Map<String, BroadcastComp>) {
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

    /** "Globo · SporTV · Premiere" para mostrar na tela. */
    fun line(options: List<String>, inList: Set<String>): String =
        options.joinToString(" · ") { if (inList.contains(it)) "$it ✓" else it }
}
