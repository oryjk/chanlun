package cn.oryjk.changuan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.oryjk.changuan.application.AppUpdateUseCases
import cn.oryjk.changuan.application.AuthUseCases
import cn.oryjk.changuan.application.EnvironmentServices
import cn.oryjk.changuan.application.EnvironmentServicesFactory
import cn.oryjk.changuan.application.EnvironmentStore
import cn.oryjk.changuan.application.InMemoryEnvironmentStore
import cn.oryjk.changuan.application.InMemoryThemePreferenceStore
import cn.oryjk.changuan.application.MarketUseCases
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.ThemePreferenceStore
import cn.oryjk.changuan.application.TrackingUseCases
import cn.oryjk.changuan.domain.ApkInstallState
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.AssetCategory
import cn.oryjk.changuan.domain.AssetSortField
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.DefaultStocks
import cn.oryjk.changuan.domain.DownloadedApk
import cn.oryjk.changuan.domain.MarketAssetItem
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.OvernightPhase
import cn.oryjk.changuan.domain.OvernightSnapshot
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.QuoteSeriesRules
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.ReleaseHistoryItem
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.ShizukuAvailability
import cn.oryjk.changuan.domain.ShizukuChannelState
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.SystemInstallResult
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.domain.TrackedAsset
import cn.oryjk.changuan.domain.TrackingSortField
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
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

const val INITIAL_UPDATE_CHECK_DELAY_MILLIS = 3_000L
const val UPDATE_CHECK_INTERVAL_MILLIS = 30_000L
const val MARKET_ASSETS_PAGE_SIZE = 100

enum class AuthMode {
    LOGIN,
    REGISTER,
}

enum class RankingMethod(val label: String) {
    CHAN("缠论"),
    OVERNIGHT("隔夜套利"),
}

data class AppUiState(
    val stock: Stock = DefaultStocks.KWEICHOW_MOUTAI,
    val timeframe: Timeframe = Timeframe.DAILY,
    val tab: RootTab = RootTab.MARKET,
    val detailSheetOpen: Boolean = false,
    val assetCategory: AssetCategory = AssetCategory.STOCK,
    val assetSortField: AssetSortField = AssetSortField.AMOUNT,
    val assetSortDescending: Boolean = true,
    val stockAssets: List<MarketAssetItem> = emptyList(),
    val etfAssets: List<MarketAssetItem> = emptyList(),
    val stockAssetsTotal: Int = 0,
    val etfAssetsTotal: Int = 0,
    val stockAssetsHasMore: Boolean = false,
    val etfAssetsHasMore: Boolean = false,
    val assetsAsOfDate: String? = null,
    val assetsLoading: Boolean = false,
    val assetsRefreshing: Boolean = false,
    val assetsLoadingMore: Boolean = false,
    val assetsError: String = "",
    val assetsLoadMoreError: String = "",
    val trackings: List<TrackedAsset> = emptyList(),
    val trackingAsOfDate: String = "",
    val trackingLoading: Boolean = false,
    val trackingRefreshing: Boolean = false,
    val trackingError: String = "",
    val pendingTrackSecids: Set<String> = emptySet(),
    val trackingSortField: TrackingSortField? = TrackingSortField.CUMULATIVE_PROFIT,
    val trackingSortDescending: Boolean = true,
    val pinnedSecids: Set<String> = emptySet(),
    val customTrackingOrder: List<String> = emptyList(),
    val environment: BackendEnvironment = BackendEnvironment.DEFAULT,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val snapshot: MarketSnapshot? = null,
    val confluence: SignalConfluence? = null,
    val quote: MarketQuote? = null,
    val series: List<QuotePoint> = emptyList(),
    val board: RankingBoard? = null,
    val rankingMethod: RankingMethod = RankingMethod.CHAN,
    val overnight: OvernightSnapshot? = null,
    val overnightLoading: Boolean = false,
    val overnightRefreshing: Boolean = false,
    val overnightError: String = "",
    val user: AppUser? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val confluenceLoading: Boolean = false,
    val boardLoading: Boolean = false,
    val boardRefreshing: Boolean = false,
    val showAuthDialog: Boolean = false,
    val accountBusy: Boolean = false,
    val sendingCode: Boolean = false,
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
    val shizukuState: ShizukuChannelState = ShizukuChannelState(),
    val installState: ApkInstallState = ApkInstallState.Idle,
    val lastUpdateCheckTime: String = "尚未检测",
    val releaseHistoryLoading: Boolean = false,
    val releaseHistoryItems: List<ReleaseHistoryItem> = emptyList(),
    val releaseHistoryError: String? = null,
    val snackbarMessage: String? = null,
)

