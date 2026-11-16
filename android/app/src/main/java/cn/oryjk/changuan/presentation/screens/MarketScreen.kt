package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.charts.MarketChartCanvas
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.ClarityCard
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.FinancialNumber
import cn.oryjk.changuan.presentation.designsystem.FixedHeaderPage
import cn.oryjk.changuan.presentation.designsystem.HeaderIconAction
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.RefreshableContent
import cn.oryjk.changuan.presentation.designsystem.SegmentedControl
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
import cn.oryjk.changuan.presentation.designsystem.SummaryItem
import cn.oryjk.changuan.presentation.designsystem.SummaryStrip
import cn.oryjk.changuan.presentation.designsystem.tabular

@Composable
fun MarketScreen(
    state: AppUiState,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleLayer: ((ChartLayers) -> ChartLayers) -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGlossary: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
) {
    val palette = LocalChanPalette.current
    val snapshot = state.snapshot
    val quote = state.quote
    val session = MarketTime.session(state.stock)
    val displayName = quote?.name?.takeIf { it.isNotBlank() }
        ?: snapshot?.instrument?.name?.takeIf { it.isNotBlank() }
        ?: state.stock.name
    val symbol = state.stock.symbol
    val marketTag = when {
        symbol.startsWith("688") || symbol.startsWith("689") -> "科创"
        state.stock.market == "ETF" -> "ETF"
        state.stock.secid.endsWith(".BJ") -> "北交"
        else -> null
    }
    val decimals = state.stock.decimals
    val latestPrice = quote?.latest ?: snapshot?.summary?.latest ?: snapshot?.candles?.lastOrNull()?.close
    val prevClose = quote?.previousClose ?: snapshot?.candles?.dropLast(1)?.lastOrNull()?.close
    val change = quote?.change
        ?: snapshot?.summary?.change
        ?: if (latestPrice != null && prevClose != null) latestPrice - prevClose else null
    val changePercent = quote?.changePercent
        ?: snapshot?.summary?.changePercent
        ?: if (change != null && prevClose != null && prevClose > 0.0) {
            (change / prevClose) * 100.0
        } else {
            null
        }
    val quoteTimeText = quote?.time?.let { MarketTime.formatDate(it, "HH:mm:ss", session.zone) }
    val headerSubtitle = buildString {
        append("$symbol ${state.stock.market}")
        if (!quoteTimeText.isNullOrBlank()) {
            append(" $quoteTimeText")
        }
    }
    val marketOpen = MarketTime.isOpen(stock = state.stock)
    val statusText = if (marketOpen) "交易中" else "休市/收盘"

    var selectedIndex by remember(snapshot?.instrument?.secid, snapshot?.timeframe, snapshot?.candles?.size) {
        mutableStateOf<Int?>(null)
    }

    FixedHeaderPage(
        modifier = modifier,
        applyStatusBarPadding = applyStatusBarPadding,
        header = {
            PageHeader(
                title = displayName,
                subtitle = headerSubtitle,
                titleSuffix = marketTag?.let { tag ->
                    { StarkBadgeTag(text = tag) }
                },
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeaderIconAction(
                            icon = Icons.Outlined.Search,
                            contentDescription = "搜索证券",
                            onClick = onOpenSearch,
                        )
                        HeaderIconAction(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "缠论术语说明",
                            onClick = onOpenGlossary,
                        )
                    }
                },
            )
        },
    ) {
        RefreshableContent(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            actionLabel = "刷新行情与结构",
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wideLayout = maxWidth >= 840.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MarketQuoteHero(
                        latestPrice = latestPrice,
                        change = change,
                        changePercent = changePercent,
                        decimals = decimals,
                        statusText = statusText,
                        open = quote?.open ?: snapshot?.candles?.lastOrNull()?.open,
                        high = quote?.high ?: snapshot?.summary?.high ?: snapshot?.candles?.lastOrNull()?.high,
                        low = quote?.low ?: snapshot?.summary?.low ?: snapshot?.candles?.lastOrNull()?.low,
                        previousClose = prevClose,
                        volume = quote?.volume ?: snapshot?.summary?.volume ?: snapshot?.candles?.lastOrNull()?.volume,
                        amount = quote?.amount,
                    )

                    val timeframes = Timeframe.entries
                    val selectedTfIndex = timeframes.indexOf(state.timeframe).coerceAtLeast(0)
                    SegmentedControl(
                        options = timeframes.map { it.label },
                        selectedIndex = selectedTfIndex,
                        onSelect = { idx -> onSelectTimeframe(timeframes[idx]) },
                        compact = true,
                    )

                    when {
                        state.loading && snapshot == null && quote == null -> {
                            ClarityCard(contentPadding = PaddingValues(24.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = palette.primaryBrand,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "正在加载行情与缠论结构…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.inkSecondary,
                                    )
                                }
                            }
                        }

                        state.error.isNotEmpty() && snapshot == null && quote == null -> {
                            ClarityCard(
                                contentPadding = PaddingValues(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.warmAmberText,
                                )
                                ActionPill(
                                    text = "重新加载",
                                    onClick = onRefresh,
                                )
                            }
                        }

                        state.timeframe == Timeframe.REALTIME -> {
                            QuotePanel(
                                stock = state.stock,
                                quote = quote,
                                points = state.series,
                            )
                        }

                        snapshot != null -> {
                            KlineChartCard(
                                snapshot = snapshot,
                                timeframe = state.timeframe,
                                layers = state.layers,
                                decimals = decimals,
                                selectedIndex = selectedIndex,
                                onSelectIndex = { selectedIndex = it },
                                onToggleLayer = onToggleLayer,
                            )

                            if (wideLayout) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    SignalPanel(
                                        snapshot = snapshot,
                                        decimals = decimals,
                                        modifier = Modifier.weight(1.25f),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        ConfluenceSection(
                                            confluence = state.confluence,
                                            loading = state.confluenceLoading,
                                            error = state.confluenceError,
                                            decimals = decimals,
                                            onJumpTimeframe = onSelectTimeframe,
                                        )
                                        RangeSection(
                                            snapshot = snapshot,
                                            decimals = decimals,
                                        )
                                    }
                                }
                            } else {
                                SignalPanel(
                                    snapshot = snapshot,
                                    decimals = decimals,
                                )
                                ConfluenceSection(
                                    confluence = state.confluence,
                                    loading = state.confluenceLoading,
                                    error = state.confluenceError,
                                    decimals = decimals,
                                    onJumpTimeframe = onSelectTimeframe,
                                )
                                RangeSection(
                                    snapshot = snapshot,
                                    decimals = decimals,
                                )
                            }
                        }
                    }

                    Text(
                        text = "买卖点为规则化结构识别，仅供研究，不构成买卖建议",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketQuoteHero(
    latestPrice: Double?,
    change: Double?,
    changePercent: Double?,
    decimals: Int,
    statusText: String,
    open: Double?,
    high: Double?,
    low: Double?,
    previousClose: Double?,
    volume: Double?,
    amount: Double?,
) {
    val palette = LocalChanPalette.current
    val toneColor = palette.directionColor(change ?: 0.0)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FinancialNumber(
                    value = latestPrice?.let { MarketFormat.price(it, decimals) } ?: "—",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = toneColor,
                    alignEnd = false,
                )
                if (change != null && changePercent != null) {
                    Text(
                        text = "${MarketFormat.signedPrice(change, decimals)}  ${MarketFormat.percent(changePercent)}",
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = toneColor,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            StarkBadgeTag(text = statusText)
        }

        SummaryStrip(
            centeredColumns = 3,
            items = listOf(
                SummaryItem(
                    label = "今开",
                    value = open?.let { MarketFormat.price(it, decimals) } ?: "—",
                ),
                SummaryItem(
                    label = "最高",
                    value = high?.let { MarketFormat.price(it, decimals) } ?: "—",
                ),
                SummaryItem(
                    label = "最低",
                    value = low?.let { MarketFormat.price(it, decimals) } ?: "—",
                ),
                SummaryItem(
                    label = "昨收",
                    value = previousClose?.let { MarketFormat.price(it, decimals) } ?: "—",
                ),
                SummaryItem(
                    label = "成交量",
                    value = volume?.let { MarketFormat.volume(it) } ?: "—",
                ),
                SummaryItem(
                    label = "成交额",
                    value = amount?.takeIf { it > 0 }?.let { MarketFormat.volume(it) } ?: "—",
                ),
            ),
        )
    }
}

@Composable
private fun KlineChartCard(
    snapshot: MarketSnapshot,
    timeframe: Timeframe,
    layers: ChartLayers,
    decimals: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    onToggleLayer: ((ChartLayers) -> ChartLayers) -> Unit,
) {
    val palette = LocalChanPalette.current
    val selectedCandle = selectedIndex?.let { snapshot.candles.getOrNull(it) }
        ?: snapshot.candles.lastOrNull()
    val datePattern = if (timeframe == Timeframe.DAILY) "yyyy-MM-dd" else "MM-dd HH:mm"
    val zone = MarketTime.zone(snapshot.instrument.currency)

    ClarityCard(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selectedCandle != null) {
            val formattedTime = MarketTime.formatDate(selectedCandle.time, datePattern, zone)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selectedIndex != null) "选中 $formattedTime" else "最新 $formattedTime",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "开 ${MarketFormat.price(selectedCandle.open, decimals)}  " +
                        "高 ${MarketFormat.price(selectedCandle.high, decimals)}  " +
                        "低 ${MarketFormat.price(selectedCandle.low, decimals)}  " +
                        "收 ${MarketFormat.price(selectedCandle.close, decimals)}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = palette.inkSecondary,
                    maxLines = 1,
                )
            }
        }

        MarketChartCanvas(
            snapshot = snapshot,
            layers = layers,
            decimals = decimals,
            selectedIndex = selectedIndex,
            onSelectIndex = onSelectIndex,
            modifier = Modifier
                .fillMaxWidth()
                .height(332.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LayerToggleChip(
                label = "分型",
                dotColor = palette.fractalSlate,
                selected = layers.fractals,
                onClick = { onToggleLayer { it.copy(fractals = !it.fractals) } },
            )
            LayerToggleChip(
                label = "笔",
                dotColor = palette.penGold,
                selected = layers.pens,
                onClick = { onToggleLayer { it.copy(pens = !it.pens) } },
            )
            LayerToggleChip(
                label = "线段",
                dotColor = palette.segmentBlue,
                selected = layers.segments,
                onClick = { onToggleLayer { it.copy(segments = !it.segments) } },
            )
            LayerToggleChip(
                label = "中枢",
                dotColor = palette.zhongshuViolet,
                selected = layers.zhongshu,
                onClick = { onToggleLayer { it.copy(zhongshu = !it.zhongshu) } },
            )
            LayerToggleChip(
                label = "买卖点",
                dotColor = palette.positiveRed,
                selected = layers.points,
                onClick = { onToggleLayer { it.copy(points = !it.points) } },
            )
        }
    }
}

@Composable
private fun LayerToggleChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalChanPalette.current
    Surface(
        color = if (selected) palette.subtleSurface else palette.cardSurface,
        shape = ClarityRadius.pill,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) palette.inkPrimary else palette.cardBorder,
        ),
        modifier = Modifier
            .clip(ClarityRadius.pill)
            .clickable(role = Role.Checkbox, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (selected) dotColor else palette.inkMuted),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) palette.inkPrimary else palette.inkMuted,
            )
        }
    }
}
