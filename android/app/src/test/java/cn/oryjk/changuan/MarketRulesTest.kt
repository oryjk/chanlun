package cn.oryjk.changuan

import cn.oryjk.changuan.domain.Candle
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.QuoteSeriesRules
import cn.oryjk.changuan.domain.RankingEntry
import cn.oryjk.changuan.domain.RankingRules
import cn.oryjk.changuan.domain.SignalPoint
import cn.oryjk.changuan.domain.SignalQuality
import cn.oryjk.changuan.domain.SignalRules
import cn.oryjk.changuan.domain.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MarketRulesTest {
    private val stock = Stock(secid = "600519.SH", symbol = "600519", name = "贵州茅台", market = "沪A")
    private val etf = Stock(secid = "510300.SH", symbol = "510300", name = "沪深300ETF", market = "ETF")

    @Test
    fun `price decimals follow stock vs ETF rule`() {
        assertEquals(2, stock.decimals)
        assertEquals(3, etf.decimals)
        assertEquals("1520.50", MarketFormat.price(1520.5, stock.decimals))
        assertEquals("3.945", MarketFormat.price(3.9451, etf.decimals))
    }

    @Test
    fun `candle change uses previous candle close`() {
        val candles = listOf(
            Candle(Instant.parse("2026-04-08T07:00:00Z"), 100.0, 100.0, 101.0, 99.0, 1000.0),
            Candle(Instant.parse("2026-04-09T07:00:00Z"), 98.0, 105.0, 106.0, 97.5, 1200.0),
        )
        val change = SignalRules.candleChange(candles, 1)
        assertNotNull(change)
        assertEquals(5.0, change!!.delta, 1e-6)
        assertEquals(5.0, change.percent, 1e-6)
    }

    @Test
    fun `candidate buy point never claims confirmed opportunity`() {
        val candidateBuy = SignalPoint(
            kind = 1,
            side = "buy",
            penId = 3,
            candleIndex = 40,
            time = Instant.parse("2026-04-09T07:00:00Z"),
            price = 1480.0,
            confirmed = false,
            reason = "趋势底背驰候选",
            invalidationPrice = 1475.0,
            sourceZoneId = 1,
        )
        val conclusion = SignalRules.conclusion(
            bias = "bullish",
            latest = 1500.0,
            point = candidateBuy,
            quality = null,
        )
        assertEquals("候选买点，等待确认", conclusion.title)
        assertFalse(conclusion.title.contains("可关注买入"))
    }

    @Test
    fun `historical reference crossing stays invalidated even after price recovers`() {
        val confirmedBuy = SignalPoint(
            kind = 3,
            side = "buy",
            penId = 6,
            candleIndex = 50,
            time = Instant.parse("2026-04-01T07:00:00Z"),
            price = 102.0,
            confirmed = true,
            reason = "三类买点",
            invalidationPrice = 100.0,
            sourceZoneId = 2,
        )
        val invalidatedQuality = SignalQuality(
            state = "invalidated",
            invalidationPrice = 100.0,
            momentumStatus = "supported",
            volumeStatus = "supported",
            historyStatus = "invalidated",
            macdHistogram = 0.2,
            macdAreaRatio = 1.4,
            pullbackMacdRatio = 0.5,
            breakoutVolumeRatio = 1.3,
            pullbackVolumeRatio = 0.7,
            referenceTouched = true,
            barsSincePivot = 5,
            firstReferenceCrossedAt = Instant.parse("2026-04-03T07:00:00Z"),
            firstReferenceCrossedClose = 99.2,
            historyCheckedThrough = Instant.parse("2026-04-09T07:00:00Z"),
            firstDetectedAt = null,
            confirmedAt = null,
            reasons = emptyList(),
        )
        val conclusion = SignalRules.conclusion(
            bias = "bullish",
            latest = 108.0, // Price recovered above 100
            point = confirmedBuy,
            quality = invalidatedQuality,
        )
        assertEquals("买点结构已失效", conclusion.title)
    }

    @Test
    fun `quote series merge keeps only points on the reference market day and deduplicates`() {
        val day1Point = QuotePoint(Instant.parse("2026-04-08T02:00:00Z"), 1490.0)
        val day2Early = QuotePoint(Instant.parse("2026-04-09T01:35:00Z"), 1500.0)
        val day2Updated = QuotePoint(Instant.parse("2026-04-09T01:35:00Z"), 1501.0)
        val day2Later = QuotePoint(Instant.parse("2026-04-09T02:10:00Z"), 1505.0)

        val merged = QuoteSeriesRules.merge(
            current = listOf(day1Point, day2Early),
            incoming = listOf(day2Later, day2Updated),
            stock = stock,
            reference = day2Later.time,
        )
        assertEquals(2, merged.size)
        assertEquals(1501.0, merged[0].price, 1e-6)
        assertEquals(1505.0, merged[1].price, 1e-6)
    }

    @Test
    fun `ranking rules split confirmed and observation groups and support STAR market keyword`() {
        val starStock = Stock(secid = "688981.SH", symbol = "688981", name = "中芯国际", market = "沪A")
        val confirmedEntry = RankingEntry(
            rank = 1,
            stock = stock,
            bias = "bullish",
            strength = 85,
            latest = 1520.0,
            changePercent = 1.5,
            invalidationPrice = 1480.0,
            latestPoint = null,
            buyPoint = null,
            sellPoint = null,
            trend = emptyList(),
            signalQuality = null,
            signalGroup = "confirmed",
        )
        val observationEntry = confirmedEntry.copy(
            rank = 2,
            stock = starStock,
            signalGroup = "observation",
        )
        val entries = listOf(confirmedEntry, observationEntry)
        val groups = RankingRules.splitGroups(entries)
        assertEquals(1, groups.confirmed.size)
        assertEquals(1, groups.observation.size)

        val filteredStar = RankingRules.filter(entries, "科创")
        assertEquals(1, filteredStar.size)
        assertEquals("688981.SH", filteredStar.first().stock.secid)

        assertTrue(MarketTime.tradingMinute(Instant.parse("2026-04-09T05:00:00Z"), stock) == 120.0)
    }
}
