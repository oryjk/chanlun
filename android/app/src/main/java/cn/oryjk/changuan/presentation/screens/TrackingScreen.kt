package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.DefaultStocks
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.TrackedAsset
import cn.oryjk.changuan.domain.TrackingRules
import cn.oryjk.changuan.domain.TrackingSortField
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.CenteredFinancialMetricGrid
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.CompanionTrackingPill
import cn.oryjk.changuan.presentation.designsystem.FilterPill
import cn.oryjk.changuan.presentation.designsystem.FinancialMetricGroup
import cn.oryjk.changuan.presentation.designsystem.FinancialNumber
import cn.oryjk.changuan.presentation.designsystem.FixedHeaderPage
import cn.oryjk.changuan.presentation.designsystem.GrowthStageTag
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.LedgerRow
import cn.oryjk.changuan.presentation.designsystem.LedgerSparkline
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.RefreshableContent
import cn.oryjk.changuan.presentation.designsystem.SortableColumnHeader
import cn.oryjk.changuan.presentation.designsystem.SummaryItem
import cn.oryjk.changuan.presentation.designsystem.TrackingActionPill
import cn.oryjk.changuan.presentation.designsystem.TrackingGrowthIcon
import cn.oryjk.changuan.presentation.designsystem.signedColor
import cn.oryjk.changuan.presentation.designsystem.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    state: AppUiState,
    onOpenAuth: () -> Unit,
    onGoMarket: () -> Unit,
    onRefresh: () -> Unit,
    onEnsureCatalog: () -> Unit,
    onStartTrack: (Stock) -> Unit,
    onCancelTracks: (List<String>) -> Unit,
    onOpenDetail: (Stock) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleSort: (TrackingSortField) -> Unit,
    onTogglePin: (String) -> Unit,
    onMoveTrack: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
) {
    val palette = LocalChanPalette.current
    val authenticated = state.user != null
    var quickAdd by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var expandedSecid by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(authenticated) {
        if (!authenticated) {
            quickAdd = false
            managing = false
            selected = emptySet()
            expandedSecid = null
            pendingDelete = emptySet()
        }
    }

    val trackedSecids = state.trackings.map { it.stock.secid }.toSet()
    LaunchedEffect(trackedSecids) {
        selected = selected.intersect(trackedSecids)
        pendingDelete = pendingDelete.intersect(trackedSecids)
        if (expandedSecid != null && expandedSecid !in trackedSecids) {
            expandedSecid = null
        }
    }

    val sorted = remember(
        state.trackings,
        state.pinnedSecids,
        state.customTrackingOrder,
        state.trackingSortField,
        state.trackingSortDescending,
        state.trackingAsOfDate,
    ) {
        TrackingRules.orderAndSort(
            items = state.trackings,
            pinnedSecids = state.pinnedSecids,
            customOrder = state.customTrackingOrder,
            sortField = state.trackingSortField,
            descending = state.trackingSortDescending,
            asOfDate = state.trackingAsOfDate,
        )
    }

    val totalPrincipal = state.trackings.size * TrackingRules.SIMULATED_PRINCIPAL_PER_ASSET
    val totalValue = if (state.trackings.isNotEmpty()) state.trackings.sumOf { it.currentValue } else null
    val totalDayProfit = if (state.trackings.isNotEmpty()) state.trackings.sumOf { it.dayProfit } else null
    val totalProfit = if (state.trackings.isNotEmpty()) state.trackings.sumOf { it.cumulativeProfit } else null
    val longestSummary = remember(state.trackings, state.trackingAsOfDate) {
        TrackingRules.summaryNote(state.trackings.map { it.startDate }, state.trackingAsOfDate)
    }

    val subtitle = if (state.trackingAsOfDate.isNotBlank() && state.trackings.isNotEmpty()) {
        "每只 ¥10,000 模拟本金记账  截至 ${state.trackingAsOfDate}"
    } else {
        "每只 ¥10,000 模拟本金记账"
    }

    if (pendingDelete.isNotEmpty()) {
        val deleteTargets = pendingDelete.toList()
        val deleteNames = state.trackings
            .filter { it.stock.secid in pendingDelete }
            .map { it.stock.name }
        val titleText = if (deleteNames.size == 1) {
            "结束跟踪「${deleteNames.single()}」？"
        } else {
            "结束选中的 ${deleteTargets.size} 只标的跟踪？"
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = emptySet() },
            containerColor = palette.backgroundCanvas,
            title = { Text(titleText, color = palette.inkPrimary) },
            text = {
                Text(
                    text = "结束后，对应标的的模拟跟踪记录将从列表移除。",
                    color = palette.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = emptySet()
                        managing = false
                        selected = emptySet()
                        onCancelTracks(deleteTargets)
                    },
                ) {
                    Text("确认结束", color = palette.positiveRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptySet() }) {
                    Text("取消", color = palette.inkSecondary)
                }
            },
        )
    }

    if (quickAdd && authenticated) {
        LaunchedEffect(Unit) {
            onEnsureCatalog()
        }
        QuickAddTrackingSheet(
            stockAssets = state.stockAssets.map { it.stock },
            etfAssets = state.etfAssets.map { it.stock },
            trackedSecids = trackedSecids,
            pendingSecids = state.pendingTrackSecids,
            onTrack = { stock ->
                if (stock.secid in trackedSecids) {
                    pendingDelete = setOf(stock.secid)
                } else {
                    onStartTrack(stock)
                }
            },
            onOpenSearch = {
                quickAdd = false
                onOpenSearch()
            },
            onDismiss = { quickAdd = false },
        )
    }

    FixedHeaderPage(
        modifier = modifier,
        applyStatusBarPadding = applyStatusBarPadding,
        header = {
            PageHeader(
                title = "跟踪",
                subtitle = subtitle,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundCanvas),
        ) {
            RefreshableContent(
                isRefreshing = state.trackingRefreshing,
                onRefresh = onRefresh,
                enabled = authenticated,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                actionLabel = "刷新跟踪列表",
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tracking-list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (!authenticated) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "登录后，你跟踪的股票和 ETF 会放在这里，每只按 ¥10,000 模拟本金持续记账。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.inkSecondary,
                                    textAlign = TextAlign.Center,
                                )
                                ActionPill(
                                    text = "登录 / 注册",
                                    onClick = onOpenAuth,
                                )
                            }
                            LedgerHeaderRule()
                        }
                    } else if (state.trackingLoading && state.trackings.isEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
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
                                    text = "正在加载跟踪记录…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.inkSecondary,
                                )
                            }
                        }
                    } else if (state.trackingError.isNotEmpty() && state.trackings.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = state.trackingError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.warmAmberText,
                                    textAlign = TextAlign.Center,
                                )
                                ActionPill(text = "重试", onClick = onRefresh)
                            }
                        }
                    } else if (state.trackings.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "这里还空着。挑一只股票或 ETF 加入跟踪，用 ¥10,000 模拟本金记录走势。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.inkSecondary,
                                    textAlign = TextAlign.Center,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ActionPill(text = "+ 跟踪", onClick = { quickAdd = true })
                                    ActionPill(text = "去行情挑选", outlined = true, onClick = onGoMarket)
                                }
                            }
                            LedgerHeaderRule()
                        }
                    } else {
                        item {
                            TrackingSummarySection(
                                totalPrincipal = totalPrincipal,
                                totalValue = totalValue,
                                totalDayProfit = totalDayProfit,
                                totalProfit = totalProfit,
                                longestNote = longestSummary,
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ActionPill(text = "+ 跟踪", onClick = { quickAdd = true })
                                Spacer(Modifier.weight(1f))
                                FilterPill(
                                    label = if (managing) "完成" else "管理跟踪",
                                    selected = managing,
                                    onClick = {
                                        managing = !managing
                                        selected = emptySet()
                                        expandedSecid = null
                                    },
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${sorted.size} 只标的",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = palette.inkMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TrackingSortField.entries.forEach { field ->
                                        SortableColumnHeader(
                                            label = field.label,
                                            active = state.trackingSortField == field,
                                            descending = state.trackingSortDescending,
                                            onClick = { onToggleSort(field) },
                                        )
                                    }
                                }
                            }
                            LedgerHeaderRule()
                        }

                        itemsIndexed(sorted, key = { _, item -> item.stock.secid }) { index, item ->
                            val isPinned = item.stock.secid in state.pinnedSecids
                            val isPending = item.stock.secid in state.pendingTrackSecids
                            if (managing) {
                                TrackingManageRow(
                                    item = item,
                                    checked = item.stock.secid in selected,
                                    pinned = isPinned,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < sorted.lastIndex,
                                    onToggleSelect = {
                                        selected = if (item.stock.secid in selected) {
                                            selected - item.stock.secid
                                        } else {
                                            selected + item.stock.secid
                                        }
                                    },
                                    onTogglePin = { onTogglePin(item.stock.secid) },
                                    onMoveUp = { onMoveTrack(item.stock.secid, true) },
                                    onMoveDown = { onMoveTrack(item.stock.secid, false) },
                                    showDivider = index < sorted.lastIndex,
                                )
                            } else {
                                TrackingAssetLedgerRow(
                                    item = item,
                                    asOfDate = state.trackingAsOfDate,
                                    pinned = isPinned,
                                    pending = isPending,
                                    expanded = expandedSecid == item.stock.secid,
                                    showDivider = index < sorted.lastIndex,
                                    onToggleExpand = {
                                        expandedSecid = if (expandedSecid == item.stock.secid) {
                                            null
                                        } else {
                                            item.stock.secid
                                        }
                                    },
                                    onOpenDetail = { onOpenDetail(item.stock) },
                                    onRequestUntrack = { pendingDelete = setOf(item.stock.secid) },
                                )
                            }
                        }
                    }
                }
            }

            if (managing && sorted.isNotEmpty()) {
                Surface(color = palette.cardSurface) {
                    Column(Modifier.fillMaxWidth()) {
                        LedgerHeaderRule()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val allSelected = selected.size == sorted.size
                            ActionPill(
                                text = if (allSelected) "取消全选" else "全选 (${sorted.size})",
                                outlined = true,
                                onClick = {
                                    selected = if (allSelected) emptySet() else sorted.map { it.stock.secid }.toSet()
                                },
                            )
                            ActionPill(
                                text = if (selected.isEmpty()) "结束跟踪" else "结束跟踪 (${selected.size})",
                                warm = selected.isNotEmpty(),
                                enabled = selected.isNotEmpty(),
                                onClick = { pendingDelete = selected },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingSummarySection(
    totalPrincipal: Double,
    totalValue: Double?,
    totalDayProfit: Double?,
    totalProfit: Double?,
    longestNote: String?,
) {
    val palette = LocalChanPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "模拟当前总市值",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.inkMuted,
                )
                FinancialNumber(
                    value = TrackingRules.formatMoney(totalValue),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = palette.inkPrimary,
                    alignEnd = false,
                )
            }
            if (longestNote != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(ClarityRadius.pill)
                            .background(palette.warmAmber),
                    )
                    Text(
                        text = longestNote,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = palette.warmAmberText,
                        maxLines = 1,
                    )
                }
            }
        }

        CenteredFinancialMetricGrid(
            columns = 3,
            items = listOf(
                SummaryItem(label = "模拟总本金", value = TrackingRules.formatMoney(totalPrincipal)),
                SummaryItem(
                    label = "当日盈亏",
                    value = TrackingRules.formatSignedMoney(totalDayProfit),
                    signedValue = totalDayProfit,
                ),
                SummaryItem(
                    label = "累计盈亏",
                    value = TrackingRules.formatSignedMoney(totalProfit),
                    signedValue = totalProfit,
                ),
            ),
        )
    }
    LedgerHeaderRule()
}

