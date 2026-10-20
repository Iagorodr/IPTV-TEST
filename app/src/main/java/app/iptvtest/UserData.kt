package app.iptvtest

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Uma medição de uma fonte de canal: teste feito pelo app ou sessão real de reprodução. */
class Obs(
    val ts: Long,
    val r: Float,
    val startupMs: Long,
    val headroom: Float,
    val stalls: Int,
    val minutes: Float,
)

fun statusLabel(r: Float?): String = when {
    r == null -> "Sem teste"
    r >= 0.85f -> "Estável"
    r >= 0.70f -> "Boa"
    r >= 0.50f -> "Média"
    r >= 0.30f -> "Oscila"
    else -> "Ruim"
}

fun statusColor(r: Float?): Color = when {
    r == null -> Muted
    r >= 0.85f -> Good
    r >= 0.70f -> Lime
    r >= 0.50f -> Warn
    else -> Orange
}

fun statusBars(r: Float?): Int = if (r == null) 0 else Math.round(r * 5f).coerceIn(1, 5)

/**
 * Tudo que é da pessoa e fica só neste aparelho: minha lista, favoritos, onde parou,
 * preferências e o histórico de estabilidade das fontes dos canais.
 */
class UserData(ctx: Context) {

    private val sp = ctx.getSharedPreferences("iptv_user", Context.MODE_PRIVATE)

    val myList = LinkedHashSet<String>()
    val favChannels = LinkedHashSet<String>()
    val resume = LinkedHashMap<String, ResumeEntry>()
    private val pinned = HashMap<String, String>()

    /** Última fonte que realmente tocou em cada canal (canal → fonte). Abre primeiro na próxima vez. */
    private val lastOk = LinkedHashMap<String, String>()
    private val stats = HashMap<String, ArrayList<Obs>>()

    /** Times favoritos (no máximo 5), na ordem em que a pessoa escolheu. */
    val favTeams = ArrayList<FavTeam>()

    /** Ligas/competições escolhidas (até 5), pelo código da ESPN (ex.: "bra.1"). */
    val favLeagues = ArrayList<String>()

    /** Lembretes de jogos ("Lembrar"), do mais próximo ao mais distante. */
    val reminders = ArrayList<Reminder>()

    /** Última praça escolhida de cada marca de TV (marca → chave do canal). */
    private val lastVariant = HashMap<String, String>()

    var stabilityFirst: Boolean = true
        private set
    var autoSwitch: Boolean = true
        private set

    /** Fuso horário escolhido nos Ajustes (vazio = o do aparelho). */
    var tzId: String = ""
        private set

    /** País da tabela "onde passa" escolhido nos Ajustes (BR = Brasil, PT = Portugal). */
    var bcCountry: String = "BR"
        private set

    /** Países/idiomas que a pessoa quer ver (vazio = todos). */
    val countryFilter = HashSet<String>()

    /** Perfil da primeira abertura: nome, idioma (ex.: "pt-BR") e se já passou pelas perguntas. */
    var profileName: String = ""
        private set
    var profileLang: String = ""
        private set
    var onboarded: Boolean = false
        private set

    /** Até 3 gêneros preferidos de filmes e séries: aparecem primeiro nos destaques e nas prateleiras. */
    val favGenres = ArrayList<String>()

    /** Modo infantil ligado: só conteúdo para crianças. */
    var kidsMode: Boolean = false
        private set

    init {
        load()
    }

    // ------------------------------------------------------------------ carregar

    private fun load() {
        try {
            stabilityFirst = sp.getBoolean("stab", true)
            autoSwitch = sp.getBoolean("auto", true)
            tzId = sp.getString("tz", "") ?: ""
            bcCountry = sp.getString("bc", "BR") ?: "BR"
            profileName = sp.getString("pname", "") ?: ""
            profileLang = sp.getString("plang", "") ?: ""
            onboarded = sp.getBoolean("onboarded", false)
            kidsMode = sp.getBoolean("kids", false)
            favGenres.clear()
            readStrings("favgen", favGenres)
            countryFilter.clear()
            countryFilter.addAll(sp.getStringSet("cfilter", emptySet()) ?: emptySet())
            readStrings("mylist", myList)
            readStrings("favch", favChannels)
            readFavTeams()
            readStrings("favleagues", favLeagues)
            readReminders()
            readVariants()

            val rs = sp.getString("resume", null)
            if (rs != null) {
                val a = JSONArray(rs)
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val e = ResumeEntry(
                        titleKey = o.optString("k"),
                        series = o.optBoolean("s"),
                        versionId = o.optString("v"),
                        epId = o.optString("e"),
                        ext = o.optString("x"),
                        label = o.optString("l"),
                        posMs = o.optLong("p"),
                        durMs = o.optLong("d"),
                        updated = o.optLong("u"),
                        zoom = o.optBoolean("z"),
                    )
                    if (e.titleKey.isNotEmpty()) resume[e.titleKey] = e
                }
            }

            val ps = sp.getString("pinned", null)
            if (ps != null) {
                val o = JSONObject(ps)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    pinned[k] = o.optString(k)
                }
            }

