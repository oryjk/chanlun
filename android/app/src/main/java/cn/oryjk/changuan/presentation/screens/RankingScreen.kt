package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.RankingRules
import cn.oryjk.changuan.domain.SignalRules
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.charts.RankingSparklineCanvas
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.ClarityCard
import cn.oryjk.changuan.presentation.designsystem.FilterPill
import cn.oryjk.changuan.presentation.designsystem.FinancialMetricGroup
import cn.oryjk.changuan.presentation.designsystem.FixedHeaderPage
import cn.oryjk.changuan.presentation.designsystem.HeaderIconAction
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.LedgerRow
import cn.oryjk.changuan.presentation.designsystem.LedgerSparkline
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.RefreshableContent
import cn.oryjk.changuan.presentation.designsystem.SegmentedControl
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
import cn.oryjk.changuan.presentation.designsystem.SummaryItem
import cn.oryjk.changuan.presentation.designsystem.tabular

@Composable
fun RankingScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onSelectStock: (Stock) -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
) {
    val palette = LocalChanPalette.current
    val board = state.board
    var directionIdx by rememberSaveable { mutableIntStateOf(0) } // 0: buy, 1: sell
    var groupIdx by rememberSaveable { mutableIntStateOf(0) } // 0: confirmed, 1: watch
    var marketFilter by rememberSaveable { mutableStateOf("全部") }
    var expandedSecid by rememberSaveable { mutableStateOf<String?>(null) }

    val direction = if (directionIdx == 0) "buy" else "sell"
    val rawEntries = board?.let {
        if (direction == "buy") it.bullish else it.bearish
    }.orEmpty()

    val grouped = RankingRules.splitGroups(rawEntries)
    val confirmedList = grouped.confirmed
    val watchList = grouped.observation
    val selectedGroupEntries = if (groupIdx == 0) confirmedList else watchList
    val filteredEntries = when (marketFilter) {
        "A股" -> selectedGroupEntries.filter { it.stock.market != "ETF" }
        "ETF" -> selectedGroupEntries.filter { it.stock.market == "ETF" }
        "科创" -> RankingRules.filter(selectedGroupEntries, "科创")
        else -> selectedGroupEntries
    }

    val subtitle = buildString {
        if (board != null && !board.tradeDate.isNullOrBlank()) {
            append("收盘日 ${board.tradeDate}")
            board.generatedAt?.let { genAt ->
                append("  更新 ${MarketTime.formatDate(genAt, "MM-dd HH:mm")} (北京时间)")
            }
        } else {
            append("沪深 A 股与境内 ETF 日线缠论收盘榜单")
        }
    }

    FixedHeaderPage(
        modifier = modifier,
        applyStatusBarPadding = applyStatusBarPadding,
        header = {
            PageHeader(
                title = "全市场榜单",
                subtitle = subtitle,
                action = {
                    HeaderIconAction(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "刷新榜单",
                        loading = state.boardRefreshing,
                        onClick = onRefresh,
                    )
                },
            )
        },
    ) {
        RefreshableContent(
            isRefreshing = state.boardRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            actionLabel = "刷新全市场收盘榜单",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.backgroundCanvas),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SegmentedControl(
                        options = listOf("偏多结构", "偏空结构"),
                        selectedIndex = directionIdx,
                        onSelect = {
                            directionIdx = it
                            expandedSecid = null
                        },
                        compact = true,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterPill(
                            label = "已确认同向 (${confirmedList.size})",
                            selected = groupIdx == 0,
                            onClick = {
                                groupIdx = 0
                                expandedSecid = null
                            },
                        )
                        FilterPill(
                            label = "候选与观察 (${watchList.size})",
                            selected = groupIdx == 1,
                            onClick = {
                                groupIdx = 1
                                expandedSecid = null
                            },
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("全部", "A股", "ETF", "科创").forEach { marketOption ->
                            FilterPill(
                                label = marketOption,
                                selected = marketFilter == marketOption,
                                onClick = {
                                    marketFilter = marketOption
                                    expandedSecid = null
                                },
                            )
                        }
                    }

                    if (board != null) {
                        BoardStatusCaption(board = board)
                    }
                }

                LedgerHeaderRule()

                when {
                    state.boardLoading && board == null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = palette.primaryBrand,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "正在读取收盘榜单快照…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkSecondary,
                            )
                        }
                    }

                    state.boardError.isNotEmpty() && board == null -> {
                        ClarityCard(
                            modifier = Modifier.padding(16.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = state.boardError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.warmAmberText,
                            )
                            ActionPill(text = "重新加载", onClick = onRefresh)
                        }
                    }

                    board != null && RankingRules.isFirstBuildPending(board) -> {
                        ClarityCard(
                            modifier = Modifier.padding(16.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "首次全市场收盘榜单正在构建中",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.inkPrimary,
                            )
                            Text(
                                text = board.updateError?.takeIf { it.isNotBlank() }
                                    ?: "后台正在扫描沪深 A 股与 ETF 日线结构，完成后自动展示。",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkSecondary,
                            )
                        }
                    }

                    filteredEntries.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "当前分组暂无符合条件的标的",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.inkPrimary,
                            )
                            Text(
                                text = "可切换「候选与观察」或更换市场筛选查看其余结构。",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredEntries, key = { it.stock.secid }) { entry ->
                                val isExpanded = expandedSecid == entry.stock.secid
                                val point = entry.latestPoint
                                val relPct = RankingRules.relativeFromPivotPercent(entry.latest, point)
                                val decimals = entry.stock.decimals
                                val headlineTone = palette.directionColor(relPct ?: entry.changePercent)
                                val signalLabel = point?.label ?: SignalRules.biasLabel(entry.bias)
                                val isConfirmed = point?.confirmed == true
                                val signalDateText = point?.let {
                                    MarketTime.formatDate(it.time, "yyyy-MM-dd")
                                } ?: (board?.tradeDate ?: "—")
                                val invalidationRef = SignalRules.invalidationReferencePrice(
                                    point = point,
                                    quality = entry.signalQuality,
                                ) ?: entry.invalidationPrice

                                LedgerRow(
                                    title = entry.stock.name,
                                    headlineValue = MarketFormat.price(entry.latest, decimals),
                                    headlineLabel = relPct?.let {
                                        "较端点 ${MarketFormat.percent(it)}"
                                    } ?: MarketFormat.percent(entry.changePercent),
                                    headlineColor = headlineTone,
                                    expanded = isExpanded,
                                    onToggleExpand = {
                                        expandedSecid = if (isExpanded) null else entry.stock.secid
                                    },
                                    titleSuffix = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            StarkBadgeTag(text = entry.stock.market)
                                            StarkBadgeTag(
                                                text = signalLabel,
                                                inverted = isConfirmed,
                                            )
                                        }
                                    },
                                    metaContent = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = entry.stock.symbol,
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                color = palette.inkMuted,
                                            )
                                            Text(
                                                text = "端点 $signalDateText",
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                color = palette.inkSecondary,
                                            )
                                            Text(
                                                text = if (isConfirmed) "已确认" else "候选",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isConfirmed) palette.primaryBrand else palette.inkMuted,
                                            )
                                        }
                                    },
                                    sparkline = {
                                        LedgerSparkline(
                                            points = entry.trend.map { it.price },
                                            changePercent = relPct ?: entry.changePercent,
                                        )
                                    },
                                    expandedContent = {
                                        if (entry.trend.isNotEmpty()) {
                                            RankingSparklineCanvas(
                                                entry = entry,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(92.dp),
                                            )
                                        }
                                        FinancialMetricGroup(
                                            items = buildList {
                                                add(
                                                    SummaryItem(
                                                        label = "结构端点价",
                                                        value = point?.let {
                                                            MarketFormat.price(it.price, decimals)
                                                        } ?: "—",
                                                    ),
                                                )
                                                add(
                                                    SummaryItem(
                                                        label = "失效参考",
                                                        value = invalidationRef?.let {
                                                            MarketFormat.price(it, decimals)
                                                        } ?: "—",
                                                    ),
                                                )
                                                add(
                                                    SummaryItem(
                                                        label = "端点至今",
                                                        value = entry.signalQuality?.barsSincePivot?.let {
                                                            "$it 根K线"
                                                        } ?: "待更新",
                                                    ),
                                                )
                                                add(
                                                    SummaryItem(
                                                        label = "日涨跌幅",
                                                        value = MarketFormat.percent(entry.changePercent),
                                                        signedValue = entry.changePercent,
                                                    ),
                                                )
                                            },
                                            alignStart = true,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "首次识别与确认时间未回溯，仅供结构观察",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.inkMuted,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            ActionPill(
                                                text = "查看 K 线",
                                                onClick = { onSelectStock(entry.stock) },
                                            )
                                        }
                                    },
                                )
                            }

                            item {
                                Text(
                                    text = "榜单日期为结构端点日期，不代表当日首次发现；仅供研究，不构成买卖建议",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardStatusCaption(board: RankingBoard) {
    val palette = LocalChanPalette.current
    val total = board.totalCount ?: 0
    val analyzed = board.analyzedCount ?: 0
    val coverage = if (total > 0) "覆盖 $analyzed/$total 只" else "已分析 $analyzed 只"
    val outdated = board.analysisVersion != null && board.analysisVersion < 2
    val progressPct = if (board.state == "running" && (board.progressTotal ?: 0) > 0) {
        val processed = board.progressProcessed ?: 0
        val pTotal = board.progressTotal ?: 1
        ((processed * 100) / pTotal).coerceIn(0, 100)
    } else {
        null
    }

    Text(
        text = buildString {
            append(coverage)
            if (progressPct != null) {
                append("  后台更新中 $progressPct%")
            }
            if (outdated) {
                append("  旧版本快照待下轮补算")
            }
        },
        style = MaterialTheme.typography.labelSmall.tabular(),
        color = if (outdated) palette.warmAmberText else palette.inkMuted,
    )
}
