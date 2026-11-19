package cn.oryjk.changuan

import cn.oryjk.changuan.application.AuthUseCases
import cn.oryjk.changuan.application.EnvironmentServices
import cn.oryjk.changuan.application.EnvironmentServicesFactory
import cn.oryjk.changuan.application.InMemoryEnvironmentStore
import cn.oryjk.changuan.application.InMemoryThemePreferenceStore
import cn.oryjk.changuan.application.MarketUseCases
import cn.oryjk.changuan.application.OvernightQuery
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.SessionStore
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.OvernightCandidateStatus
import cn.oryjk.changuan.domain.OvernightPhase
import cn.oryjk.changuan.domain.OvernightStatus
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.infrastructure.ApiClient
import cn.oryjk.changuan.infrastructure.AuthGateway
import cn.oryjk.changuan.infrastructure.HttpTransport
import cn.oryjk.changuan.infrastructure.MarketGateway
import cn.oryjk.changuan.infrastructure.OvernightGateway
import cn.oryjk.changuan.infrastructure.RawHttpResponse
import cn.oryjk.changuan.presentation.AppViewModel
import cn.oryjk.changuan.presentation.RankingMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OvernightGatewayTest {
    @Test
    fun `reads a public snapshot with independent scan and reminder evidence`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "trade_date": "2026-11-18", "phase": "exit",
                      "observed_at": "2026-11-18T14:37:16+08:00",
                      "history_sessions": 20, "universe_count": 5300, "fresh_count": 5270,
                      "matched_count": 2, "minute_checked_count": 1, "elapsed_ms": 235,
                      "max_quote_age_seconds": 4.25, "status": "partial",
                      "candidates": [{
                        "secid": "600519.SH", "symbol": "600519", "name": "贵州茅台",
                        "price": 1520.5, "change_percent": 3.25, "volume_ratio": 1.12,
                        "turnover_rate": 5.25, "total_market_cap": 3100000000,
                        "limit_up_date": "2026-11-11", "as_of": "2026-11-18T14:37:15+08:00",
                        "average_price": 1518.0, "benchmark_change_percent": 0.8,
                        "minute_at": "2026-11-18T14:37:00+08:00", "minute_change_percent": 3.20,
                        "pullback_at": null, "status": "watching", "reason": "等待回踩"
                      }],
                      "reminders_until": "2026-11-19T11:30:00+08:00",
                      "reminders": [{
                        "secid": "000001.SZ", "symbol": "000001", "name": "平安银行",
                        "signal_date": "2026-11-18", "signal_at": "2026-11-18T14:48:00+08:00",
                        "exception_observed": true, "exception_as_of": "2026-11-19T10:00:00+08:00",
                        "exception_valid_until": "2026-11-19T10:02:00+08:00"
                      }]
                    }
                    """.trimIndent(),
                ),
            )
            val store = object : SessionStore {
                override fun token() = "existing-session"
                override fun save(token: String) = Unit
                override fun clear() = Unit
            }
            val snapshot = OvernightGateway(ApiClient(server.url("/chanlun/").toString(), store))
                .snapshot()

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/chanlun/api/methods/overnight", request.path)
            assertNull(request.getHeader("Authorization"))
            assertEquals(OvernightPhase.EXIT, snapshot.phase)
            assertEquals(OvernightStatus.PARTIAL, snapshot.status)
            assertEquals(Instant.parse("2026-11-18T06:37:16Z"), snapshot.observedAt)
            assertEquals(5270, snapshot.freshCount)
            assertEquals(4.25, snapshot.maxQuoteAgeSeconds ?: 0.0, 1e-6)

            val candidate = snapshot.candidates.single()
            assertEquals("600519.SH", candidate.stock.secid)
            assertEquals(OvernightCandidateStatus.WATCHING, candidate.status)
            assertEquals("等待回踩", candidate.reason)
            assertEquals(3.20, candidate.minuteChangePercent ?: 0.0, 1e-6)
            assertEquals(Instant.parse("2026-11-18T06:37:00Z"), candidate.minuteAt)
            assertNull(candidate.pullbackAt)

            val reminder = snapshot.reminders.single()
            assertEquals("000001.SZ", reminder.stock.secid)
            assertEquals("2026-11-18", reminder.signalDate)
            assertTrue(reminder.exceptionObserved == true)
            assertEquals(Instant.parse("2026-11-19T02:00:00Z"), reminder.exceptionAsOf)
            assertEquals(Instant.parse("2026-11-19T02:02:00Z"), reminder.exceptionValidUntil)
            assertEquals(Instant.parse("2026-11-19T03:30:00Z"), snapshot.remindersUntil)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `polls only on the visible method and uses the selected environment`() = runTest {
        val requests = mutableListOf<BackendEnvironment>()
        val store = object : SessionStore {
            override fun token(): String? = null
            override fun save(token: String) = Unit
            override fun clear() = Unit
        }
        val routes = object : RouteStore {
            override fun restore() = Triple(null, Timeframe.DAILY, RootTab.RANKING)
            override fun save(stock: Stock?, timeframe: Timeframe, tab: RootTab) = Unit
        }
        val viewModel = AppViewModel(
            servicesFactory = EnvironmentServicesFactory { environment ->
                val date = if (environment == BackendEnvironment.Production) "2026-11-19" else "2026-11-18"
                val transport = HttpTransport { url, _, _, _ ->
                    if (url.endsWith("/api/methods/overnight")) {
                        requests += environment
                        RawHttpResponse(
                            200,
                            """
                            {
                              "trade_date": "$date", "phase": "scanning", "observed_at": null,
                              "history_sessions": 20, "universe_count": 0, "fresh_count": 0,
                              "matched_count": 0, "minute_checked_count": 0, "elapsed_ms": null,
                              "max_quote_age_seconds": null, "status": "pending",
                              "candidates": [], "reminders": []
                            }
                            """.trimIndent(),
                        )
                    } else {
                        RawHttpResponse(404, "{}")
                    }
                }
                val api = ApiClient(environment.baseUrl, store, transport)
                val market = MarketGateway(api)
                val auth = AuthGateway(api)
                EnvironmentServices(
                    market = MarketUseCases(market, market),
                    auth = AuthUseCases(auth, auth, store),
                    overnight = OvernightQuery(OvernightGateway(api)),
                )
            },
            environmentStore = InMemoryEnvironmentStore(),
            themeStore = InMemoryThemePreferenceStore(),
            routes = routes,
            externalScope = backgroundScope,
        )

        viewModel.activate(true)
        runCurrent()
        assertTrue(requests.isEmpty())

        viewModel.selectRankingMethod(RankingMethod.OVERNIGHT)
        runCurrent()
        advanceTimeBy(5_001)
        assertEquals(listOf(BackendEnvironment.DEFAULT, BackendEnvironment.DEFAULT), requests)
        assertEquals("2026-11-18", viewModel.state.value.overnight?.tradeDate)

        viewModel.changeTab(RootTab.MARKET)
        advanceTimeBy(35_000)
        assertEquals(2, requests.size)
        viewModel.changeTab(RootTab.RANKING)
        runCurrent()
        assertEquals(3, requests.size)

        viewModel.activate(false)
        advanceTimeBy(35_000)
        assertEquals(3, requests.size)
        viewModel.selectEnvironment(BackendEnvironment.Production)
        assertNull(viewModel.state.value.overnight)
        viewModel.activate(true)
        runCurrent()
        assertEquals(BackendEnvironment.Production, requests.last())
        assertEquals(4, requests.size)
        assertEquals("2026-11-19", viewModel.state.value.overnight?.tradeDate)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class ExitRankingScenario(scope: TestScope) {
        private val deadline = Instant.parse("2026-11-19T03:30:00Z")
        private val start = deadline.minusSeconds(45)
        var readCount = 0
            private set
        val store = object : SessionStore {
            override fun token(): String? = null
            override fun save(token: String) = Unit
            override fun clear() = Unit
        }
        val routes = object : RouteStore {
            override fun restore() = Triple(null, Timeframe.DAILY, RootTab.RANKING)
            override fun save(stock: Stock?, timeframe: Timeframe, tab: RootTab) = Unit
        }
        val viewModel = AppViewModel(
            servicesFactory = EnvironmentServicesFactory { environment ->
                val transport = HttpTransport { url, _, _, _ ->
                    if (url.endsWith("/api/methods/overnight")) {
                        if (++readCount > 1) awaitCancellation()
                        RawHttpResponse(
                            200,
                            """
                            {
                              "trade_date": "2026-11-18", "phase": "exit", "observed_at": null,
                              "history_sessions": 20, "universe_count": 0, "fresh_count": 0,
                              "matched_count": 0, "minute_checked_count": 0, "elapsed_ms": null,
                              "max_quote_age_seconds": null, "status": "pending", "candidates": [],
                              "reminders_until": "$deadline",
                              "reminders": [{
                                "secid": "000001.SZ", "symbol": "000001", "name": "平安银行",
                                "signal_date": "2026-11-18", "signal_at": "2026-11-18T14:48:00+08:00",
                                "exception_observed": true, "exception_as_of": "2026-11-19T11:27:55+08:00",
                                "exception_valid_until": "2026-11-19T11:29:55+08:00"
                              }]
                            }
                            """.trimIndent(),
                        )
                    } else {
                        RawHttpResponse(404, "{}")
                    }
                }
                val api = ApiClient(environment.baseUrl, store, transport)
                val market = MarketGateway(api)
                val auth = AuthGateway(api)
                EnvironmentServices(
                    market = MarketUseCases(market, market),
                    auth = AuthUseCases(auth, auth, store),
                    overnight = OvernightQuery(OvernightGateway(api)),
                )
            },
            environmentStore = InMemoryEnvironmentStore(),
            themeStore = InMemoryThemePreferenceStore(),
            routes = routes,
            externalScope = scope.backgroundScope,
            now = { start.plusMillis(scope.testScheduler.currentTime) },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `clears exception evidence first and the reminder at its deadline while refresh is pending`() = runTest {
        val scenario = ExitRankingScenario(this)
        val viewModel = scenario.viewModel
        viewModel.selectRankingMethod(RankingMethod.OVERNIGHT)
        viewModel.activate(true)
        runCurrent()
        assertEquals(true, viewModel.state.value.overnight?.reminders?.single()?.exceptionObserved)

        advanceTimeBy(40_001)
        runCurrent()
        assertEquals(2, scenario.readCount)
        assertNull(viewModel.state.value.overnight?.reminders?.single()?.exceptionObserved)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, viewModel.state.value.overnight?.reminders?.size)
        assertEquals(OvernightPhase.WAITING, viewModel.state.value.overnight?.phase)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rechecks cached reminder on resume before a new response arrives`() = runTest {
        val scenario = ExitRankingScenario(this)
        val viewModel = scenario.viewModel
        viewModel.selectRankingMethod(RankingMethod.OVERNIGHT)
        viewModel.activate(true)
        runCurrent()
        assertEquals(1, viewModel.state.value.overnight?.reminders?.size)

        viewModel.activate(false)
        advanceTimeBy(60_000)
        viewModel.activate(true)
        assertEquals(0, viewModel.state.value.overnight?.reminders?.size)
    }
}
