package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.ChartLayers
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.SignalRules
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.presentation.AppUiState
import cn.oryjk.changuan.presentation.ChanTheme
import cn.oryjk.changuan.presentation.charts.MarketChartCanvas
import java.util.Locale

@Composable
fun MarketScreen(
    state: AppUiState,
    onChangeTimeframe: (Timeframe) -> Unit,
    onToggleLayer: ((ChartLayers) -> ChartLayers) -> Unit,
    onRetry: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCandleIndex by remember(state.stock.secid, state.timeframe) {
        mutableStateOf<Int?>(null)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ChanTheme.Page),
    ) {
        val isWide = maxWidth >= 900.dp
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(ChanTheme.Sheet),
        ) {
            MarketHeader(state = state)
            HorizontalDivider(color = ChanTheme.Line)
            TimeframeStrip(
                current = state.timeframe,
                onSelect = { tf ->
                    selectedCandleIndex = null
                    onChangeTimeframe(tf)
                },
            )
            HorizontalDivider(color = ChanTheme.Line)

            if (state.error.isNotEmpty()) {
                ErrorBanner(message = state.error, onRetry = onRetry)
            }
            if (state.loading) {
                StatusBanner(text = "正在读取行情与结构...")
            }

            if (state.timeframe == Timeframe.REALTIME) {
                QuotePanel(
                    stock = state.stock,
                    quote = state.quote,
                    points = state.series,
                )
            } else if (state.snapshot != null) {
                val snapshot = state.snapshot
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(0.62f),
                        ) {
                            ChartSection(
                                snapshot = snapshot,
                                layers = state.layers,
                                decimals = state.stock.decimals,
                                selectedIndex = selectedCandleIndex,
                                onSelectIndex = { selectedCandleIndex = it },
                                onToggleLayer = onToggleLayer,
                                chartHeight = 480,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(640.dp)
                                .background(ChanTheme.Line),
                        )
                        Column(
                            modifier = Modifier.weight(0.38f),
                        ) {
                            SignalPanel(
                                snapshot = snapshot,
                                decimals = state.stock.decimals,
                            )
                            SectionBreak()
                            ConfluenceSection(state = state)
                            SectionBreak()
                            RangeSection(
                                snapshot = snapshot,
                                decimals = state.stock.decimals,
                                onOpenTerms = onOpenTerms,
                            )
                        }
                    }
                } else {
                    ChartSection(
                        snapshot = snapshot,
                        layers = state.layers,
                        decimals = state.stock.decimals,
                        selectedIndex = selectedCandleIndex,
                        onSelectIndex = { selectedCandleIndex = it },
                        onToggleLayer = onToggleLayer,
                        chartHeight = 380,
                    )
                    SectionBreak()
                    SignalPanel(
                        snapshot = snapshot,
                        decimals = state.stock.decimals,
                    )
                    SectionBreak()
                    ConfluenceSection(state = state)
                    SectionBreak()
                    RangeSection(
                        snapshot = snapshot,
                        decimals = state.stock.decimals,
                        onOpenTerms = onOpenTerms,
                    )
                }
            } else if (!state.loading) {
                Text(
                    text = "暂无行情，请搜索证券或点击右上角刷新",
                    fontSize = 14.sp,
                    color = ChanTheme.TextSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }

            Text(
                text = "结构识别仅供研究，不构成买卖建议",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MarketHeader(state: AppUiState) {
    val decimals = state.stock.decimals
    val latest = if (state.timeframe == Timeframe.REALTIME) {
        state.quote?.latest
    } else {
        state.snapshot?.summary?.latest
    }
    val changePct = if (state.timeframe == Timeframe.REALTIME) {
        state.quote?.changePercent
    } else {
        state.snapshot?.summary?.changePercent
    }
    val priceColor = ChanTheme.directionColor(changePct ?: 0.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.stock.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${state.stock.symbol}  ${state.stock.market}",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = MarketFormat.price(latest, decimals),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = priceColor,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = MarketFormat.percent(changePct),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = priceColor,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = state.timeframe.label,
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }
    }
}

@Composable
private fun TimeframeStrip(
    current: Timeframe,
    onSelect: (Timeframe) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Timeframe.entries.forEach { tf ->
            val selected = tf == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(tf) }
                    .padding(top = 10.dp),
            ) {
                Text(
                    text = tf.label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) ChanTheme.Accent else ChanTheme.TextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (selected) ChanTheme.Accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun ChartSection(
    snapshot: MarketSnapshot,
    layers: ChartLayers,
    decimals: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    onToggleLayer: ((ChartLayers) -> ChartLayers) -> Unit,
    chartHeight: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LayerToggleBar(layers = layers, onToggleLayer = onToggleLayer)
        if (selectedIndex != null && selectedIndex in snapshot.candles.indices) {
            CandleDetailBar(
                snapshot = snapshot,
                index = selectedIndex,
                decimals = decimals,
            )
        } else {
            Text(
                text = "左右拖动查看历史，双指缩放，点按或长按查看单根 K 线详情",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        MarketChartCanvas(
            snapshot = snapshot,
            layers = layers,
            decimals = decimals,
            selectedIndex = selectedIndex,
            onSelectIndex = onSelectIndex,
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp),
        )
    }
}

@Composable
private fun LayerToggleBar(
    layers: ChartLayers,
    onToggleLayer: ((ChartLayers) -> ChartLayers) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayerLegendButton(
            title = "分型",
            enabled = layers.fractals,
            color = ChanTheme.Fractal,
            isBox = false,
            onClick = { onToggleLayer { it.copy(fractals = !it.fractals) } },
        )
        LayerLegendButton(
            title = "笔",
            enabled = layers.pens,
            color = ChanTheme.Gold,
            isBox = false,
            onClick = { onToggleLayer { it.copy(pens = !it.pens) } },
        )
        LayerLegendButton(
            title = "线段",
            enabled = layers.segments,
            color = ChanTheme.Accent,
            isBox = false,
            onClick = { onToggleLayer { it.copy(segments = !it.segments) } },
        )
        LayerLegendButton(
            title = "笔中枢",
            enabled = layers.zhongshu,
            color = ChanTheme.Violet,
            isBox = true,
            onClick = { onToggleLayer { it.copy(zhongshu = !it.zhongshu) } },
        )
        LayerLegendButton(
            title = "买卖点",
            enabled = layers.points,
            color = ChanTheme.Up,
            isBox = false,
            onClick = { onToggleLayer { it.copy(points = !it.points) } },
        )
    }
}

@Composable
private fun LayerLegendButton(
    title: String,
    enabled: Boolean,
    color: Color,
    isBox: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(if (isBox) 7.dp else 2.5.dp)
                .background(color.copy(alpha = alpha)),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            color = ChanTheme.TextPrimary.copy(alpha = alpha),
        )
    }
}

