package cn.oryjk.changuan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.Candle
import cn.oryjk.changuan.domain.ChanAnalysis
import cn.oryjk.changuan.domain.ChanSignals
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.DefaultStocks
import cn.oryjk.changuan.domain.Divergence
import cn.oryjk.changuan.domain.Fractal
import cn.oryjk.changuan.domain.Instrument
import cn.oryjk.changuan.domain.LevelSignal
import cn.oryjk.changuan.domain.MacdPoint
import cn.oryjk.changuan.domain.MarketGuidance
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketSummary
import cn.oryjk.changuan.domain.Pen
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.RankingEntry
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.Segment
import cn.oryjk.changuan.domain.ShizukuAvailability
import cn.oryjk.changuan.domain.ShizukuChannelState
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.SignalPoint
import cn.oryjk.changuan.domain.SignalQuality
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.domain.Zhongshu
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.AuthMode
import cn.oryjk.changuan.presentation.ChanGuanTheme
import cn.oryjk.changuan.presentation.designsystem.MainBottomBar
import cn.oryjk.changuan.presentation.screens.AccountScreen
import cn.oryjk.changuan.presentation.screens.AuthDialog
import cn.oryjk.changuan.presentation.screens.BackendEnvironmentDialog
import cn.oryjk.changuan.presentation.screens.MarketScreen
import cn.oryjk.changuan.presentation.screens.RankingScreen
import cn.oryjk.changuan.presentation.screens.StockSearchScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClarityUiScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun outputDir(): File =
        File("build/outputs/clarity-screenshots").apply { mkdirs() }

    private fun captureAllWindows(fileName: String) {
        composeRule.waitForIdle()
        val decorView = composeRule.activity.window.decorView
        val width = decorView.width.coerceAtLeast(780)
        val height = decorView.height.coerceAtLeast(1688)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val windows = runCatching { WindowInspector.getGlobalWindowViews() }.getOrNull()
        if (!windows.isNullOrEmpty()) {
            val loc = IntArray(2)
            windows.forEach { win ->
                canvas.save()
                if (win !== decorView) {
                    win.getLocationOnScreen(loc)
                    val baseLoc = IntArray(2)
                    decorView.getLocationOnScreen(baseLoc)
                    canvas.translate(
                        (loc[0] - baseLoc[0]).toFloat(),
                        (loc[1] - baseLoc[1]).toFloat(),
                    )
                }
                win.draw(canvas)
                canvas.restore()
            }
        } else {
            decorView.draw(canvas)
        }

        val target = File(outputDir(), fileName)
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot missing or empty: $target", target.isFile && target.length() > 10_000L)
    }

    private fun sampleSnapshot(): MarketSnapshot {
        val stock = DefaultStocks.KWEICHOW_MOUTAI
        val baseInstant = Instant.parse("2026-09-10T07:00:00Z")
        val basePrice = 1520.0
        val candles = (0 until 45).map { idx ->
            val drift = when {
                idx < 14 -> idx * 3.8
                idx < 28 -> 53.0 - (idx - 14) * 5.2
                else -> -20.0 + (idx - 28) * 6.1
            }
            val open = basePrice + drift
            val close = open + if (idx % 3 == 0) -4.2 else 6.8
            val high = maxOf(open, close) + 8.5
            val low = minOf(open, close) - 7.5
            Candle(
                time = baseInstant.plus(idx.toLong(), ChronoUnit.DAYS),
                open = open,
                close = close,
                high = high,
                low = low,
                volume = 38_000.0 + idx * 900.0,
            )
        }
        val fractals = listOf(
            Fractal(type = "top", candleIndex = 14, time = candles[14].time, price = candles[14].high),
            Fractal(type = "bottom", candleIndex = 28, time = candles[28].time, price = candles[28].low),
            Fractal(type = "top", candleIndex = 44, time = candles[44].time, price = candles[44].high),
        )
        val pens = listOf(
            Pen(
                id = 1,
                startIndex = 0,
                endIndex = 14,
                direction = "up",
                startTime = candles[0].time,
                endTime = candles[14].time,
                startPrice = candles[0].low,
                endPrice = candles[14].high,
                high = candles[14].high,
                low = candles[0].low,
                confirmed = true,
            ),
            Pen(
                id = 2,
                startIndex = 14,
                endIndex = 28,
                direction = "down",
                startTime = candles[14].time,
                endTime = candles[28].time,
                startPrice = candles[14].high,
                endPrice = candles[28].low,
                high = candles[14].high,
                low = candles[28].low,
                confirmed = true,
            ),
            Pen(
                id = 3,
                startIndex = 28,
                endIndex = 44,
                direction = "up",
                startTime = candles[28].time,
                endTime = candles[44].time,
                startPrice = candles[28].low,
                endPrice = candles[44].high,
                high = candles[44].high,
                low = candles[28].low,
                confirmed = true,
            ),
        )
        val segments = listOf(
            Segment(
                id = 1,
                startIndex = 0,
                endIndex = 28,
                direction = "up",
                startTime = candles[0].time,
                endTime = candles[28].time,
                startPrice = candles[0].low,
                endPrice = candles[28].low,
                high = candles[14].high,
                low = candles[28].low,
                confirmed = true,
            ),
            Segment(
                id = 2,
                startIndex = 28,
                endIndex = 44,
                direction = "up",
                startTime = candles[28].time,
                endTime = candles[44].time,
                startPrice = candles[28].low,
                endPrice = candles[44].high,
                high = candles[44].high,
                low = candles[28].low,
                confirmed = true,
            ),
        )
        val zhongshu = listOf(
            Zhongshu(
                id = 1,
                startIndex = 10,
                endIndex = 30,
                startTime = candles[10].time,
                endTime = candles[30].time,
                low = 1496.0,
                high = 1568.0,
                gg = 1584.0,
                dd = 1482.0,
                confirmed = true,
            ),
        )
        val macd = candles.mapIndexed { idx, _ ->
            val dif = (idx - 22) * 0.45
            val dea = dif * 0.8
            MacdPoint(dif = dif, dea = dea, histogram = (dif - dea) * 2.0)
        }
        val latestBuy = SignalPoint(
            kind = 2,
            side = "buy",
            penId = 2,
            candleIndex = 28,
            time = candles[28].time,
            price = candles[28].low,
            confirmed = true,
            reason = "一买后首次回抽不破前低且 MACD 柱收敛",
            invalidationPrice = 1478.0,
            sourceZoneId = 1,
        )
        val lastCandle = candles.last()
        val prevCandle = candles[candles.size - 2]
        return MarketSnapshot(
            instrument = Instrument(
                secid = stock.secid,
                symbol = stock.symbol,
                name = stock.name,
                decimal = 2,
                currency = "CNY",
                exchange = "SH",
            ),
            timeframe = Timeframe.DAILY.rawValue,
            timeframeLabel = Timeframe.DAILY.label,
            candles = candles,
            analysis = ChanAnalysis(
                fractals = fractals,
                pens = pens,
                segments = segments,
                zhongshu = zhongshu,
                mergedCount = candles.size,
                trend = "consolidation",
            ),
            summary = MarketSummary(
                latest = lastCandle.close,
                change = lastCandle.close - prevCandle.close,
                changePercent = ((lastCandle.close - prevCandle.close) / prevCandle.close) * 100.0,
                high = lastCandle.high,
                low = lastCandle.low,
                volume = lastCandle.volume,
            ),
            guidance = MarketGuidance(
                bias = "bullish",
                reasons = listOf("已确认二买结构有效", "站稳中枢上沿"),
                invalidationPrice = 1478.0,
                strength = 84,
            ),
            macd = macd,
            signals = ChanSignals(
                trendType = "consolidation",
                divergences = listOf(
                    Divergence(
                        kind = "trend",
                        direction = "bullish",
                        time = candles[28].time,
                        price = candles[28].low,
                        confirmed = true,
                    ),
                ),
                points = listOf(latestBuy),
            ),
            signalQuality = SignalQuality(
                state = "confirmed",
                invalidationPrice = 1478.0,
                momentumStatus = "supported",
                volumeStatus = "supported",
                historyStatus = "checked_from_pivot",
                macdHistogram = 1.85,
                macdAreaRatio = 1.24,
                pullbackMacdRatio = 0.62,
                breakoutVolumeRatio = 1.28,
                pullbackVolumeRatio = 0.74,
                referenceTouched = false,
                barsSincePivot = 16,
                firstReferenceCrossedAt = null,
                firstReferenceCrossedClose = null,
                historyCheckedThrough = lastCandle.time,
                firstDetectedAt = null,
                confirmedAt = null,
                reasons = listOf("MACD 红柱面积放大（待验证）"),
            ),
        )
    }

    private fun sampleState(dark: Boolean = false): AppUiState {
        val stock = DefaultStocks.KWEICHOW_MOUTAI
        val snapshot = sampleSnapshot()
        val buyPoint = snapshot.signals.points.first()
        val lastClose = snapshot.candles.last().close
        return AppUiState(
            stock = stock,
            timeframe = Timeframe.DAILY,
            tab = RootTab.MARKET,
            environment = BackendEnvironment.DevelopmentProxy,
            themeMode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT,
            snapshot = snapshot,
            confluence = SignalConfluence(
                secid = stock.secid,
                conclusion = "日线与 1 小时级别均已确认买点同向，次级别端点不早于大级别。",
                levels = listOf(
                    LevelSignal(
                        timeframe = "1d",
                        timeframeLabel = "日线",
                        trendType = "consolidation",
                        latestPoint = buyPoint,
                        lowerConfirmation = buyPoint,
                    ),
                    LevelSignal(
                        timeframe = "1h",
                        timeframeLabel = "1 小时",
                        trendType = "up_trend",
                        latestPoint = buyPoint.copy(kind = 1, price = 1504.5),
                        lowerConfirmation = null,
                    ),
                    LevelSignal(
                        timeframe = "30m",
                        timeframeLabel = "30 分钟",
                        trendType = "consolidation",
                        latestPoint = null,
                        lowerConfirmation = null,
                    ),
                ),
            ),
            quote = MarketQuote(
                secid = stock.secid,
                symbol = stock.symbol,
                name = stock.name,
                time = Instant.parse("2026-11-16T07:00:00Z"),
                open = 1574.0,
                high = 1598.4,
                low = 1569.2,
                previousClose = 1572.0,
                latest = lastClose,
                change = lastClose - 1572.0,
                changePercent = ((lastClose - 1572.0) / 1572.0) * 100.0,
                volume = 42_500.0,
                amount = 6_720_000_000.0,
            ),
            board = sampleBoard(buyPoint),
            user = AppUser(
                userId = "usr-8f41c09d",
                nickname = "林观澜",
            ),
            layers = ChartLayers(),
            shizukuState = ShizukuChannelState(enabled = true, availability = ShizukuAvailability.Ready),
        )
    }

    private fun sampleBoard(buyPoint: SignalPoint): RankingBoard {
        val t0 = Instant.parse("2026-11-12T07:00:00Z")
        val bull = listOf(
            RankingEntry(
                rank = 1,
                stock = DefaultStocks.KWEICHOW_MOUTAI,
                bias = "bullish",
                strength = 86,
                latest = 1591.2,
                changePercent = 1.22,
                invalidationPrice = 1495.0,
                latestPoint = buyPoint,
                buyPoint = buyPoint,
                sellPoint = null,
                trend = listOf(
                    QuotePoint(t0, 1535.0),
                    QuotePoint(t0.plus(1, ChronoUnit.DAYS), 1540.0),
                    QuotePoint(t0.plus(2, ChronoUnit.DAYS), 1558.0),
                    QuotePoint(t0.plus(3, ChronoUnit.DAYS), 1574.0),
                    QuotePoint(t0.plus(4, ChronoUnit.DAYS), 1591.2),
                ),
                signalQuality = SignalQuality(
                    state = "confirmed",
                    invalidationPrice = 1495.0,
                    momentumStatus = "supported",
                    volumeStatus = "supported",
                    historyStatus = "checked_from_pivot",
                    macdHistogram = 1.2,
                    macdAreaRatio = 1.1,
                    pullbackMacdRatio = 0.6,
                    breakoutVolumeRatio = 1.2,
                    pullbackVolumeRatio = 0.7,
                    referenceTouched = false,
                    barsSincePivot = 4,
                    firstReferenceCrossedAt = null,
                    firstReferenceCrossedClose = null,
                    historyCheckedThrough = t0.plus(4, ChronoUnit.DAYS),
                    firstDetectedAt = null,
                    confirmedAt = null,
                    reasons = emptyList(),
                ),
                signalGroup = "confirmed",
            ),
            RankingEntry(
                rank = 2,
                stock = Stock("510300.SH", "510300", "沪深300ETF", "ETF"),
                bias = "bullish",
                strength = 78,
                latest = 4.182,
                changePercent = 0.94,
                invalidationPrice = 4.025,
                latestPoint = buyPoint.copy(kind = 3, price = 4.110, time = t0.plus(1, ChronoUnit.DAYS)),
                buyPoint = buyPoint.copy(kind = 3, price = 4.110, time = t0.plus(1, ChronoUnit.DAYS)),
                sellPoint = null,
                trend = listOf(
                    QuotePoint(t0, 4.095),
                    QuotePoint(t0.plus(1, ChronoUnit.DAYS), 4.110),
                    QuotePoint(t0.plus(2, ChronoUnit.DAYS), 4.142),
                    QuotePoint(t0.plus(3, ChronoUnit.DAYS), 4.182),
                ),
                signalQuality = null,
                signalGroup = "confirmed",
            ),
        )
        return RankingBoard(
            generatedAt = Instant.parse("2026-11-16T07:42:10Z"),
            timeframe = "1d",
            bullish = bull,
            bearish = emptyList(),
            tradeDate = "2026-11-16",
            totalCount = 5412,
            analyzedCount = 5412,
            failedCount = 0,
            progressTotal = 5412,
            progressProcessed = 5412,
            state = "idle",
            updateError = null,
            analysisVersion = 2,
        )
    }

    @Composable
    private fun PreviewScaffold(
        darkTheme: Boolean,
        tab: RootTab,
        content: @Composable (Modifier) -> Unit,
    ) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            ChanGuanTheme(darkTheme = darkTheme) {
                Scaffold(
                    bottomBar = { MainBottomBar(currentTab = tab, onSelect = {}) },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        content(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @Test
    fun renderMarketLight() {
        val state = sampleState(dark = false)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectTimeframe = {},
                    onToggleLayer = {},
                    onRefresh = {},
                    onOpenSearch = {},
                    onOpenGlossary = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("market_light.png")
    }

    @Test
    fun renderMarketDark() {
        val state = sampleState(dark = true)
        composeRule.setContent {
            PreviewScaffold(darkTheme = true, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectTimeframe = {},
                    onToggleLayer = {},
                    onRefresh = {},
                    onOpenSearch = {},
                    onOpenGlossary = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("market_dark.png")
    }

    @Test
    fun renderRankingExpandedLight() {
        val state = sampleState(dark = false).copy(tab = RootTab.RANKING)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.RANKING) { m ->
                RankingScreen(
                    state = state,
                    onRefresh = {},
                    onSelectStock = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        composeRule.onNodeWithText("贵州茅台").performClick()
        captureAllWindows("ranking_expanded_light.png")
    }

    @Test
    fun renderSearchLight() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ChanGuanTheme(darkTheme = false) {
                    StockSearchScreen(
                        currentSecid = DefaultStocks.KWEICHOW_MOUTAI.secid,
                        results = emptyList<Stock>(),
                        loading = false,
                        error = "",
                        onQueryChange = {},
                        onSelectStock = {},
                        onBack = {},
                        applyStatusBarPadding = false,
                        requestAutoFocus = false,
                    )
                }
            }
        }
        captureAllWindows("search_light.png")
    }

    @Test
    fun renderMenuLight() {
        val state = sampleState(dark = false).copy(tab = RootTab.ACCOUNT)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.ACCOUNT) { m ->
                AccountScreen(
                    state = state,
                    onOpenAuth = {},
                    onLogout = {},
                    onDeleteAccount = {},
                    onSelectThemeMode = {},
                    onOpenEnvironmentDialog = {},
                    onOpenInstallerSettings = {},
                    onCheckUpdate = {},
                    onOpenReleaseHistory = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("menu_light.png")
    }

    @Test
    fun renderAuthRegisterDialogLight() {
        val state = sampleState(dark = false).copy(tab = RootTab.ACCOUNT)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.ACCOUNT) { m ->
                AccountScreen(
                    state = state,
                    onOpenAuth = {},
                    onLogout = {},
                    onDeleteAccount = {},
                    onSelectThemeMode = {},
                    onOpenEnvironmentDialog = {},
                    onOpenInstallerSettings = {},
                    onCheckUpdate = {},
                    onOpenReleaseHistory = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
                AuthDialog(
                    mode = AuthMode.REGISTER,
                    busy = false,
                    sendingCode = false,
                    cooldownSeconds = 0,
                    error = "",
                    message = "验证码已发送至邮箱，请在 10 分钟内完成验证",
                    onSwitchMode = {},
                    onSendCode = {},
                    onRegister = { _, _, _, _ -> },
                    onLogin = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        captureAllWindows("auth_register_light.png")
    }

    @Test
    fun renderEnvDialogLight() {
        val state = sampleState(dark = false).copy(tab = RootTab.ACCOUNT)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.ACCOUNT) { m ->
                AccountScreen(
                    state = state,
                    onOpenAuth = {},
                    onLogout = {},
                    onDeleteAccount = {},
                    onSelectThemeMode = {},
                    onOpenEnvironmentDialog = {},
                    onOpenInstallerSettings = {},
                    onCheckUpdate = {},
                    onOpenReleaseHistory = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
                BackendEnvironmentDialog(
                    current = BackendEnvironment.DevelopmentProxy,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        captureAllWindows("env_dialog_light.png")
    }
}