@Composable
private fun TrackingAssetLedgerRow(
    item: TrackedAsset,
    asOfDate: String,
    pinned: Boolean,
    pending: Boolean,
    expanded: Boolean,
    showDivider: Boolean,
    onToggleExpand: () -> Unit,
    onOpenDetail: () -> Unit,
    onRequestUntrack: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val growth = remember(item.startDate, asOfDate) {
        TrackingRules.growth(item.startDate, asOfDate)
    }
    val decimals = item.stock.decimals

    LedgerRow(
        title = item.stock.name,
        headlineValue = TrackingRules.formatSignedMoney(item.cumulativeProfit),
        headlineLabel = MarketFormat.percent(item.cumulativeReturnPct),
        headlineColor = signedColor(item.cumulativeProfit),
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        showDivider = showDivider,
        modifier = Modifier.testTag("tracking-row-${item.stock.secid}"),
        titlePrefix = growth?.let { g ->
            { TrackingGrowthIcon(stage = g.stage, size = 28.dp) }
        },
        titleSuffix = if (pinned) {
            {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "已置顶",
                    tint = palette.warmAmberText,
                    modifier = Modifier.size(13.dp),
                )
            }
        } else {
            null
        },
        sparkline = if (item.sparkline.size >= 2) {
            {
                LedgerSparkline(
                    points = item.sparkline,
                    changePercent = item.cumulativeReturnPct,
                )
            }
        } else {
            null
        },
        metaContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.stock.symbol,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = palette.inkMuted,
                    maxLines = 1,
                )
                if (growth != null) {
                    GrowthStageTag(stage = growth.stage)
                    Text(
                        text = if (growth.days == 0) "今天加入" else "第 ${growth.days} 天",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = palette.inkSecondary,
                        maxLines = 1,
                    )
                }
            }
        },
        expandedContent = {
            FinancialMetricGroup(
                items = listOf(
                    SummaryItem("最新价", MarketFormat.price(item.latestPrice, decimals)),
                    SummaryItem("起跟价", MarketFormat.price(item.startPrice, decimals)),
                    SummaryItem("模拟市值", TrackingRules.formatMoney(item.currentValue)),
                    SummaryItem(
                        label = "当日盈亏",
                        value = TrackingRules.formatSignedMoney(item.dayProfit),
                        signedValue = item.dayProfit,
                    ),
                ),
                alignStart = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionPill(
                    text = "看详情",
                    outlined = true,
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                )
                CompanionTrackingPill(
                    text = if (pending) "同步中…" else "跟踪中",
                    enabled = !pending,
                    compact = true,
                    onClick = onRequestUntrack,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun TrackingManageRow(
    item: TrackedAsset,
    checked: Boolean,
    pinned: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    showDivider: Boolean,
) {
    val palette = LocalChanPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleSelect)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggleSelect() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.stock.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.stock.symbol}  起跟 ${item.startDate}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = palette.inkMuted,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "取消置顶" else "置顶",
                    tint = if (pinned) palette.warmAmber else palette.inkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    imageVector = Icons.Outlined.ArrowUpward,
                    contentDescription = "上移",
                    tint = if (canMoveUp) palette.inkPrimary else palette.inkMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = "下移",
                    tint = if (canMoveDown) palette.inkPrimary else palette.inkMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            LedgerHeaderRule()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddTrackingSheet(
    stockAssets: List<Stock>,
    etfAssets: List<Stock>,
    trackedSecids: Set<String>,
    pendingSecids: Set<String>,
    onTrack: (Stock) -> Unit,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val candidates = remember(stockAssets, etfAssets) {
        (stockAssets.take(15) + etfAssets.take(10) + DefaultStocks.POPULAR_SEARCHES)
            .distinctBy { it.secid }
            .take(24)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundCanvas,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "添加跟踪标的",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.inkPrimary,
                )
                ActionPill(
                    text = "搜索全部",
                    outlined = true,
                    onClick = onOpenSearch,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
            LedgerHeaderRule()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                itemsIndexed(candidates, key = { _, s -> s.secid }) { index, stock ->
                    val isTracked = stock.secid in trackedSecids
                    val isPending = stock.secid in pendingSecids
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                        ) {
                            Text(
                                text = stock.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${stock.symbol}  ${stock.market}",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = palette.inkMuted,
                                maxLines = 1,
                            )
                        }
                        TrackingActionPill(
                            tracked = isTracked,
                            pending = isPending,
                            onClick = { onTrack(stock) },
                        )
                    }
                    if (index < candidates.lastIndex) {
                        LedgerHeaderRule()
                    }
                }
            }
        }
    }
}
