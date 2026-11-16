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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import cn.oryjk.changuan.presentation.designsystem.CenteredFinancialMetricGrid
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.FinancialNumber
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.RefreshableContent
import cn.oryjk.changuan.presentation.designsystem.SegmentedControl
import cn.oryjk.changuan.presentation.designsystem.SummaryItem
import cn.oryjk.changuan.presentation.designsystem.TrackingActionPill
import cn.oryjk.changuan.presentation.designsystem.tabular

@Composable
fun AssetDetailScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleLayer: ((ChartLayers) -> ChartLayers) -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGlossary: () -> Unit,
    onToggleTrack: () -> Unit,
    modifier: Modifier = Modifier,
    dragHeaderModifier: Modifier = Modifier,
    insideSheet: Boolean = true,
) {
    val palette = LocalChanPalette.current
    val snapshot = state.snapshot
    val quote = state.quote
    val session = MarketTime.session(state.stock)
    val displayName = quote?.name?.takeIf { it.isNotBlank() }
        ?: snapshot?.instrument?.name?.takeIf { it.isNotBlank() }
        ?: state.stock.name
    val symbol = state.stock.symbol
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
    val subtitleText = buildString {
        append("$symbol ${state.stock.market}")
        if (!quoteTimeText.isNullOrBlank()) {
            append("  $quoteTimeText")
        }
    }
    val marketOpen = MarketTime.isOpen(stock = state.stock)
    val statusText = if (marketOpen) "交易中" else "休市/收盘"

    val isTracked = state.trackings.any { it.stock.secid == state.stock.secid }
    val isPending = state.stock.secid in state.pendingTrackSecids
    var confirmUntrack by remember(state.stock.secid) { mutableStateOf(false) }

    var selectedIndex by remember(snapshot?.instrument?.secid, snapshot?.timeframe, snapshot?.candles?.size) {
        mutableStateOf<Int?>(null)
    }

    if (confirmUntrack) {
        AlertDialog(
            onDismissRequest = { confirmUntrack = false },
            containerColor = palette.backgroundCanvas,
            title = {
                Text(
                    text = "结束跟踪「$displayName」？",
                    color = palette.inkPrimary,
                )
            },
            text = {
                Text(
                    text = "结束后，该标的模拟跟踪记录将从列表中移除。",
                    color = palette.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUntrack = false
                        onToggleTrack()
                    },
                ) {
                    Text("确认结束", color = palette.positiveRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUntrack = false }) {
                    Text("继续跟踪", color = palette.inkSecondary)
                }
            },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("asset-detail-screen"),
        containerColor = palette.backgroundCanvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.backgroundCanvas)
                    .then(
                        if (insideSheet) {
                            Modifier
                        } else {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        },
                    )
                    .then(dragHeaderModifier),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail-back-button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = palette.inkPrimary,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "搜索证券",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.primaryBrand,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新行情",
                                tint = palette.inkSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                LedgerHeaderRule()
            }
        },
        bottomBar = {
            Surface(
                color = palette.backgroundCanvas,
                modifier = Modifier.testTag("detail-bottom-bar"),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = palette.cardBorder, thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedIconButton(
                            onClick = onOpenGlossary,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.cardBorder),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                containerColor = palette.cardSurface,
                                contentColor = palette.inkPrimary,
                            ),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "缠论术语说明",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        TrackingActionPill(
                            tracked = isTracked,
                            pending = isPending,
                            onClick = {
                                if (isTracked) {
                                    confirmUntrack = true
                                } else {
                                    onToggleTrack()
                                }
                            },
                            large = true,
                            horizontalPadding = 16.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("detail-track-button"),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        RefreshableContent(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            actionLabel = "刷新行情与结构",
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wideLayout = maxWidth >= 840.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    DetailHeroHeader(
                        latestPrice = latestPrice,
                        change = change,
                        changePercent = changePercent,
                        decimals = decimals,
                        statusText = statusText,
                    )

                    when {
                        state.loading && snapshot == null && quote == null -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 32.dp),
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

                        state.error.isNotEmpty() && snapshot == null && quote == null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.warmAmberText,
                                )
                                ActionPill(text = "重新加载", onClick = onRefresh)
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
                            DetailEdgeToEdgeChart(
                                snapshot = snapshot,
                                timeframe = state.timeframe,
                                layers = state.layers,
                                decimals = decimals,
                                selectedIndex = selectedIndex,
                                onSelectIndex = { selectedIndex = it },
                                onToggleLayer = onToggleLayer,
                            )
                        }
                    }

                    val timeframes = Timeframe.entries
                    val selectedTfIndex = timeframes.indexOf(state.timeframe).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        SegmentedControl(
                            options = timeframes.map { it.label },
                            selectedIndex = selectedTfIndex,
                            onSelect = { idx -> onSelectTimeframe(timeframes[idx]) },
                            compact = true,
                        )
                    }

                    LedgerHeaderRule()

                    CenteredFinancialMetricGrid(
                        columns = 3,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        items = listOf(
                            SummaryItem(
                                label = "今开",
                                value = (quote?.open ?: snapshot?.candles?.lastOrNull()?.open)
                                    ?.let { MarketFormat.price(it, decimals) } ?: "—",
                            ),
                            SummaryItem(
                                label = "最高",
                                value = (quote?.high ?: snapshot?.summary?.high ?: snapshot?.candles?.lastOrNull()?.high)
                                    ?.let { MarketFormat.price(it, decimals) } ?: "—",
                            ),
                            SummaryItem(
                                label = "最低",
                                value = (quote?.low ?: snapshot?.summary?.low ?: snapshot?.candles?.lastOrNull()?.low)
                                    ?.let { MarketFormat.price(it, decimals) } ?: "—",
                            ),
                            SummaryItem(
                                label = "昨收",
                                value = prevClose?.let { MarketFormat.price(it, decimals) } ?: "—",
                            ),
                            SummaryItem(
                                label = "成交量",
                                value = (quote?.volume ?: snapshot?.summary?.volume ?: snapshot?.candles?.lastOrNull()?.volume)
                                    ?.let { MarketFormat.volume(it) } ?: "—",
                            ),
                            SummaryItem(
                                label = "成交额",
                                value = quote?.amount?.takeIf { it > 0 }?.let { MarketFormat.volume(it) } ?: "—",
                            ),
                        ),
                    )

                    if (snapshot != null && state.timeframe != Timeframe.REALTIME) {
                        LedgerHeaderRule()
                        if (wideLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                SignalPanel(
                                    snapshot = snapshot,
                                    decimals = decimals,
                                    modifier = Modifier.weight(1.25f),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    ConfluenceSection(
                                        confluence = state.confluence,
                                        loading = state.confluenceLoading,
                                        error = state.confluenceError,
                                        decimals = decimals,
                                        onJumpTimeframe = onSelectTimeframe,
                                    )
                                    LedgerHeaderRule()
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
                            LedgerHeaderRule()
                            ConfluenceSection(
                                confluence = state.confluence,
                                loading = state.confluenceLoading,
                                error = state.confluenceError,
                                decimals = decimals,
                                onJumpTimeframe = onSelectTimeframe,
                            )
                            LedgerHeaderRule()
                            RangeSection(
                                snapshot = snapshot,
                                decimals = decimals,
                            )
                        }
                    }

                    LedgerHeaderRule()
                    Text(
                        text = "买卖点为规则化结构识别，仅供研究，不构成买卖建议",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeroHeader(
    latestPrice: Double?,
    change: Double?,
    changePercent: Double?,
    decimals: Int,
    statusText: String,
) {
    val palette = LocalChanPalette.current
    val toneColor = palette.directionColor(change ?: 0.0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FinancialNumber(
                value = latestPrice?.let { MarketFormat.price(it, decimals) } ?: "—",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
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
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = palette.inkMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun DetailEdgeToEdgeChart(
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selectedCandle != null) {
            val formattedTime = MarketTime.formatDate(selectedCandle.time, datePattern, zone)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                .height(316.dp)
                .padding(horizontal = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailLayerSwitch("分型", palette.fractalSlate, layers.fractals) {
                onToggleLayer { it.copy(fractals = !it.fractals) }
            }
            DetailLayerSwitch("笔", palette.penGold, layers.pens) {
                onToggleLayer { it.copy(pens = !it.pens) }
            }
            DetailLayerSwitch("线段", palette.segmentBlue, layers.segments) {
                onToggleLayer { it.copy(segments = !it.segments) }
            }
            DetailLayerSwitch("中枢", palette.zhongshuViolet, layers.zhongshu) {
                onToggleLayer { it.copy(zhongshu = !it.zhongshu) }
            }
            DetailLayerSwitch("买卖点", palette.positiveRed, layers.points) {
                onToggleLayer { it.copy(points = !it.points) }
            }
        }
    }
}

@Composable
private fun DetailLayerSwitch(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalChanPalette.current
    Row(
        modifier = Modifier
            .clip(ClarityRadius.tile)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 10.dp, height = 3.dp)
                .clip(ClarityRadius.pill)
                .background(if (selected) dotColor else palette.inkMuted),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.inkPrimary else palette.inkMuted,
        )
    }
}
