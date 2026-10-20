package app.iptvtest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda as listas SOMENTE neste aparelho (armazenamento privado do app, sem backup na nuvem).
 * Pode haver várias listas: uma fica ativa, ou todas são juntadas num só conteúdo ([merge]).
 * Quem tinha uma lista só (versões antigas) é migrado sozinho.
 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences("iptv_account", Context.MODE_PRIVATE)

    private fun toJson(a: Account): JSONObject = JSONObject()
        .put("server", a.server).put("user", a.user).put("pass", a.pass).put("m3u", a.m3u)

    private fun fromJson(o: JSONObject): Account =
        Account(o.optString("server"), o.optString("user"), o.optString("pass"), o.optString("m3u"))

    private fun legacy(): Account? {
        val m3u = prefs.getString("m3u", null)
        if (!m3u.isNullOrEmpty()) return Account("", "", "", m3u)
        val server = prefs.getString("server", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val pass = prefs.getString("pass", null) ?: return null
        return Account(server, user, pass)
    }

    fun lists(): List<Account> {
        val raw = prefs.getString("lists", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                val out = ArrayList<Account>()
                for (i in 0 until arr.length()) out.add(fromJson(arr.getJSONObject(i)))
                return out
            } catch (e: Exception) {
                // dados estragados: cai para o formato antigo
            }
        }
        val old = legacy() ?: return emptyList()
        saveLists(listOf(old))
        return listOf(old)
    }

    private fun saveLists(l: List<Account>) {
        val arr = JSONArray()
        for (a in l) arr.put(toJson(a))
        prefs.edit().putString("lists", arr.toString()).apply()
    }

    /** Identificação (User-Agent) que o provedor aceitou no login. Vazio = a padrão. */
    var agent: String
        get() = prefs.getString("agent", "") ?: ""
        set(v) {
            prefs.edit().putString("agent", v).apply()
        }

    var active: Int
        get() = prefs.getInt("active", 0)
        set(v) {
            prefs.edit().putInt("active", v).apply()
        }

    var merge: Boolean
        get() = prefs.getBoolean("merge", false)
        set(v) {
            prefs.edit().putBoolean("merge", v).apply()
        }

    /** Junta a lista às que já existem (se for a mesma, só a torna ativa) e devolve a posição dela. */
    fun add(acc: Account): Int {
        val l = lists().toMutableList()
        val k = CatalogCache.accKey(acc)
        var idx = l.indexOfFirst { CatalogCache.accKey(it) == k }
        if (idx >= 0) {
            l[idx] = acc
        } else {
            l.add(acc)
            idx = l.size - 1
        }
        saveLists(l)
        return idx
    }

    fun removeAt(i: Int) {
        val l = lists().toMutableList()
        if (i !in l.indices) return
        l.removeAt(i)
        saveLists(l)
        val a = active
        if (a >= l.size) active = (l.size - 1).coerceAtLeast(0) else if (i < a) active = a - 1
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
