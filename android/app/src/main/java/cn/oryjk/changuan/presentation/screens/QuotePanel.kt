package cn.oryjk.changuan.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.oryjk.changuan.domain.MarketFormat
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketTime
import cn.oryjk.changuan.domain.QuotePoint
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.presentation.ChanTheme
import cn.oryjk.changuan.presentation.charts.RealtimeChartCanvas

@Composable
fun QuotePanel(
    stock: Stock,
    quote: MarketQuote?,
    points: List<QuotePoint>,
    modifier: Modifier = Modifier,
) {
    fun price(value: Double?): String = MarketFormat.price(value, stock.decimals)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (quote != null) {
            val session = MarketTime.session(stock)
            Text(
                text = "报价时间 ${MarketTime.formatDate(quote.time, "yyyy-MM-dd HH:mm:ss", session.zone)} ${session.label}",
                fontSize = 12.sp,
                color = ChanTheme.TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "今开 ${price(quote.open)}  最高 ${price(quote.high)}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ChanTheme.TextPrimary,
                )
                Text(
                    text = "最低 ${price(quote.low)}  昨收 ${price(quote.previousClose)}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ChanTheme.TextPrimary,
                )
            }
            Text(
                text = "成交量 ${MarketFormat.volume(quote.volume)}  成交额 ${MarketFormat.volume(quote.amount)}",
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
            )
        } else {
            Text(
                text = "正在读取实时报价与分时序列",
                fontSize = 13.sp,
                color = ChanTheme.TextSecondary,
            )
        }

        RealtimeChartCanvas(
            stock = stock,
            quote = quote,
            points = points,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
    }
}
