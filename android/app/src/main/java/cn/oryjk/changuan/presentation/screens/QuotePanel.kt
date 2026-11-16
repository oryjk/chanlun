package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.LocalChanPalette
import cn.oryjk.changuan.presentation.charts.RealtimeChartCanvas
import cn.oryjk.changuan.presentation.designsystem.SectionTitle
import cn.oryjk.changuan.presentation.designsystem.StarkBadgeTag
import cn.oryjk.changuan.presentation.designsystem.tabular

@Composable
fun QuotePanel(
    stock: Stock,
    quote: MarketQuote?,
    points: List<QuotePoint>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChanPalette.current
    val decimals = stock.decimals
    val session = MarketTime.session(stock)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle(
            title = "分时走势",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StarkBadgeTag(text = session.label)
                    StarkBadgeTag(text = "${points.size} 个采样")
                }
            },
        )
        if (quote == null && points.isEmpty()) {
            Text(
                text = "正在等待实时报价与分时采样数据…",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkSecondary,
            )
        } else {
            RealtimeChartCanvas(
                stock = stock,
                quote = quote,
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp),
            )
            if (points.size < 2) {
                Text(
                    text = "盘中每次轮询将自动记录分时轨迹，非交易时段展示最近收盘价参考线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val prevClose = quote?.previousClose?.takeIf { it > 0.0 }
                    Text(
                        text = if (prevClose != null) {
                            "昨收参考 ${MarketFormat.price(prevClose, decimals)}"
                        } else {
                            "分时采样曲线"
                        },
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = palette.inkMuted,
                    )
                    val lastPointTime = points.lastOrNull()?.time
                    Text(
                        text = if (lastPointTime != null) {
                            MarketTime.formatDate(lastPointTime, "HH:mm:ss", session.zone)
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = palette.inkMuted,
                    )
                }
            }
        }
    }
}
