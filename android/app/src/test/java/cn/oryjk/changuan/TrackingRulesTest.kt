package cn.oryjk.changuan

import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.TrackedAsset
import cn.oryjk.changuan.domain.TrackingGrowthStage
import cn.oryjk.changuan.domain.TrackingRules
import cn.oryjk.changuan.domain.TrackingSortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrackingRulesTest {
    @Test
    fun `growth stages map elapsed days across all thresholds`() {
        assertEquals(TrackingGrowthStage.SPROUT, TrackingRules.growth("2026-11-16", "2026-11-16")?.stage)
        assertEquals(TrackingGrowthStage.SPROUT, TrackingRules.growth("2026-11-10", "2026-11-16")?.stage)
        assertEquals(TrackingGrowthStage.NEW_LEAF, TrackingRules.growth("2026-11-09", "2026-11-16")?.stage)
        assertEquals(TrackingGrowthStage.SEEDLING, TrackingRules.growth("2026-10-17", "2026-11-16")?.stage)
        assertEquals(TrackingGrowthStage.SAPLING, TrackingRules.growth("2026-08-18", "2026-11-16")?.stage)
        assertEquals(TrackingGrowthStage.TREE, TrackingRules.growth("2026-05-20", "2026-11-16")?.stage)
        assertEquals(TrackingGrowthStage.YEAR_RING, TrackingRules.growth("2025-11-16", "2026-11-16")?.stage)

        val growth = TrackingRules.growth("2026-11-01", "2026-11-16")
        assertNotNull(growth)
        assertEquals(15, growth?.days)
        assertEquals(TrackingGrowthStage.SEEDLING, growth?.next)
        assertEquals(15, growth?.daysToNext)
    }

    @Test
    fun `order and sort respects pinned secids and descending profit`() {
        val a = TrackedAsset(
            stock = Stock("600519.SH", "600519", "贵州茅台", "沪A"),
            startDate = "2026-11-10",
            startPrice = 1400.0,
            latestPrice = 1450.0,
            dayReturnPct = 1.2,
            dayProfit = 120.0,
            cumulativeReturnPct = 3.57,
            cumulativeProfit = 357.14,
            currentValue = 10357.14,
        )
        val b = TrackedAsset(
            stock = Stock("300750.SZ", "300750", "宁德时代", "深A"),
            startDate = "2026-10-20",
            startPrice = 240.0,
            latestPrice = 264.0,
            dayReturnPct = 2.0,
            dayProfit = 210.0,
            cumulativeReturnPct = 10.0,
            cumulativeProfit = 1000.0,
            currentValue = 11000.0,
        )
        val c = TrackedAsset(
            stock = Stock("510300.SH", "510300", "沪深300ETF", "ETF"),
            startDate = "2026-11-15",
            startPrice = 4.000,
            latestPrice = 3.960,
            dayReturnPct = -1.0,
            dayProfit = -100.0,
            cumulativeReturnPct = -1.0,
            cumulativeProfit = -100.0,
            currentValue = 9900.0,
        )

        val ordered = TrackingRules.orderAndSort(
            items = listOf(a, b, c),
            pinnedSecids = setOf("510300.SH"),
            customOrder = emptyList(),
            sortField = TrackingSortField.CUMULATIVE_PROFIT,
            descending = true,
            asOfDate = "2026-11-16",
        )
        assertEquals(listOf("510300.SH", "300750.SZ", "600519.SH"), ordered.map { it.stock.secid })
        assertEquals("+¥1,000.00", TrackingRules.formatSignedMoney(1000.0))
        assertEquals("-¥100.00", TrackingRules.formatSignedMoney(-100.0))
        assertEquals("¥11,000.00", TrackingRules.formatMoney(11000.0))
    }
}
