import { formatPrice, formatSignedPercent } from '../../domain/market.ts'
import { isMarketOpen } from '../../domain/marketHours.ts'
import type { QuoteSeries, RealtimeQuote, ResolvedTheme } from '../../domain/types.ts'
import DragonLoader from './DragonLoader.tsx'
import RealtimeChart from './RealtimeChart.tsx'

function formatVolume(value: number): string {
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿股`
  if (value >= 10000) return `${(value / 10000).toFixed(1)} 万股`
  return `${value} 股`
}

function formatAmount(value: number): string {
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿元`
  if (value >= 10000) return `${(value / 10000).toFixed(1)} 万元`
  return `${value} 元`
}

function formatQuoteTime(value: string): string {
  const date = new Date(value)
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export interface RealtimeQuotePanelProps {
  quote: RealtimeQuote | null
  series: QuoteSeries
  loading: boolean
  decimals: number
  theme?: ResolvedTheme
}

export default function RealtimeQuotePanel({
  quote,
  series,
  loading,
  decimals,
  theme = 'light',
}: RealtimeQuotePanelProps) {
  const pct = quote ? formatSignedPercent(quote.change_percent) : { text: '—', tone: 'flat' as const }
  const direction = pct.tone === 'flat' ? 'flat' : (quote && quote.change > 0 ? 'up' : 'down')

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
            <strong className={direction}>{formatPrice(quote.latest, decimals)}</strong>
            <span className={direction}>
              {Math.abs(quote.change) < 1e-6 ? formatPrice(0, decimals) : `${quote.change > 0 ? '+' : ''}${formatPrice(quote.change, decimals)}`}
              <b>{pct.text}</b>
            </span>
          </div>
          <div className="realtime-chart">
            {series.points.length > 0 ? (
              <RealtimeChart
                points={series.points}
                secid={series.secid || quote.secid}
                previousClose={quote.previous_close}
                decimals={decimals}
                theme={theme}
              />
            ) : (
              <div className="realtime-chart-empty">正在积累实时走势数据…</div>
            )}
          </div>
          <div className="realtime-metrics">
            <div><span>今开</span><strong>{formatPrice(quote.open, decimals)}</strong></div>
            <div><span>最高</span><strong>{formatPrice(quote.high, decimals)}</strong></div>
            <div><span>最低</span><strong>{formatPrice(quote.low, decimals)}</strong></div>
            <div><span>昨收</span><strong>{formatPrice(quote.previous_close, decimals)}</strong></div>
            <div><span>成交量</span><strong>{formatVolume(quote.volume)}</strong></div>
            <div><span>成交额</span><strong>{formatAmount(quote.amount)}</strong></div>
          </div>
        </>
      ) : (
        <div className="realtime-loading">
          {loading ? <DragonLoader title="游龙探盘中" label="正在获取实时报价…" /> : '暂无实时报价'}
        </div>
      )}
    </section>
  )
}
