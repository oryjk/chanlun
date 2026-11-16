package cn.oryjk.changuan.application

import cn.oryjk.changuan.domain.AppLoginResult
import cn.oryjk.changuan.domain.AppSession
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.AssetCategory
import cn.oryjk.changuan.domain.AssetSortField
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.DownloadedApk
import cn.oryjk.changuan.domain.EmailCodeSent
import cn.oryjk.changuan.domain.MarketAssetList
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.QuoteSeries
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.ReleaseHistoryItem
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.ShizukuChannelState
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.SystemInstallResult
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.domain.TrackingOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MarketReader {
    suspend fun search(query: String): List<Stock>
    suspend fun snapshot(secid: String, timeframe: Timeframe, refresh: Boolean = false): MarketSnapshot
    suspend fun quote(secid: String): MarketQuote
    suspend fun series(secid: String): QuoteSeries
    suspend fun rankings(): RankingBoard
    suspend fun confluence(secid: String): SignalConfluence
    suspend fun assets(
        category: AssetCategory,
        sortField: AssetSortField = AssetSortField.AMOUNT,
        descending: Boolean = true,
        limit: Int = 60,
    ): MarketAssetList = MarketAssetList(category = category, asOfDate = null, items = emptyList())
}

interface TrackingReader {
    suspend fun fetchTrackings(): TrackingOverview
}

interface TrackingCommands {
    suspend fun startTracking(secid: String)
    suspend fun cancelTrackings(secids: List<String>)
}

interface TrackingPreferenceStore {
    fun pinnedSecids(userId: String): Set<String>
    fun savePinnedSecids(userId: String, secids: Set<String>)
    fun customOrder(userId: String): List<String>
    fun saveCustomOrder(userId: String, order: List<String>)
}

class InMemoryTrackingPreferenceStore : TrackingPreferenceStore {
    private val pinnedByUser = mutableMapOf<String, Set<String>>()
    private val orderByUser = mutableMapOf<String, List<String>>()

    override fun pinnedSecids(userId: String): Set<String> = pinnedByUser[userId].orEmpty()

    override fun savePinnedSecids(userId: String, secids: Set<String>) {
        pinnedByUser[userId] = secids
    }

    override fun customOrder(userId: String): List<String> = orderByUser[userId].orEmpty()

    override fun saveCustomOrder(userId: String, order: List<String>) {
        orderByUser[userId] = order
    }
}

interface QuoteWriter {
    suspend fun record(quote: MarketQuote): QuoteSeries
}

interface SessionStore {
    fun token(): String?
    fun save(token: String)
    fun clear()
}

interface RouteStore {
    fun restore(): Triple<Stock?, Timeframe, RootTab>
    fun save(stock: Stock?, timeframe: Timeframe, tab: RootTab)
}

interface EnvironmentStore {
    val environment: StateFlow<BackendEnvironment>
    fun select(environment: BackendEnvironment)
}

class InMemoryEnvironmentStore(
    initial: BackendEnvironment = BackendEnvironment.DEFAULT,
) : EnvironmentStore {
    private val _environment = MutableStateFlow(initial)
    override val environment: StateFlow<BackendEnvironment> = _environment.asStateFlow()
    override fun select(environment: BackendEnvironment) {
        _environment.value = environment
    }
}

interface ThemePreferenceStore {
    val themeMode: StateFlow<AppThemeMode>
    fun setThemeMode(mode: AppThemeMode)
}

class InMemoryThemePreferenceStore(
    initial: AppThemeMode = AppThemeMode.SYSTEM,
) : ThemePreferenceStore {
    private val _themeMode = MutableStateFlow(initial)
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()
    override fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }
}

data class EnvironmentServices(
    val market: MarketUseCases,
    val auth: AuthUseCases,
    val tracking: TrackingUseCases? = null,
)

fun interface EnvironmentServicesFactory {
    fun create(environment: BackendEnvironment): EnvironmentServices
}

interface AuthReader {
    suspend fun session(): AppSession
}

interface AuthCommands {
    suspend fun sendEmailCode(email: String): EmailCodeSent
    suspend fun registerWithEmail(
        email: String,
        code: String,
        password: String,
        nickname: String? = null,
    ): AppLoginResult

    suspend fun loginWithEmail(
        email: String,
        password: String,
    ): AppLoginResult

    suspend fun logout()
    suspend fun deleteAccount()
}

interface AppUpdateReader {
    suspend fun checkLatest(): AppUpdateInfo?
    suspend fun fetchReleaseHistory(): List<ReleaseHistoryItem> = emptyList()
}

interface AppUpdateDownloader {
    suspend fun download(
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): DownloadedApk
}

interface AppUpdateInstaller {
    val channelState: StateFlow<ShizukuChannelState>
    fun setShizukuEnabled(enabled: Boolean)
    fun refreshShizukuState()
    fun requestShizukuPermission()
    fun openShizuku()
    suspend fun installShizuku(apk: DownloadedApk, info: AppUpdateInfo)
    fun installWithSystem(apk: DownloadedApk): SystemInstallResult
    fun close() {}
}
