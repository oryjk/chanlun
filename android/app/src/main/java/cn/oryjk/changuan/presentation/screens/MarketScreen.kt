package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.AssetCategory
import cn.oryjk.changuan.domain.AssetSortField
import cn.oryjk.changuan.domain.MarketAssetItem
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ActionPill
import cn.oryjk.changuan.presentation.designsystem.FixedHeaderPage
import cn.oryjk.changuan.presentation.designsystem.HeaderIconAction
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.RefreshableContent
import cn.oryjk.changuan.presentation.designsystem.StarkUnderlineTabs
import cn.oryjk.changuan.presentation.designsystem.tabular
import kotlinx.coroutines.flow.distinctUntilChanged

private data class MarketColumnWidths(
    val amount: Dp,
    val changePercent: Dp,
    val latest: Dp,
    val gap: Dp,
)

@Composable
fun MarketScreen(
    state: AppUiState,
    onSelectCategory: (AssetCategory) -> Unit,
    onToggleSort: (AssetSortField) -> Unit,
    onRefresh: () -> Unit,
    onOpenAsset: (Stock) -> Unit,
    onToggleTrack: (Stock) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
    onLoadMore: () -> Unit = {},
) {
    val palette = LocalChanPalette.current
    val category = state.assetCategory
    val items = if (category == AssetCategory.STOCK) state.stockAssets else state.etfAssets
    val totalCount = when (category) {
        AssetCategory.STOCK -> state.stockAssetsTotal
        AssetCategory.ETF -> state.etfAssetsTotal
    }.let { if (it > 0) it else items.size }
    val hasMore = if (category == AssetCategory.STOCK) state.stockAssetsHasMore else state.etfAssetsHasMore
    val trackedSecids = state.trackings.map { it.stock.secid }.toSet()
    val subtitle = state.assetsAsOfDate?.takeIf { it.isNotBlank() }?.let { "行情截至 $it" }
        ?: "沪深北 A 股与境内 ETF 实时行情列表"
    val listState = rememberLazyListState()
    var previousSortKey by remember {
        mutableStateOf(Triple(category, state.assetSortField, state.assetSortDescending))
    }
    val currentSortKey = Triple(category, state.assetSortField, state.assetSortDescending)

    LaunchedEffect(currentSortKey) {
        if (previousSortKey != currentSortKey) {
            previousSortKey = currentSortKey
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, items.size, hasMore, state.assetsLoadingMore, state.assetsLoadMoreError) {
        if (!hasMore || items.isEmpty() || state.assetsLoadingMore || state.assetsLoadMoreError.isNotEmpty()) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= (items.size - 15).coerceAtLeast(0)) {
                    onLoadMore()
                }
            }
    }

    FixedHeaderPage(
        modifier = modifier,
        applyStatusBarPadding = applyStatusBarPadding,
        header = {
            PageHeader(
                title = "行情",
                subtitle = subtitle,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeaderIconAction(
                            icon = Icons.Outlined.Search,
                            contentDescription = "搜索证券",
                            onClick = onOpenSearch,
                        )
                        HeaderIconAction(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = "刷新行情列表",
                            loading = state.assetsRefreshing,
                            onClick = onRefresh,
                        )
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.backgroundCanvas),
        ) {
            StarkUnderlineTabs(
                options = AssetCategory.entries.map { it.label },
                selectedIndex = AssetCategory.entries.indexOf(category).coerceAtLeast(0),
                onSelect = { idx -> onSelectCategory(AssetCategory.entries[idx]) },
                optionTag = { idx -> "market-category-${AssetCategory.entries[idx].rawValue}" },
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widths = when {
                    maxWidth >= 600.dp -> MarketColumnWidths(
                        amount = 104.dp,
                        changePercent = 92.dp,
                        latest = 96.dp,
                        gap = 12.dp,
                    )
                    maxWidth < 375.dp -> MarketColumnWidths(
                        amount = 68.dp,
                        changePercent = 62.dp,
                        latest = 64.dp,
                        gap = 6.dp,
                    )
                    else -> MarketColumnWidths(
                        amount = 74.dp,
                        changePercent = 68.dp,
                        latest = 68.dp,
                        gap = 8.dp,
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    MarketTableHeaderRow(
                        category = category,
                        count = totalCount,
                        activeSortField = state.assetSortField,
                        descending = state.assetSortDescending,
                        widths = widths,
                        onToggleSort = onToggleSort,
                    )

                    LedgerHeaderRule()

                    RefreshableContent(
                        isRefreshing = state.assetsRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                        actionLabel = "刷新行情列表",
                    ) {
                        when {
                            state.assetsLoading && items.isEmpty() -> {
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
                                        text = "正在加载${category.label}列表…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.inkSecondary,
                                    )
                                }
                            }

                            state.assetsError.isNotEmpty() && items.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = state.assetsError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.warmAmberText,
                                        textAlign = TextAlign.Center,
                                    )
                                    ActionPill(text = "重新加载", onClick = onRefresh)
                                }
                            }

                            items.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "暂无${category.label}行情数据",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.inkPrimary,
                                    )
                                    ActionPill(text = "刷新重试", outlined = true, onClick = onRefresh)
                                }
                            }

                            else -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("market-asset-list"),
                                    contentPadding = PaddingValues(bottom = 18.dp),
                                ) {
                                    itemsIndexed(items, key = { _, item -> item.stock.secid }) { index, item ->
                                        MarketAssetTableRow(
                                            item = item,
                                            tracked = item.stock.secid in trackedSecids,
                                            widths = widths,
                                            showDivider = index < items.lastIndex,
                                            onOpen = { onOpenAsset(item.stock) },
                                            onToggleTrack = { onToggleTrack(item.stock) },
                                        )
                                    }
                                    item(key = "market-list-footer") {
                                        MarketListFooter(
                                            totalCount = totalCount,
                                            categoryLabel = category.label,
                                            hasMore = hasMore,
                                            loadingMore = state.assetsLoadingMore,
                                            loadMoreError = state.assetsLoadMoreError,
                                            onLoadMore = onLoadMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketTableHeaderRow(
    category: AssetCategory,
    count: Int,
    activeSortField: AssetSortField,
    descending: Boolean,
    widths: MarketColumnWidths,
    onToggleSort: (AssetSortField) -> Unit,
) {
    val palette = LocalChanPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 0) category.label else "共 $count 只${category.label}",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
        )
        MarketSortableHeaderCell(
            label = AssetSortField.AMOUNT.label,
            active = activeSortField == AssetSortField.AMOUNT,
            descending = descending,
            onClick = { onToggleSort(AssetSortField.AMOUNT) },
            modifier = Modifier
                .width(widths.amount)
                .testTag("market-sort-amount"),
        )
        Spacer(Modifier.width(widths.gap))
        MarketSortableHeaderCell(
            label = AssetSortField.CHANGE_PERCENT.label,
            active = activeSortField == AssetSortField.CHANGE_PERCENT,
            descending = descending,
            onClick = { onToggleSort(AssetSortField.CHANGE_PERCENT) },
            modifier = Modifier
                .width(widths.changePercent)
                .testTag("market-sort-change_percent"),
        )
        Spacer(Modifier.width(widths.gap))
        MarketSortableHeaderCell(
            label = AssetSortField.LATEST.label,
            active = activeSortField == AssetSortField.LATEST,
            descending = descending,
            onClick = { onToggleSort(AssetSortField.LATEST) },
            modifier = Modifier
                .width(widths.latest)
                .testTag("market-sort-latest"),
        )
    }
}

