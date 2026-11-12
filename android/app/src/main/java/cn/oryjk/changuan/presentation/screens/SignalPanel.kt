package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.SignalRules
import cn.oryjk.changuan.presentation.ChanTheme
import java.util.Locale

@Composable
fun SignalPanel(
    snapshot: MarketSnapshot,
    decimals: Int,
    modifier: Modifier = Modifier,
) {
    fun price(value: Double?): String = MarketFormat.price(value, decimals)

    val latestPoint = snapshot.signals.points.lastOrNull()
    val conclusion = SignalRules.conclusion(
        bias = snapshot.guidance.bias,
        latest = snapshot.summary.latest,
        point = latestPoint,
        quality = snapshot.signalQuality,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "结构解读",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChanTheme.TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = SignalRules.trend(snapshot.signals.trendType),
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
            )
        }

        Text(
            text = conclusion.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChanTheme.TextPrimary,
        )
        Text(
            text = conclusion.detail,
            fontSize = 13.sp,
            color = ChanTheme.TextSecondary,
        )

        snapshot.guidance.reasons.forEach { reason ->
            Text(
                text = reason,
                fontSize = 13.sp,
                color = ChanTheme.TextPrimary,
            )
        }

        snapshot.analysis.pens.lastOrNull()?.let { pen ->
            val dirText = if (pen.direction == "up") "向上" else "向下"
            val stateText = if (pen.confirmed) "已确认" else "候选"
            Text(
                text = "最近一笔 $dirText  $stateText  ${price(pen.startPrice)} → ${price(pen.endPrice)}",
                fontSize = 13.sp,
                color = ChanTheme.TextPrimary,
            )
        }

        snapshot.analysis.zhongshu.lastOrNull()?.let { zone ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "当前周期笔中枢 ${zone.id}  ${if (zone.confirmed) "已确认" else "候选"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChanTheme.TextPrimary,
                )
                Text(
                    text = "核心下沿 ${price(zone.low)}  核心上沿 ${price(zone.high)}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ChanTheme.TextPrimary,
                )
                Text(
                    text = "核心宽度 ${price(zone.high - zone.low)}  波动范围 ${price(zone.dd)}–${price(zone.gg)}",
                    fontSize = 13.sp,
                    color = ChanTheme.TextSecondary,
                )
            }
        }

        snapshot.signalQuality?.let { quality ->
            HorizontalDivider(color = ChanTheme.Line)
            quality.invalidationPrice?.let { ref ->
                Text(
                    text = "该信号失效参考 ${price(ref)}",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = ChanTheme.TextPrimary,
                )
            }
            quality.firstReferenceCrossedAt?.let { crossedAt ->
                Text(
                    text = "首次收盘越界 ${MarketTime.formatDate(crossedAt)}  收盘 ${price(quality.firstReferenceCrossedClose)}",
                    fontSize = 13.sp,
                    color = ChanTheme.Up,
                )
            }
            quality.barsSincePivot?.let { bars ->
                Text(
                    text = "距结构端点 $bars 根已收盘 K 线",
                    fontSize = 13.sp,
                    color = ChanTheme.TextPrimary,
                )
            }
            quality.historyCheckedThrough?.let { checkedThrough ->
                Text(
                    text = "历史核对至 ${MarketTime.formatDate(checkedThrough, "yyyy-MM-dd HH:mm")}",
                    fontSize = 12.sp,
                    color = ChanTheme.TextSecondary,
                )
            }
            Text(
                text = SignalRules.historySummary(quality, decimals),
                fontSize = 13.sp,
                color = ChanTheme.TextPrimary,
            )
            if (quality.referenceTouched == true) {
                Text(
                    text = "本根 K 线盘中已触及参考，收盘越界另行核对",
                    fontSize = 13.sp,
                    color = ChanTheme.TextSecondary,
                )
            }

            val macdParts = buildList {
                quality.macdAreaRatio?.let { add(String.format(Locale.US, "MACD 面积比 %.2f", it)) }
                quality.pullbackMacdRatio?.let { add(String.format(Locale.US, "回抽动能比 %.2f", it)) }
            }
            if (macdParts.isNotEmpty()) {
                Text(
                    text = macdParts.joinToString("  "),
                    fontSize = 12.sp,
                    color = ChanTheme.TextSecondary,
                )
            }

            val volParts = buildList {
                quality.breakoutVolumeRatio?.let { add(String.format(Locale.US, "前段量比 %.2f", it)) }
                quality.pullbackVolumeRatio?.let { add(String.format(Locale.US, "回抽量比 %.2f", it)) }
            }
            if (volParts.isNotEmpty()) {
                Text(
                    text = volParts.joinToString("  "),
                    fontSize = 12.sp,
                    color = ChanTheme.TextSecondary,
                )
            }

            Text(
                text = "动能与量能为待验证证据，不代表成功概率",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
        }

        HorizontalDivider(color = ChanTheme.Line)
        Text(
            text = "最近买卖点",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ChanTheme.TextPrimary,
        )
        if (snapshot.signals.points.isEmpty()) {
            Text(
                text = "当前结构尚未出现买卖点",
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
            )
        } else {
            val fmt = if (snapshot.timeframe == "1d") "yyyy-MM-dd" else "MM-dd HH:mm"
            snapshot.signals.points.takeLast(4).asReversed().forEach { pt ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pt.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (pt.side == "buy") ChanTheme.Up else ChanTheme.Down,
                        )
                        Text(
                            text = "  ${if (pt.confirmed) "已确认" else "候选"}",
                            fontSize = 13.sp,
                            color = ChanTheme.TextSecondary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = price(pt.price),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = ChanTheme.TextPrimary,
                        )
                    }
                    Text(
                        text = "结构端点 ${MarketTime.formatDate(pt.time, fmt)}",
                        fontSize = 12.sp,
                        color = ChanTheme.TextSecondary,
                    )
                    if (pt.reason.isNotBlank()) {
                        Text(
                            text = pt.reason,
                            fontSize = 12.sp,
                            color = ChanTheme.TextSecondary,
                        )
                    }
                }
            }
        }

        snapshot.signals.divergences.takeLast(2).forEach { div ->
            val kindText = if (div.kind == "trend") "趋势" else "盘整"
            val dirText = if (div.direction == "bottom") "底" else "顶"
            val stateText = if (div.confirmed) "已确认" else "候选"
            Text(
                text = "${kindText}${dirText}背驰  $stateText",
                fontSize = 13.sp,
                color = ChanTheme.TextPrimary,
            )
        }

        Text(
            text = SignalRules.recognitionTimeSummary(snapshot.signalQuality),
            fontSize = 12.sp,
            color = ChanTheme.TextSecondary,
        )
    }
}