            val lo = sp.getString("lastok", null)
            if (lo != null) {
                val o = JSONObject(lo)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    lastOk[k] = o.optString(k)
                }
            }

            val ss = sp.getString("stats", null)
            if (ss != null) {
                val o = JSONObject(ss)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val arr = o.optJSONArray(k) ?: continue
                    val list = ArrayList<Obs>()
                    for (i in 0 until arr.length()) {
                        val x = arr.optJSONArray(i) ?: continue
                        list.add(
                            Obs(
                                ts = x.optLong(0),
                                r = x.optDouble(1, 0.0).toFloat(),
                                startupMs = x.optLong(2),
                                headroom = x.optDouble(3, 0.0).toFloat(),
                                stalls = x.optInt(4),
                                minutes = x.optDouble(5, 0.0).toFloat(),
                            ),
                        )
                    }
                    if (list.isNotEmpty()) stats[k] = list
                }
            }
        } catch (e: Exception) {
            // dados antigos ou corrompidos: começa do zero, sem travar o app
        }
    }

    private fun readStrings(key: String, into: MutableCollection<String>) {
        val s = sp.getString(key, null) ?: return
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val v = a.optString(i)
            if (v.isNotEmpty()) into.add(v)
        }
    }

    private fun writeStrings(key: String, from: Collection<String>) {
        val a = JSONArray()
        for (v in from) a.put(v)
        sp.edit().putString(key, a.toString()).apply()
    }

    // ------------------------------------------------------------------ times favoritos

    private fun readFavTeams() {
        val s = sp.getString("favteams", null) ?: return
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("i")
            if (id.isEmpty()) continue
            val lg = ArrayList<String>()
            val la = o.optJSONArray("g")
            if (la != null) for (j in 0 until la.length()) lg.add(la.optString(j))
            favTeams.add(
                FavTeam(
                    id = id,
                    name = o.optString("n"),
                    short = o.optString("s"),
                    logo = o.optString("l").ifEmpty { null },
                    leagues = lg,
                    national = o.optBoolean("t"),
                ),
            )
        }
    }

    fun saveFavTeams(list: List<FavTeam>) {
        favTeams.clear()
        favTeams.addAll(list)
        val a = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("i", t.id)
            o.put("n", t.name)
            o.put("s", t.short)
            o.put("l", t.logo ?: "")
            val g = JSONArray()
            for (x in t.leagues) g.put(x)
            o.put("g", g)
            o.put("t", t.national)
            a.put(o)
        }
        sp.edit().putString("favteams", a.toString()).apply()
    }

    // ------------------------------------------------------------------ ligas favoritas

    fun saveFavLeagues(list: List<String>) {
        favLeagues.clear()
        favLeagues.addAll(list)
        writeStrings("favleagues", favLeagues)
    }

    // ------------------------------------------------------------------ lembretes de jogos

    private fun readReminders() {
        val s = sp.getString("reminders", null) ?: return
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val key = o.optString("k")
            if (key.isEmpty()) continue
            reminders.add(
                Reminder(
                    gameKey = key,
                    title = o.optString("t"),
                    league = o.optString("l"),
                    startMs = o.optLong("s"),
                    homeId = o.optString("h"),
                    awayId = o.optString("a"),
                    homeName = o.optString("hn"),
                    awayName = o.optString("an"),
                    seen = o.optBoolean("x"),
                ),
            )
        }
    }

    fun saveReminders(list: List<Reminder>) {
        reminders.clear()
        reminders.addAll(list)
        val a = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("k", r.gameKey)
            o.put("t", r.title)
            o.put("l", r.league)
            o.put("s", r.startMs)
            o.put("h", r.homeId)
            o.put("a", r.awayId)
            o.put("hn", r.homeName)
            o.put("an", r.awayName)
            o.put("x", r.seen)
            a.put(o)
        }
        sp.edit().putString("reminders", a.toString()).apply()
    }

    // ------------------------------------------------------------------ praça escolhida de cada marca

    private fun readVariants() {
        val s = sp.getString("variants", null) ?: return
        val o = JSONObject(s)
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            lastVariant[k] = o.optString(k)
        }
    }

    fun lastVariantOf(brandKey: String): String? = lastVariant[brandKey]

    fun setLastVariant(brandKey: String, channelKey: String) {
        lastVariant[brandKey] = channelKey
        val o = JSONObject()
        for ((k, v) in lastVariant) o.put(k, v)
        sp.edit().putString("variants", o.toString()).apply()
    }

    // ------------------------------------------------------------------ minha lista / favoritos

    fun toggleMyList(titleKey: String): Boolean {
        val now = if (myList.contains(titleKey)) {
            myList.remove(titleKey)
            false
        } else {
            myList.add(titleKey)
            true
        }
        writeStrings("mylist", myList)
        return now
    }

    fun toggleFavChannel(channelKey: String): Boolean {
        val now = if (favChannels.contains(channelKey)) {
            favChannels.remove(channelKey)
            false
        } else {
            favChannels.add(channelKey)
            true
        }
        writeStrings("favch", favChannels)
        return now
    }

    // ------------------------------------------------------------------ continuar assistindo

    fun saveResume(e: ResumeEntry) {
        resume.remove(e.titleKey)
        resume[e.titleKey] = e
        while (resume.size > 60) {
            val first = resume.keys.first()
            resume.remove(first)
        }
        persistResume()
    }

    fun clearResume(titleKey: String) {
        if (resume.remove(titleKey) != null) persistResume()
    }

    private fun persistResume() {
        val a = JSONArray()
        for (e in resume.values) {
            val o = JSONObject()
            o.put("k", e.titleKey)
            o.put("s", e.series)
            o.put("v", e.versionId)
            o.put("e", e.epId)
            o.put("x", e.ext)
            o.put("l", e.label)
            o.put("p", e.posMs)
            o.put("d", e.durMs)
            o.put("u", e.updated)
            o.put("z", e.zoom)
            a.put(o)
        }
        sp.edit().putString("resume", a.toString()).apply()
    }

    // ------------------------------------------------------------------ preferências

    fun setStabilityFirst(v: Boolean) {
        stabilityFirst = v
        sp.edit().putBoolean("stab", v).apply()
    }

    fun setAutoSwitch(v: Boolean) {
        autoSwitch = v
        sp.edit().putBoolean("auto", v).apply()
    }

    fun setCountryFilter(v: Set<String>) {
        countryFilter.clear()
        countryFilter.addAll(v)
        sp.edit().putStringSet("cfilter", HashSet(v)).apply()
    }

    fun setBcCountry(v: String) {
        bcCountry = v
        sp.edit().putString("bc", v).apply()
    }

    fun setProfile(name: String, lang: String) {
        profileName = name.trim()
        profileLang = lang
        sp.edit().putString("pname", profileName).putString("plang", lang).apply()
    }

    fun setOnboarded() {
        onboarded = true
        sp.edit().putBoolean("onboarded", true).apply()
    }

    fun setKids(on: Boolean) {
        kidsMode = on
        sp.edit().putBoolean("kids", on).apply()
    }

    fun saveFavGenres(list: List<String>) {
        favGenres.clear()
        favGenres.addAll(list.take(3))
        val a = JSONArray()
        for (g in favGenres) a.put(g)
        sp.edit().putString("favgen", a.toString()).apply()
    }

    fun setTzId(v: String) {
        tzId = v
        sp.edit().putString("tz", v).apply()
    }

    fun pinnedSource(channelKey: String): String? = pinned[channelKey]

    fun setPinned(channelKey: String, sourceId: String?) {
        if (sourceId == null) pinned.remove(channelKey) else pinned[channelKey] = sourceId
        val o = JSONObject()
        for ((k, v) in pinned) o.put(k, v)
        sp.edit().putString("pinned", o.toString()).apply()
    }

    fun lastOkSource(channelKey: String): String? = lastOk[channelKey]

    fun setLastOk(channelKey: String, sourceId: String) {
        if (lastOk[channelKey] == sourceId) return
        lastOk[channelKey] = sourceId
        // não deixa crescer sem fim: guarda só as últimas 400
        while (lastOk.size > 400) lastOk.remove(lastOk.keys.first())
        val o = JSONObject()
        for ((k, v) in lastOk) o.put(k, v)
        sp.edit().putString("lastok", o.toString()).apply()
    }

    // ------------------------------------------------------------------ estabilidade das fontes

    fun record(sourceId: String, r: Float, startupMs: Long, headroom: Float, stalls: Int, minutes: Float) {
        val list = stats.getOrPut(sourceId) { ArrayList() }
        list.add(Obs(System.currentTimeMillis(), r.coerceIn(0f, 1f), startupMs, headroom, stalls, minutes))
        while (list.size > 12) list.removeAt(0)
        persistStats()
    }

    fun clearStats() {
        stats.clear()
        sp.edit().remove("stats").apply()
    }

    private fun persistStats() {
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
        val o = JSONObject()
        for ((k, list) in stats) {
            val a = JSONArray()
            for (ob in list) {
                if (ob.ts < cutoff) continue
                val x = JSONArray()
                x.put(ob.ts)
                x.put(ob.r.toDouble())
                x.put(ob.startupMs)
                x.put(ob.headroom.toDouble())
                x.put(ob.stalls)
                x.put(ob.minutes.toDouble())
                a.put(x)
            }
            if (a.length() > 0) o.put(k, a)
        }
        sp.edit().putString("stats", o.toString()).apply()
    }

    /** Nota de 0 a 1 (média das últimas medições; as recentes pesam mais). Null se nunca foi medida. */
    fun reliability(sourceId: String): Float? {
        val list = stats[sourceId] ?: return null
        if (list.isEmpty()) return null
        val now = System.currentTimeMillis()
        var sw = 0.0
        var s = 0.0
        for (o in list) {
            val ageDays = (now - o.ts) / 86_400_000.0
            val w = Math.pow(0.5, ageDays / 3.0)
            sw += w
            s += w * o.r
        }
        return if (sw <= 0.0) null else (s / sw).toFloat()
    }

    fun lastObs(sourceId: String): Obs? = stats[sourceId]?.lastOrNull()

    private fun sourceScore(s: Source): Float {
        val prior = when (s.quality) {
            "SD" -> 0.80f
            "HD" -> 0.75f
            "FHD" -> 0.70f
            "H265" -> 0.60f
            else -> 0.50f
        }
        val rr = reliability(s.id) ?: prior
        return if (stabilityFirst) rr * 100f + s.rank * 6f else s.rank * 40f + rr * 40f
    }

    /** Nota que pesa estabilidade e qualidade juntas (botão "melhor fonte automática"). */
    private fun balancedScore(s: Source): Float {
        val prior = when (s.quality) {
            "SD" -> 0.80f
            "HD" -> 0.75f
            "FHD" -> 0.70f
            "H265" -> 0.60f
            else -> 0.50f
        }
        val rr = reliability(s.id) ?: prior
        return rr * 60f + s.rank * 10f
    }

    /** A fonte com a melhor mistura de estabilidade e qualidade, independente da prioridade escolhida nos Ajustes. */
    fun bestBalanced(channel: Channel): Source? = channel.sources.maxByOrNull { balancedScore(it) }

    /** Quantas fontes do canal ainda não têm nenhuma medição. */
    fun unmeasured(channel: Channel): Int = channel.sources.count { reliability(it.id) == null }

    /** Fontes do canal da melhor para a pior, segundo a prioridade escolhida. */
    fun rank(channel: Channel): List<Source> = channel.sources.sortedByDescending { sourceScore(it) }

    /** Resumo para o card do canal: quantas fontes e a nota da melhor (null se nada foi medido). */
    fun bestReliability(channel: Channel): Float? {
        var best: Float? = null
        for (s in channel.sources) {
            val r = reliability(s.id) ?: continue
            if (best == null || r > best) best = r
        }
        return best
    }
}
