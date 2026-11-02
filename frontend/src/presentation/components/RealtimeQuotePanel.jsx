import { isMarketOpen } from '../../domain/marketHours.js'
import RealtimeChart from './RealtimeChart.jsx'

function formatPrice(value) {
  return Number(value).toFixed(3)
}

function formatVolume(value) {
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿股`
  if (value >= 10000) return `${(value / 10000).toFixed(1)} 万股`
  return `${value} 股`
}

function formatAmount(value) {
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿元`
  if (value >= 10000) return `${(value / 10000).toFixed(1)} 万元`
  return `${value} 元`
}

function formatQuoteTime(value) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export default function RealtimeQuotePanel({ quote, series, loading }) {
  const direction = quote?.change >= 0 ? 'up' : 'down'

  return (
    <section className="realtime-card" aria-label="实时行情">
      <div className="realtime-heading">
        <div>
          <h2>实时行情</h2>
          {!isMarketOpen(quote?.secid || series.secid) && <span className="realtime-status"><i />非交易时段</span>}
        </div>
        {quote && <time dateTime={quote.time}>更新于 {formatQuoteTime(quote.time)}</time>}
      </div>

      {quote ? (
        <>
          <div className="realtime-price">
            <strong className={direction}>{formatPrice(quote.latest)}</strong>
            <span className={direction}>
              {quote.change >= 0 ? '+' : ''}{formatPrice(quote.change)}
              <b>{quote.change >= 0 ? '+' : ''}{quote.change_percent}%</b>
            </span>
          </div>
          <div className="realtime-chart">
            {series.points.length > 0 ? (
              <RealtimeChart points={series.points} secid={series.secid || quote.secid} previousClose={quote.previous_close} />
            ) : (
              <div className="realtime-chart-empty">正在积累实时走势数据…</div>
            )}
          </div>
          <div className="realtime-metrics">
            <div><span>今开</span><strong>{formatPrice(quote.open)}</strong></div>
            <div><span>最高</span><strong>{formatPrice(quote.high)}</strong></div>
            <div><span>最低</span><strong>{formatPrice(quote.low)}</strong></div>
            <div><span>昨收</span><strong>{formatPrice(quote.previous_close)}</strong></div>
            <div><span>成交量</span><strong>{formatVolume(quote.volume)}</strong></div>
            <div><span>成交额</span><strong>{formatAmount(quote.amount)}</strong></div>
          </div>
        </>
      ) : (
        <div className="realtime-loading">
          {loading ? <><span className="loader" />正在获取实时报价…</> : '暂无实时报价'}
        </div>
      )}
    </section>
  )
}
