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

    /**
     * Semente do sorteio das prateleiras: nova a cada vez que o app é aberto, e igual enquanto ele está aberto
     * (para o catálogo atualizado em segundo plano não embaralhar tudo na frente da pessoa).
     */
    private var mixSeed: Long = System.nanoTime()
    private var api: ListApi? = null

    // ---- login pelo celular (QR code); fica no topo porque o init abaixo já usa
    var phoneUrl by mutableStateOf<String?>(null)
        private set
    var phoneMsg by mutableStateOf<String?>(null)
        private set
    private var phoneServer: PhoneLoginServer? = null

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
    var heroAll by mutableStateOf<List<Title>>(emptyList())
        private set
    var heroMovies by mutableStateOf<List<Title>>(emptyList())
        private set
    var heroSeries by mutableStateOf<List<Title>>(emptyList())
        private set

    /** Canais como aparecem na aba Canais: marcas de TV juntas, jogos avulsos limpos, em ~9 pastas. */
    var channelGroups by mutableStateOf<List<Pair<String, List<Channel>>>>(emptyList())
        private set
    private var channelCards: List<Channel> = emptyList()
    private var cleanChannels: List<Channel> = emptyList()
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

    // ---- esportes
    var favTeams by mutableStateOf<List<FavTeam>>(emptyList())
        private set
    var games by mutableStateOf<List<Game>>(emptyList())
        private set
    var gamesLoading by mutableStateOf(false)
        private set
    var gamesError by mutableStateOf<String?>(null)
        private set
    var sportEvents by mutableStateOf<Map<SportKind, SportEvent>>(emptyMap())
        private set
    var pickerOpen by mutableStateOf(false)
        private set
    var pickerCountry by mutableStateOf<String?>(null)
        private set
    var channelList by mutableStateOf<ChannelListRequest?>(null)
        private set
    var searchPrefill by mutableStateOf<String?>(null)
        private set
    var favLeagues by mutableStateOf<List<String>>(emptyList())
        private set
    var leagueGames by mutableStateOf<List<Game>>(emptyList())
        private set
    var allGamesOpen by mutableStateOf(false)
        private set
    var allGames by mutableStateOf<List<Game>>(emptyList())
        private set
    var allGamesLoading by mutableStateOf(false)
        private set
    var allGamesError by mutableStateOf<String?>(null)
        private set
    var popGames by mutableStateOf<List<Game>>(emptyList())
        private set
    var popLoading by mutableStateOf(false)
        private set
    var popError by mutableStateOf<String?>(null)
        private set
    var gameDialog by mutableStateOf<Game?>(null)
        private set
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set
    var dueReminder by mutableStateOf<Reminder?>(null)
        private set
    private var popAt = 0L
    private var allGamesAt = 0L
    private var eventIdx: List<EventChan> = emptyList()
    private var gamesJob: Job? = null
    private var eventsJob: Job? = null
    private var gamesAt = 0L
    private var eventsAt = 0L
    private var gamesGen = 0
    private var sportsDirty = false

    /** Sobe quando minha lista, favoritos ou "continuar assistindo" mudam, para a tela se atualizar. */
    var dataRev by mutableIntStateOf(0)
        private set

    val vodInfos = mutableStateMapOf<String, VodInfo>()
    val episodes = mutableStateMapOf<String, EpisodesState>()

    private var loadJob: Job? = null

    init {
        AppTz.apply(user.tzId)
        favTeams = user.favTeams.toList()
        favLeagues = user.favLeagues.toList()
        reminders = user.reminders.toList()
        BroadcastStore.load(appCtx)
        viewModelScope.launch { BroadcastStore.refresh(appCtx) }
        store.load()?.let { saved ->
            startSession(saved)
            refreshAccountInfo()
            refreshSports(force = true)
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
                val x = XtreamApi(acc)
                val info = x.login()
                store.save(acc)
                accountInfo = info
                startSession(acc, x)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = friendly(e)
            } finally {
                loginBusy = false
            }
        }
    }

    /**
     * Entra com um link de lista. Se o link é do tipo get.php?username=...&password=... entra como Xtream
     * (fica bem mais completo, com sinopse e episódios); senão trata como uma lista M3U comum.
     */
    fun loginM3u(linkRaw: String) {
        val link = linkRaw.trim()
        if (link.isEmpty()) {
            loginError = "Cole o link da lista."
            return
        }
        if (!link.startsWith("http://", ignoreCase = true) && !link.startsWith("https://", ignoreCase = true)) {
            loginError = "O link precisa começar com http:// ou https://"
            return
        }
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                var started = false
                val xt = ListLink.xtreamFrom(link)
                if (xt != null) {
                    try {
                        val x = XtreamApi(xt)
                        val info = x.login()
                        store.save(xt)
                        accountInfo = info
                        startSession(xt, x)
                        started = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // não era um servidor Xtream de verdade: segue como lista M3U
                    }
                }
                if (!started) {
                    val acc = Account("", "", "", link)
                    val m = M3uApi(appCtx, link)
                    val info = m.login()
                    store.save(acc)
                    accountInfo = info
                    startSession(acc, m)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = friendly(e)
            } catch (e: OutOfMemoryError) {
                loginError = friendly(e)
            } finally {
                loginBusy = false
            }
        }
    }

    fun clearLoginError() {
        loginError = null
    }

    // ---- login pelo celular (QR code)

    /** Abre o servidorzinho da rede local e mostra o QR code. Chamado quando a pessoa abre a aba "Pelo celular". */
    fun startPhoneLogin() {
        if (phoneServer != null) return
        phoneMsg = null
        val ip = PhoneLoginServer.localIp()
        if (ip == null) {
            phoneUrl = null
            phoneMsg = "A TV não está numa rede Wi-Fi/cabo. Conecte e abra esta aba de novo."
            return
        }
        val token = PhoneLoginServer.newToken()
        val srv = PhoneLoginServer(token) { mode, f ->
            viewModelScope.launch { onPhoneSubmit(mode, f) }
        }
        if (!srv.start()) {
            phoneUrl = null
            phoneMsg = "Não consegui abrir a conexão com o celular nesta TV. Use as outras abas."
            return
        }
        phoneServer = srv
        phoneUrl = "http://$ip:${srv.port}/?k=$token"
    }

    fun stopPhoneLogin() {
        phoneServer?.stop()
        phoneServer = null
        phoneUrl = null
        phoneMsg = null
    }

    private fun onPhoneSubmit(mode: String, f: Map<String, String>) {
        phoneMsg = "Recebido do celular. Entrando..."
        if (mode == "m3u") {
            loginM3u(f["link"] ?: "")
        } else {
            login(f["server"] ?: "", f["user"] ?: "", f["pass"] ?: "")
        }
    }

    override fun onCleared() {
        phoneServer?.stop()
        phoneServer = null
        super.onCleared()
    }

    private fun refreshAccountInfo() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                a.prepare()
                accountInfo = a.info()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                accountInfo = "Não foi possível verificar a conta: ${friendly(e)}"
            }
        }
    }

    private fun startSession(acc: Account, ready: ListApi? = null) {
        stopPhoneLogin()
        api = ready ?: (if (acc.m3u.isNotEmpty()) M3uApi(appCtx, acc.m3u) else XtreamApi(acc))
        account = acc
        tab = Tab.HOME
        openTitle = null
        grid = null
        playing = null
        channelList = null
        pickerOpen = false
        pickerCountry = null
        allGamesOpen = false
        gameDialog = null
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
        heroAll = emptyList()
        heroMovies = emptyList()
        heroSeries = emptyList()
        channelGroups = emptyList()
        channelCards = emptyList()
        cleanChannels = emptyList()
        allGamesOpen = false
        gameDialog = null
        allTitles = emptyList()
        titleIndex = HashMap()
        channelIndex = HashMap()
        openTitle = null
        grid = null
        playing = null
        channelList = null
        pickerOpen = false
        pickerCountry = null
        eventIdx = emptyList()
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
                a.prepare()
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

    private suspend fun downloadAndBuild(a: ListApi): Catalog {
        val show = catalog == null
        if (show) loadMsg = "Baixando a lista..."
        a.refresh()
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

    /** Chamado quando a pessoa volta ao app depois de um tempo fora: sorteia as prateleiras de novo. */
    fun reshuffle() {
        mixSeed = System.nanoTime()
        catalog?.let { c -> viewModelScope.launch { applyCatalog(c) } }
    }

    private suspend fun applyCatalog(c: Catalog) {
        val seed = mixSeed
        val built = withContext(Dispatchers.Default) { Shelves.buildAll(c, seed) }
        val chIdx = HashMap<String, Channel>(c.channels.size * 2)
        for (ch in c.channels) chIdx[ch.key] = ch
        eventIdx = withContext(Dispatchers.Default) { SportsChannels.indexEvents(c.channels) }
        // canais: jogos avulsos limpos (hora local), marcas de TV num cartão só e as pastas reduzidas
        val shaped = withContext(Dispatchers.Default) {
            val clean = SportsChannels.cleanList(c.channels, System.currentTimeMillis(), java.util.TimeZone.getDefault())
            val cards = Brands.group(clean)
            val groups = ArrayList<Pair<String, List<Channel>>>()
            val byGroup = HashMap<String, ArrayList<Channel>>()
            for (ch in cards) {
                val g = ChannelGroups.groupOf(ch) ?: continue
                byGroup.getOrPut(g) { ArrayList() }.add(ch)
            }
            for (g in ChannelGroups.ORDER) {
                val l = byGroup[g] ?: continue
                groups.add(Pair(g, l.sortedBy { ChannelGroups.order(it, g) }))
            }
            Triple(clean, cards, groups)
        }
        cleanChannels = shaped.first
        channelCards = shaped.second
        channelGroups = shaped.third
        for (ch in shaped.first) chIdx[ch.key] = ch
        for (ch in shaped.second) chIdx[ch.key] = ch
        titleIndex = built.index
        channelIndex = chIdx
        allTitles = built.all
        heroAll = built.featured.shuffled(java.util.Random(seed))
        heroMovies = built.featuredMovies.shuffled(java.util.Random(seed + 1))
        heroSeries = built.featuredSeries.shuffled(java.util.Random(seed + 2))
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
        get() = gameDialog != null || allGamesOpen || channelList != null || pickerOpen || openTitle != null || grid != null || tab != Tab.HOME

    fun back(): Boolean {
        if (gameDialog != null) {
            gameDialog = null
            return true
        }
        if (allGamesOpen) {
            allGamesOpen = false
            FocusMemory.arm()
            return true
        }
        if (channelList != null) {
            channelList = null
            FocusMemory.arm()
            return true
        }
        if (pickerOpen) {
            if (pickerCountry != null) {
                pickerCountry = null
                FocusMemory.arm()
            } else {
                closePicker()
            }
            return true
        }
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

    /** Abre "Ver todos". Gêneros e plataformas vêm sorteados de novo a cada abertura; Favoritos mantém a ordem. */
    fun openGrid(s: Shelf) {
        grid = s.reshuffled()
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
        // série: garante que a lista de episódios está carregada (é ela que diz qual é o próximo)
        if (title.series) ensureInfo(title, v)
        playing = PlaybackRequest(
            title = shown,
            url = url,
            isLive = false,
            startMs = start,
            zoom = zoom,
            onProgress = { pos, dur -> saveProgress(title, v, epId, ext, label, pos, dur, zoom) },
            next = if (title.series) ({ nextEpisodeOf(title, v, epId, zoom) }) else null,
        )
    }

    fun playMovie(title: Title, v: Version, zoom: Boolean) {
        ignoreResumeEp = null
        startPlayback(title, v, "", v.ext, "", zoom)
    }

    fun playEpisode(title: Title, v: Version, ep: Episode, zoom: Boolean) {
        ignoreResumeEp = null
        val label = String.format(Locale.US, "T%d E%d", ep.season, ep.number)
        startPlayback(title, v, ep.id, ep.ext, label, zoom)
    }

    fun resumeEntry(e: ResumeEntry) {
        ignoreResumeEp = null
        val t = titleIndex[e.titleKey] ?: return
        val v = t.versions.firstOrNull { it.id == e.versionId } ?: t.versions.firstOrNull() ?: return
        startPlayback(t, v, e.epId, e.ext, e.label, e.zoom)
    }

    /** O episódio que vem depois de [epId] (a lista é ordenada por temporada e número; passa de uma temporada para a outra). */
    private fun nextEpisodeOf(title: Title, v: Version, epId: String, zoom: Boolean): NextEp? {
        val st = episodes[v.id] ?: return null
        val list = st.episodes.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
        val i = list.indexOfFirst { it.id == epId }
        if (i < 0 || i + 1 >= list.size) return null
        val n = list[i + 1]
        val label = String.format(Locale.US, "T%d E%d", n.season, n.number)
        return NextEp(label) { playNext(title, v, epId, n, zoom) }
    }

    /**
     * Troca para o próximo episódio. O episódio que acabou de ser deixado não deve virar "continuar assistindo"
     * (o player antigo ainda guarda a posição ao fechar), então ele é ignorado daqui em diante.
     */
    private fun playNext(title: Title, v: Version, oldEpId: String, n: Episode, zoom: Boolean) {
        ignoreResumeEp = oldEpId
        user.clearResume(title.key)
        val label = String.format(Locale.US, "T%d E%d", n.season, n.number)
        startPlayback(title, v, n.id, n.ext, label, zoom)
        dataRev++
    }

    private var ignoreResumeEp: String? = null

    private fun saveProgress(title: Title, v: Version, epId: String, ext: String, label: String, pos: Long, dur: Long, zoom: Boolean) {
        if (epId.isNotEmpty() && epId == ignoreResumeEp) return
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
        if (ch.isBrand) {
            val v = Brands.variantFor(ch, user.lastVariantOf(ch.key))
            playVariant(ch, v)
            return
        }
        val a = api ?: return
        playing = PlaybackRequest(
            title = ch.name,
            url = "",
            isLive = true,
            live = LiveInfo(ch, { s -> a.streamUrl(Kind.LIVE, s.id, "m3u8") }),
        )
    }

    /** Toca uma praça de uma marca (Globo São Paulo...) e guarda a escolha para a próxima vez. */
    fun playVariant(brand: Channel, v: Variant) {
        val a = api ?: return
        user.setLastVariant(brand.key, v.channel.key)
        playing = PlaybackRequest(
            title = brand.name + " · " + v.label,
            url = "",
            isLive = true,
            live = LiveInfo(v.channel, { s -> a.streamUrl(Kind.LIVE, s.id, "m3u8") }, brand),
        )
    }

    /**
     * O que a mini tela da aba Canais toca: o próprio canal ou, numa marca (Globo...), a praça atual dela.
     * Lê [dataRev] para se atualizar quando a praça muda.
     */
    fun previewLive(ch: Channel): LiveInfo? {
        if (dataRev < 0) return null
        val a = api ?: return null
        val urlOf: (Source) -> String = { s -> a.streamUrl(Kind.LIVE, s.id, "m3u8") }
        if (ch.isBrand) {
            val v = Brands.variantFor(ch, user.lastVariantOf(ch.key))
            return LiveInfo(v.channel, urlOf, ch)
        }
        return LiveInfo(ch, urlOf)
    }

    /** Escolhe a praça de uma marca na aba Canais (vale também quando abrir em tela cheia). */
    fun chooseVariant(brand: Channel, v: Variant) {
        user.setLastVariant(brand.key, v.channel.key)
        dataRev++
    }

    /** Fixa uma fonte do canal (ou volta ao automático com null). */
    fun pinSource(channelKey: String, sourceId: String?) {
        user.setPinned(channelKey, sourceId)
        dataRev++
    }

    fun isFavChannel(key: String): Boolean {
        if (dataRev < 0) return false
        return user.favChannels.contains(key)
    }

    fun toggleFavChannel(key: String) {
        user.toggleFavChannel(key)
        dataRev++
    }

    /** A praça que está tocando agora numa marca (para marcar na lista). */
    fun currentVariantKey(brand: Channel): String = Brands.variantFor(brand, user.lastVariantOf(brand.key)).channel.key

    /** Busca na lista de canais; marcas também casam pelo nome de qualquer praça (ex.: "Anhanguera" acha a Globo). */
    fun searchChannels(q: String): List<Channel> {
        val f = Organizer.fold(q)
        if (f.length < 2) return emptyList()
        val out = ArrayList<Channel>()
        for (ch in channelCards) {
            if (ch.searchFold.contains(f)) {
                out.add(ch)
                if (out.size >= 30) break
            }
        }
        return out
    }

    /** Canais de uma pasta do provedor (já com os jogos avulsos limpos). */
    fun channelsInCategory(cat: String): List<Channel> = cleanChannels.filter { it.category == cat }

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
    // Esportes: times e ligas favoritas, jogos do dia, lembretes, F1/UFC/tênis
    // =====================================================================

    private val tz: java.util.TimeZone get() = java.util.TimeZone.getDefault()

    /** Busca jogos e agenda na internet. Sem [force], só busca se os dados já estão velhos (1 min com jogo ao vivo, 10 min sem). */
    fun refreshSports(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val live = games.any { it.state == 1 } || leagueGames.any { it.state == 1 }
        val maxAge = if (live) 60_000L else 600_000L
        val mustGames = force || sportsDirty || now - gamesAt >= maxAge
        if (mustGames && !(gamesJob?.isActive == true && !force && !sportsDirty)) {
            sportsDirty = false
            gamesAt = now
            val teams = favTeams
            val leagues = favLeagues
            val my = ++gamesGen
            gamesJob?.cancel()
            gamesJob = viewModelScope.launch {
                gamesLoading = true
                var err: String? = null
                try {
                    games = if (teams.isEmpty()) emptyList() else SportsApi.gamesFor(teams)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    err = "Não consegui buscar os jogos agora."
                }
                try {
                    leagueGames = if (leagues.isEmpty()) emptyList() else SportsApi.leagueGames(leagues, now - SportsTime.DAY, now + 2L * SportsTime.DAY)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    err = "Não consegui buscar os jogos agora."
                }
                gamesError = err
                if (my == gamesGen) gamesLoading = false
            }
        }
        if (force || now - eventsAt >= 1_800_000L) {
            eventsAt = now
            eventsJob?.cancel()
            eventsJob = viewModelScope.launch {
                try {
                    sportEvents = SportsApi.sportEvents()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // sem internet ou serviço fora do ar: os cartões seguem sem data
                }
            }
        }
        checkReminders(now)
    }

    /** Faixa do Início: jogos dos meus times (★) primeiro, depois os de hoje/amanhã das ligas escolhidas. */
    fun stripGames(now: Long): List<StripGame> = LeaguePick.strip(games, leagueGames, favTeams, favLeagues, now, tz)

    fun gameChannelCount(g: Game): Int = SportsChannels.matchGame(g, eventIdx).size

    /** Devolve um aviso se não deu para escolher (máximo de 5 times). */
    fun toggleFavTeam(t: FavTeam): String? {
        val cur = favTeams
        if (cur.any { it.id == t.id }) {
            favTeams = cur.filter { it.id != t.id }
        } else {
            if (cur.size >= 5) return "Você já escolheu 5 times. Tire um para escolher outro."
            favTeams = cur + t
        }
        user.saveFavTeams(favTeams)
        sportsDirty = true
        allGamesAt = 0L
        dataRev++
        return null
    }

    fun isFavTeam(id: String): Boolean = favTeams.any { it.id == id }

    /** Devolve um aviso se não deu para escolher (máximo de 5 ligas). */
    fun toggleFavLeague(slug: String): String? {
        val cur = favLeagues
        if (cur.contains(slug)) {
            favLeagues = cur.filter { it != slug }
        } else {
            if (cur.size >= SportsData.MAX_LEAGUES) return "Você já escolheu ${SportsData.MAX_LEAGUES} ligas. Tire uma para escolher outra."
            favLeagues = cur + slug
        }
        user.saveFavLeagues(favLeagues)
        sportsDirty = true
        allGamesAt = 0L
        dataRev++
        return null
    }

    fun isFavLeague(slug: String): Boolean = favLeagues.contains(slug)

    fun openPicker() {
        pickerCountry = null
        pickerOpen = true
    }

    fun closePicker() {
        pickerOpen = false
        pickerCountry = null
        FocusMemory.arm()
        refreshSports(force = false)
    }

    fun pickCountry(code: String?) {
        pickerCountry = code
    }

    // ---- "Ver todos": jogos dos próximos dias, agrupados por dia

    fun openAllGames() {
        allGamesOpen = true
        loadAllGames(false)
    }

    fun loadAllGames(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && allGames.isNotEmpty() && now - allGamesAt < 600_000L) return
        if (allGamesLoading) return
        val slugs = (favLeagues + favTeams.flatMap { it.leagues }).distinct()
        if (slugs.isEmpty()) {
            allGames = emptyList()
            return
        }
        allGamesLoading = true
        allGamesError = null
        allGamesAt = now
        viewModelScope.launch {
            try {
                allGames = SportsApi.leagueGames(slugs, now - SportsTime.DAY, now + 7L * SportsTime.DAY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allGamesError = "Não consegui buscar os jogos agora."
                allGamesAt = 0L
            } finally {
                allGamesLoading = false
            }
        }
    }

    fun gamesByDay(now: Long): List<Pair<Long, List<StripGame>>> =
        LeaguePick.byDay(allGames, allGames, favTeams, favLeagues, now, tz)

    // ---- aba "Jogos": ontem, hoje, amanhã e os próximos dias, dos mais populares para os menos

    /** Busca ontem até daqui a 5 dias nas ligas mais populares + as dos meus times e ligas. A ESPN agrupa pelo dia dos EUA, por isso 2 dias para trás. */
    fun loadDayGames(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val live = popGames.any { it.state == 1 }
        val maxAge = if (live) 60_000L else 600_000L
        if (!force && popGames.isNotEmpty() && now - popAt < maxAge) return
        if (popLoading) return
        popLoading = true
        popError = null
        popAt = now
        val slugs = (SportsData.popularLeagues + favLeagues + favTeams.flatMap { it.leagues }).distinct()
        viewModelScope.launch {
            try {
                popGames = SportsApi.leagueGames(slugs, now - 2L * SportsTime.DAY, now + 6L * SportsTime.DAY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                popAt = 0L
                popError = "Não consegui buscar os jogos agora."
            } finally {
                popLoading = false
            }
        }
    }

    /** Jogos do dia [offset] (-1 ontem, 0 hoje, 1 amanhã...) já com os dados mais novos de cada fonte. */
    fun dayGames(offset: Int, now: Long): List<StripGame> =
        LeaguePick.forDay(LeaguePick.mergeAll(LeaguePick.mergeAll(popGames, games), leagueGames), favTeams, offset, now, tz)

    // ---- pop-up do jogo

    class GameInfo(
        val matched: List<Channel>,
        val comp: BroadcastComp?,
        val inList: Set<String>,
        val tableDate: String,
    )

    fun showGame(g: Game) {
        gameDialog = g
    }

    fun closeGame() {
        gameDialog = null
        FocusMemory.arm()
    }

    fun gameInfo(g: Game): GameInfo {
        val now = System.currentTimeMillis()
        val matched = SportsChannels.displayMerged(SportsChannels.matchGame(g, eventIdx), now, tz)
        val table = BroadcastStore.table
        val comp = table.forKey(g.leagueSlug)
        val inList = if (comp == null) emptySet() else Broadcasts.inList(comp.options, channelCards)
        return GameInfo(matched, comp, inList, table.updatedPretty())
    }

    /** "Assistir agora": um canal só na lista → toca; vários → abre a lista deles. */
    fun watchNow(g: Game) {
        val m = gameInfo(g).matched
        gameDialog = null
        if (m.size == 1) playChannel(m[0]) else openGameChannels(g)
    }

    /** Mostra os canais da lista para o jogo (os achados, os das emissoras que costumam passar e os de esporte). */
    fun openGameChannels(g: Game) {
        gameDialog = null
        val info = gameInfo(g)
        val now = System.currentTimeMillis()
        val comp = info.comp
        val extra = if (comp != null) Broadcasts.channelsFor(comp.options, channelCards) else emptyList()
        val when0 = SportsTime.dayLabel(g.startMs, now, tz) + " " + SportsTime.clock(g.startMs, tz)
        val hint = if (info.matched.isNotEmpty()) {
            "Achei na sua lista. Se travar, volte e escolha outra fonte."
        } else {
            "Este jogo ainda não está em \"Jogos em Destaque\" da sua lista. Veja abaixo os canais de quem costuma passar, " +
                "os canais de esporte ou use a busca."
        }
        channelList = ChannelListRequest(
            title = g.home.short + " x " + g.away.short,
            subtitle = when0 + " · " + g.leagueName,
            hint = hint,
            highlight = info.matched,
            searchQuery = g.home.short,
            extra = extra,
            extraTitle = if (comp != null) "Costuma passar em: " + comp.options.take(4).joinToString(" · ") else "",
        )
    }

    // ---- lembretes

    fun isReminded(g: Game): Boolean = reminders.any { it.gameKey == g.key }

    /** Liga/desliga o lembrete do jogo. Devolve a frase para mostrar na tela. */
    fun toggleReminder(g: Game): String {
        val cur = reminders
        val ex = cur.firstOrNull { it.gameKey == g.key }
        if (ex != null) {
            ReminderAlarms.cancel(appCtx, ex)
            reminders = cur.filter { it.gameKey != g.key }
            user.saveReminders(reminders)
            return "Lembrete removido."
        }
        if (g.state != 0) return "Esse jogo já começou."
        val r = Reminder(
            gameKey = g.key,
            title = g.home.short + " x " + g.away.short,
            league = g.leagueName,
            startMs = g.startMs,
            homeId = g.home.id,
            awayId = g.away.id,
            homeName = g.home.name,
            awayName = g.away.name,
        )
        reminders = (cur + r).sortedBy { it.startMs }
        user.saveReminders(reminders)
        ReminderAlarms.schedule(appCtx, r)
        return "Pronto! Vou avisar 10 minutos antes do jogo."
    }

    private fun gameOf(r: Reminder): Game {
        fun side(id: String, name: String, short: String) =
            Side(id, name, short, null, null, listOf(Organizer.fold(name), Organizer.fold(short)).filter { it.isNotEmpty() })
        val parts = r.title.split(" x ")
        return Game(
            id = r.gameKey.substringAfter('|'),
            leagueSlug = r.gameKey.substringBefore('|'),
            leagueName = r.league,
            startMs = r.startMs,
            state = 0,
            detail = "",
            home = side(r.homeId, r.homeName, parts.getOrElse(0) { r.homeName }),
            away = side(r.awayId, r.awayName, parts.getOrElse(1) { r.awayName }),
        )
    }

    /** Vê se algum lembrete chegou na hora (10 min antes do jogo). Chamado ao abrir o app e a cada meio minuto. */
    fun checkReminders(now: Long) {
        if (reminders.isEmpty()) return
        val old = reminders.filter { it.startMs < now - 6L * 3_600_000L }
        if (old.isNotEmpty()) {
            reminders = reminders.filter { it.startMs >= now - 6L * 3_600_000L }
            user.saveReminders(reminders)
        }
        if (dueReminder != null) return
        dueReminder = reminders.firstOrNull { !it.seen && it.startMs - now <= ReminderAlarms.LEAD_MS && it.startMs > now - 3L * 3_600_000L }
    }

    fun dismissDue(openChannels: Boolean) {
        val r = dueReminder ?: return
        dueReminder = null
        reminders = reminders.map { if (it.gameKey == r.gameKey) it.withSeen(true) else it }
        user.saveReminders(reminders)
        if (openChannels) openGameChannels(gameOf(r))
    }

    // ---- outros esportes

    private fun sportCategoryNames(): List<String> {
        val c = catalog ?: return emptyList()
        return SportsChannels.sportCategories(c.channelCategories)
    }

    /** Toque em Fórmula 1 / UFC / Tênis: mostra os canais da lista para aquele esporte. */
    fun openSport(kind: SportKind) {
        val c = catalog ?: return
        val ev = sportEvents[kind]
        val now = System.currentTimeMillis()
        val sub = if (ev == null) {
            "Sem data confirmada agora"
        } else {
            ev.name + " · " + SportsTime.dayLabel(ev.startMs, now, tz) + " " + SportsTime.clock(ev.startMs, tz)
        }
        val list = SportsChannels.forKind(kind, channelCards)
        val key = when (kind) {
            SportKind.F1 -> "f1"
            SportKind.UFC -> "ufc"
            SportKind.TENNIS -> "tennis"
        }
        val comp = BroadcastStore.table.forKey(key)
        val extra = if (comp != null) Broadcasts.channelsFor(comp.options, channelCards).filter { e -> list.none { it.key == e.key } } else emptyList()
        val hint = if (list.isNotEmpty()) {
            "Canais da sua lista para ${kind.label}."
        } else {
            "Não achei canais de ${kind.label} pelo nome. Veja os canais de esporte abaixo ou use a busca."
        }
        val q = when (kind) {
            SportKind.F1 -> "F1"
            SportKind.UFC -> "UFC"
            SportKind.TENNIS -> "tenis"
        }
        channelList = ChannelListRequest(
            kind.label, sub, hint, list, q,
            extra = extra,
            extraTitle = if (comp != null) "Costuma passar em: " + comp.options.joinToString(" · ") else "",
        )
    }

    fun sportCategoriesForList(): List<String> = sportCategoryNames()

    fun searchFor(q: String) {
        channelList = null
        pickerOpen = false
        searchPrefill = q
        selectTab(Tab.SEARCH)
    }

    fun clearSearchPrefill() {
        searchPrefill = null
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

    /** Troca o fuso dos horários (vazio = o do aparelho) e refaz os nomes dos jogos avulsos dos canais. */
    fun setTimeZone(id: String) {
        user.setTzId(id)
        AppTz.apply(id)
        catalog?.let { c -> viewModelScope.launch { applyCatalog(c) } }
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
