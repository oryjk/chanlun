package cn.oryjk.changuan.domain

import java.util.Locale

data class GroupedRankingEntries(
    val confirmed: List<RankingEntry>,
    val observation: List<RankingEntry>,
)

object RankingRules {
    fun filter(entries: List<RankingEntry>, query: String): List<RankingEntry> {
        val keyword = query.trim().lowercase(Locale.ROOT)
        if (keyword.isEmpty()) return entries
        return entries.filter { entry ->
            val title = SignalRules.conclusion(
                bias = entry.bias,
                latest = entry.latest,
                point = entry.latestPoint,
                quality = entry.signalQuality,
            ).title
            val star = if (entry.stock.symbol.startsWith("688") || entry.stock.symbol.startsWith("689")) {
                "科创板"
            } else {
                ""
            }
            val state = entry.latestPoint?.let { if (it.confirmed) "已确认" else "待确认" }.orEmpty()
            val haystack = "${entry.stock.name} ${entry.stock.symbol} ${entry.stock.market} $star $state $title"
                .lowercase(Locale.ROOT)
            haystack.contains(keyword)
        }
    }

    fun splitGroups(entries: List<RankingEntry>): GroupedRankingEntries {
        val confirmed = entries.filter { it.isConfirmedGroup }
        val observation = entries.filterNot { it.isConfirmedGroup }
        return GroupedRankingEntries(confirmed = confirmed, observation = observation)
    }

    fun relativeFromPivotPercent(latest: Double, point: SignalPoint?): Double? {
        val pivot = point?.price ?: return null
        if (pivot <= 0.0 || !pivot.isFinite() || !latest.isFinite()) return null
        return ((latest - pivot) / pivot) * 100.0
    }

    fun isFirstBuildPending(board: RankingBoard?): Boolean {
        if (board == null) return true
        return board.generatedAt == null && board.tradeDate == null &&
            board.bullish.isEmpty() && board.bearish.isEmpty()
    }
}