@Composable
private fun CandleDetailBar(
    snapshot: MarketSnapshot,
    index: Int,
    decimals: Int,
) {
    val candle = snapshot.candles[index]
    val change = SignalRules.candleChange(snapshot.candles, index)
    val zone = MarketTime.zone(snapshot.instrument.currency)
    fun price(v: Double): String = MarketFormat.price(v, decimals)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = MarketTime.formatDate(candle.time, "yyyy-MM-dd HH:mm", zone),
            fontSize = 12.sp,
            color = ChanTheme.TextSecondary,
        )
        Text(
            text = "开 ${price(candle.open)}  高 ${price(candle.high)}  低 ${price(candle.low)}  收 ${price(candle.close)}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = ChanTheme.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "涨跌 ${MarketFormat.percent(change?.percent)} (${MarketFormat.signedPrice(change?.delta, decimals)})",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = ChanTheme.directionColor(change?.delta ?: 0.0),
            )
            Text(
                text = "量 ${MarketFormat.volume(candle.volume)}",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }
        if (index in snapshot.macd.indices) {
            val m = snapshot.macd[index]
            Text(
                text = String.format(
                    Locale.US,
                    "DIF %.3f  DEA %.3f  MACD %.3f",
                    m.dif,
                    m.dea,
                    m.histogram,
                ),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = ChanTheme.TextSecondary,
            )
        }
        snapshot.signals.points.filter { it.candleIndex == index }.forEach { pt ->
            Text(
                text = "${pt.label} ${if (pt.confirmed) "结构已确认" else "候选"}  首次识别与确认时间未追溯",
                fontSize = 12.sp,
                color = if (pt.side == "buy") ChanTheme.Up else ChanTheme.Down,
            )
        }
    }
}

@Composable
private fun ConfluenceSection(state: AppUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "多级别联立",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChanTheme.TextPrimary,
        )
        if (state.confluenceLoading) {
            Text(
                text = "正在比对日线、1 小时、30 分钟、5 分钟结构...",
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
            )
        }
        state.confluence?.let { conf ->
            Text(
                text = conf.conclusion,
                fontSize = 14.sp,
                color = ChanTheme.TextPrimary,
            )
            conf.levels.forEach { lvl ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = lvl.timeframeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChanTheme.TextPrimary,
                        modifier = Modifier.width(68.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val ptText = lvl.latestPoint?.let {
                            "${it.label}  ${if (it.confirmed) "已确认" else "候选"}"
                        } ?: "暂无买卖点"
                        Text(
                            text = ptText,
                            fontSize = 13.sp,
                            color = ChanTheme.TextPrimary,
                        )
                        Text(
                            text = if (lvl.lowerConfirmation == null) {
                                "暂无已确认的次级别印证"
                            } else {
                                "已形成同向结构印证"
                            },
                            fontSize = 12.sp,
                            color = ChanTheme.TextSecondary,
                        )
                    }
                }
            }
        }
        if (state.confluenceError.isNotEmpty()) {
            Text(
                text = state.confluenceError,
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }
    }
}

@Composable
private fun RangeSection(
    snapshot: MarketSnapshot,
    decimals: Int,
    onOpenTerms: () -> Unit,
) {
    fun price(v: Double): String = MarketFormat.price(v, decimals)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "行情区间",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenTerms) {
                Text(
                    text = "术语说明",
                    fontSize = 13.sp,
                    color = ChanTheme.Accent,
                )
            }
        }
        Text(
            text = "区间最高 ${price(snapshot.summary.high)}  区间最低 ${price(snapshot.summary.low)}",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = ChanTheme.TextPrimary,
        )
        Text(
            text = "近 20 根量能 ${MarketFormat.volume(snapshot.summary.volume)}",
            fontSize = 13.sp,
            color = ChanTheme.TextSecondary,
        )
    }
}

@Composable
internal fun SectionBreak() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(ChanTheme.Page),
    )
}

@Composable
internal fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = ChanTheme.Up,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("重试", fontSize = 13.sp, color = ChanTheme.Accent)
        }
    }
}

@Composable
internal fun StatusBanner(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = ChanTheme.TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
