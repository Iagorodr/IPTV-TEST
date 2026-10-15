package app.iptvtest

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app.applicationContext
    private val store = AccountStore(app)
    val user = UserData(app)
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

    // ---- catálogo
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var loadMsg by mutableStateOf<String?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var homeShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var movieShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var seriesShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var featured by mutableStateOf<Title?>(null)
        private set
    var allTitles: List<Title> = emptyList()
        private set
    private var titleIndex: HashMap<String, Title> = HashMap()
    private var channelIndex: HashMap<String, Channel> = HashMap()

    // ---- navegação
    var tab by mutableStateOf(Tab.HOME)
        private set
    var openTitle by mutableStateOf<Title?>(null)
        private set
    var grid by mutableStateOf<Shelf?>(null)
        private set
    var playing by mutableStateOf<PlaybackRequest?>(null)
        private set
    private val titleStack = ArrayList<Title>()

    /** Sobe quando minha lista, favoritos ou "continuar assistindo" mudam, para a tela se atualizar. */
    var dataRev by mutableIntStateOf(0)
        private set

    val vodInfos = mutableStateMapOf<String, VodInfo>()
    val episodes = mutableStateMapOf<String, EpisodesState>()

    private var loadJob: Job? = null

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
        val u = userRaw.trim()
        val p = passRaw.trim()
        if (server.isEmpty() || u.isEmpty() || p.isEmpty()) {
            loginError = "Preencha servidor, usuário e senha."
            return
        }
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                val acc = Account(server, u, p)
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
        tab = Tab.HOME
        openTitle = null
        grid = null
        playing = null
        titleStack.clear()
        CatalogCache.deleteOthers(appCtx, CatalogCache.accKey(acc))
        loadCatalog(force = false)
    }

    fun logout() {
        loadJob?.cancel()
        store.clear()
        CatalogCache.clear(appCtx)
        api = null
        account = null
        accountInfo = ""
        catalog = null
        homeShelves = emptyList()
        movieShelves = emptyList()
        seriesShelves = emptyList()
        featured = null
        allTitles = emptyList()
        titleIndex = HashMap()
        channelIndex = HashMap()
        openTitle = null
        grid = null
        playing = null
        titleStack.clear()
        loadMsg = null
        loadError = null
        refreshing = false
        vodInfos.clear()
        episodes.clear()
    }

    // =====================================================================
    // Catálogo: baixa uma vez, organiza, guarda no aparelho
    // =====================================================================

    fun loadCatalog(force: Boolean) {
        val a = api ?: return
        val acc = account ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadError = null
            val key = CatalogCache.accKey(acc)
            if (!force && catalog == null) {
                val cached = withContext(Dispatchers.IO) { CatalogCache.load(appCtx, key) }
                if (cached != null) {
                    applyCatalog(cached)
                    val ageHours = (System.currentTimeMillis() - cached.builtAt) / 3_600_000L
                    if (ageHours < 12L) return@launch
                }
            }
            if (catalog == null) {
                loadMsg = "Preparando..."
            } else {
                refreshing = true
            }
            try {
                val fresh = downloadAndBuild(a)
                withContext(Dispatchers.IO) { CatalogCache.save(appCtx, key, fresh) }
                applyCatalog(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (catalog == null) loadError = friendly(e)
            } catch (e: OutOfMemoryError) {
                if (catalog == null) loadError = friendly(e)
            } finally {
                loadMsg = null
                refreshing = false
            }
        }
    }

    private suspend fun downloadAndBuild(a: XtreamApi): Catalog {
        val show = catalog == null
        if (show) loadMsg = "Baixando categorias..."
        val vc = a.categories(Kind.MOVIES)
        val sc = a.categories(Kind.SERIES)
        val lc = a.categories(Kind.LIVE)
        if (show) loadMsg = "Baixando filmes... (na primeira vez demora um pouco)"
        val vod = a.fetchItems(Kind.MOVIES)
        if (show) loadMsg = "Baixando séries..."
        val ser = a.fetchItems(Kind.SERIES)
        if (show) loadMsg = "Baixando canais..."
        val live = a.fetchItems(Kind.LIVE)
        if (show) loadMsg = "Organizando por gênero..."
        return withContext(Dispatchers.Default) { Organizer.build(vc, sc, lc, vod, ser, live) }
    }

    private suspend fun applyCatalog(c: Catalog) {
        val built = withContext(Dispatchers.Default) { Shelves.buildAll(c) }
        val chIdx = HashMap<String, Channel>(c.channels.size * 2)
        for (ch in c.channels) chIdx[ch.key] = ch
        titleIndex = built.index
        channelIndex = chIdx
        allTitles = built.all
        featured = if (built.featured.isEmpty()) null else built.featured.random()
        homeShelves = built.home
        movieShelves = built.movies
        seriesShelves = built.series
        catalog = c
        dataRev++
    }

    fun catalogAgeText(): String {
        val c = catalog ?: return ""
        val mins = (System.currentTimeMillis() - c.builtAt) / 60_000L
        return when {
            mins < 2L -> "agora há pouco"
            mins < 90L -> "há $mins min"
            mins < 48L * 60L -> "há ${mins / 60L} h"
            else -> "há ${mins / 1440L} dias"
        }
    }

    // =====================================================================
    // Navegação
    // =====================================================================

    val canGoBack: Boolean
        get() = openTitle != null || grid != null || tab != Tab.HOME

    fun back(): Boolean {
        if (openTitle != null) {
            openTitle = if (titleStack.isNotEmpty()) titleStack.removeAt(titleStack.size - 1) else null
            FocusMemory.arm()
            return true
        }
        if (grid != null) {
            grid = null
            FocusMemory.arm()
            return true
        }
        if (tab != Tab.HOME) {
            tab = Tab.HOME
            return true
        }
        return false
    }

    fun selectTab(t: Tab) {
        tab = t
        grid = null
        openTitle = null
        titleStack.clear()
    }

    fun open(t: Title) {
        val cur = openTitle
        if (cur != null && cur.key != t.key) titleStack.add(cur)
        openTitle = t
    }

    fun openGrid(s: Shelf) {
        grid = s
    }

    // =====================================================================
    // Cartão do título: sinopse (filmes) e episódios (séries)
    // =====================================================================

    fun ensureInfo(title: Title, v: Version) {
        val a = api ?: return
        if (title.series) {
            val cur = episodes[v.id]
            if (cur != null && cur.error == null) return
            episodes[v.id] = EpisodesState(true, null, emptyList())
            viewModelScope.launch {
                try {
                    val eps = a.seriesEpisodes(v.id)
                    episodes[v.id] = EpisodesState(false, if (eps.isEmpty()) "Nenhum episódio encontrado." else null, eps)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    episodes[v.id] = EpisodesState(false, friendly(e), emptyList())
                }
            }
        } else {
            if (vodInfos.containsKey(v.id)) return
            viewModelScope.launch {
                try {
                    vodInfos[v.id] = a.vodInfo(v.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // a sinopse é opcional; se falhar, o card segue sem ela
                }
            }
        }
    }

    // =====================================================================
    // Reprodução
    // =====================================================================

    private fun startPlayback(title: Title, v: Version, epId: String, ext: String, label: String, zoom: Boolean) {
        val a = api ?: return
        val res = user.resume[title.key]
        val same = res != null && res.versionId == v.id && res.epId == epId &&
            res.durMs > 0L && res.posMs < res.durMs * 95L / 100L
        val start = if (same && res != null) res.posMs else 0L
        val url = if (title.series) {
            a.streamUrl(Kind.SERIES, epId, ext)
        } else {
            a.streamUrl(Kind.MOVIES, v.id, ext)
        }
        val shown = if (label.isEmpty()) title.name else title.name + "  " + label
        playing = PlaybackRequest(
            title = shown,
            url = url,
            isLive = false,
            startMs = start,
            zoom = zoom,
            onProgress = { pos, dur -> saveProgress(title, v, epId, ext, label, pos, dur, zoom) },
        )
    }

    fun playMovie(title: Title, v: Version, zoom: Boolean) {
        startPlayback(title, v, "", v.ext, "", zoom)
    }

    fun playEpisode(title: Title, v: Version, ep: Episode, zoom: Boolean) {
        val label = String.format(Locale.US, "T%d E%d", ep.season, ep.number)
        startPlayback(title, v, ep.id, ep.ext, label, zoom)
    }

    fun resumeEntry(e: ResumeEntry) {
        val t = titleIndex[e.titleKey] ?: return
        val v = t.versions.firstOrNull { it.id == e.versionId } ?: t.versions.firstOrNull() ?: return
        startPlayback(t, v, e.epId, e.ext, e.label, e.zoom)
    }

    private fun saveProgress(title: Title, v: Version, epId: String, ext: String, label: String, pos: Long, dur: Long, zoom: Boolean) {
        if (dur <= 0L || pos < 30_000L) return
        if (pos > dur * 95L / 100L) {
            user.clearResume(title.key)
        } else {
            user.saveResume(
                ResumeEntry(title.key, title.series, v.id, epId, ext, label, pos, dur, System.currentTimeMillis(), zoom),
            )
        }
        dataRev++
    }

    fun playChannel(ch: Channel) {
        val a = api ?: return
        playing = PlaybackRequest(
            title = ch.name,
            url = "",
            isLive = true,
            live = LiveInfo(ch) { s -> a.streamUrl(Kind.LIVE, s.id, "m3u8") },
        )
    }

    fun stopPlaying() {
        playing = null
        FocusMemory.arm()
        dataRev++
    }

    // =====================================================================
    // Listas da pessoa
    // =====================================================================

    fun continueEntries(): List<Pair<Title, ResumeEntry>> {
        if (dataRev < 0) return emptyList()
        val out = ArrayList<Pair<Title, ResumeEntry>>()
        for (e in user.resume.values.sortedByDescending { it.updated }) {
            val t = titleIndex[e.titleKey] ?: continue
            out.add(Pair(t, e))
        }
        return out
    }

    fun myListTitles(): List<Title> {
        if (dataRev < 0) return emptyList()
        val out = ArrayList<Title>()
        for (k in user.myList) {
            val t = titleIndex[k] ?: continue
            out.add(t)
        }
        out.reverse()
        return out
    }

    fun inMyList(key: String): Boolean {
        if (dataRev < 0) return false
        return user.myList.contains(key)
    }

    fun toggleMyList(key: String) {
        user.toggleMyList(key)
        dataRev++
    }

    fun favoriteChannels(): List<Channel> {
        if (dataRev < 0) return emptyList()
        val out = ArrayList<Channel>()
        for (k in user.favChannels) {
            val c = channelIndex[k] ?: continue
            out.add(c)
        }
        return out
    }

    fun channelStability(ch: Channel): Float? {
        if (dataRev < 0) return null
        return user.bestReliability(ch)
    }

    // =====================================================================
    // Ajustes
    // =====================================================================

    fun setStabilityFirst(v: Boolean) {
        user.setStabilityFirst(v)
        dataRev++
    }

    fun setAutoSwitch(v: Boolean) {
        user.setAutoSwitch(v)
        dataRev++
    }

    fun clearStats() {
        user.clearStats()
        dataRev++
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
        is OutOfMemoryError -> "A TV ficou sem memória ao organizar o catálogo."
        else -> e.message ?: "Erro desconhecido."
    }
}
