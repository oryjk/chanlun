package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.SignalPoint
import cn.oryjk.changuan.domain.SignalQuality
import cn.oryjk.changuan.domain.SignalRules
import cn.oryjk.changuan.domain.Timeframe
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.designsystem.ClarityCard
import cn.oryjk.changuan.presentation.designsystem.ClarityRadius
import cn.oryjk.changuan.presentation.designsystem.FinancialMetricGroup
import cn.oryjk.changuan.presentation.designsystem.FinancialNumber
import cn.oryjk.changuan.presentation.designsystem.LedgerHeaderRule
import cn.oryjk.changuan.presentation.designsystem.SectionTitle
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
import cn.oryjk.changuan.presentation.designsystem.SummaryItem
import cn.oryjk.changuan.presentation.designsystem.tabular
import java.time.ZoneId

@Composable
fun SignalPanel(
    snapshot: MarketSnapshot,
    decimals: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val zone = MarketTime.zone(snapshot.instrument.currency)
    val latestPoint = snapshot.signals.points.maxByOrNull { it.candleIndex }
    val quality = snapshot.signalQuality
    val conclusion = SignalRules.conclusion(
        bias = snapshot.guidance.bias,
        latest = snapshot.summary.latest,
        point = latestPoint,
        quality = quality,
    )
    val isInvalidated = latestPoint != null && (
        quality?.state == "invalidated" ||
            quality?.historyStatus == "invalidated" ||
            quality?.firstReferenceCrossedAt != null
        )
    val points = snapshot.signals.points
        .sortedByDescending { it.candleIndex }
        .take(6)

    val divergenceSummary = snapshot.signals.divergences.lastOrNull()?.let { div ->
        val kindLabel = if (div.kind == "trend") "趋势" else "盘整"
        val dirLabel = if (div.direction == "bullish" || div.direction == "up" || div.direction == "buy") "底" else "顶"
        "${kindLabel}${dirLabel}背驰"
    } ?: "暂无背驰"

    ClarityCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = "缠论结构与买卖点",
            trailing = {
                StarkBadgeTag(
                    text = SignalRules.trend(snapshot.signals.trendType),
                    inverted = true,
                )
            },
        )

        val conclusionContainer = when {
            isInvalidated -> palette.warmAmberSurface
            snapshot.guidance.bias == "bullish" -> palette.positiveRedSurface
            snapshot.guidance.bias == "bearish" -> palette.negativeGreenSurface
            else -> palette.subtleSurface
        }
        val conclusionBorder = when {
            isInvalidated -> palette.warmAmberBorder
            else -> palette.cardBorder
        }
        val conclusionTextColor = when {
            isInvalidated -> palette.warmAmberText
            snapshot.guidance.bias == "bullish" -> palette.positiveRed
            snapshot.guidance.bias == "bearish" -> palette.negativeGreen
            else -> palette.inkPrimary
        }

        Surface(
            color = conclusionContainer,
            shape = ClarityRadius.tile,
            border = BorderStroke(1.dp, conclusionBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conclusion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = conclusionTextColor,
                    )
                    StarkBadgeTag(text = SignalRules.biasLabel(snapshot.guidance.bias))
                }
                Text(
                    text = conclusion.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary,
                )
            }
        }

        if (quality != null) {
            QualityFactBlock(
                point = latestPoint,
                quality = quality,
                decimals = decimals,
            )
        }

        FinancialMetricGroup(
            items = listOf(
                SummaryItem(label = "走势类型", value = SignalRules.trend(snapshot.signals.trendType)),
                SummaryItem(label = "背驰判断", value = divergenceSummary),
                SummaryItem(
                    label = "笔 / 线段",
                    value = "${snapshot.analysis.pens.size} / ${snapshot.analysis.segments.size}",
                ),
                SummaryItem(label = "笔中枢数", value = "${snapshot.analysis.zhongshu.size}"),
            ),
            alignStart = true,
        )

        if (points.isNotEmpty()) {
            LedgerHeaderRule()
            Text(
                text = "结构端点买卖点（日期为结构端点日期）",
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                points.forEach { point ->
                    SignalPointRow(
                        point = point,
                        decimals = decimals,
                        isDaily = snapshot.timeframe == Timeframe.DAILY.rawValue,
                        zone = zone,
                    )
                }
            }
        } else {
            LedgerHeaderRule()
            Text(
                text = "当前周期未识别出有效三类买卖点。",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
            )
        }
    }
}

@Composable
private fun QualityFactBlock(
    point: SignalPoint?,
    quality: SignalQuality,
    decimals: Int,
) {
    val palette = LocalChanPalette.current
    val invalidated = quality.state == "invalidated" ||
        quality.historyStatus == "invalidated" ||
        quality.firstReferenceCrossedAt != null
    val statusText = when {
        invalidated -> "已失效"
        quality.state == "confirmed" && quality.historyStatus == "checked_from_pivot" -> "端点后核查有效"
        quality.state == "confirmed" -> "结构已确认（待核对历史）"
        else -> "观察中"
    }
    val statusColor = when {
        invalidated -> palette.warmAmberText
        quality.state == "confirmed" && quality.historyStatus == "checked_from_pivot" -> palette.positiveRed
        else -> palette.inkSecondary
    }
    val refPrice = SignalRules.invalidationReferencePrice(point, quality)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最新信号核查",
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkSecondary,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
            )
        }

        FinancialMetricGroup(
            items = buildList {
                add(
                    SummaryItem(
                        label = "失效参考",
                        value = if (refPrice != null) MarketFormat.price(refPrice, decimals) else "—",
                    ),
                )
                add(
                    SummaryItem(
                        label = "后续已核K线",
                        value = quality.barsSincePivot?.let { "$it 根" } ?: "待核查",
                    ),
                )
                quality.firstReferenceCrossedAt?.let { crossedAt ->
                    val dateStr = MarketTime.formatDate(crossedAt, "yyyy-MM-dd")
                    val closeStr = quality.firstReferenceCrossedClose?.let {
                        MarketFormat.price(it, decimals)
                    } ?: "—"
                    add(SummaryItem(label = "首次越界 ($dateStr)", value = closeStr))
                }
            },
            alignStart = true,
        )

        Text(
            text = SignalRules.historySummary(quality, decimals),
            style = MaterialTheme.typography.bodySmall,
            color = if (invalidated) palette.warmAmberText else palette.inkSecondary,
        )

        val reasonText = quality.reasons.filter { it.isNotBlank() }.joinToString("；")
        if (reasonText.isNotBlank()) {
            Text(
                text = "待验证证据：$reasonText",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
            )
        }
        Text(
            text = SignalRules.recognitionTimeSummary(quality),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
        )
    }
}

