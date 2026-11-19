package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.OvernightCandidate
import cn.oryjk.changuan.domain.OvernightCandidateStatus
import cn.oryjk.changuan.domain.OvernightPhase
import cn.oryjk.changuan.domain.OvernightReminder
import cn.oryjk.changuan.domain.OvernightSnapshot
import cn.oryjk.changuan.domain.OvernightStatus
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.tabular

@Composable
internal fun OvernightRankingContent(
    snapshot: OvernightSnapshot?,
    loading: Boolean,
    error: String,
    onOpenStock: (Stock) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OvernightScanSummary(snapshot, loading, error)
            LedgerHeaderRule()
        }
        if (snapshot != null) {
            if (snapshot.phase == OvernightPhase.EXIT) {
                item {
                    OvernightSectionTitle("次交易日上午策略清仓提醒")
                    if (snapshot.reminders.isEmpty()) {
                        Text(
                            text = "当前暂无提醒",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalChanPalette.current.inkSecondary,
                        )
                        LedgerHeaderRule()
                    }
                }
                items(snapshot.reminders, key = { "reminder-${it.stock.secid}" }) { reminder ->
                    OvernightReminderRow(reminder, onOpenStock)
                    LedgerHeaderRule()
                }
            }
            if (snapshot.candidates.isNotEmpty()) {
                item { OvernightSectionTitle("尾盘候选") }
                items(snapshot.candidates, key = { "candidate-${it.stock.secid}" }) { candidate ->
                    OvernightCandidateRow(candidate, onOpenStock)
                    LedgerHeaderRule()
                }
            } else if (
                snapshot.status == OvernightStatus.READY &&
                snapshot.phase in listOf(OvernightPhase.SCANNING, OvernightPhase.CLOSED)
            ) {
                item {
                    Text(
                        text = "当前扫描没有匹配候选",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = LocalChanPalette.current.inkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun OvernightScanSummary(snapshot: OvernightSnapshot?, loading: Boolean, error: String) {
    val palette = LocalChanPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (snapshot != null) {
            val phaseLabel = when (snapshot.phase) {
                OvernightPhase.WAITING -> "等待尾盘扫描"
                OvernightPhase.PREPARING -> "准备近 20 个交易日数据"
                OvernightPhase.SCANNING -> "尾盘扫描中"
                OvernightPhase.CLOSED -> "尾盘扫描时段已结束"
                OvernightPhase.EXIT -> "上午提醒时段"
            }
            val statusLabel = when (snapshot.status) {
                OvernightStatus.PENDING -> "数据准备中"
                OvernightStatus.READY -> "数据已就绪"
                OvernightStatus.PARTIAL -> "数据覆盖不完整"
                OvernightStatus.UNAVAILABLE -> "数据暂不可用"
            }
            Text(phaseLabel, style = MaterialTheme.typography.titleMedium, color = palette.inkPrimary)
            Text(
                text = "${snapshot.tradeDate?.let { "交易日 $it" } ?: "交易日待确认"}  $statusLabel",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = palette.inkSecondary,
            )
            if (snapshot.observedAt != null) {
                OvernightCaption("数据截至 ${MarketTime.formatDate(snapshot.observedAt, "MM-dd HH:mm:ss")}（北京时间）")
            }
            OvernightCaption(
                "历史 ${snapshot.historySessions}/20 个交易日  主板行情 ${snapshot.freshCount}/${snapshot.universeCount} 只",
            )
            OvernightCaption("初筛 ${snapshot.matchedCount} 只  分时核验 ${snapshot.minuteCheckedCount} 只")
            val elapsed = snapshot.elapsedMillis?.let { "扫描耗时 ${it}ms" }
            val age = snapshot.maxQuoteAgeSeconds?.let { "最旧有效报价滞后 ${MarketFormat.price(it, 1)} 秒" }
            val latency = listOfNotNull(elapsed, age).joinToString("  ")
            if (latency.isNotEmpty()) OvernightCaption(latency)
        } else if (loading) {
            Text("正在读取扫描结果…", style = MaterialTheme.typography.bodyMedium, color = palette.inkSecondary)
        }
        if (error.isNotEmpty()) {
            Text(
                text = if (snapshot == null) error else "刷新失败，保留上次快照。$error",
                style = MaterialTheme.typography.bodySmall,
                color = palette.warmAmberText,
            )
        }
        OvernightCaption("扫描 14:30–15:00  次交易日提醒 09:30–11:30（北京时间）")
        OvernightCaption("仅供策略观察，不构成买卖建议。")
    }
}

@Composable
private fun OvernightCandidateRow(candidate: OvernightCandidate, onOpenStock: (Stock) -> Unit) {
    val palette = LocalChanPalette.current
    val stock = candidate.stock
    val statusLabel = when (candidate.status) {
        OvernightCandidateStatus.WATCHING -> "等待回踩"
        OvernightCandidateStatus.PULLBACK -> "观察到回踩"
        OvernightCandidateStatus.FILTERED -> "分时条件未通过"
        OvernightCandidateStatus.UNAVAILABLE -> "分时数据待核验"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenStock(stock) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stock.name,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = palette.inkPrimary,
            )
            Text(
                text = "${MarketFormat.price(candidate.price, stock.decimals)}  ${MarketFormat.percent(candidate.changePercent)}",
                style = MaterialTheme.typography.titleSmall.tabular(),
                color = palette.directionColor(candidate.changePercent),
            )
        }
        val quoteTime = MarketTime.formatDate(candidate.asOf, "HH:mm:ss")
        OvernightCaption("${stock.symbol}  $statusLabel  筛选报价 $quoteTime")
        OvernightCaption(
            "量比 ${MarketFormat.price(candidate.volumeRatio, 2)}  " +
                "换手 ${MarketFormat.price(candidate.turnoverRate, 2)}%  " +
                "总市值 ${MarketFormat.volume(candidate.totalMarketCap)}元",
        )
        OvernightCaption("近 20 日涨停日期 ${candidate.limitUpDate}")
        val average = candidate.averagePrice?.let { "分时均价 ${MarketFormat.price(it, stock.decimals)}" }
        val pullback = candidate.pullbackAt?.let { "回踩分钟 ${MarketTime.formatDate(it, "HH:mm")}" }
        val evidence = listOfNotNull(average, pullback).joinToString("  ")
        if (evidence.isNotEmpty()) OvernightCaption(evidence)
        if (candidate.minuteAt != null && candidate.minuteChangePercent != null && candidate.benchmarkChangePercent != null) {
            val minute = MarketTime.formatDate(candidate.minuteAt, "HH:mm")
            val stockGain = MarketFormat.percent(candidate.minuteChangePercent)
            val benchmarkGain = MarketFormat.percent(candidate.benchmarkChangePercent)
            OvernightCaption("同分钟 $minute  个股 $stockGain  沪深300 $benchmarkGain")
        }
        candidate.reason?.let { OvernightCaption(it) }
    }
}

@Composable
private fun OvernightReminderRow(reminder: OvernightReminder, onOpenStock: (Stock) -> Unit) {
    val stock = reminder.stock
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenStock(stock) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "${stock.name}  ${stock.symbol}",
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = LocalChanPalette.current.inkPrimary,
        )
        OvernightCaption("回踩记录 ${reminder.signalDate} ${MarketTime.formatDate(reminder.signalAt, "HH:mm:ss")}")
        if (reminder.exceptionObserved == true) {
            val time = reminder.exceptionAsOf?.let { "截至 ${MarketTime.formatDate(it, "HH:mm:ss")} 观察到" }
                ?: "观察到"
            OvernightCaption("${time}缩量一字板，复核清仓提醒。")
        }
    }
}

@Composable
private fun OvernightSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        color = LocalChanPalette.current.inkPrimary,
    )
}

@Composable
private fun OvernightCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.tabular(),
        color = LocalChanPalette.current.inkSecondary,
    )
}
