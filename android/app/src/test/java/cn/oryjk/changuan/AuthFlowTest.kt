package cn.oryjk.changuan

import cn.oryjk.changuan.application.AuthCommands
import cn.oryjk.changuan.application.AuthReader
import cn.oryjk.changuan.application.AuthUseCases
import cn.oryjk.changuan.application.EnvironmentServices
import cn.oryjk.changuan.application.EnvironmentServicesFactory
import cn.oryjk.changuan.application.InMemoryEnvironmentStore
import cn.oryjk.changuan.application.InMemoryThemePreferenceStore
import cn.oryjk.changuan.application.InMemoryTrackingPreferenceStore
import cn.oryjk.changuan.application.MarketReader
import cn.oryjk.changuan.application.MarketUseCases
import cn.oryjk.changuan.application.QuoteWriter
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.SessionStore
import cn.oryjk.changuan.application.TrackingCommands
import cn.oryjk.changuan.application.TrackingReader
import cn.oryjk.changuan.application.TrackingUseCases
import cn.oryjk.changuan.domain.AppLoginResult
import cn.oryjk.changuan.domain.AppSession
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.AssetCategory
import cn.oryjk.changuan.domain.AssetSortField
import cn.oryjk.changuan.domain.DefaultStocks
import cn.oryjk.changuan.domain.EmailCodeSent
import cn.oryjk.changuan.domain.MarketAssetItem
import cn.oryjk.changuan.domain.MarketAssetList
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.QuoteSeries
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.domain.TrackedAsset
import cn.oryjk.changuan.domain.TrackingOverview
import cn.oryjk.changuan.presentation.AppViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowTest {
    private class InMemorySessionStore(var saved: String? = null) : SessionStore {
        override fun token(): String? = saved
        override fun save(token: String) { saved = token }
        override fun clear() { saved = null }
    }

    private class InMemoryRouteStore : RouteStore {
        private var current: Triple<Stock?, Timeframe, RootTab> =
            Triple(null, Timeframe.DAILY, RootTab.MARKET)

        override fun restore(): Triple<Stock?, Timeframe, RootTab> = current
        override fun save(stock: Stock?, timeframe: Timeframe, tab: RootTab) {
            current = Triple(stock, timeframe, tab)
        }
    }

    private class FakeMarketGateway : MarketReader, QuoteWriter {
        override suspend fun search(query: String): List<Stock> = emptyList()
        override suspend fun snapshot(secid: String, timeframe: Timeframe, refresh: Boolean): MarketSnapshot =
            error("Not called in auth test")
        override suspend fun quote(secid: String): MarketQuote = error("Not called")
        override suspend fun series(secid: String): QuoteSeries = error("Not called")
        override suspend fun rankings(): RankingBoard = error("Not called")
        override suspend fun confluence(secid: String): SignalConfluence = error("Not called")
        override suspend fun record(quote: MarketQuote): QuoteSeries = error("Not called")
    }

    private class FakeAuthGateway : AuthReader, AuthCommands {
        var sessionGate: CompletableDeferred<AppSession>? = null
        var lastCodeEmail: String? = null
        var currentUser: AppUser? = null

        override suspend fun session(): AppSession {
            sessionGate?.let { return it.await() }
            return AppSession(authenticated = currentUser != null, user = currentUser)
        }

        override suspend fun sendEmailCode(email: String): EmailCodeSent {
            lastCodeEmail = email
            return EmailCodeSent(expiresInSeconds = 600, cooldownSeconds = 60)
        }

        override suspend fun registerWithEmail(
            email: String,
            code: String,
            password: String,
            nickname: String?,
        ): AppLoginResult {
            val user = AppUser("u-reg", nickname ?: email.substringBefore('@'))
            currentUser = user
            return AppLoginResult(user, "token-reg", Instant.parse("2026-05-10T00:00:00Z"))
        }

        override suspend fun loginWithEmail(email: String, password: String): AppLoginResult {
            val user = AppUser("u-login", email.substringBefore('@'))
            currentUser = user
            return AppLoginResult(user, "token-login", Instant.parse("2026-05-10T00:00:00Z"))
        }

        override suspend fun logout() {
            currentUser = null
        }

        override suspend fun deleteAccount() {
            currentUser = null
        }
    }

    @Test
    fun `email code registration and password login update session and handle logout race`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val store = InMemorySessionStore()
        val authGateway = FakeAuthGateway()
        val marketGateway = FakeMarketGateway()
        val vm = AppViewModel(
            market = MarketUseCases(marketGateway, marketGateway),
            auth = AuthUseCases(authGateway, authGateway, store),
            routes = InMemoryRouteStore(),
            externalScope = scope,
        )

        // 1. Send verification code & register
        vm.sendEmailCode("trader@example.com")
        assertEquals("trader@example.com", authGateway.lastCodeEmail)
        assertEquals(60, vm.state.value.codeCooldownSeconds)

        vm.registerWithEmail("trader@example.com", "123456", "StrongPass#123", "缠观测试员")
        assertEquals("缠观测试员", vm.state.value.user?.nickname)
        assertEquals("token-reg", store.saved)

        // 2. Logout clears user and token
        vm.logout()
        assertNull(vm.state.value.user)
        assertNull(store.saved)

        // 3. Login with email & password
        vm.loginWithEmail("trader@example.com", "StrongPass#123")
        assertEquals("trader", vm.state.value.user?.nickname)
        assertEquals("token-login", store.saved)

        // 4. Stale restoreSession must not resurrect user after deleteAccount
        val gate = CompletableDeferred<AppSession>()
        authGateway.sessionGate = gate
        val restoreJob = launch { vm.restoreSession() }
        vm.deleteAccount()
        assertNull(vm.state.value.user)

        gate.complete(AppSession(authenticated = true, user = AppUser("u-stale", "旧会话")))
        restoreJob.join()
        advanceUntilIdle()
        assertNull(vm.state.value.user)
    }

    @Test
    fun `tracking requires login for guests and syncs after login and logout`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val store = InMemorySessionStore()
        val authGateway = FakeAuthGateway()
        val marketGateway = object : MarketReader, QuoteWriter {
            override suspend fun search(query: String): List<Stock> = emptyList()
            override suspend fun snapshot(secid: String, timeframe: Timeframe, refresh: Boolean): MarketSnapshot =
                error("not used")
            override suspend fun quote(secid: String): MarketQuote = error("not used")
            override suspend fun series(secid: String): QuoteSeries = error("not used")
            override suspend fun rankings(): RankingBoard = error("not used")
            override suspend fun confluence(secid: String): SignalConfluence = error("not used")
            override suspend fun record(quote: MarketQuote): QuoteSeries = error("not used")
            override suspend fun assets(
                category: AssetCategory,
                sortField: AssetSortField,
                descending: Boolean,
                limit: Int,
            ): MarketAssetList = MarketAssetList(
                category = category,
                asOfDate = "2026-11-16",
                items = listOf(
                    MarketAssetItem(
                        stock = DefaultStocks.KWEICHOW_MOUTAI,
                        latest = 1453.9,
                        change = 18.64,
                        changePercent = 1.3,
                        open = 1426.3,
                        high = 1454.93,
                        low = 1425.0,
                        previousClose = 1435.26,
                        volume = 1000.0,
                        amount = 1453900.0,
                    ),
                ),
            )
        }
        val trackedSecids = mutableSetOf<String>()
        val trackingGateway = object : TrackingReader, TrackingCommands {
            override suspend fun fetchTrackings(): TrackingOverview = TrackingOverview(
                asOfDate = "2026-11-16",
                items = trackedSecids.map { secid ->
                    TrackedAsset(
                        stock = Stock(secid, secid.substringBefore('.'), "贵州茅台", "沪A"),
                        startDate = "2026-11-16",
                        startPrice = 1453.9,
                        latestPrice = 1453.9,
                        dayReturnPct = 0.0,
                        dayProfit = 0.0,
                        cumulativeReturnPct = 0.0,
                        cumulativeProfit = 0.0,
                        currentValue = 10_000.0,
                    )
                },
            )

            override suspend fun startTracking(secid: String) {
                trackedSecids.add(secid)
            }

            override suspend fun cancelTrackings(secids: List<String>) {
                trackedSecids.removeAll(secids.toSet())
            }
        }
        val vm = AppViewModel(
            servicesFactory = EnvironmentServicesFactory {
                EnvironmentServices(
                    market = MarketUseCases(marketGateway, marketGateway),
                    auth = AuthUseCases(authGateway, authGateway, store),
                    tracking = TrackingUseCases(trackingGateway, trackingGateway, InMemoryTrackingPreferenceStore()),
                )
            },
            environmentStore = InMemoryEnvironmentStore(),
            themeStore = InMemoryThemePreferenceStore(),
            routes = InMemoryRouteStore(),
            externalScope = scope,
        )

        // Guest tapping track opens login dialog instead of calling API
        vm.startTracking(DefaultStocks.KWEICHOW_MOUTAI)
        assertEquals(true, vm.state.value.showAuthDialog)
        vm.closeAuthDialog()

        // After login, tracking works and logout clears local tracking state
        vm.loginWithEmail("trader@example.com", "StrongPass#123")
        vm.activate(true)
        vm.startTracking(DefaultStocks.KWEICHOW_MOUTAI)
        advanceUntilIdle()
        assertEquals(listOf("600519.SH"), vm.state.value.trackings.map { it.stock.secid })

        vm.logout()
        advanceUntilIdle()
        assertEquals(emptyList<TrackedAsset>(), vm.state.value.trackings)
        vm.activate(false)
    }
}