class AppViewModel(
    private val servicesFactory: EnvironmentServicesFactory,
    private val environmentStore: EnvironmentStore,
    private val themeStore: ThemePreferenceStore,
    private val routes: RouteStore,
    val updates: AppUpdateUseCases? = null,
    externalScope: CoroutineScope? = null,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    constructor(
        market: MarketUseCases,
        auth: AuthUseCases,
        routes: RouteStore,
        updates: AppUpdateUseCases? = null,
        externalScope: CoroutineScope? = null,
    ) : this(
        servicesFactory = EnvironmentServicesFactory { EnvironmentServices(market = market, auth = auth) },
        environmentStore = InMemoryEnvironmentStore(BackendEnvironment.DEFAULT),
        themeStore = InMemoryThemePreferenceStore(AppThemeMode.SYSTEM),
        routes = routes,
        updates = updates,
        externalScope = externalScope,
    )

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private var activeServices: EnvironmentServices = servicesFactory.create(environmentStore.environment.value)
    val market: MarketUseCases
        get() = activeServices.market
    val auth: AuthUseCases
        get() = activeServices.auth
    val tracking: TrackingUseCases?
        get() = activeServices.tracking

    private val _state = MutableStateFlow(
        AppUiState(
            environment = environmentStore.environment.value,
            themeMode = themeStore.themeMode.value,
            shizukuState = updates?.installer?.channelState?.value ?: ShizukuChannelState(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var active: Boolean = false
    var authEpoch: Int = 0
        private set
    private var generation: Int = 0
    private var loadJob: Job? = null
    private var assetsJob: Job? = null
    private var assetsLoadMoreJob: Job? = null
    private var trackingJob: Job? = null
    private var boardJob: Job? = null
    private var overnightJob: Job? = null
    private var overnightExpiryJob: Job? = null
    private var searchJob: Job? = null
    private var cooldownJob: Job? = null
    private var updateLoopJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var updateInstallJob: Job? = null
    private var historyJob: Job? = null
    private var assetsGeneration: Long = 0L
    private var trackingGeneration: Long = 0L
    private var downloadGeneration: Long = 0L
    private var historyGeneration: Long = 0L
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
            refreshInstallerState()
            reload()
            startPeriodicUpdateCheck()
            scope.launch { restoreSession() }
        } else {
            generation += 1
            assetsGeneration += 1
            trackingGeneration += 1
            loadJob?.cancel()
            assetsJob?.cancel()
            assetsLoadMoreJob?.cancel()
            trackingJob?.cancel()
            boardJob?.cancel()
            overnightJob?.cancel()
            overnightExpiryJob?.cancel()
            updateLoopJob?.cancel()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    confluenceLoading = false,
                    assetsLoading = false,
                    assetsRefreshing = false,
                    assetsLoadingMore = false,
                    trackingLoading = false,
                    trackingRefreshing = false,
                    boardLoading = false,
                    boardRefreshing = false,
                    overnightLoading = false,
                    overnightRefreshing = false,
                )
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        themeStore.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun selectEnvironment(environment: BackendEnvironment) {
        if (_state.value.environment == environment) return
        try {
            environmentStore.select(environment)
        } catch (error: Exception) {
            _state.update { it.copy(snackbarMessage = error.message ?: "无法保存后端环境，请重试") }
            return
        }
        generation += 1
        assetsGeneration += 1
        trackingGeneration += 1
        authEpoch += 1
        loadJob?.cancel()
        assetsJob?.cancel()
        assetsLoadMoreJob?.cancel()
        trackingJob?.cancel()
        boardJob?.cancel()
        overnightJob?.cancel()
        overnightExpiryJob?.cancel()
        searchJob?.cancel()
        cooldownJob?.cancel()
        activeServices = servicesFactory.create(environment)
        _state.update {
            it.copy(
                environment = environment,
                detailSheetOpen = false,
                snapshot = null,
                confluence = null,
                quote = null,
                series = emptyList(),
                stockAssets = emptyList(),
                etfAssets = emptyList(),
                stockAssetsTotal = 0,
                etfAssetsTotal = 0,
                stockAssetsHasMore = false,
                etfAssetsHasMore = false,
                assetsAsOfDate = null,
                assetsLoading = false,
                assetsRefreshing = false,
                assetsLoadingMore = false,
                assetsError = "",
                assetsLoadMoreError = "",
                trackings = emptyList(),
                trackingAsOfDate = "",
                trackingLoading = false,
                trackingRefreshing = false,
                trackingError = "",
                pendingTrackSecids = emptySet(),
                pinnedSecids = emptySet(),
                customTrackingOrder = emptyList(),
                board = null,
                overnight = null,
                overnightLoading = false,
                overnightRefreshing = false,
                overnightError = "",
                user = null,
                loading = false,
                refreshing = false,
                confluenceLoading = false,
                boardLoading = false,
                boardRefreshing = false,
                accountBusy = false,
                sendingCode = false,
                error = "",
                boardError = "",
                confluenceError = "",
                accountError = "",
                accountMessage = "",
                codeCooldownSeconds = 0,
                searchResults = emptyList(),
                searchLoading = false,
                searchError = "",
                snackbarMessage = "已切换至${environment.label}",
            )
        }
        if (active) {
            reload()
            scope.launch { restoreSession() }
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun startPeriodicUpdateCheck() {
        val updateService = updates ?: return
        updateLoopJob?.cancel()
        updateLoopJob = scope.launch {
            delay(INITIAL_UPDATE_CHECK_DELAY_MILLIS)
            while (active && isActive) {
                performUpdateCheck(updateService, forceShowEvenIfDismissed = false, surfaceFailure = false)
                delay(UPDATE_CHECK_INTERVAL_MILLIS)
            }
        }
    }

    fun checkUpdateNow(onResult: ((Boolean) -> Unit)? = null) {
        val updateService = updates ?: return
        scope.launch {
            val found = performUpdateCheck(
                updateService = updateService,
                forceShowEvenIfDismissed = true,
                surfaceFailure = true,
            )
            onResult?.invoke(found)
        }
    }

    private suspend fun performUpdateCheck(
        updateService: AppUpdateUseCases,
        forceShowEvenIfDismissed: Boolean,
        surfaceFailure: Boolean,
    ): Boolean {
        val result = runCatching { updateService.reader.checkLatest() }
        val timeText = MarketTime.formatDate(Instant.now(), "HH:mm:ss")
        _state.update { it.copy(lastUpdateCheckTime = timeText) }
        result.onFailure { err ->
            if (err is CancellationException) throw err
            if (surfaceFailure) {
                _state.update { it.copy(snackbarMessage = err.message ?: "无法检查更新，请稍后重试") }
            }
            return false
        }
        val info = result.getOrNull()
        if (info != null) {
            val allowed = forceShowEvenIfDismissed || info.versionCode !in dismissedVersionCodes
            if (allowed && _state.value.updateDownloadState !is UpdateDownloadState.Downloading) {
                _state.update { it.copy(pendingUpdate = info) }
                return true
            }
        } else if (surfaceFailure) {
            _state.update { it.copy(snackbarMessage = "当前已是最新版本") }
        }
        return false
    }

    fun dismissUpdate() {
        if (_state.value.installState == ApkInstallState.Installing) return
        downloadGeneration += 1
        updateDownloadJob?.cancel()
        updateInstallJob?.cancel()
        _state.value.pendingUpdate?.let { dismissedVersionCodes.add(it.versionCode) }
        _state.update {
            it.copy(
                pendingUpdate = null,
                updateDownloadState = UpdateDownloadState.Idle,
                installState = ApkInstallState.Idle,
            )
        }
    }

    fun startUpdateDownload(info: AppUpdateInfo) {
        val updateService = updates ?: return
        if (updateDownloadJob?.isActive == true) return
        val gen = ++downloadGeneration
        _state.update {
            it.copy(
                updateDownloadState = UpdateDownloadState.Downloading(0L, info.fileSizeBytes),
                installState = ApkInstallState.Idle,
            )
        }
        updateDownloadJob = scope.launch {
            try {
                val apk = updateService.downloader.download(info) { downloaded, total ->
                    if (gen == downloadGeneration) {
                        _state.update {
                            it.copy(updateDownloadState = UpdateDownloadState.Downloading(downloaded, total))
                        }
                    }
                }
                if (gen == downloadGeneration) {
                    _state.update {
                        it.copy(updateDownloadState = UpdateDownloadState.Ready(apk))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (gen == downloadGeneration) {
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
    }

    fun refreshInstallerState() {
        val installer = updates?.installer ?: return
        installer.refreshShizukuState()
        _state.update { it.copy(shizukuState = installer.channelState.value) }
    }

    fun setShizukuEnabled(enabled: Boolean) {
        val installer = updates?.installer ?: return
        installer.setShizukuEnabled(enabled)
        _state.update { it.copy(shizukuState = installer.channelState.value) }
    }

    fun requestShizukuPermission() {
        val installer = updates?.installer ?: return
        installer.refreshShizukuState()
        if (installer.channelState.value.availability == ShizukuAvailability.NeedsAuthorization) {
            installer.requestShizukuPermission()
        }
        _state.update { it.copy(shizukuState = installer.channelState.value) }
    }

    fun openShizuku() {
        updates?.installer?.openShizuku()
    }

    fun installDownloadedUpdate(apk: DownloadedApk, info: AppUpdateInfo) {
        val installer = updates?.installer ?: return
        val currentInstall = _state.value.installState
        if (currentInstall == ApkInstallState.Installing || currentInstall == ApkInstallState.Submitted) return
        refreshInstallerState()
        if (!installer.channelState.value.useShizuku) {
            installWithSystem(apk)
            return
        }
        _state.update { it.copy(installState = ApkInstallState.Installing) }
        updateInstallJob = scope.launch {
            try {
                installer.installShizuku(apk, info)
                _state.update { it.copy(installState = ApkInstallState.Submitted) }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(installState = ApkInstallState.Idle) }
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        installState = ApkInstallState.Failed(
                            error.message ?: "Shizuku 安装失败，可改用系统安装",
                        ),
                    )
                }
            }
        }
    }

    fun installWithSystem(apk: DownloadedApk) {
        val installer = updates?.installer ?: return
        val currentInstall = _state.value.installState
        if (currentInstall == ApkInstallState.Installing || currentInstall == ApkInstallState.Submitted) return
        try {
            val result = installer.installWithSystem(apk)
            _state.update {
                it.copy(
                    installState = when (result) {
                        SystemInstallResult.Opened -> ApkInstallState.Idle
                        SystemInstallResult.PermissionRequired -> ApkInstallState.NeedsSystemPermission
                    },
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(installState = ApkInstallState.Failed(error.message ?: "无法打开系统安装器"))
            }
        }
    }

    fun loadReleaseHistory(force: Boolean = false) {
        val reader = updates?.reader ?: return
        val current = _state.value
        if (!force && (current.releaseHistoryLoading || current.releaseHistoryItems.isNotEmpty())) return
        historyJob?.cancel()
        val gen = ++historyGeneration
        _state.update { it.copy(releaseHistoryLoading = true, releaseHistoryError = null) }
        historyJob = scope.launch {
            try {
                val items = reader.fetchReleaseHistory()
                if (gen == historyGeneration) {
                    _state.update {
                        it.copy(
                            releaseHistoryLoading = false,
                            releaseHistoryItems = items,
                            releaseHistoryError = null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (gen == historyGeneration) {
                    _state.update {
                        it.copy(
                            releaseHistoryLoading = false,
                            releaseHistoryError = error.message ?: "无法加载历史版本记录",
                        )
                    }
                }
            }
        }
    }

    fun changeTab(nextTab: RootTab) {
        if (_state.value.tab == nextTab) return
        _state.update { it.copy(tab = nextTab) }
        routes.save(_state.value.stock, _state.value.timeframe, nextTab)
        reload()
    }

    fun selectRankingMethod(method: RankingMethod) {
        if (_state.value.rankingMethod == method) return
        _state.update { it.copy(rankingMethod = method) }
        if (_state.value.tab == RootTab.RANKING) reload()
    }

    fun selectAssetCategory(category: AssetCategory) {
        if (_state.value.assetCategory == category) return
        assetsLoadMoreJob?.cancel()
        _state.update {
            it.copy(
                assetCategory = category,
                assetsError = "",
                assetsLoadingMore = false,
                assetsLoadMoreError = "",
            )
        }
        reloadAssets(forceRefresh = false)
    }

    fun toggleAssetSort(field: AssetSortField) {
        val current = _state.value
        val nextDescending = if (current.assetSortField == field) !current.assetSortDescending else true
        _state.update {
            it.copy(
                assetSortField = field,
                assetSortDescending = nextDescending,
            )
        }
        reloadAssets(forceRefresh = true)
    }

    fun openAssetDetail(stock: Stock, forceDaily: Boolean = false) {
        val nextTimeframe = if (forceDaily) Timeframe.DAILY else _state.value.timeframe
        _state.update {
            it.copy(
                stock = stock,
                timeframe = nextTimeframe,
                detailSheetOpen = true,
                snapshot = null,
                quote = null,
                series = emptyList(),
                confluence = null,
                error = "",
                confluenceError = "",
            )
        }
        routes.save(stock, nextTimeframe, _state.value.tab)
        reload()
    }

    fun closeAssetDetail() {
        if (!_state.value.detailSheetOpen) return
        generation += 1
        loadJob?.cancel()
        _state.update {
            it.copy(
                detailSheetOpen = false,
                loading = false,
                refreshing = false,
                confluenceLoading = false,
            )
        }
        if (_state.value.tab == RootTab.RANKING) reload()
    }

    fun selectStock(stock: Stock, forceDaily: Boolean = false) {
        openAssetDetail(stock = stock, forceDaily = forceDaily)
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
        overnightJob?.cancel()
        overnightExpiryJob?.cancel()
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                confluenceLoading = false,
                boardLoading = false,
                boardRefreshing = false,
                overnightLoading = false,
                overnightRefreshing = false,
            )
        }
        if (!active) return
        val currentState = _state.value
        if (currentState.detailSheetOpen) {
            loadDetail(
                stock = currentState.stock,
                period = currentState.timeframe,
                currentGen = currentGen,
                refresh = refresh,
            )
            return
        }
        when (currentState.tab) {
            RootTab.MARKET -> reloadAssets(forceRefresh = refresh)
            RootTab.TRACKING -> reloadTrackings(forceRefresh = refresh)
            RootTab.RANKING -> when (currentState.rankingMethod) {
                RankingMethod.CHAN -> startRankings(currentGen, showPullIndicator = refresh)
                RankingMethod.OVERNIGHT -> startOvernight(currentGen, showPullIndicator = refresh)
            }
            RootTab.ACCOUNT -> Unit
        }
    }

    fun reloadAssets(forceRefresh: Boolean = true) {
        if (!active) return
        val current = _state.value
        val category = current.assetCategory
        val existingList = if (category == AssetCategory.STOCK) current.stockAssets else current.etfAssets
        if (!forceRefresh && existingList.isNotEmpty()) return
        val gen = ++assetsGeneration
        assetsJob?.cancel()
        assetsLoadMoreJob?.cancel()
        _state.update {
            it.copy(
                assetsError = "",
                assetsLoadMoreError = "",
                assetsLoadingMore = false,
                assetsLoading = existingList.isEmpty(),
                assetsRefreshing = forceRefresh && existingList.isNotEmpty(),
            )
        }
        assetsJob = scope.launch {
            runCatching {
                market.reader.assets(
                    category = category,
                    sortField = _state.value.assetSortField,
                    descending = _state.value.assetSortDescending,
                    limit = MARKET_ASSETS_PAGE_SIZE,
                    offset = 0,
                )
            }.onSuccess { result ->
                if (!active || gen != assetsGeneration) return@onSuccess
                _state.update { state ->
                    when (result.category) {
                        AssetCategory.STOCK -> state.copy(
                            stockAssets = result.items,
                            stockAssetsTotal = result.total,
                            stockAssetsHasMore = result.hasMore,
                            assetsAsOfDate = result.asOfDate ?: state.assetsAsOfDate,
                            assetsLoading = false,
                            assetsRefreshing = false,
                            assetsError = "",
                            assetsLoadMoreError = "",
                        )
                        AssetCategory.ETF -> state.copy(
                            etfAssets = result.items,
                            etfAssetsTotal = result.total,
                            etfAssetsHasMore = result.hasMore,
                            assetsAsOfDate = result.asOfDate ?: state.assetsAsOfDate,
                            assetsLoading = false,
                            assetsRefreshing = false,
                            assetsError = "",
                            assetsLoadMoreError = "",
                        )
                    }
                }
            }.onFailure { err ->
                if (!active || gen != assetsGeneration || err is CancellationException) return@onFailure
                _state.update {
                    it.copy(
                        assetsLoading = false,
                        assetsRefreshing = false,
                        assetsError = err.message ?: "读取行情清单失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun loadMoreAssets() {
        if (!active) return
        val current = _state.value
        if (current.assetsLoading || current.assetsRefreshing || current.assetsLoadingMore) return
        val category = current.assetCategory
        val existingList = if (category == AssetCategory.STOCK) current.stockAssets else current.etfAssets
        val hasMore = if (category == AssetCategory.STOCK) current.stockAssetsHasMore else current.etfAssetsHasMore
        if (existingList.isEmpty() || !hasMore) return

        val gen = assetsGeneration
        val sortField = current.assetSortField
        val descending = current.assetSortDescending
        val offset = existingList.size
        assetsLoadMoreJob?.cancel()
        _state.update { it.copy(assetsLoadingMore = true, assetsLoadMoreError = "") }
        assetsLoadMoreJob = scope.launch {
            runCatching {
                market.reader.assets(
                    category = category,
                    sortField = sortField,
                    descending = descending,
                    limit = MARKET_ASSETS_PAGE_SIZE,
                    offset = offset,
                )
            }.onSuccess { result ->
                if (!active || gen != assetsGeneration || _state.value.assetCategory != category) return@onSuccess
                _state.update { state ->
                    val currentItems = if (result.category == AssetCategory.STOCK) state.stockAssets else state.etfAssets
                    val seen = currentItems.mapTo(HashSet()) { it.stock.secid }
                    val merged = currentItems + result.items.filter { seen.add(it.stock.secid) }
                    when (result.category) {
                        AssetCategory.STOCK -> state.copy(
                            stockAssets = merged,
                            stockAssetsTotal = result.total.coerceAtLeast(merged.size),
                            stockAssetsHasMore = result.hasMore,
                            assetsLoadingMore = false,
                            assetsLoadMoreError = "",
                        )
                        AssetCategory.ETF -> state.copy(
                            etfAssets = merged,
                            etfAssetsTotal = result.total.coerceAtLeast(merged.size),
                            etfAssetsHasMore = result.hasMore,
                            assetsLoadingMore = false,
                            assetsLoadMoreError = "",
                        )
                    }
                }
            }.onFailure { err ->
                if (!active || gen != assetsGeneration || _state.value.assetCategory != category || err is CancellationException) {
                    return@onFailure
                }
                _state.update {
                    it.copy(
                        assetsLoadingMore = false,
                        assetsLoadMoreError = err.message ?: "加载更多行情失败，点击重试",
                    )
                }
            }
        }
    }

    fun reloadTrackings(forceRefresh: Boolean = true) {
        val trackingService = tracking ?: return
        val user = _state.value.user ?: return
        if (!active) return
        val current = _state.value
        if (!forceRefresh && current.trackings.isNotEmpty()) return
        val gen = ++trackingGeneration
        val prefs = trackingService.prefs
        _state.update {
            it.copy(
                pinnedSecids = prefs.pinnedSecids(user.userId),
                customTrackingOrder = prefs.customOrder(user.userId),
                trackingError = "",
                trackingLoading = current.trackings.isEmpty(),
                trackingRefreshing = forceRefresh && current.trackings.isNotEmpty(),
            )
        }
        trackingJob?.cancel()
        trackingJob = scope.launch {
            runCatching { trackingService.reader.fetchTrackings() }
                .onSuccess { overview ->
                    if (!active || gen != trackingGeneration) return@onSuccess
                    _state.update {
                        it.copy(
                            trackings = overview.items,
                            trackingAsOfDate = overview.asOfDate,
                            trackingLoading = false,
                            trackingRefreshing = false,
                            trackingError = "",
                        )
                    }
                }
                .onFailure { err ->
                    if (!active || gen != trackingGeneration || err is CancellationException) return@onFailure
                    _state.update {
                        it.copy(
                            trackingLoading = false,
                            trackingRefreshing = false,
                            trackingError = err.message ?: "读取跟踪列表失败，请稍后重试",
                        )
                    }
                }
        }
    }

    fun toggleTracking(stock: Stock) {
        val isTracked = _state.value.trackings.any { it.stock.secid == stock.secid }
        if (isTracked) {
            cancelTrackings(listOf(stock.secid))
        } else {
            startTracking(stock)
        }
    }

    fun startTracking(stock: Stock) {
        if (_state.value.user == null) {
            openAuthDialog(AuthMode.LOGIN)
            _state.update { it.copy(snackbarMessage = "登录后即可保存跟踪记录") }
            return
        }
        val trackingService = tracking ?: return
        if (stock.secid in _state.value.pendingTrackSecids) return
        _state.update { it.copy(pendingTrackSecids = it.pendingTrackSecids + stock.secid) }
        scope.launch {
            try {
                trackingService.commands.startTracking(stock.secid)
                reloadTrackings(forceRefresh = true)
                _state.update { it.copy(snackbarMessage = "已加入跟踪：${stock.name}") }
            } catch (err: Exception) {
                if (err !is CancellationException) {
                    _state.update { it.copy(snackbarMessage = err.message ?: "加入跟踪失败，请稍后重试") }
                }
            } finally {
                _state.update { it.copy(pendingTrackSecids = it.pendingTrackSecids - stock.secid) }
            }
        }
    }

    fun cancelTrackings(secids: List<String>) {
        if (_state.value.user == null) {
            openAuthDialog(AuthMode.LOGIN)
            return
        }
        val targets = secids.distinct().filter { it.isNotBlank() }
        if (targets.isEmpty()) return
        val trackingService = tracking ?: return
        _state.update { it.copy(pendingTrackSecids = it.pendingTrackSecids + targets) }
        scope.launch {
            try {
                trackingService.commands.cancelTrackings(targets)
                val removedSet = targets.toSet()
                val userId = _state.value.user?.userId
                if (userId != null) {
                    val nextPinned = _state.value.pinnedSecids - removedSet
                    val nextOrder = _state.value.customTrackingOrder.filterNot { it in removedSet }
                    trackingService.prefs.savePinnedSecids(userId, nextPinned)
                    trackingService.prefs.saveCustomOrder(userId, nextOrder)
                    _state.update { it.copy(pinnedSecids = nextPinned, customTrackingOrder = nextOrder) }
                }
                reloadTrackings(forceRefresh = true)
                _state.update {
                    it.copy(
                        snackbarMessage = if (targets.size == 1) "已结束跟踪" else "已结束 ${targets.size} 只标的跟踪",
                    )
                }
            } catch (err: Exception) {
                if (err !is CancellationException) {
                    _state.update { it.copy(snackbarMessage = err.message ?: "结束跟踪失败，请稍后重试") }
                }
            } finally {
                _state.update { it.copy(pendingTrackSecids = it.pendingTrackSecids - targets.toSet()) }
            }
        }
    }

    fun toggleTrackingSort(field: TrackingSortField) {
        val current = _state.value
        val nextDesc = if (current.trackingSortField == field) !current.trackingSortDescending else true
        _state.update { it.copy(trackingSortField = field, trackingSortDescending = nextDesc) }
    }

    fun togglePinTracking(secid: String) {
        val userId = _state.value.user?.userId ?: return
        val prefs = tracking?.prefs ?: return
        val next = if (secid in _state.value.pinnedSecids) {
            _state.value.pinnedSecids - secid
        } else {
            _state.value.pinnedSecids + secid
        }
        prefs.savePinnedSecids(userId, next)
        _state.update { it.copy(pinnedSecids = next) }
    }

    fun moveTracking(secid: String, moveUp: Boolean) {
        val userId = _state.value.user?.userId ?: return
        val prefs = tracking?.prefs ?: return
        val currentSecids = _state.value.trackings.map { it.stock.secid }
        val baseOrder = (_state.value.customTrackingOrder + currentSecids).distinct()
        val index = baseOrder.indexOf(secid)
        if (index < 0) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in baseOrder.indices) return
        val mutable = baseOrder.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        prefs.saveCustomOrder(userId, mutable)
        _state.update {
            it.copy(
                customTrackingOrder = mutable,
                trackingSortField = null,
            )
        }
    }

    private fun loadDetail(
        stock: Stock,
        period: Timeframe,
        currentGen: Int,
        refresh: Boolean,
    ) {
        val currentState = _state.value
        val hasContent = currentState.snapshot != null || currentState.quote != null
        _state.update {
            it.copy(
                error = "",
                confluenceError = "",
                loading = !hasContent,
                refreshing = refresh && hasContent,
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
                    _state.update {
                        it.copy(
                            snapshot = snapshot,
                            loading = false,
                            refreshing = false,
                            error = "",
                        )
                    }
                }.onFailure { err ->
                    if (err !is CancellationException) {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
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
                            refreshing = false,
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
                                refreshing = false,
                                error = err.message ?: "读取实时报价失败",
                            )
                        }
                    }
                }
            } else {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
            val sleepSeconds = if (failures > 0) {
                min(30.0, 3.0 * 2.0.pow(failures.toDouble())).toLong()
            } else {
                3L
            }
            delay(sleepSeconds * 1000L)
        }
    }

    private fun startRankings(currentGen: Int, showPullIndicator: Boolean = false) {
        val hasBoard = _state.value.board != null
        _state.update {
            it.copy(
                boardLoading = !hasBoard,
                boardRefreshing = showPullIndicator && hasBoard,
                boardError = "",
            )
        }
        boardJob = scope.launch {
            while (accept(currentGen)) {
                runCatching { market.reader.rankings() }
                    .onSuccess { board ->
                        if (accept(currentGen)) {
                            _state.update {
                                it.copy(
                                    board = board,
                                    boardLoading = false,
                                    boardRefreshing = false,
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
                                    boardRefreshing = false,
                                    boardError = err.message ?: "读取收盘榜单失败",
                                )
                            }
                        }
                    }
                delay(60_000L)
            }
        }
    }

    private fun startOvernight(currentGen: Int, showPullIndicator: Boolean) {
        val query = activeServices.overnight ?: return
        val cached = trackOvernightDeadline(currentGen, _state.value.overnight)
        _state.update {
            it.copy(
                overnight = cached,
                overnightLoading = cached == null,
                overnightRefreshing = showPullIndicator && cached != null,
            )
        }
        overnightJob = scope.launch {
            while (accept(currentGen)) {
                try {
                    val snapshot = query.snapshot()
                    if (!accept(currentGen)) return@launch
                    val current = trackOvernightDeadline(currentGen, snapshot)
                    _state.update {
                        it.copy(
                            overnight = current,
                            overnightLoading = false,
                            overnightRefreshing = false,
                            overnightError = "",
                        )
                    }
                    delay(if (snapshot.phase == OvernightPhase.SCANNING) 5_000L else 30_000L)
                } catch (error: Exception) {
                    if (error is CancellationException || !accept(currentGen)) return@launch
                    _state.update {
                        it.copy(
                            overnightLoading = false,
                            overnightRefreshing = false,
                            overnightError = error.message ?: "读取隔夜套利扫描结果失败",
                        )
                    }
                    delay(30_000L)
                }
            }
        }
    }

    private fun trackOvernightDeadline(currentGen: Int, snapshot: OvernightSnapshot?): OvernightSnapshot? {
        overnightExpiryJob?.cancel()
        if (snapshot?.phase != OvernightPhase.EXIT) return snapshot

        val deadline = snapshot.remindersUntil
        val receivedAt = now()
        if (deadline == null || !receivedAt.isBefore(deadline)) {
            return snapshot.copy(phase = OvernightPhase.WAITING, reminders = emptyList(), remindersUntil = null)
        }

        var nextDeadline: Instant = deadline
        val reminders = snapshot.reminders.map { reminder ->
            val exceptionDeadline = reminder.exceptionValidUntil
            when {
                reminder.exceptionObserved != true -> reminder
                exceptionDeadline == null || !receivedAt.isBefore(exceptionDeadline) ->
                    reminder.copy(exceptionObserved = null, exceptionAsOf = null, exceptionValidUntil = null)
                else -> {
                    nextDeadline = minOf(nextDeadline, exceptionDeadline)
                    reminder
                }
            }
        }
        val current = snapshot.copy(reminders = reminders)

        overnightExpiryJob = scope.launch {
            delay(Duration.between(receivedAt, nextDeadline).toMillis().coerceAtLeast(1L))
            if (accept(currentGen)) {
                overnightExpiryJob = null
                val updated = trackOvernightDeadline(currentGen, current)
                _state.update { it.copy(overnight = updated) }
            }
        }
        return current
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

    fun openAuthDialog(mode: AuthMode = AuthMode.LOGIN) {
        _state.update {
            it.copy(
                showAuthDialog = true,
                authMode = mode,
                accountError = "",
                accountMessage = "",
            )
        }
    }

    fun closeAuthDialog() {
        if (_state.value.accountBusy) return
        _state.update {
            it.copy(
                showAuthDialog = false,
                accountError = "",
                accountMessage = "",
            )
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
                    if (session.user != null) {
                        reloadTrackings(forceRefresh = true)
                    } else {
                        clearTrackingState()
                    }
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
        if (_state.value.sendingCode || _state.value.accountBusy || _state.value.codeCooldownSeconds > 0) return
        _state.update { it.copy(sendingCode = true, accountError = "", accountMessage = "") }
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
            _state.update { it.copy(sendingCode = false) }
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
                        showAuthDialog = false,
                        accountError = "",
                        accountMessage = "",
                        snackbarMessage = "注册成功，欢迎来到缠观",
                    )
                }
                reloadTrackings(forceRefresh = true)
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
                        showAuthDialog = false,
                        accountError = "",
                        accountMessage = "",
                        snackbarMessage = "欢迎回来，${result.user.nickname}",
                    )
                }
                reloadTrackings(forceRefresh = true)
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
            _state.update { it.copy(snackbarMessage = "已退出当前账号") }
        } catch (_: Exception) {
            _state.update {
                it.copy(accountError = "本机已退出，服务端退出未完成，请联网后重新登录或等待会话到期")
            }
        } finally {
            _state.update { it.copy(accountBusy = false, user = null) }
            clearTrackingState()
        }
    }

    suspend fun deleteAccount() {
        authEpoch += 1
        _state.update { it.copy(accountBusy = true, accountError = "", accountMessage = "") }
        try {
            auth.deleteAccount()
            clearTrackingState()
            _state.update {
                it.copy(
                    user = null,
                    accountMessage = "账号与登录信息已删除，您仍可作为游客查看行情",
                    snackbarMessage = "账号已删除",
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
        clearTrackingState()
    }

    private fun clearTrackingState() {
        trackingGeneration += 1
        trackingJob?.cancel()
        _state.update {
            it.copy(
                trackings = emptyList(),
                trackingAsOfDate = "",
                trackingLoading = false,
                trackingRefreshing = false,
                trackingError = "",
                pendingTrackSecids = emptySet(),
                pinnedSecids = emptySet(),
                customTrackingOrder = emptyList(),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        updates?.installer?.close()
    }

    class Factory(
        private val servicesFactory: EnvironmentServicesFactory,
        private val environmentStore: EnvironmentStore,
        private val themeStore: ThemePreferenceStore,
        private val routes: RouteStore,
        private val updates: AppUpdateUseCases? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(
                servicesFactory = servicesFactory,
                environmentStore = environmentStore,
                themeStore = themeStore,
                routes = routes,
                updates = updates,
            ) as T
    }
}
