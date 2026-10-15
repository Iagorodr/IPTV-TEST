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
    private val stats = HashMap<String, ArrayList<Obs>>()

    var stabilityFirst: Boolean = true
        private set
    var autoSwitch: Boolean = true
        private set

    init {
        load()
    }

    // ------------------------------------------------------------------ carregar

    private fun load() {
        try {
            stabilityFirst = sp.getBoolean("stab", true)
            autoSwitch = sp.getBoolean("auto", true)
            readStrings("mylist", myList)
            readStrings("favch", favChannels)

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

    fun pinnedSource(channelKey: String): String? = pinned[channelKey]

    fun setPinned(channelKey: String, sourceId: String?) {
        if (sourceId == null) pinned.remove(channelKey) else pinned[channelKey] = sourceId
        val o = JSONObject()
        for ((k, v) in pinned) o.put(k, v)
        sp.edit().putString("pinned", o.toString()).apply()
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
