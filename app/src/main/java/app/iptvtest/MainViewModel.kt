package app.iptvtest

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AccountStore(app)
    private var api: XtreamApi? = null

    // ---- conta / login
    var account by mutableStateOf<Account?>(null)
        private set
    var accountInfo by mutableStateOf("")
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // ---- navegação da home
    var tab by mutableStateOf(Kind.MOVIES)
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var loadingCats by mutableStateOf(false)
        private set
    var catError by mutableStateOf<String?>(null)
        private set
    var selectedCategory by mutableStateOf<Category?>(null)
        private set
    var items by mutableStateOf<List<MediaEntry>>(emptyList())
        private set
    var loadingItems by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // ---- telas empilhadas
    var seriesDetail by mutableStateOf<SeriesDetail?>(null)
        private set
    var playing by mutableStateOf<PlaybackRequest?>(null)
        private set

    private val catCache = HashMap<Kind, List<Category>>()
    private val itemCache = HashMap<String, List<MediaEntry>>()
    private var catsToken = 0
    private var itemsToken = 0

    init {
        store.load()?.let { saved ->
            startSession(saved)
            refreshAccountInfo()
        }
    }

    // =====================================================================
    // Login
    // =====================================================================

    fun login(serverRaw: String, userRaw: String, passRaw: String) {
        val server = normalizeServer(serverRaw)
        val user = userRaw.trim()
        val pass = passRaw.trim()
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            loginError = "Preencha servidor, usuário e senha."
            return
        }
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                val acc = Account(server, user, pass)
                val info = XtreamApi(acc).login()
                store.save(acc)
                accountInfo = info
                startSession(acc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = friendly(e)
            } finally {
                loginBusy = false
            }
        }
    }

    private fun refreshAccountInfo() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                accountInfo = a.login()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                accountInfo = "Não foi possível verificar a conta: ${friendly(e)}"
            }
        }
    }

    private fun startSession(acc: Account) {
        api = XtreamApi(acc)
        account = acc
        catCache.clear()
        itemCache.clear()
        selectTab(Kind.MOVIES)
    }

    fun logout() {
        store.clear()
        api = null
        account = null
        accountInfo = ""
        catCache.clear()
        itemCache.clear()
        categories = emptyList()
        items = emptyList()
        selectedCategory = null
        seriesDetail = null
        playing = null
        error = null
        catError = null
        catsToken++
        itemsToken++
        loadingCats = false
        loadingItems = false
    }

    // =====================================================================
    // Abas, categorias e itens
    // =====================================================================

    fun selectTab(kind: Kind) {
        tab = kind
        selectedCategory = null
        items = emptyList()
        error = null
        catError = null
        itemsToken++
        loadingItems = false
        val token = ++catsToken

        val cached = catCache[kind]
        if (cached != null) {
            categories = cached
            loadingCats = false
            cached.firstOrNull()?.let { selectCategory(it) }
            return
        }

        categories = emptyList()
        val a = api ?: return
        loadingCats = true
        viewModelScope.launch {
            try {
                val list = a.categories(kind).filterNot { isAdultCategory(it.name) }
                catCache[kind] = list
                if (token == catsToken && tab == kind) {
                    categories = list
                    list.firstOrNull()?.let { selectCategory(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == catsToken) catError = friendly(e)
            } finally {
                if (token == catsToken) loadingCats = false
            }
        }
    }

    fun selectCategory(cat: Category) {
        selectedCategory = cat
        error = null
        val kind = tab
        val key = "${kind.name}:${cat.id}"
        val token = ++itemsToken

        val cached = itemCache[key]
        if (cached != null) {
            items = cached
            loadingItems = false
            return
        }

        items = emptyList()
        val a = api ?: return
        loadingItems = true
        viewModelScope.launch {
            try {
                val list = a.items(kind, cat.id)
                itemCache[key] = list
                if (token == itemsToken) items = list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == itemsToken) error = friendly(e)
            } finally {
                if (token == itemsToken) loadingItems = false
            }
        }
    }

    // =====================================================================
    // Abrir item / séries / reprodução
    // =====================================================================

    fun open(entry: MediaEntry) {
        val a = api ?: return
        when (tab) {
            Kind.MOVIES -> playing = PlaybackRequest(entry.title, a.streamUrl(Kind.MOVIES, entry.id, entry.ext), isLive = false)
            Kind.LIVE -> playing = PlaybackRequest(entry.title, a.streamUrl(Kind.LIVE, entry.id, "m3u8"), isLive = true)
            Kind.SERIES -> openSeries(entry)
        }
    }

    private fun openSeries(entry: MediaEntry) {
        val a = api ?: return
        seriesDetail = SeriesDetail(entry, loading = true, error = null, episodes = emptyList())
        viewModelScope.launch {
            try {
                val eps = a.seriesEpisodes(entry.id)
                if (seriesDetail?.entry?.id == entry.id) {
                    seriesDetail = SeriesDetail(
                        entry,
                        loading = false,
                        error = if (eps.isEmpty()) "Nenhum episódio encontrado." else null,
                        episodes = eps,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seriesDetail?.entry?.id == entry.id) {
                    seriesDetail = SeriesDetail(entry, loading = false, error = friendly(e), episodes = emptyList())
                }
            }
        }
    }

    fun closeSeries() {
        seriesDetail = null
    }

    fun playEpisode(series: MediaEntry, ep: Episode) {
        val a = api ?: return
        val label = String.format("%s  T%02d E%02d", series.title, ep.season, ep.number)
        playing = PlaybackRequest(label, a.streamUrl(Kind.SERIES, ep.id, ep.ext), isLive = false)
    }

    fun stopPlaying() {
        playing = null
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private fun normalizeServer(raw: String): String {
        var t = raw.trim()
        if (t.isEmpty()) return ""
        if (!t.contains("://")) t = "http://$t"
        return try {
            val u = URL(t)
            val port = if (u.port != -1) ":${u.port}" else ""
            "${u.protocol}://${u.host}$port"
        } catch (e: Exception) {
            t.trimEnd('/')
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is UnknownHostException -> "Servidor não encontrado. Confira o endereço e a internet."
        is SocketTimeoutException -> "O servidor demorou demais para responder."
        is ConnectException -> "Não foi possível conectar ao servidor."
        is JSONException -> "O servidor devolveu uma resposta inválida."
        else -> e.message ?: "Erro desconhecido."
    }
}