@Composable
private fun SignalPointRow(
    point: SignalPoint,
    decimals: Int,
    isDaily: Boolean,
    zone: ZoneId,
) {
    val palette = LocalChanPalette.current
    val isBuy = point.side == "buy"
    val toneColor = if (isBuy) palette.positiveRed else palette.negativeGreen
    val datePattern = if (isDaily) "yyyy-MM-dd" else "MM-dd HH:mm"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = if (isBuy) palette.positiveRedSurface else palette.negativeGreenSurface,
                shape = ClarityRadius.pill,
            ) {
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = toneColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = if (point.confirmed) "已确认" else "候选未确认",
                style = MaterialTheme.typography.labelSmall,
                color = if (point.confirmed) palette.inkPrimary else palette.inkMuted,
                maxLines = 1,
            )
            if (point.reason.isNotBlank()) {
                Text(
                    text = point.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            FinancialNumber(
                value = MarketFormat.price(point.price, decimals),
                style = MaterialTheme.typography.titleSmall,
                color = toneColor,
            )
            Text(
                text = "端点 ${MarketTime.formatDate(point.time, datePattern, zone)}",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = palette.inkMuted,
            )
        }
    }
}

@Composable
fun ConfluenceSection(
    confluence: SignalConfluence?,
    loading: Boolean,
    error: String,
    decimals: Int,
    onJumpTimeframe: (Timeframe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val hasConfirmedMatch = confluence?.levels?.any { it.lowerConfirmation != null } == true

    ClarityCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(
            title = "多级别联立",
            trailing = {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = palette.primaryBrand,
                    )
                } else if (confluence != null) {
                    StarkBadgeTag(
                        text = if (hasConfirmedMatch) "存在印证" else "未形成印证",
                        inverted = hasConfirmedMatch,
                    )
                }
            },
        )
        when {
            error.isNotEmpty() && confluence == null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )
            }
            confluence == null -> {
                Text(
                    text = "正在汇总日线 / 1小时 / 30分 / 5分级别结构…",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )
            }
            else -> {
                Text(
                    text = confluence.conclusion,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkSecondary,
                )
                LedgerHeaderRule()
                confluence.levels.forEachIndexed { index, level ->
                    val targetTf = Timeframe.fromRaw(level.timeframe)
                    val point = level.latestPoint
                    val levelStatus = point?.let {
                        "${it.label}${if (it.confirmed) " 已确认" else " 候选"}"
                    } ?: SignalRules.trend(level.trendType)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ClarityRadius.tile)
                            .clickable(role = Role.Button) { onJumpTimeframe(targetTf) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            StarkBadgeTag(text = level.timeframeLabel)
                            Text(
                                text = levelStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            level.lowerConfirmation?.let { conf ->
                                StarkBadgeTag(text = "印证 ${conf.shortTag}", inverted = true)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (point != null) {
                            val color = if (point.side == "buy") palette.positiveRed else palette.negativeGreen
                            FinancialNumber(
                                value = MarketFormat.price(point.price, decimals),
                                style = MaterialTheme.typography.labelLarge,
                                color = color,
                            )
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.inkMuted,
                            )
                        }
                    }
                    if (index < confluence.levels.lastIndex) {
                        LedgerHeaderRule()
                    }
                }
                Text(
                    text = "多级别印证仅表示跨周期结构方向一致与端点先后关系，不等于实盘同时触发。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted,
                )
            }
        }
    }
}

@Composable
fun RangeSection(
    snapshot: MarketSnapshot,
    decimals: Int,
    modifier: Modifier = Modifier,
) {
    val candles = snapshot.candles
    val latestZs = snapshot.analysis.zhongshu.lastOrNull()
    val rangeHigh = candles.maxOfOrNull { it.high }
    val rangeLow = candles.minOfOrNull { it.low }

    ClarityCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(title = "区间与最近中枢")
        FinancialMetricGroup(
            items = buildList {
                add(SummaryItem("样本K线", "${candles.size} 根"))
                add(
                    SummaryItem(
                        label = "区间最高",
                        value = rangeHigh?.let { MarketFormat.price(it, decimals) } ?: "—",
                    ),
                )
                add(
                    SummaryItem(
                        label = "区间最低",
                        value = rangeLow?.let { MarketFormat.price(it, decimals) } ?: "—",
                    ),
                )
                if (latestZs != null) {
                    add(
                        SummaryItem(
                            label = "中枢上沿 ZG",
                            value = MarketFormat.price(latestZs.high, decimals),
                        ),
                    )
                    add(
                        SummaryItem(
                            label = "中枢下沿 ZD",
                            value = MarketFormat.price(latestZs.low, decimals),
                        ),
                    )
                }
            },
            alignStart = true,
        )
    }
}
