package app.iptvtest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uma gravação agendada. Guarda os endereços do canal (as melhores fontes) já prontos, para o serviço gravar
 * mesmo com o app fechado. [state]: "pending" (aguardando), "started" (já começou) ou "missed" (passou da hora sem gravar).
 */
class Sched(
    val id: String,
    val channelKey: String,
    val channelName: String,
    var title: String,
    val urls: List<String>,
    val startMs: Long,
    val endMs: Long,
    val quality: Int,
    var state: String = "pending",
    var recId: String = "",
    var error: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("key", channelKey).put("ch", channelName).put("title", title)
        .put("urls", JSONArray(urls)).put("start", startMs).put("end", endMs).put("q", quality)
        .put("state", state).put("rec", recId).put("err", error)

    companion object {
        fun fromJson(o: JSONObject): Sched {
            val u = o.optJSONArray("urls") ?: JSONArray()
            return Sched(
                o.getString("id"), o.optString("key"), o.optString("ch"), o.optString("title"),
                (0 until u.length()).map { u.getString(it) },
                o.optLong("start"), o.optLong("end"), o.optInt("q"),
                o.optString("state", "pending"), o.optString("rec"), o.optString("err"),
            )
        }
    }
}

object SchedStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("iptv_sched", Context.MODE_PRIVATE)

    @Synchronized
    fun all(ctx: Context): List<Sched> {
        val out = ArrayList<Sched>()
        try {
            val a = JSONArray(sp(ctx).getString("list", "[]") ?: "[]")
            for (i in 0 until a.length()) out.add(Sched.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) {
            // lista quebrada: começa vazia
        }
        return out.sortedBy { it.startMs }
    }

    @Synchronized
    private fun save(ctx: Context, list: List<Sched>) {
        val a = JSONArray()
        for (s in list) a.put(s.toJson())
        sp(ctx).edit().putString("list", a.toString()).apply()
    }

    @Synchronized
    fun add(ctx: Context, s: Sched) {
        save(ctx, all(ctx) + s)
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        save(ctx, all(ctx).filter { it.id != id })
    }

    @Synchronized
    fun update(ctx: Context, id: String, change: (Sched) -> Unit) {
        val l = all(ctx)
        l.firstOrNull { it.id == id }?.let(change)
        save(ctx, l)
    }
}

/** O que a tela de agendar recebe: o canal já escolhido e um horário sugerido. */
class SchedInit(
    val title: String,
    val channelKey: String,
    val channelName: String,
    val urls: List<String>,
    val startMs: Long,
    val endMs: Long,
    /** Veio de um jogo: pede para a pessoa conferir se o jogo passa mesmo nesse canal. */
    val fromGame: Boolean,
)
