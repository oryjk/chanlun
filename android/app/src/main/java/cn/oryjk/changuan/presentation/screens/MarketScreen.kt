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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import cn.oryjk.changuan.presentation.designsystem.LedgerRow
import cn.oryjk.changuan.presentation.designsystem.LedgerSparkline
import cn.oryjk.changuan.presentation.designsystem.PageHeader
import cn.oryjk.changuan.presentation.designsystem.RefreshableContent
import cn.oryjk.changuan.presentation.designsystem.SortableColumnHeader
import cn.oryjk.changuan.presentation.designsystem.StarkUnderlineTabs
import cn.oryjk.changuan.presentation.designsystem.tabular

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
) {
    val palette = LocalChanPalette.current
    val category = state.assetCategory
    val items = if (category == AssetCategory.STOCK) state.stockAssets else state.etfAssets
    val trackedSecids = state.trackings.map { it.stock.secid }.toSet()
    val subtitle = state.assetsAsOfDate?.takeIf { it.isNotBlank() }?.let { "行情截至 $it" }
        ?: "沪深股票与境内 ETF 实时行情列表"

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (items.isEmpty()) {
                        category.label
                    } else {
                        "共 ${items.size} 只${category.label}"
                    },
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssetSortField.entries.forEach { field ->
                        SortableColumnHeader(
                            label = field.label,
                            active = state.assetSortField == field,
                            descending = state.assetSortDescending,
                            onClick = { onToggleSort(field) },
                            modifier = Modifier.testTag("market-sort-${field.rawValue}"),
                        )
                    }
                }
            }

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
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("market-asset-list"),
                            contentPadding = PaddingValues(bottom = 18.dp),
                        ) {
                            itemsIndexed(items, key = { _, item -> item.stock.secid }) { index, item ->
                                MarketAssetLedgerRow(
                                    item = item,
                                    tracked = item.stock.secid in trackedSecids,
                                    showDivider = index < items.lastIndex,
                                    onOpen = { onOpenAsset(item.stock) },
                                    onToggleTrack = { onToggleTrack(item.stock) },
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
private fun MarketAssetLedgerRow(
    item: MarketAssetItem,
    tracked: Boolean,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onToggleTrack: () -> Unit,
) {
    val palette = LocalChanPalette.current
    val toneColor = palette.directionColor(item.changePercent)
    val metaText = buildString {
        append(item.stock.symbol)
        append("  ")
        append(item.stock.market)
        if (!item.signalLabel.isNullOrBlank()) {
            append("  ")
            append(item.signalLabel)
        } else if (item.amount > 0.0) {
            append("  成交 ")
            append(MarketFormat.volume(item.amount))
        }
    }

    LedgerRow(
        title = item.stock.name,
        headlineValue = MarketFormat.price(item.latest, item.stock.decimals),
        headlineLabel = MarketFormat.percent(item.changePercent),
        headlineColor = toneColor,
        expanded = false,
        expandable = false,
        onToggleExpand = onOpen,
        showDivider = showDivider,
        modifier = Modifier.testTag("market-row-${item.stock.secid}"),
        headerStateDescription = if (tracked) "已跟踪" else null,
        titleSuffix = {
            Box(
                modifier = Modifier
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (tracked) "结束跟踪" else "加入跟踪",
                        onClick = onToggleTrack,
                    )
                    .padding(2.dp)
                    .semantics { contentDescription = if (tracked) "取消跟踪${item.stock.name}" else "跟踪${item.stock.name}" }
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
        },
        sparkline = if (item.sparkline.size >= 2) {
            {
                LedgerSparkline(
                    points = item.sparkline,
                    changePercent = item.changePercent,
                )
            }
        } else {
            null
        },
        metaContent = {
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
