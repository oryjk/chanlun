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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.AssetCategory
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
import cn.oryjk.changuan.domain.MarketAssetItem
import cn.oryjk.changuan.domain.MarketFormat
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
import cn.oryjk.changuan.domain.TrackedAsset
import cn.oryjk.changuan.domain.Zhongshu
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.AuthMode
import cn.oryjk.changuan.presentation.ChanGuanTheme
import cn.oryjk.changuan.presentation.designsystem.AssetDetailSheetHost
import cn.oryjk.changuan.presentation.designsystem.MainBottomBar
import cn.oryjk.changuan.presentation.screens.AccountScreen
import cn.oryjk.changuan.presentation.screens.AssetDetailScreen
import cn.oryjk.changuan.presentation.screens.AuthDialog
import cn.oryjk.changuan.presentation.screens.BackendEnvironmentDialog
import cn.oryjk.changuan.presentation.screens.MarketScreen
import cn.oryjk.changuan.presentation.screens.RankingScreen
import cn.oryjk.changuan.presentation.screens.StockSearchScreen
import cn.oryjk.changuan.presentation.screens.TrackingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun captureAllWindows(fileName: String, darkTheme: Boolean = false): Bitmap {
        composeRule.waitForIdle()
        val decorView = composeRule.activity.window.decorView
        val width = decorView.width.coerceAtLeast(780)
        val height = decorView.height.coerceAtLeast(1688)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (darkTheme) AndroidColor.BLACK else AndroidColor.WHITE)

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
        return bitmap
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
            Pen(1, 0, 14, "up", candles[0].time, candles[14].time, candles[0].low, candles[14].high, candles[14].high, candles[0].low, true),
            Pen(2, 14, 28, "down", candles[14].time, candles[28].time, candles[14].high, candles[28].low, candles[14].high, candles[28].low, true),
            Pen(3, 28, 44, "up", candles[28].time, candles[44].time, candles[28].low, candles[44].high, candles[44].high, candles[28].low, true),
        )
        val segments = listOf(
            Segment(1, 0, 28, "up", candles[0].time, candles[28].time, candles[0].low, candles[28].low, candles[14].high, candles[28].low, true),
            Segment(2, 28, 44, "up", candles[28].time, candles[44].time, candles[28].low, candles[44].high, candles[44].high, candles[28].low, true),
        )
        val zhongshu = listOf(
            Zhongshu(1, 10, 30, candles[10].time, candles[30].time, 1496.0, 1568.0, 1584.0, 1482.0, true),
        )
        val macd = candles.mapIndexed { idx, _ ->
            val dif = (idx - 22) * 0.45
            val dea = dif * 0.8
            MacdPoint(dif = dif, dea = dea, histogram = (dif - dea) * 2.0)
        }
        val firstBuy = SignalPoint(
            kind = 1,
            side = "buy",
            penId = 1,
            candleIndex = 0,
            time = candles[0].time,
            price = candles[0].low,
            confirmed = true,
            reason = "趋势背驰一买",
            invalidationPrice = candles[0].low,
            sourceZoneId = 1,
        )
        val midSell = SignalPoint(
            kind = 3,
            side = "sell",
            penId = 1,
            candleIndex = 14,
            time = candles[14].time,
            price = candles[14].high,
            confirmed = true,
            reason = "中枢上方三卖",
            invalidationPrice = candles[14].high,
            sourceZoneId = 1,
        )
        val candidateBuy = SignalPoint(
            kind = 3,
            side = "buy",
            penId = 3,
            candleIndex = 36,
            time = candles[36].time,
            price = candles[36].low,
            confirmed = false,
            reason = "回抽接近中枢上沿候选三买",
            invalidationPrice = 1496.0,
            sourceZoneId = 1,
        )
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
            instrument = Instrument(stock.secid, stock.symbol, stock.name, 2, "CNY", "SH"),
            timeframe = Timeframe.DAILY.rawValue,
            timeframeLabel = Timeframe.DAILY.label,
            candles = candles,
            analysis = ChanAnalysis(fractals, pens, segments, zhongshu, candles.size, "consolidation"),
            summary = MarketSummary(
                latest = lastCandle.close,
                change = lastCandle.close - prevCandle.close,
                changePercent = ((lastCandle.close - prevCandle.close) / prevCandle.close) * 100.0,
                high = lastCandle.high,
                low = lastCandle.low,
                volume = lastCandle.volume,
            ),
            guidance = MarketGuidance("bullish", listOf("已确认二买结构有效", "站稳中枢上沿"), 1478.0, 84),
            macd = macd,
            signals = ChanSignals(
                trendType = "consolidation",
                divergences = listOf(Divergence("trend", "bullish", candles[28].time, candles[28].low, true)),
                points = listOf(firstBuy, midSell, candidateBuy, latestBuy),
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

    private fun sampleStockAssets(): List<MarketAssetItem> = listOf(
        MarketAssetItem(
            stock = DefaultStocks.KWEICHOW_MOUTAI,
            latest = 1453.90,
            change = 18.64,
            changePercent = 1.30,
            open = 1426.30,
            high = 1454.93,
            low = 1425.00,
            previousClose = 1435.26,
            volume = 3_996_700.0,
            amount = 5_767_305_714.0,
            sparkline = listOf(1412.0, 1421.0, 1435.26, 1440.5, 1453.90),
            signalLabel = "二类买点",
        ),
        MarketAssetItem(
            stock = Stock("300750.SZ", "300750", "宁德时代", "深A"),
            latest = 264.80,
            change = 6.20,
            changePercent = 2.40,
            open = 259.0,
            high = 266.0,
            low = 257.8,
            previousClose = 258.60,
            volume = 18_500_000.0,
            amount = 4_890_000_000.0,
            sparkline = listOf(248.0, 252.0, 255.0, 258.6, 264.8),
            signalLabel = "一类买点",
        ),
        MarketAssetItem(
            stock = Stock("600036.SH", "600036", "招商银行", "沪A"),
            latest = 38.42,
            change = -0.34,
            changePercent = -0.88,
            open = 38.80,
            high = 38.95,
            low = 38.20,
            previousClose = 38.76,
            volume = 42_100_000.0,
            amount = 1_618_000_000.0,
            sparkline = listOf(39.2, 39.0, 38.9, 38.76, 38.42),
            signalLabel = null,
        ),
    )

    private fun sampleEtfAssets(): List<MarketAssetItem> = listOf(
        MarketAssetItem(
            stock = Stock("510300.SH", "510300", "沪深300ETF", "ETF"),
            latest = 4.182,
            change = 0.039,
            changePercent = 0.94,
            open = 4.150,
            high = 4.195,
            low = 4.132,
            previousClose = 4.143,
            volume = 120_000_000.0,
            amount = 5_018_400_000.0,
            sparkline = listOf(4.09, 4.11, 4.14, 4.143, 4.182),
            signalLabel = "三类买点",
        ),
        MarketAssetItem(
            stock = Stock("159915.SZ", "159915", "创业板ETF", "ETF"),
            latest = 2.334,
            change = -0.028,
            changePercent = -1.19,
            open = 2.360,
            high = 2.371,
            low = 2.325,
            previousClose = 2.362,
            volume = 95_000_000.0,
            amount = 2_217_300_000.0,
            sparkline = listOf(2.41, 2.39, 2.37, 2.362, 2.334),
            signalLabel = null,
        ),
    )

    private fun sampleTrackings(): List<TrackedAsset> = listOf(
        TrackedAsset(
            stock = Stock("300750.SZ", "300750", "宁德时代", "深A"),
            startDate = "2026-10-08",
            startPrice = 240.00,
            latestPrice = 264.80,
            dayReturnPct = 2.40,
            dayProfit = 258.33,
            cumulativeReturnPct = 10.33,
            cumulativeProfit = 1033.33,
            currentValue = 11033.33,
            sparkline = listOf(240.0, 245.5, 251.0, 258.6, 264.8),
        ),
        TrackedAsset(
            stock = DefaultStocks.KWEICHOW_MOUTAI,
            startDate = "2026-11-02",
            startPrice = 1405.00,
            latestPrice = 1453.90,
            dayReturnPct = 1.30,
            dayProfit = 132.67,
            cumulativeReturnPct = 3.48,
            cumulativeProfit = 348.04,
            currentValue = 10348.04,
            sparkline = listOf(1405.0, 1418.0, 1435.26, 1453.90),
        ),
        TrackedAsset(
            stock = Stock("159915.SZ", "159915", "创业板ETF", "ETF"),
            startDate = "2026-11-14",
            startPrice = 2.380,
            latestPrice = 2.334,
            dayReturnPct = -1.19,
            dayProfit = -117.65,
            cumulativeReturnPct = -1.93,
            cumulativeProfit = -193.28,
            currentValue = 9806.72,
            sparkline = listOf(2.380, 2.370, 2.362, 2.334),
        ),
    )

    private fun sampleState(dark: Boolean = false): AppUiState {
        val stock = DefaultStocks.KWEICHOW_MOUTAI
        val snapshot = sampleSnapshot()
        val buyPoint = snapshot.signals.points.last { it.confirmed && it.side == "buy" }
        val lastClose = snapshot.candles.last().close
        return AppUiState(
            stock = stock,
            timeframe = Timeframe.DAILY,
            tab = RootTab.MARKET,
            stockAssets = sampleStockAssets(),
            etfAssets = sampleEtfAssets(),
            assetsAsOfDate = "2026-11-16",
            trackings = sampleTrackings(),
            trackingAsOfDate = "2026-11-16",
            pinnedSecids = setOf("300750.SZ"),
            environment = BackendEnvironment.DevelopmentProxy,
            themeMode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT,
            snapshot = snapshot,
            confluence = SignalConfluence(
                secid = stock.secid,
                conclusion = "日线与 1 小时级别均已确认买点同向，次级别端点不早于大级别。",
                levels = listOf(
                    LevelSignal("1d", "日线", "consolidation", buyPoint, buyPoint),
                    LevelSignal("1h", "1 小时", "up_trend", buyPoint.copy(kind = 1, price = 1504.5), null),
                    LevelSignal("30m", "30 分钟", "consolidation", null, null),
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
            user = AppUser(userId = "usr-8f41c09d", nickname = "林观澜"),
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
                stock = Stock("300750.SZ", "300750", "宁德时代", "深A"),
                bias = "bullish",
                strength = 82,
                latest = 264.80,
                changePercent = 2.40,
                invalidationPrice = 246.50,
                latestPoint = buyPoint.copy(kind = 1, price = 251.20, time = t0.plus(1, ChronoUnit.DAYS)),
                buyPoint = buyPoint.copy(kind = 1, price = 251.20, time = t0.plus(1, ChronoUnit.DAYS)),
                sellPoint = null,
                trend = listOf(
                    QuotePoint(t0, 248.0),
                    QuotePoint(t0.plus(1, ChronoUnit.DAYS), 251.2),
                    QuotePoint(t0.plus(2, ChronoUnit.DAYS), 256.0),
                    QuotePoint(t0.plus(3, ChronoUnit.DAYS), 264.8),
                ),
                signalQuality = null,
                signalGroup = "confirmed",
            ),
            RankingEntry(
                rank = 3,
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
        hasTrackedAssets: Boolean = true,
        content: @Composable (Modifier) -> Unit,
    ) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            ChanGuanTheme(darkTheme = darkTheme) {
                Scaffold(
                    bottomBar = {
                        MainBottomBar(
                            currentTab = tab,
                            hasTrackedAssets = hasTrackedAssets,
                            onSelect = {},
                        )
                    },
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
    fun renderMarketListLight() {
        assertEquals("0.00%", MarketFormat.percent(0.0))
        assertEquals("+1.30%", MarketFormat.percent(1.3))
        assertEquals("-0.88%", MarketFormat.percent(-0.88))

        val state = sampleState(dark = false)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectCategory = {},
                    onToggleSort = {},
                    onRefresh = {},
                    onOpenAsset = {},
                    onToggleTrack = {},
                    onOpenSearch = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        composeRule.onNodeWithText("600519 沪A 二类买点").assertExists()
        composeRule.onNodeWithText("300750 深A 一类买点").assertExists()
        composeRule.onNodeWithText("600036 沪A").assertExists()
        composeRule.onNodeWithText("57.67 亿").assertExists()
        composeRule.onNodeWithText("+1.30%").assertExists()
        composeRule.onNodeWithText("1453.90").assertExists()

        val amountHeader = composeRule.onNodeWithTag("market-sort-amount").fetchSemanticsNode().boundsInRoot
        val amountCell = composeRule.onNodeWithTag("market-cell-amount-600519.SH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Amount header/cell left misaligned: $amountHeader vs $amountCell", abs(amountHeader.left - amountCell.left) <= 2f)
        assertTrue("Amount header/cell right misaligned: $amountHeader vs $amountCell", abs(amountHeader.right - amountCell.right) <= 2f)

        val changeHeader = composeRule.onNodeWithTag("market-sort-change_percent").fetchSemanticsNode().boundsInRoot
        val changeCell = composeRule.onNodeWithTag("market-cell-change-600519.SH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Change header/cell left misaligned: $changeHeader vs $changeCell", abs(changeHeader.left - changeCell.left) <= 2f)
        assertTrue("Change header/cell right misaligned: $changeHeader vs $changeCell", abs(changeHeader.right - changeCell.right) <= 2f)

        val latestHeader = composeRule.onNodeWithTag("market-sort-latest").fetchSemanticsNode().boundsInRoot
        val latestCell = composeRule.onNodeWithTag("market-cell-latest-600519.SH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Latest header/cell left misaligned: $latestHeader vs $latestCell", abs(latestHeader.left - latestCell.left) <= 2f)
        assertTrue("Latest header/cell right misaligned: $latestHeader vs $latestCell", abs(latestHeader.right - latestCell.right) <= 2f)

        captureAllWindows("market_list_light.png", darkTheme = false)
    }

    @Test
    fun renderMarketListDark() {
        val state = sampleState(dark = true)
        composeRule.setContent {
            PreviewScaffold(darkTheme = true, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectCategory = {},
                    onToggleSort = {},
                    onRefresh = {},
                    onOpenAsset = {},
                    onToggleTrack = {},
                    onOpenSearch = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("market_list_dark.png", darkTheme = true)
    }

    @Test
    fun renderMarketEtfListLight() {
        val state = sampleState(dark = false).copy(assetCategory = AssetCategory.ETF)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectCategory = {},
                    onToggleSort = {},
                    onRefresh = {},
                    onOpenAsset = {},
                    onToggleTrack = {},
                    onOpenSearch = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("market_etf_light.png", darkTheme = false)
    }

    @Test
    fun renderDetailSheetLight() {
        val state = sampleState(dark = false).copy(detailSheetOpen = true)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectCategory = {},
                    onToggleSort = {},
                    onRefresh = {},
                    onOpenAsset = {},
                    onToggleTrack = {},
                    onOpenSearch = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ChanGuanTheme(darkTheme = false) {
                    AssetDetailSheetHost(activeKey = state.stock.secid, onDismissRequest = { true }) { _, drag ->
                        AssetDetailScreen(
                            state = state,
                            onBack = {},
                            onSelectTimeframe = {},
                            onToggleLayer = {},
                            onRefresh = {},
                            onOpenSearch = {},
                            onOpenGlossary = {},
                            onToggleTrack = {},
                            dragHeaderModifier = drag.headerModifier(state.stock.secid),
                            insideSheet = true,
                        )
                    }
                }
            }
        }
        captureAllWindows("detail_sheet_light.png", darkTheme = false)
    }

    @Test
    fun renderDetailSheetDark() {
        val state = sampleState(dark = true).copy(detailSheetOpen = true)
        composeRule.setContent {
            PreviewScaffold(darkTheme = true, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectCategory = {},
                    onToggleSort = {},
                    onRefresh = {},
                    onOpenAsset = {},
                    onToggleTrack = {},
                    onOpenSearch = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ChanGuanTheme(darkTheme = true) {
                    AssetDetailSheetHost(activeKey = state.stock.secid, onDismissRequest = { true }) { _, drag ->
                        AssetDetailScreen(
                            state = state,
                            onBack = {},
                            onSelectTimeframe = {},
                            onToggleLayer = {},
                            onRefresh = {},
                            onOpenSearch = {},
                            onOpenGlossary = {},
                            onToggleTrack = {},
                            dragHeaderModifier = drag.headerModifier(state.stock.secid),
                            insideSheet = true,
                        )
                    }
                }
            }
        }
        captureAllWindows("detail_sheet_dark.png", darkTheme = true)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
    fun renderDetailSheet3xHighDensityBadgesComplete() {
        assertEquals(3f, composeRule.activity.resources.displayMetrics.density, 0.01f)
        val state = sampleState(dark = false).copy(detailSheetOpen = true)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.MARKET) { m ->
                MarketScreen(
                    state = state,
                    onSelectCategory = {},
                    onToggleSort = {},
                    onRefresh = {},
                    onOpenAsset = {},
                    onToggleTrack = {},
                    onOpenSearch = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ChanGuanTheme(darkTheme = false) {
                    AssetDetailSheetHost(activeKey = state.stock.secid, onDismissRequest = { true }) { _, drag ->
                        AssetDetailScreen(
                            state = state,
                            onBack = {},
                            onSelectTimeframe = {},
                            onToggleLayer = {},
                            onRefresh = {},
                            onOpenSearch = {},
                            onOpenGlossary = {},
                            onToggleTrack = {},
                            dragHeaderModifier = drag.headerModifier(state.stock.secid),
                            insideSheet = true,
                        )
                    }
                }
            }
        }
        val bitmap = captureAllWindows("detail_sheet_3x_light.png", darkTheme = false)

        val scanTop = (bitmap.height * 0.24).toInt()
        val scanBottom = (bitmap.height * 0.56).toInt()
        val isRedPillPixel: (Int) -> Boolean = { px ->
            val r = AndroidColor.red(px)
            val g = AndroidColor.green(px)
            val b = AndroidColor.blue(px)
            r >= 185 && g in 40..115 && b in 40..115
        }
        val isGreenPillPixel: (Int) -> Boolean = { px ->
            val r = AndroidColor.red(px)
            val g = AndroidColor.green(px)
            val b = AndroidColor.blue(px)
            r <= 65 && g >= 115 && b in 75..140
        }

        val redPills = verifyIntactPills(bitmap, scanTop, scanBottom, isRedPillPixel)
        val greenPills = verifyIntactPills(bitmap, scanTop, scanBottom, isGreenPillPixel)
        assertTrue("Expected >= 2 intact red buy pills (B1, B2) at 3x, found $redPills", redPills >= 2)
        assertTrue("Expected >= 1 intact green sell pill (S3) at 3x, found $greenPills", greenPills >= 1)
    }

    private fun verifyIntactPills(
        bitmap: Bitmap,
        scanTop: Int,
        scanBottom: Int,
        matchesColor: (Int) -> Boolean,
    ): Int {
        val w = bitmap.width
        val hBand = (scanBottom - scanTop + 1).coerceAtLeast(1)
        val visited = BooleanArray(w * hBand)
        val queue = IntArray(w * hBand)
        var verifiedCount = 0

        for (y0 in scanTop..scanBottom) {
            for (x0 in 0 until w) {
                val startKey = (y0 - scanTop) * w + x0
                if (visited[startKey] || !matchesColor(bitmap.getPixel(x0, y0))) continue
                visited[startKey] = true
                var head = 0
                var tail = 0
                queue[tail++] = startKey
                var minX = x0
                var maxX = x0
                var minY = y0
                var maxY = y0
                while (head < tail) {
                    val cur = queue[head++]
                    val cy = scanTop + cur / w
                    val cx = cur % w
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy
                    for (dy in -1..1) {
                        val ny = cy + dy
                        if (ny < scanTop || ny > scanBottom) continue
                        for (dx in -1..1) {
                            val nx = cx + dx
                            if (nx < 0 || nx >= w) continue
                            val nk = (ny - scanTop) * w + nx
                            if (!visited[nk] && matchesColor(bitmap.getPixel(nx, ny))) {
                                visited[nk] = true
                                queue[tail++] = nk
                            }
                        }
                    }
                }
                val boxW = maxX - minX + 1
                val boxH = maxY - minY + 1
                if (boxW < 40 || boxH < 24) continue

                val whiteRows = mutableListOf<Int>()
                for (y in minY..maxY) {
                    var rowLeftMatch = Int.MAX_VALUE
                    var rowRightMatch = Int.MIN_VALUE
                    for (x in minX..maxX) {
                        if (matchesColor(bitmap.getPixel(x, y))) {
                            if (x < rowLeftMatch) rowLeftMatch = x
                            if (x > rowRightMatch) rowRightMatch = x
                        }
                    }
                    if (rowLeftMatch + 2 >= rowRightMatch) continue
                    var rowHasEnclosedWhite = false
                    for (x in (rowLeftMatch + 1) until rowRightMatch) {
                        val px = bitmap.getPixel(x, y)
                        if (AndroidColor.red(px) >= 210 &&
                            AndroidColor.green(px) >= 210 &&
                            AndroidColor.blue(px) >= 210
                        ) {
                            rowHasEnclosedWhite = true
                            break
                        }
                    }
                    if (rowHasEnclosedWhite) whiteRows.add(y)
                }
                if (whiteRows.isEmpty()) continue

                assertTrue("3x pill height too small ($boxH px) for [$minX..$maxX, $minY..$maxY]", boxH >= 40)
                assertTrue("Glyph clipped at top edge (white starts at ${whiteRows.first()} vs pill top $minY)", whiteRows.first() >= minY + 2)
                assertTrue("Glyph clipped at bottom edge (white ends at ${whiteRows.last()} vs pill bottom $maxY)", whiteRows.last() <= maxY - 2)
                val midY = (minY + maxY) / 2
                assertTrue("Glyph missing from upper pill half ($minY..$midY)", whiteRows.any { it < midY })
                assertTrue("Glyph missing from lower pill half ($midY..$maxY)", whiteRows.any { it > midY })
                verifiedCount++
            }
        }
        return verifiedCount
    }

    @Test
    fun renderTrackingLight() {
        val state = sampleState(dark = false).copy(tab = RootTab.TRACKING)
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.TRACKING) { m ->
                TrackingScreen(
                    state = state,
                    onOpenAuth = {},
                    onGoMarket = {},
                    onRefresh = {},
                    onEnsureCatalog = {},
                    onStartTrack = {},
                    onCancelTracks = {},
                    onOpenDetail = {},
                    onOpenSearch = {},
                    onToggleSort = {},
                    onTogglePin = {},
                    onMoveTrack = { _, _ -> },
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        composeRule.onNodeWithText("宁德时代").performClick()
        captureAllWindows("tracking_light.png", darkTheme = false)
    }

    @Test
    fun renderTrackingDark() {
        val state = sampleState(dark = true).copy(tab = RootTab.TRACKING)
        composeRule.setContent {
            PreviewScaffold(darkTheme = true, tab = RootTab.TRACKING) { m ->
                TrackingScreen(
                    state = state,
                    onOpenAuth = {},
                    onGoMarket = {},
                    onRefresh = {},
                    onEnsureCatalog = {},
                    onStartTrack = {},
                    onCancelTracks = {},
                    onOpenDetail = {},
                    onOpenSearch = {},
                    onToggleSort = {},
                    onTogglePin = {},
                    onMoveTrack = { _, _ -> },
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("tracking_dark.png", darkTheme = true)
    }

    @Test
    fun renderRankingExpandedLightAndVerifyFourCharNamesNotTruncated() {
        val state = sampleState(dark = false).copy(tab = RootTab.RANKING)
        val layouts = mutableMapOf<String, TextLayoutResult>()
        composeRule.setContent {
            PreviewScaffold(darkTheme = false, tab = RootTab.RANKING) { m ->
                RankingScreen(
                    state = state,
                    onRefresh = {},
                    onSelectStock = {},
                    onToggleTrack = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                    onTitleTextLayout = { secid, result -> layouts[secid] = result },
                )
            }
        }
        composeRule.onNodeWithText("贵州茅台").performClick()
        composeRule.waitForIdle()

        assertFalse("贵州茅台 truncated!", layouts["600519.SH"]?.hasVisualOverflow ?: true)
        assertFalse("宁德时代 truncated!", layouts["300750.SZ"]?.hasVisualOverflow ?: true)
        composeRule.onAllNodesWithText("已确认").assertCountEquals(0)
        composeRule.onAllNodesWithText("候选").assertCountEquals(0)

        captureAllWindows("ranking_expanded_light.png", darkTheme = false)
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
        captureAllWindows("search_light.png", darkTheme = false)
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
                    onOpenGlossary = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("menu_light.png", darkTheme = false)
    }

    @Test
    fun renderMenuDark() {
        val state = sampleState(dark = true).copy(tab = RootTab.ACCOUNT)
        composeRule.setContent {
            PreviewScaffold(darkTheme = true, tab = RootTab.ACCOUNT) { m ->
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
                    onOpenGlossary = {},
                    modifier = m,
                    applyStatusBarPadding = false,
                )
            }
        }
        captureAllWindows("menu_dark.png", darkTheme = true)
    }
}
