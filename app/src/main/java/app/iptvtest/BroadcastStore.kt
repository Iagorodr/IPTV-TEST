package app.iptvtest

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A tabela "onde costuma passar" (competição → emissoras do Brasil).
 * Vem embutida no app (assets/broadcasts.json) e, uma vez por dia, o app tenta baixar a versão mais nova do GitHub:
 * assim a atualização mensal não exige instalar um app novo.
 */
object BroadcastStore {

    private const val REMOTE = "https://raw.githubusercontent.com/Iagorodr/iptv-test/main/data/broadcasts.json"
    private const val PREFS = "iptv_broadcasts"
    private const val DAY_MS = 86_400_000L

    @Volatile
    var table: BroadcastTable = BroadcastTable("", emptyMap())
        private set

    fun parse(text: String): BroadcastTable? {
        return try {
            val root = JSONObject(text)
            val comps = root.optJSONObject("comps") ?: return null
            val map = LinkedHashMap<String, BroadcastComp>()
            val keys = comps.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = comps.optJSONObject(k) ?: continue
                val arr = o.optJSONArray("options") ?: continue
                val opts = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i)
                    if (v.isNotEmpty()) opts.add(v)
                }
                if (opts.isEmpty()) continue
                map[k] = BroadcastComp(o.optString("label"), opts, o.optString("note"))
            }
            if (map.isEmpty()) null else BroadcastTable(root.optString("updated"), map)
        } catch (e: Exception) {
            null
        }
    }

    /** Carrega a tabela embutida e a última baixada (a mais nova das duas vale). Chamar uma vez ao abrir o app. */
    fun load(ctx: Context) {
        var best: BroadcastTable? = null
        try {
            val text = ctx.assets.open("broadcasts.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            best = parse(text)
        } catch (e: Exception) {
            // sem o arquivo embutido, tenta só o baixado
        }
        try {
            val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", null)
            val t = if (saved == null) null else parse(saved)
            if (t != null && (best == null || t.updated >= best.updated)) best = t
        } catch (e: Exception) {
            // cópia salva ilegível: usa a embutida
        }
        if (best != null) table = best
    }

    /** Baixa a versão nova (no máximo uma vez por dia). Falhar não atrapalha nada. */
    suspend fun refresh(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong("at", 0L)
        if (System.currentTimeMillis() - last < DAY_MS) return
        withContext(Dispatchers.IO) {
            try {
                val conn = Net.open(REMOTE, readTimeoutMs = 15_000, accept = "application/json")
                val text = try {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                val t = parse(text) ?: return@withContext
                sp.edit().putString("json", text).putLong("at", System.currentTimeMillis()).apply()
                if (t.updated >= table.updated) table = t
            } catch (e: Exception) {
                // sem internet ou arquivo ainda não publicado: segue com o que já temos
            }
        }
    }
}
