package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.RankingEntry
import cn.oryjk.changuan.domain.RankingRules
import cn.oryjk.changuan.domain.SignalPoint
import cn.oryjk.changuan.domain.SignalRules
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.ChanTheme
import cn.oryjk.changuan.presentation.charts.RankingSparklineCanvas

@Composable
fun RankingScreen(
    state: AppUiState,
    onSelectStock: (Stock) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val board = state.board

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(ChanTheme.Sheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "全市场收盘结构榜单",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            if (board != null) {
                Text(
                    text = "收盘日期 ${board.tradeDate ?: "待更新"}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChanTheme.TextPrimary,
                )
                board.generatedAt?.let { generated ->
                    Text(
                        text = "更新时间 ${MarketTime.formatDate(generated, "yyyy-MM-dd HH:mm:ss")} 北京时间",
                        fontSize = 12.sp,
                        color = ChanTheme.TextSecondary,
                    )
                    val failedSuffix = if ((board.failedCount ?: 0) > 0) {
                        "，${board.failedCount ?: 0} 只暂无有效行情"
                    } else {
                        ""
                    }
                    Text(
                        text = "已分析 ${board.analyzedCount ?: 0} / ${board.totalCount ?: 0} 只$failedSuffix",
                        fontSize = 13.sp,
                        color = ChanTheme.TextPrimary,
                    )
                }
                if (board.state == "running" || board.state == "preparing") {
                    Text(
                        text = "后台正在更新，已处理 ${board.progressProcessed ?: 0} / ${board.progressTotal ?: 0} 只",
                        fontSize = 13.sp,
                        color = ChanTheme.TextSecondary,
                    )
                }
                if (!board.updateError.isNullOrBlank()) {
                    Text(
                        text = board.updateError,
                        fontSize = 12.sp,
                        color = ChanTheme.TextSecondary,
                    )
                }
            }
            Text(
                text = "结构端点日期不等于当时已识别信号，首次识别与确认时间未追溯",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索代码、名称、科创、结论", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.boardError.isNotEmpty()) {
            ErrorBanner(message = state.boardError, onRetry = onRetry)
        }
        if (state.boardLoading) {
            StatusBanner(text = "正在读取收盘榜单...")
        }

        if (board != null) {
            if (RankingRules.isFirstBuildPending(board)) {
                StatusBanner(text = "首份榜单正在准备，后台完成后会自动显示")
            } else {
                RankingSideSection(
                    title = "偏多结构",
                    entries = board.bullish,
                    query = query,
                    onSelectStock = onSelectStock,
                )
                RankingSideSection(
                    title = "偏空结构",
                    entries = board.bearish,
                    query = query,
                    onSelectStock = onSelectStock,
                )
            }
        }

        Text(
            text = "结构识别仅供研究，不构成买卖建议",
            fontSize = 12.sp,
            color = ChanTheme.TextSecondary,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun RankingSideSection(
    title: String,
    entries: List<RankingEntry>,
    query: String,
    onSelectStock: (Stock) -> Unit,
) {
    val filtered = RankingRules.filter(entries, query)
    val groups = RankingRules.splitGroups(filtered)

    SectionBreak()
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = ChanTheme.TextPrimary,
        modifier = Modifier.padding(16.dp),
    )
    RankingGroupBlock(
        title = "已确认同向结构",
        entries = groups.confirmed,
        query = query,
        onSelectStock = onSelectStock,
    )
    RankingGroupBlock(
        title = "候选与其他观察结构",
        entries = groups.observation,
        query = query,
        onSelectStock = onSelectStock,
    )
}

@Composable
private fun RankingGroupBlock(
    title: String,
    entries: List<RankingEntry>,
    query: String,
    onSelectStock: (Stock) -> Unit,
) {
    var visibleCount by remember(query, title) { mutableIntStateOf(10) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${entries.size} 只",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }
        if (entries.isEmpty()) {
            Text(
                text = if (query.isBlank()) "当前没有符合条件的结构" else "没有匹配结果，请调整关键词",
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            entries.take(visibleCount).forEach { entry ->
                HorizontalDivider(
                    color = ChanTheme.Line,
                    modifier = Modifier.padding(start = 16.dp),
                )
                RankingEntryRow(
                    entry = entry,
                    onClick = { onSelectStock(entry.stock) },
                )
            }
            if (entries.size > visibleCount) {
                TextButton(
                    onClick = { visibleCount += 10 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Text(
                        text = "展开更多（剩余 ${entries.size - visibleCount} 只）",
                        fontSize = 13.sp,
                        color = ChanTheme.Accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingEntryRow(
    entry: RankingEntry,
    onClick: () -> Unit,
) {
    val decimals = entry.stock.decimals
    fun price(v: Double?): String = MarketFormat.price(v, decimals)
    val dirColor = ChanTheme.directionColor(entry.changePercent)
    val isStar = entry.stock.symbol.startsWith("688") || entry.stock.symbol.startsWith("689")
    val conclusion = SignalRules.conclusion(
        bias = entry.bias,
        latest = entry.latest,
        point = entry.latestPoint,
        quality = entry.signalQuality,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${entry.rank}",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
                modifier = Modifier.width(24.dp),
            )
            Text(
                text = entry.stock.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = price(entry.latest),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = dirColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = MarketFormat.percent(entry.changePercent),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = dirColor,
            )
        }

        Text(
            text = "${entry.stock.symbol}  ${entry.stock.market}  ${if (isStar) "科创  " else ""}强度 ${entry.strength}",
            fontSize = 12.sp,
            color = ChanTheme.TextSecondary,
        )

        Text(
            text = conclusion.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ChanTheme.TextPrimary,
        )

        if (entry.trend.size >= 2) {
            RankingSparklineCanvas(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
            )
        }

        val buyPt = entry.buyPoint ?: entry.latestPoint?.takeIf { it.side == "buy" }
        val sellPt = entry.sellPoint ?: entry.latestPoint?.takeIf { it.side == "sell" }
        buyPt?.let { PointSummaryLine(latest = entry.latest, point = it) }
        sellPt?.let { PointSummaryLine(latest = entry.latest, point = it) }

        val refPrice = entry.signalQuality?.invalidationPrice
        if (refPrice != null && refPrice.isFinite()) {
            val distPct = if (entry.latest > 0) ((refPrice - entry.latest) / entry.latest) * 100.0 else 0.0
            Text(
                text = "信号失效参考 ${price(refPrice)}（距现价 ${MarketFormat.percent(distPct)}）",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        } else {
            Text(
                text = "信号自身失效参考待补齐",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }

        Text(
            text = entry.signalQuality?.barsSincePivot?.let { "距结构端点 $it 根已收盘 K 线" } ?: "端点年龄待计算",
            fontSize = 12.sp,
            color = ChanTheme.TextSecondary,
        )
    }
}

@Composable
private fun PointSummaryLine(
    latest: Double,
    point: SignalPoint,
) {
    val rel = RankingRules.relativeFromPivotPercent(latest, point)
    Text(
        text = "${point.label}结构端点 ${MarketTime.formatDate(point.time)}  ${
            if (point.confirmed) "已确认" else "待确认"
        }  较端点价 ${MarketFormat.percent(rel)}",
        fontSize = 12.sp,
        color = ChanTheme.TextSecondary,
    )
}
