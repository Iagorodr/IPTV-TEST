package app.iptvtest

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A tabela "onde passa" do país escolhido nos Ajustes (competição → emissoras, e jogo a jogo para os próximos dias).
 * Vem embutida no app (assets/broadcasts*.json) e, uma vez por dia (ou na hora, pelo botão "Conferir de novo"),
 * o app tenta baixar a versão mais nova do GitHub: assim a atualização semanal não exige instalar um app novo.
 * Um arquivo por país: o do Brasil é broadcasts.json (nome antigo, que os apps mais velhos também leem);
 * os outros são broadcasts-xx.json (ex.: broadcasts-pt.json).
 */
object BroadcastStore {

    private const val BASE = "https://raw.githubusercontent.com/Iagorodr/iptv-test/main/data/"
    private const val PREFS = "iptv_broadcasts"
    private const val DAY_MS = 86_400_000L

    /** (código do país, nome na tela). */
    val COUNTRIES: List<Pair<String, String>> = listOf("BR" to "Brasil", "PT" to "Portugal")

    @Volatile
    var country: String = "BR"
        private set

    @Volatile
    var table: BroadcastTable = BroadcastTable("", emptyMap())
        private set

    /** Sobe quando a tabela muda, para as telas se refazerem. */
    var rev by mutableIntStateOf(0)
        private set

    fun countryName(cc: String = country): String = COUNTRIES.firstOrNull { it.first == cc }?.second ?: cc

    private fun fileOf(cc: String): String = if (cc == "BR") "broadcasts.json" else "broadcasts-" + cc.lowercase() + ".json"

    // os apps antigos guardavam a tabela do Brasil em "json"/"at": mantém essas chaves para o BR
    private fun keyJson(cc: String): String = if (cc == "BR") "json" else "json_$cc"
    private fun keyAt(cc: String): String = if (cc == "BR") "at" else "at_$cc"

    private fun defaultTz(cc: String): String = if (cc == "PT") "Europe/Lisbon" else "America/Sao_Paulo"

    fun parse(text: String, cc: String = "BR"): BroadcastTable? {
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
            if (map.isEmpty()) return null

            val games = ArrayList<BroadcastGame>()
            val ga = root.optJSONArray("games")
            if (ga != null) {
                for (i in 0 until ga.length()) {
                    val o = ga.optJSONObject(i) ?: continue
                    val tv = ArrayList<String>()
                    val ta = o.optJSONArray("tv")
                    if (ta != null) {
                        for (j in 0 until ta.length()) {
                            val v = ta.optString(j)
                            if (v.isNotEmpty()) tv.add(v)
                        }
                    }
                    val d = o.optString("d")
                    val h = o.optString("h")
                    val a = o.optString("a")
                    if (d.isEmpty() || h.isEmpty() || a.isEmpty() || tv.isEmpty()) continue
                    games.add(
                        BroadcastGame(
                            date = d,
                            comp = o.optString("comp"),
                            home = h,
                            away = a,
                            tv = tv,
                            confirmed = o.optInt("ok", 0) == 1,
                            obs = o.optString("obs"),
                            src = o.optString("src"),
                        ),
                    )
                }
            }
            BroadcastTable(
                updated = root.optString("updated"),
                comps = map,
                games = games,
                country = root.optString("country").ifEmpty { cc },
                tz = root.optString("tz").ifEmpty { defaultTz(cc) },
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Carrega a tabela do país: a embutida e a última baixada (a mais nova das duas vale). Chamar ao abrir o app. */
    fun load(ctx: Context, cc: String) {
        country = cc
        var best: BroadcastTable? = null
        try {
            val text = ctx.assets.open(fileOf(cc)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            best = parse(text, cc)
        } catch (e: Exception) {
            // sem o arquivo embutido, tenta só o baixado
        }
        try {
            val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(keyJson(cc), null)
            val t = if (saved == null) null else parse(saved, cc)
            if (t != null && (best == null || t.updated >= best.updated)) best = t
        } catch (e: Exception) {
            // cópia salva ilegível: usa a embutida
        }
        table = best ?: BroadcastTable("", emptyMap(), emptyList(), cc, defaultTz(cc))
        rev++
    }

    /**
     * Baixa a versão nova do país escolhido (no máximo uma vez por dia, a não ser que [force]).
     * Devolve true se baixou e leu uma tabela; falhar não atrapalha nada.
     */
    suspend fun refresh(ctx: Context, force: Boolean = false): Boolean {
        val cc = country
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(keyAt(cc), 0L)
        if (!force && System.currentTimeMillis() - last < DAY_MS) return false
        return withContext(Dispatchers.IO) {
            try {
                // o parâmetro evita pegar uma cópia velha guardada no caminho
                val conn = Net.open(BASE + fileOf(cc) + "?t=" + System.currentTimeMillis(), readTimeoutMs = 15_000, accept = "application/json")
                val text = try {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    conn.disconnect()
                }
                val t = parse(text, cc) ?: return@withContext false
                sp.edit().putString(keyJson(cc), text).putLong(keyAt(cc), System.currentTimeMillis()).apply()
                if (country == cc && t.updated >= table.updated) {
                    table = t
                    rev++
                }
                true
            } catch (e: Exception) {
                // sem internet ou arquivo ainda não publicado: segue com o que já temos
                false
            }
        }
    }
}