@Composable
private fun MarketSortableHeaderCell(
    label: String,
    active: Boolean,
    descending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val icon = when {
        !active -> Icons.Outlined.UnfoldMore
        descending -> Icons.Outlined.ExpandMore
        else -> Icons.Outlined.ExpandLess
    }
    val sortStateText = when {
        !active -> "未排序"
        descending -> "降序"
        else -> "升序"
    }
    val tint = if (active) palette.actionPrimary else palette.inkMuted
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = sortStateText },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun MarketAssetTableRow(
    item: MarketAssetItem,
    tracked: Boolean,
    widths: MarketColumnWidths,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onToggleTrack: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val toneColor = palette.directionColor(item.changePercent)
    val metaText = buildString {
        append(item.stock.symbol)
        append(" ")
        append(item.stock.market)
        if (!item.signalLabel.isNullOrBlank()) {
            append(" ")
            append(item.signalLabel)
        }
    }
    val amountText = when {
        item.amount > 0.0 -> MarketFormat.volume(item.amount)
        item.volume <= 0.0 -> "停牌"
        else -> "—"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "查看${item.stock.name}行情",
                    onClick = onOpen,
                )
                .then(
                    if (tracked) {
                        Modifier.semantics { stateDescription = "已跟踪" }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("market-row-${item.stock.secid}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.stock.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .clickable(
                                role = Role.Button,
                                onClickLabel = if (tracked) "结束跟踪" else "加入跟踪",
                                onClick = onToggleTrack,
                            )
                            .padding(2.dp)
                            .semantics {
                                contentDescription = if (tracked) "取消跟踪${item.stock.name}" else "跟踪${item.stock.name}"
                            }
                            .testTag("market-bookmark-${item.stock.secid}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (tracked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = if (tracked) palette.warmAmber else palette.inkMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .width(widths.amount)
                    .testTag("market-cell-amount-${item.stock.secid}"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleSmall.tabular(),
                    fontWeight = FontWeight.Medium,
                    color = if (item.amount > 0.0) palette.inkPrimary else palette.inkMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Spacer(Modifier.width(widths.gap))

            Box(
                modifier = Modifier
                    .width(widths.changePercent)
                    .testTag("market-cell-change-${item.stock.secid}"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = MarketFormat.percent(item.changePercent),
                    style = MaterialTheme.typography.titleSmall.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = toneColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Spacer(Modifier.width(widths.gap))

            Box(
                modifier = Modifier
                    .width(widths.latest)
                    .testTag("market-cell-latest-${item.stock.secid}"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = MarketFormat.price(item.latest, item.stock.decimals),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = toneColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = palette.cardBorder,
            )
        }
    }
}

@Composable
private fun MarketListFooter(
    totalCount: Int,
    categoryLabel: String,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreError: String,
    onLoadMore: () -> Unit,
) {
    val palette = LocalChanPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("market-list-footer"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadingMore -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = palette.primaryBrand,
                    )
                    Text(
                        text = "正在加载更多${categoryLabel}…",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.inkMuted,
                    )
                }
            }

            loadMoreError.isNotEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = loadMoreError,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.warmAmberText,
                        textAlign = TextAlign.Center,
                    )
                    ActionPill(text = "重新加载", outlined = true, onClick = onLoadMore)
                }
            }

            !hasMore && totalCount > 0 -> {
                Text(
                    text = "已显示全部 $totalCount 只${categoryLabel}",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = palette.inkMuted,
                )
            }
        }
    }
}
