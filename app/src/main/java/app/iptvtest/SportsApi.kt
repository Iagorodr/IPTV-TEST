package app.iptvtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

class TeamInfo(val id: String, val name: String, val short: String, val logo: String?)

/** Pedido para abrir a tela "onde assistir": canais achados na lista + atalhos. */
class ChannelListRequest(
    val title: String,
    val subtitle: String,
    val hint: String,
    val highlight: List<Channel>,
    val searchQuery: String,
)

private fun JSONObject.s(key: String): String = if (isNull(key)) "" else optString(key, "")

/**
 * Jogos e agenda de esportes vindos da API pública (sem chave) do site da ESPN.
 * É uma API não oficial: serve para testar agora; depois trocamos por uma fonte com contrato (e pelo nosso servidor).
 * Ela não informa quem transmite no Brasil/Bélgica, por isso "onde passa" vem da lista do provedor.
 */
object SportsApi {

    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports"

    private val teamCache = HashMap<String, List<TeamInfo>>()

    /** null = ainda não sabemos se a ESPN aceita um intervalo de datas numa só chamada. */
    @Volatile
    private var rangeOk: Boolean? = null

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val conn = Net.open(url, readTimeoutMs = 20_000, accept = "application/json")
        try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ times (para a pessoa escolher)

