package cn.oryjk.changuan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.oryjk.changuan.application.AppUpdateUseCases
import cn.oryjk.changuan.application.AuthUseCases
import cn.oryjk.changuan.application.MarketUseCases
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.DefaultStocks
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.QuoteSeriesRules
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.domain.UpdateDownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

const val INITIAL_UPDATE_CHECK_DELAY_MILLIS = 3_000L
const val UPDATE_CHECK_INTERVAL_MILLIS = 30_000L

enum class AuthMode {
    LOGIN,
    REGISTER,
}

data class AppUiState(
    val stock: Stock = DefaultStocks.KWEICHOW_MOUTAI,
    val timeframe: Timeframe = Timeframe.DAILY,
    val tab: RootTab = RootTab.MARKET,
    val snapshot: MarketSnapshot? = null,
    val confluence: SignalConfluence? = null,
    val quote: MarketQuote? = null,
    val series: List<QuotePoint> = emptyList(),
    val board: RankingBoard? = null,
    val user: AppUser? = null,
    val loading: Boolean = false,
    val confluenceLoading: Boolean = false,
    val boardLoading: Boolean = false,
    val accountBusy: Boolean = false,
    val error: String = "",
    val boardError: String = "",
    val confluenceError: String = "",
    val accountError: String = "",
    val accountMessage: String = "",
    val authMode: AuthMode = AuthMode.LOGIN,
    val codeCooldownSeconds: Int = 0,
    val layers: ChartLayers = ChartLayers(),
    val searchResults: List<Stock> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String = "",
    val pendingUpdate: AppUpdateInfo? = null,
    val updateDownloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val lastUpdateCheckTime: String = "尚未检测",
)

