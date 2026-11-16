package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.DefaultStocks
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.FilterPill
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
import cn.oryjk.changuan.presentation.designsystem.tabular

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockSearchScreen(
    currentSecid: String,
    results: List<Stock>,
    loading: Boolean,
    error: String,
    onQueryChange: (String) -> Unit,
    onSelectStock: (Stock) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
    requestAutoFocus: Boolean = true,
) {
    val palette = LocalChanPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestAutoFocus) {
        if (requestAutoFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        color = palette.backgroundCanvas,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (applyStatusBarPadding) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回行情",
                        tint = palette.inkPrimary,
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(ClarityRadius.field)
                        .background(palette.subtleSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = palette.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "输入代码、名称或 ETF（如 600519）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkMuted,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { next ->
                                query = next
                                onQueryChange(next)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = palette.inkPrimary,
                            ),
                            cursorBrush = SolidColor(palette.primaryBrand),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                onQueryChange("")
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "清空关键词",
                                tint = palette.inkSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            LedgerHeaderRule()

            when {
                query.isBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "常用标的",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DefaultStocks.POPULAR_SEARCHES.forEach { stock ->
                                FilterPill(
                                    label = "${stock.name} ${stock.symbol}",
                                    selected = stock.secid == currentSecid,
                                    onClick = { onSelectStock(stock) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "支持沪深北 A 股、科创板、创业板及境内上市 ETF。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                        )
                    }
                }

                loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
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
                            text = "正在搜索证券…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkSecondary,
                        )
                    }
                }

                error.isNotEmpty() -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.warmAmberText,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                results.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "未找到匹配证券",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary,
                        )
                        Text(
                            text = "请检查代码或名称关键字，仅支持沪深北 A 股及境内 ETF。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results, key = { it.secid }) { item ->
                            val isCurrent = item.secid == currentSecid
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { onSelectStock(item) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = palette.inkPrimary,
                                        )
                                        StarkBadgeTag(text = item.market)
                                        if (isCurrent) {
                                            StarkBadgeTag(text = "当前", inverted = true)
                                        }
                                    }
                                    Text(
                                        text = item.secid,
                                        style = MaterialTheme.typography.labelLarge.tabular(),
                                        fontWeight = FontWeight.Medium,
                                        color = palette.inkSecondary,
                                    )
                                }
                                LedgerHeaderRule()
                            }
                        }
                    }
                }
            }
        }
    }
}
