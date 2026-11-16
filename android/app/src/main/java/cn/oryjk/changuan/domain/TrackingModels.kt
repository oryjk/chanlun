package cn.oryjk.changuan.domain

import java.text.DecimalFormat
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class TrackedAsset(
    val stock: Stock,
    val startDate: String,
    val startPrice: Double,
    val latestPrice: Double,
    val dayReturnPct: Double,
    val dayProfit: Double,
    val cumulativeReturnPct: Double,
    val cumulativeProfit: Double,
    val currentValue: Double,
    val sparkline: List<Double> = emptyList(),
)

data class TrackingOverview(
    val asOfDate: String,
    val items: List<TrackedAsset>,
)

enum class TrackingSortField(val label: String) {
    DAY_PROFIT("当日"),
    CUMULATIVE_PROFIT("累计"),
    DAYS("天数"),
}

enum class TrackingGrowthStage(val label: String, val fromDays: Int) {
    SPROUT("萌芽", 0),
    NEW_LEAF("新叶", 7),
    SEEDLING("成苗", 30),
    SAPLING("小树", 90),
    TREE("成树", 180),
    YEAR_RING("年轮", 365),
}

data class TrackingGrowth(
    val stage: TrackingGrowthStage,
    val days: Int,
    val next: TrackingGrowthStage?,
    val daysToNext: Int?,
    val progressToNext: Float,
)

object TrackingRules {
    const val SIMULATED_PRINCIPAL_PER_ASSET = 10_000.0
    private val moneyFormatter = DecimalFormat("#,##0.00")

    fun companionDays(startDate: String, asOfDate: String): Int? {
        val start = parseDate(startDate) ?: return null
        val asOf = parseDate(asOfDate) ?: return null
        return ChronoUnit.DAYS.between(start, asOf).toInt().coerceAtLeast(0)
    }

    fun growth(startDate: String, asOfDate: String): TrackingGrowth? {
        val days = companionDays(startDate, asOfDate) ?: return null
        val stages = TrackingGrowthStage.entries
        val idx = stages.indexOfLast { days >= it.fromDays }.coerceAtLeast(0)
        val stage = stages[idx]
        val next = stages.getOrNull(idx + 1)
        val daysToNext = next?.let { (it.fromDays - days).coerceAtLeast(0) }
        val progress = if (next == null) {
            1f
        } else {
            val span = (next.fromDays - stage.fromDays).coerceAtLeast(1)
            ((days - stage.fromDays).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        return TrackingGrowth(
            stage = stage,
            days = days,
            next = next,
            daysToNext = daysToNext,
            progressToNext = progress,
        )
    }

    fun summaryNote(startDates: List<String>, asOfDate: String): String? {
        val longestDays = startDates.mapNotNull { companionDays(it, asOfDate) }.maxOrNull() ?: return null
        return if (longestDays == 0) "今天开始记录" else "最久的一只已跟踪 $longestDays 天"
    }

    fun orderAndSort(
        items: List<TrackedAsset>,
        pinnedSecids: Set<String>,
        customOrder: List<String>,
        sortField: TrackingSortField?,
        descending: Boolean,
        asOfDate: String,
    ): List<TrackedAsset> {
        val orderMap = customOrder.withIndex().associate { (index, secid) -> secid to index }
        val pinned = items.filter { it.stock.secid in pinnedSecids }
        val unpinned = items.filterNot { it.stock.secid in pinnedSecids }

        fun compareItems(a: TrackedAsset, b: TrackedAsset): Int {
            if (sortField != null) {
                val va = sortValue(a, sortField, asOfDate)
                val vb = sortValue(b, sortField, asOfDate)
                val cmp = if (descending) vb.compareTo(va) else va.compareTo(vb)
                if (cmp != 0) return cmp
            }
            val oa = orderMap[a.stock.secid] ?: Int.MAX_VALUE
            val ob = orderMap[b.stock.secid] ?: Int.MAX_VALUE
            if (oa != ob) return oa.compareTo(ob)
            return a.stock.secid.compareTo(b.stock.secid)
        }

        val sortedPinned = pinned.sortedWith(::compareItems)
        val sortedUnpinned = unpinned.sortedWith(::compareItems)
        return sortedPinned + sortedUnpinned
    }

    private fun sortValue(item: TrackedAsset, field: TrackingSortField, asOfDate: String): Double = when (field) {
        TrackingSortField.DAY_PROFIT -> item.dayProfit
        TrackingSortField.CUMULATIVE_PROFIT -> item.cumulativeProfit
        TrackingSortField.DAYS -> (companionDays(item.startDate, asOfDate) ?: 0).toDouble()
    }

    fun formatMoney(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        return "¥${moneyFormatter.format(value)}"
    }

    fun formatSignedMoney(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        val prefix = when {
            value > 0.0 -> "+¥"
            value < 0.0 -> "-¥"
            else -> "¥"
        }
        return "$prefix${moneyFormatter.format(abs(value))}"
    }

    private fun parseDate(raw: String?): LocalDate? = try {
        raw?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.take(10)) }
    } catch (_: DateTimeException) {
        null
    }
}