class AppViewModel(
    val market: MarketUseCases,
    val auth: AuthUseCases,
    private val routes: RouteStore,
    val updates: AppUpdateUseCases? = null,
    externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var active: Boolean = false
    var authEpoch: Int = 0
        private set
    private var generation: Int = 0
    private var loadJob: Job? = null
    private var boardJob: Job? = null
    private var searchJob: Job? = null
    private var cooldownJob: Job? = null
    private var updateLoopJob: Job? = null
    private var updateDownloadJob: Job? = null
    private val dismissedVersionCodes = mutableSetOf<Int>()

    init {
        val restored = routes.restore()
        _state.update { current ->
            current.copy(
                stock = restored.first ?: DefaultStocks.KWEICHOW_MOUTAI,
                timeframe = restored.second,
                tab = restored.third,
            )
        }
    }

    fun activate(isForeground: Boolean) {
        if (active == isForeground) return
        active = isForeground
        if (isForeground) {
            reload()
            startPeriodicUpdateCheck()
            scope.launch { restoreSession() }
        } else {
            generation += 1
            loadJob?.cancel()
            boardJob?.cancel()
            updateLoopJob?.cancel()
            _state.update {
                it.copy(
                    loading = false,
                    confluenceLoading = false,
                    boardLoading = false,
                )
            }
        }
    }

    private fun startPeriodicUpdateCheck() {
        val updateService = updates ?: return
        updateLoopJob?.cancel()
        updateLoopJob = scope.launch {
            delay(INITIAL_UPDATE_CHECK_DELAY_MILLIS)
            while (active && isActive) {
                performUpdateCheck(updateService, forceShowEvenIfDismissed = false)
                delay(UPDATE_CHECK_INTERVAL_MILLIS)
            }
        }
    }

    fun checkUpdateNow(onResult: ((Boolean) -> Unit)? = null) {
        val updateService = updates ?: return
        scope.launch {
            val found = performUpdateCheck(updateService, forceShowEvenIfDismissed = true)
            onResult?.invoke(found)
        }
    }

    private suspend fun performUpdateCheck(
        updateService: AppUpdateUseCases,
        forceShowEvenIfDismissed: Boolean,
    ): Boolean {
        val info = runCatching { updateService.reader.checkLatest() }.getOrNull()
        val timeText = MarketTime.formatDate(Instant.now(), "HH:mm:ss")
        _state.update { it.copy(lastUpdateCheckTime = timeText) }
        if (info != null) {
            val allowed = forceShowEvenIfDismissed || info.versionCode !in dismissedVersionCodes
            if (allowed && _state.value.updateDownloadState !is UpdateDownloadState.Downloading) {
                _state.update { it.copy(pendingUpdate = info) }
                return true
            }
        }
        return false
    }

    fun dismissUpdate() {
        updateDownloadJob?.cancel()
        _state.value.pendingUpdate?.let { dismissedVersionCodes.add(it.versionCode) }
        _state.update {
            it.copy(
                pendingUpdate = null,
                updateDownloadState = UpdateDownloadState.Idle,
            )
        }
    }

    fun startUpdateDownload(info: AppUpdateInfo) {
        val updateService = updates ?: return
        if (updateDownloadJob?.isActive == true) return
        _state.update {
            it.copy(updateDownloadState = UpdateDownloadState.Downloading(0L, info.fileSizeBytes))
        }
        updateDownloadJob = scope.launch {
            try {
                val apkPath = updateService.downloader.download(info) { downloaded, total ->
                    _state.update {
                        it.copy(updateDownloadState = UpdateDownloadState.Downloading(downloaded, total))
                    }
                }
                _state.update {
                    it.copy(updateDownloadState = UpdateDownloadState.Ready(apkPath))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        updateDownloadState = UpdateDownloadState.Failed(
                            error.message ?: "下载更新包失败",
                        ),
                    )
                }
            }
        }
    }

    fun canInstallPackages(): Boolean =
        updates?.installer?.canInstallPackages() ?: false

    fun openInstallPermissionSettings() {
        updates?.installer?.openInstallPermissionSettings()
    }

    fun installDownloadedUpdate(apkPath: String) {
        updates?.installer?.installApk(apkPath)
    }

    fun changeTab(nextTab: RootTab) {
        if (_state.value.tab == nextTab) return
        _state.update { it.copy(tab = nextTab) }
        routes.save(_state.value.stock, _state.value.timeframe, nextTab)
        reload()
    }

    fun selectStock(stock: Stock, forceDaily: Boolean = false) {
        val nextTimeframe = if (forceDaily) Timeframe.DAILY else _state.value.timeframe
        _state.update {
            it.copy(
                stock = stock,
                timeframe = nextTimeframe,
                tab = RootTab.MARKET,
                snapshot = null,
                quote = null,
                series = emptyList(),
                confluence = null,
                error = "",
                confluenceError = "",
            )
        }
        routes.save(stock, nextTimeframe, RootTab.MARKET)
        reload()
    }

    fun changeTimeframe(nextTimeframe: Timeframe) {
        if (_state.value.timeframe == nextTimeframe) return
        _state.update {
            it.copy(
                timeframe = nextTimeframe,
                snapshot = null,
                confluence = null,
                quote = null,
                series = emptyList(),
                error = "",
                confluenceError = "",
            )
        }
        routes.save(_state.value.stock, nextTimeframe, _state.value.tab)
        reload()
    }

    fun toggleLayer(transform: (ChartLayers) -> ChartLayers) {
        _state.update { it.copy(layers = transform(it.layers)) }
    }

    fun reload(refresh: Boolean = false) {
        generation += 1
        val currentGen = generation
        loadJob?.cancel()
        boardJob?.cancel()
        _state.update {
            it.copy(
                loading = false,
                confluenceLoading = false,
                boardLoading = false,
            )
        }
        if (!active) return
        val currentState = _state.value
        if (currentState.tab == RootTab.RANKING) {
            startRankings(currentGen)
            return
        }
        if (currentState.tab != RootTab.MARKET) return
        val stock = currentState.stock
        val period = currentState.timeframe
        _state.update {
            it.copy(
                error = "",
                confluenceError = "",
                loading = it.snapshot == null && it.quote == null,
            )
        }
        if (period == Timeframe.REALTIME) {
            loadJob = scope.launch {
                pollQuotes(stock = stock, currentGen = currentGen)
            }
        } else {
            _state.update { it.copy(confluenceLoading = true) }
            loadJob = scope.launch {
                val confluenceDeferred = async {
                    runCatching { market.reader.confluence(secid = stock.secid) }
                }
                val snapResult = runCatching {
                    market.reader.snapshot(
                        secid = stock.secid,
                        timeframe = period,
                        refresh = refresh,
                    )
                }
                if (!accept(currentGen)) return@launch
                snapResult.onSuccess { snapshot ->
                    _state.update { it.copy(snapshot = snapshot, loading = false, error = "") }
                }.onFailure { err ->
                    if (err !is CancellationException) {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = err.message ?: "读取行情失败，请稍后重试",
                            )
                        }
                    }
                }
                val confResult = confluenceDeferred.await()
                if (!accept(currentGen)) return@launch
                confResult.onSuccess { conf ->
                    _state.update {
                        it.copy(
                            confluence = conf,
                            confluenceLoading = false,
                            confluenceError = "",
                        )
                    }
                }.onFailure { err ->
                    if (err !is CancellationException) {
                        _state.update {
                            it.copy(
                                confluenceLoading = false,
                                confluenceError = err.message ?: "多级别联立暂时不可用",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun CoroutineScope.accept(targetGen: Int): Boolean =
        active && generation == targetGen && isActive

    private suspend fun CoroutineScope.pollQuotes(stock: Stock, currentGen: Int) {
        runCatching { market.reader.series(secid = stock.secid) }
            .onSuccess { history ->
                if (accept(currentGen)) {
                    _state.update { it.copy(series = history.points) }
                }
            }
            .onFailure { err ->
                if (accept(currentGen) && err !is CancellationException) {
                    _state.update { it.copy(error = err.message ?: "读取分时序列失败") }
                }
            }
        var first = true
        var failures = 0
        while (accept(currentGen)) {
            if (first || MarketTime.isOpen(stock = stock)) {
                first = false
                try {
                    val latestQuote = market.reader.quote(secid = stock.secid)
                    if (!accept(currentGen)) return
                    var merged = QuoteSeriesRules.merge(
                        current = _state.value.series,
                        incoming = emptyList(),
                        stock = stock,
                        reference = latestQuote.time,
                    )
                    if (MarketTime.isOpen(stock = stock)) {
                        val sampled = market.writer.record(latestQuote)
                        if (!accept(currentGen)) return
                        merged = QuoteSeriesRules.merge(
                            current = merged,
                            incoming = sampled.points,
                            stock = stock,
                            reference = latestQuote.time,
                        )
                    }
                    _state.update {
                        it.copy(
                            quote = latestQuote,
                            series = merged,
                            loading = false,
                            error = "",
                        )
                    }
                    failures = 0
                } catch (err: Exception) {
                    if (err is CancellationException) return
                    if (accept(currentGen)) {
                        failures = min(4, failures + 1)
                        _state.update {
                            it.copy(
                                loading = false,
                                error = err.message ?: "读取实时报价失败",
                            )
                        }
                    }
                }
            }
            val sleepSeconds = if (failures > 0) {
                min(30.0, 3.0 * 2.0.pow(failures.toDouble())).toLong()
            } else {
                3L
            }
            delay(sleepSeconds * 1000L)
        }
    }

    private fun startRankings(currentGen: Int) {
        _state.update { it.copy(boardLoading = it.board == null, boardError = "") }
        boardJob = scope.launch {
            while (accept(currentGen)) {
                runCatching { market.reader.rankings() }
                    .onSuccess { board ->
                        if (accept(currentGen)) {
                            _state.update {
                                it.copy(
                                    board = board,
                                    boardLoading = false,
                                    boardError = "",
                                )
                            }
                        }
                    }
                    .onFailure { err ->
                        if (accept(currentGen) && err !is CancellationException) {
                            _state.update {
                                it.copy(
                                    boardLoading = false,
                                    boardError = err.message ?: "读取收盘榜单失败",
                                )
                            }
                        }
                    }
                delay(60_000L)
            }
        }
    }

    fun searchStocks(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    searchLoading = false,
                    searchError = "",
                )
            }
            return
        }
        _state.update { it.copy(searchLoading = true, searchError = "") }
        searchJob = scope.launch {
            delay(200L)
            runCatching { market.reader.search(trimmed) }
                .onSuccess { items ->
                    _state.update {
                        it.copy(
                            searchResults = items,
                            searchLoading = false,
                            searchError = "",
                        )
                    }
                }
                .onFailure { err ->
                    if (err !is CancellationException) {
                        _state.update {
                            it.copy(
                                searchLoading = false,
                                searchError = err.message ?: "搜索证券失败，请稍后重试",
                            )
                        }
                    }
                }
        }
    }

    fun switchAuthMode(mode: AuthMode) {
        _state.update {
            it.copy(
                authMode = mode,
                accountError = "",
                accountMessage = "",
            )
        }
    }

    suspend fun restoreSession() {
        if (_state.value.accountBusy) return
        authEpoch += 1
        val epoch = authEpoch
        runCatching { auth.currentSession() }
            .onSuccess { session ->
                if (epoch == authEpoch) {
                    _state.update { it.copy(user = session.user) }
                }
            }
            .onFailure {
                if (epoch == authEpoch) {
                    _state.update {
                        it.copy(accountError = "无法恢复登录状态，可继续查看行情，稍后重试登录")
                    }
                }
            }
    }

    suspend fun sendEmailCode(email: String) {
        if (_state.value.accountBusy || _state.value.codeCooldownSeconds > 0) return
        _state.update { it.copy(accountBusy = true, accountError = "", accountMessage = "") }
        try {
            val sent = auth.sendEmailCode(email)
            _state.update {
                it.copy(
                    accountMessage = "验证码已发送至邮箱，${sent.expiresInSeconds / 60} 分钟内有效",
                    codeCooldownSeconds = sent.cooldownSeconds,
                )
            }
            startCooldown(sent.cooldownSeconds)
        } catch (err: Exception) {
            if (err !is CancellationException) {
                _state.update { it.copy(accountError = err.message ?: "发送验证码失败，请稍后重试") }
            }
        } finally {
            _state.update { it.copy(accountBusy = false) }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            var remaining = seconds
            while (remaining > 0 && isActive) {
                delay(1_000L)
                remaining -= 1
                _state.update { it.copy(codeCooldownSeconds = remaining) }
            }
        }
    }

    suspend fun registerWithEmail(
        email: String,
        code: String,
        password: String,
        nickname: String? = null,
    ) {
        if (_state.value.accountBusy) return
        _state.update { it.copy(accountBusy = true, accountError = "", accountMessage = "") }
        authEpoch += 1
        val epoch = authEpoch
        try {
            val result = auth.registerWithEmail(
                email = email,
                code = code,
                password = password,
                nickname = nickname,
            )
            if (epoch == authEpoch) {
                _state.update {
                    it.copy(
                        user = result.user,
                        accountError = "",
                        accountMessage = "",
                    )
                }
            }
        } catch (err: Exception) {
            if (epoch == authEpoch && err !is CancellationException) {
                _state.update { it.copy(accountError = err.message ?: "注册失败，请检查输入后重试") }
            }
        } finally {
            _state.update { it.copy(accountBusy = false) }
        }
    }

    suspend fun loginWithEmail(email: String, password: String) {
        if (_state.value.accountBusy) return
        _state.update { it.copy(accountBusy = true, accountError = "", accountMessage = "") }
        authEpoch += 1
        val epoch = authEpoch
        try {
            val result = auth.loginWithEmail(email = email, password = password)
            if (epoch == authEpoch) {
                _state.update {
                    it.copy(
                        user = result.user,
                        accountError = "",
                        accountMessage = "",
                    )
                }
            }
        } catch (err: Exception) {
            if (epoch == authEpoch && err !is CancellationException) {
                _state.update { it.copy(accountError = err.message ?: "登录失败，请检查邮箱与密码") }
            }
        } finally {
            _state.update { it.copy(accountBusy = false) }
        }
    }

    suspend fun logout() {
        authEpoch += 1
        _state.update { it.copy(accountBusy = true, accountError = "", accountMessage = "") }
        try {
            auth.logout()
        } catch (_: Exception) {
            _state.update {
                it.copy(accountError = "本机已退出，服务端退出未完成，请联网后重新登录或等待会话到期")
            }
        } finally {
            _state.update { it.copy(accountBusy = false, user = null) }
        }
    }

    suspend fun deleteAccount() {
        authEpoch += 1
        _state.update { it.copy(accountBusy = true, accountError = "", accountMessage = "") }
        try {
            auth.deleteAccount()
            _state.update {
                it.copy(
                    user = null,
                    accountMessage = "账号与登录信息已删除，您仍可作为游客查看行情",
                )
            }
        } catch (err: Exception) {
            val current = runCatching { auth.reader.session() }.getOrNull()
            if (current != null && !current.authenticated) {
                credentialsRevoked()
            }
            _state.update { it.copy(accountError = err.message ?: "删除账号未完成，请重试") }
        } finally {
            _state.update { it.copy(accountBusy = false) }
        }
    }

    fun credentialsRevoked() {
        authEpoch += 1
        auth.store.clear()
        _state.update { it.copy(user = null) }
    }

    class Factory(
        private val market: MarketUseCases,
        private val auth: AuthUseCases,
        private val routes: RouteStore,
        private val updates: AppUpdateUseCases? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(
                market = market,
                auth = auth,
                routes = routes,
                updates = updates,
            ) as T
    }
}
