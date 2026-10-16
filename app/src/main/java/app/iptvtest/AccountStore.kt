package app.iptvtest

import android.content.Context

/**
 * Guarda os dados da lista SOMENTE neste aparelho (armazenamento privado do app, sem backup na nuvem).
 * Na v0.1 não há criptografia extra; ela entra junto com as contas.
 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences("iptv_account", Context.MODE_PRIVATE)

    fun load(): Account? {
        val m3u = prefs.getString("m3u", null)
        if (!m3u.isNullOrEmpty()) return Account("", "", "", m3u)
        val server = prefs.getString("server", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val pass = prefs.getString("pass", null) ?: return null
        return Account(server, user, pass)
    }

    fun save(account: Account) {
        prefs.edit()
            .putString("server", account.server)
            .putString("user", account.user)
            .putString("pass", account.pass)
            .putString("m3u", account.m3u)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
