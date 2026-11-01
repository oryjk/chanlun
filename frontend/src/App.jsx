import { useState } from 'react'
import { DEFAULT_STOCK, getLatestPen, getLatestZhongshu, getQuoteDirection } from './domain/market.js'
import { TIMEFRAMES } from './domain/timeframes.js'
import MarketChart from './presentation/components/MarketChart.jsx'
import StockSearch from './presentation/components/StockSearch.jsx'
import { useMarketSnapshot } from './presentation/hooks/useMarketSnapshot.js'

const LAYERS = [
  { key: 'fractals', label: '分型' },
  { key: 'pens', label: '笔' },
  { key: 'zhongshu', label: '中枢' },
]

function formatPrice(value) {
  return Number(value).toFixed(3)
}

function formatVolume(value) {
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿`
  if (value >= 10000) return `${(value / 10000).toFixed(1)} 万`
  return String(value)
}

function formatTime(value, includeDate = true) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  const day = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return includeDate ? `${day} ${time}` : time
}

function RefreshIcon({ spinning }) {
  return (
    <svg className={spinning ? 'spin' : ''} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M20 11a8 8 0 0 0-14.9-4M4 3v5h5M4 13a8 8 0 0 0 14.9 4M20 21v-5h-5" />
    </svg>
  )
}

function App({ useCases }) {
  const [selectedStock, setSelectedStock] = useState(DEFAULT_STOCK)
  const [timeframe, setTimeframe] = useState('30m')
  const [reloadKey, setReloadKey] = useState(0)
  const [layers, setLayers] = useState({ fractals: true, pens: true, zhongshu: true })
  const { snapshot, loading, error } = useMarketSnapshot(
    useCases.getMarketSnapshot,
    selectedStock,
    timeframe,
    reloadKey,
  )

  const selectStock = (stock) => {
    if (stock.secid === selectedStock.secid) {
      setReloadKey((value) => value + 1)
      return
    }
    setSelectedStock(stock)
  }

  const summary = snapshot?.summary
  const analysis = snapshot?.analysis
  const lastPen = getLatestPen(analysis)
  const lastZone = getLatestZhongshu(analysis)
  const direction = getQuoteDirection(summary)

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="缠观首页">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <span className="brand-name">缠观<small>CHAN THEORY</small></span>
        </a>
        <div className="topbar-actions">
          <span className="data-status"><i />公开行情</span>
          <button
            className="icon-button"
            type="button"
            aria-label="刷新行情"
            title="刷新行情"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            <RefreshIcon spinning={loading} />
          </button>
        </div>
      </header>

      <main>
        <section className="market-header" aria-labelledby="instrument-name">
          <div className="market-identity">
            <p className="eyebrow">STRUCTURE OVER NOISE</p>
            <div className="instrument-row">
              <h1 id="instrument-name">{selectedStock.name}</h1>
              <span className="symbol">{selectedStock.symbol} · {selectedStock.market}</span>
            </div>
            <div className="quote-row">
              <strong className={direction}>{summary ? formatPrice(summary.latest) : '—'}</strong>
              <span className={`quote-change ${direction}`}>
                {summary ? `${direction === 'up' ? '+' : ''}${formatPrice(summary.change)}  ${direction === 'up' ? '+' : ''}${summary.change_percent}%` : '等待行情'}
              </span>
            </div>
          </div>
          <div className="session-meta">
            <span>分析周期</span>
            <strong>{snapshot?.timeframe_label || TIMEFRAMES.find((item) => item.value === timeframe)?.label}</strong>
            <small>{snapshot ? `${snapshot.candles.length} 根 K 线 · ${snapshot.instrument.currency || ''}` : '正在载入'}</small>
          </div>
        </section>

        <section className="stock-tools" aria-label="股票与 ETF 查询">
          <StockSearch selected={selectedStock} onSelect={selectStock} searchStocks={useCases.searchStocks} />
          <p>支持 股票 / ETF<span>A 股 · 港股 · 美股</span></p>
        </section>

        <nav className="timeframe-tabs" aria-label="K 线周期" role="tablist">
          {TIMEFRAMES.map((item) => (
            <button
              key={item.value}
              type="button"
              role="tab"
              aria-selected={timeframe === item.value}
              className={timeframe === item.value ? 'active' : ''}
              onClick={() => setTimeframe(item.value)}
            >
              <span>{item.label}</span><small>{item.short}</small>
            </button>
          ))}
        </nav>

        {error && (
          <div className="error-banner" role="alert">
            <span>{error}</span>
            <button type="button" onClick={() => setReloadKey((value) => value + 1)}>重试</button>
          </div>
        )}

        <div className="workspace">
          <section className="chart-card" aria-label="量价与缠论结构图">
            <div className="card-header">
              <div><h2>量价结构</h2><p>PRICE · VOLUME · CHAN</p></div>
              <div className="layer-controls" aria-label="图层控制">
                {LAYERS.map((layer) => (
                  <button
                    key={layer.key}
                    type="button"
                    className={layers[layer.key] ? `layer-${layer.key} enabled` : ''}
                    aria-pressed={layers[layer.key]}
                    onClick={() => setLayers((current) => ({ ...current, [layer.key]: !current[layer.key] }))}
                  >
                    <i />{layer.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="chart-wrap">
              {snapshot && (
                <MarketChart
                  candles={snapshot.candles}
                  analysis={snapshot.analysis}
                  timeframe={timeframe}
                  layers={layers}
                />
              )}
              {loading && <div className="chart-state"><span className="loader" />正在获取行情并解析结构…</div>}
              {!loading && !snapshot && <div className="chart-state">暂无图表数据，请重新查询</div>}
            </div>
            <div className="chart-legend">
              <span><i className="legend-candle up-bg" />上涨</span>
              <span><i className="legend-candle down-bg" />下跌</span>
              <span className="chart-hint">拖动浏览 · 滚轮或双指缩放</span>
            </div>
          </section>

          <aside className="insight-panel">
            <div className="panel-heading">
              <div><p className="eyebrow">STRUCTURE</p><h2>结构解读</h2></div>
              <span className={`trend-badge ${analysis?.trend || ''}`}>
                {analysis?.trend === 'up' ? '向上笔' : analysis?.trend === 'down' ? '向下笔' : '等待结构'}
              </span>
            </div>

            <div className="structure-stats">
              <div><span>分型</span><strong>{analysis?.fractals.length ?? '—'}</strong><small>已确认</small></div>
              <div><span>笔</span><strong>{analysis?.pens.length ?? '—'}</strong><small>当前周期</small></div>
              <div><span>中枢</span><strong>{analysis?.zhongshu.length ?? '—'}</strong><small>已识别</small></div>
            </div>

            <div className="structure-detail">
              <div className="detail-title"><span>最近一笔</span><small>LAST PEN</small></div>
              {lastPen ? (
                <>
                  <div className="pen-direction">
                    <span className={lastPen.direction === 'up' ? 'direction-icon up' : 'direction-icon down'}>
                      {lastPen.direction === 'up' ? '↗' : '↘'}
                    </span>
                    <div><strong>{lastPen.direction === 'up' ? '向上延伸' : '向下延伸'}</strong><small>{formatTime(lastPen.end_time)}</small></div>
                    <b className={lastPen.direction === 'up' ? 'up' : 'down'}>
                      {lastPen.direction === 'up' ? '+' : ''}{((lastPen.end_price / lastPen.start_price - 1) * 100).toFixed(2)}%
                    </b>
                  </div>
                  <div className="price-range">
                    <span>{formatPrice(lastPen.start_price)}</span><i><em className={lastPen.direction} /></i><span>{formatPrice(lastPen.end_price)}</span>
                  </div>
                </>
              ) : <p className="empty-copy">当前样本尚未形成有效的笔</p>}
            </div>

            <div className="structure-detail zone-detail">
              <div className="detail-title"><span>最近中枢</span><small>ZHONGSHU</small></div>
              {lastZone ? (
                <>
                  <div className="zone-range">
                    <div><span>上沿 ZG</span><strong>{formatPrice(lastZone.high)}</strong></div>
                    <div><span>下沿 ZD</span><strong>{formatPrice(lastZone.low)}</strong></div>
                  </div>
                  <p>区间宽度 <b>{formatPrice(lastZone.high - lastZone.low)}</b><span>{formatTime(lastZone.end_time)}</span></p>
                </>
              ) : <p className="empty-copy">连续三笔尚无价格重叠区间</p>}
            </div>

            <div className="market-range">
              <div><span>区间最高</span><strong>{summary ? formatPrice(summary.high) : '—'}</strong></div>
              <div><span>区间最低</span><strong>{summary ? formatPrice(summary.low) : '—'}</strong></div>
              <div><span>近 20 根量能</span><strong>{summary ? formatVolume(summary.volume) : '—'}</strong></div>
            </div>
          </aside>
        </div>

        <footer>
          <span className="footer-mark">缠</span>
          <p><strong>先看结构，再看方向。</strong><br />{snapshot?.instrument.data_source || '公开市场行情'}；缠论采用简化算法，仅供技术研究，不构成投资建议。</p>
          <span className="demo-note">PUBLIC MARKET DATA<br />东方财富搜索 · YAHOO 行情</span>
        </footer>
      </main>
    </div>
  )
}

export default App
