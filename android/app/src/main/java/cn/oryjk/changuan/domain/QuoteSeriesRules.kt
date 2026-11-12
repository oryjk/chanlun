package cn.oryjk.changuan.domain

import java.time.Instant

object QuoteSeriesRules {
    fun merge(
        current: List<QuotePoint>,
        incoming: List<QuotePoint>,
        stock: Stock,
        reference: Instant,
    ): List<QuotePoint> {
        val zone = MarketTime.session(stock).zone
        val day = MarketTime.formatDate(reference, zone = zone)
        val byTime = LinkedHashMap<Instant, QuotePoint>()
        for (point in current + incoming) {
            if (MarketTime.formatDate(point.time, zone = zone) == day) {
                byTime[point.time] = point
            }
        }
        return byTime.values.sortedBy { it.time }
    }
}