    suspend fun teams(slug: String): List<TeamInfo> {
        synchronized(teamCache) { teamCache[slug] }?.let { return it }
        val root = JSONObject(getText("$BASE/soccer/$slug/teams?limit=200"))
        val arr = root.optJSONArray("sports")?.optJSONObject(0)
            ?.optJSONArray("leagues")?.optJSONObject(0)
            ?.optJSONArray("teams")
        val out = ArrayList<TeamInfo>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i)?.optJSONObject("team") ?: continue
                val id = t.s("id")
                val name = t.s("displayName").ifEmpty { t.s("name") }
                if (id.isEmpty() || name.isEmpty() || name.startsWith("TBD")) continue
                val logo = t.optJSONArray("logos")?.optJSONObject(0)?.s("href")?.ifEmpty { null }
                out.add(TeamInfo(id, name, t.s("shortDisplayName").ifEmpty { name }, logo ?: t.s("logo").ifEmpty { null }))
            }
        }
        out.sortBy { Organizer.fold(it.name) }
        if (out.isNotEmpty()) synchronized(teamCache) { teamCache[slug] = out }
        return out
    }

    /** A seleção do país: procura nas listas da Copa do Mundo e da Liga das Nações. */
    suspend fun nationalTeam(c: Country): TeamInfo? {
        val want = Organizer.fold(c.national)
        for (slug in listOf("fifa.world", "uefa.nations")) {
            try {
                val hit = teams(slug).firstOrNull { Organizer.fold(it.name) == want }
                if (hit != null) return hit
            } catch (e: IOException) {
                // tenta a próxima lista
            }
        }
        return null
    }

    // ------------------------------------------------------------------ jogos

    private fun side(c: JSONObject): Side? {
        val t = c.optJSONObject("team") ?: return null
        val id = t.s("id")
        if (id.isEmpty()) return null
        val name = t.s("displayName").ifEmpty { t.s("name") }
        val short = t.s("shortDisplayName").ifEmpty { name }
        val logo = t.s("logo").ifEmpty { t.optJSONArray("logos")?.optJSONObject(0)?.s("href") ?: "" }.ifEmpty { null }
        val names = LinkedHashSet<String>()
        for (k in listOf("displayName", "shortDisplayName", "name", "location")) {
            val f = Organizer.fold(t.s(k))
            if (f.isNotEmpty()) names.add(f)
        }
        return Side(id, name, short, logo, c.s("score").ifEmpty { null }, names.toList())
    }

    private fun parseGames(text: String, slug: String): List<Game> {
        val root = JSONObject(text)
        val lgRaw = root.optJSONArray("leagues")?.optJSONObject(0)?.s("name") ?: ""
        val lgName = SportsData.leagueLabel(slug, lgRaw)
        val events = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<Game>()
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val cs = comp.optJSONArray("competitors") ?: continue
            val sides = ArrayList<Pair<String, Side>>()
            for (j in 0 until cs.length()) {
                val c = cs.optJSONObject(j) ?: continue
                val sd = side(c) ?: continue
                sides.add(Pair(c.s("homeAway"), sd))
            }
            if (sides.size < 2) continue
            val home = sides.firstOrNull { it.first == "home" }?.second ?: sides[0].second
            val away = sides.firstOrNull { it.first == "away" }?.second ?: sides[1].second

            val st = ev.optJSONObject("status") ?: comp.optJSONObject("status")
            val type = st?.optJSONObject("type")
            val what = (type?.s("name") ?: "") + " " + (type?.s("description") ?: "")
            if (what.contains("postpone", true) || what.contains("cancel", true)) continue
            val state = when (type?.s("state")) {
                "in" -> 1
                "post" -> 2
                else -> 0
            }
            val shortD = type?.s("shortDetail") ?: ""
            val clock = st?.s("displayClock") ?: ""
            val detail = when {
                state != 1 -> ""
                shortD.equals("HT", true) || what.contains("half", true) -> "Intervalo"
                clock.isNotEmpty() && clock != "0'" -> clock
                else -> shortD
            }
            val start = SportsTime.parseIso(ev.s("date").ifEmpty { comp.s("date") })
            if (start == 0L) continue
            out.add(Game(ev.s("id"), slug, lgName, start, state, detail, home, away))
        }
        return out
    }

    private suspend fun scoreboard(slug: String, dates: String): List<Game> =
        parseGames(getText("$BASE/soccer/$slug/scoreboard?dates=$dates&limit=300"), slug)

    /**
     * Jogos dos times escolhidos (de ontem até duas semanas à frente, quando a ESPN aceita intervalo;
     * senão procura dia a dia: hoje, amanhã e, para quem estiver sem jogo, os próximos dias).
     */
    suspend fun gamesFor(teams: List<FavTeam>): List<Game> = coroutineScope {
        val ids = HashSet<String>()
        for (t in teams) ids.add(t.id)
        val leagues = teams.flatMap { it.leagues }.distinct()
        val now = System.currentTimeMillis()
        val found = LinkedHashMap<String, Game>()
        var ok = 0
        val sem = Semaphore(5)

        suspend fun load(slugs: List<String>, dates: String) {
            val res = slugs.map { slug ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        try {
                            scoreboard(slug, dates)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll()
            for (r in res) {
                if (r == null) continue
                ok++
                for (g in r) if (ids.contains(g.home.id) || ids.contains(g.away.id)) found[g.key] = g
            }
        }

        if (rangeOk != false) {
            val dates = SportsTime.espnDay(now - SportsTime.DAY) + "-" + SportsTime.espnDay(now + 14L * SportsTime.DAY)
            load(leagues, dates)
            if (ok > 0) {
                rangeOk = true
                return@coroutineScope found.values.toList()
            }
        }

        // plano B: dia a dia
        load(leagues, SportsTime.espnDay(now))
        load(leagues, SportsTime.espnDay(now + SportsTime.DAY))
        var d = 2L
        while (d <= 10L) {
            val lacking = teams.filter { t ->
                found.values.none { (it.home.id == t.id || it.away.id == t.id) && it.state != 2 && it.startMs >= now - 3L * 3_600_000L }
            }
            if (lacking.isEmpty()) break
            load(lacking.flatMap { it.leagues }.distinct(), SportsTime.espnDay(now + d * SportsTime.DAY))
            d++
        }
        if (ok == 0) throw IOException("Sem resposta do serviço de jogos.")
        if (rangeOk == null) rangeOk = false
        found.values.toList()
    }

    // ------------------------------------------------------------------ outros esportes (só eventos)

    private fun parseEvents(text: String, kind: SportKind, tag: String, now: Long): List<SportEvent> {
        val root = JSONObject(text)
        val events = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<SportEvent>()
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            var name = ev.s("name").ifEmpty { ev.s("shortName") }
            if (name.isEmpty()) continue
            if (tag.isNotEmpty()) name = "$tag · $name"
            var start = SportsTime.parseIso(ev.s("date"))
            var end = SportsTime.parseIso(ev.s("endDate"))
            if (kind == SportKind.F1) {
                // a data que interessa é a da corrida, não a do primeiro treino
                val comps = ev.optJSONArray("competitions")
                if (comps != null) {
                    for (j in 0 until comps.length()) {
                        val c = comps.optJSONObject(j) ?: continue
                        val ab = c.optJSONObject("type")?.s("abbreviation") ?: ""
                        if (ab.equals("Race", true)) {
                            val d = SportsTime.parseIso(c.s("date"))
                            if (d > 0L) {
                                start = d
                                end = d + 2L * 3_600_000L
                            }
                        }
                    }
                }
            }
            if (start == 0L) continue
            if (end == 0L) end = start + 4L * 3_600_000L
            val esp = ev.optJSONObject("status")?.optJSONObject("type")?.s("state") ?: ""
            val state = when {
                esp == "in" -> 1
                esp == "post" -> 2
                esp == "pre" -> 0
                now in start..end -> 1
                now < start -> 0
                else -> 2
            }
            out.add(SportEvent(kind, ev.s("id"), name, start, end, state))
        }
        return out
    }

    private fun best(list: List<SportEvent>, now: Long): SportEvent? {
        val open = list.filter { it.state != 2 && it.endMs >= now - 3_600_000L }
        return open.firstOrNull { it.state == 1 } ?: open.minByOrNull { it.startMs }
    }

    private suspend fun nextEvent(kind: SportKind, path: String, tag: String): SportEvent? {
        val now = System.currentTimeMillis()
        try {
            val pick = best(parseEvents(getText("$BASE/$path/scoreboard"), kind, tag, now), now)
            if (pick != null) return pick
        } catch (e: Exception) {
            // cai para o calendário do ano
        }
        return try {
            val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
            best(parseEvents(getText("$BASE/$path/scoreboard?dates=$year"), kind, tag, now), now)
        } catch (e: Exception) {
            null
        }
    }

    /** O próximo (ou o que está rolando) de cada esporte: Fórmula 1, UFC e tênis. */
    suspend fun sportEvents(): Map<SportKind, SportEvent> = coroutineScope {
        val f1 = async { nextEvent(SportKind.F1, "racing/f1", "") }
        val ufc = async { nextEvent(SportKind.UFC, "mma/ufc", "") }
        val atp = async { nextEvent(SportKind.TENNIS, "tennis/atp", "ATP") }
        val wta = async { nextEvent(SportKind.TENNIS, "tennis/wta", "WTA") }
        val out = LinkedHashMap<SportKind, SportEvent>()
        f1.await()?.let { out[SportKind.F1] = it }
        ufc.await()?.let { out[SportKind.UFC] = it }
        val tennis = listOfNotNull(atp.await(), wta.await())
        val t = tennis.firstOrNull { it.state == 1 } ?: tennis.minByOrNull { it.startMs }
        if (t != null) out[SportKind.TENNIS] = t
        out
    }
}
