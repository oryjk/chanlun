import { useState } from 'react'
import { getLatestPen, getLatestZhongshu, getQuoteDirection } from './domain/market.js'
import { VIEW_TABS as TIMEFRAMES } from './domain/timeframes.js'
import AccountChip from './presentation/components/AccountChip.jsx'
import MarketChart from './presentation/components/MarketChart.jsx'
import RankingPage from './presentation/components/RankingPage.jsx'
import RealtimeQuotePanel from './presentation/components/RealtimeQuotePanel.jsx'
import SignalPanel from './presentation/components/SignalPanel.jsx'
import StockSearch from './presentation/components/StockSearch.jsx'
import TermInfo from './presentation/components/TermInfo.jsx'
import { GLOSSARY } from './presentation/glossary.js'
import { useMarketRoute } from './presentation/hooks/useMarketRoute.js'
import { useMarketSnapshot } from './presentation/hooks/useMarketSnapshot.js'
import { useRealtimeQuote } from './presentation/hooks/useRealtimeQuote.js'
import { useSignalConfluence } from './presentation/hooks/useSignalConfluence.js'

const LAYERS = [
  { key: 'fractals', label: '分型' },
  { key: 'pens', label: '笔' },
  { key: 'segments', label: '线段' },
  { key: 'zhongshu', label: '笔中枢' },
  { key: 'points', label: '买卖点' },
]

const GUIDANCE_LABELS = {
  bullish: '偏多观察',
  bearish: '偏空观察',
  neutral: '暂时等待',
}

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
  const { selectedStock, setSelectedStock, timeframe, setTimeframe, view, setView } = useMarketRoute(useCases.searchStocks)
  const [reloadKey, setReloadKey] = useState(0)
  const [layers, setLayers] = useState({ fractals: true, pens: true, segments: true, zhongshu: true, points: true })
  const isRealtime = timeframe === 'realtime'
  const { snapshot, loading: snapshotLoading, error: snapshotError } = useMarketSnapshot(
    useCases.getMarketSnapshot,
    selectedStock,
    timeframe,
    reloadKey,
    view === 'market' && !isRealtime,
  )
  const { quote, series, loading: quoteLoading, refreshing, error: quoteError } = useRealtimeQuote(
    useCases.getQuote,
    useCases.getQuoteSeries,
    useCases.recordQuoteSample,
    selectedStock.secid,
    view === 'market' && isRealtime,
    reloadKey,
  )
  const { confluence, loading: confluenceLoading, error: confluenceError } = useSignalConfluence(
    useCases.getSignalConfluence,
    selectedStock.secid,
    view === 'market' && !isRealtime,
    reloadKey,
  )

  const selectStock = (stock) => {
    if (view === 'rankings') {
      setView('market')
      setTimeframe('1d')
    }
    if (stock.secid === selectedStock.secid) {
      setReloadKey((value) => value + 1)
      return
    }
    setSelectedStock(stock)
  }

  const summary = isRealtime && quote ? quote : snapshot?.summary
  const loading = isRealtime ? quoteLoading : snapshotLoading
  const error = isRealtime ? quoteError : snapshotError
  const analysis = snapshot?.analysis
  const lastPen = getLatestPen(analysis)
  const lastZone = getLatestZhongshu(analysis)
  const direction = getQuoteDirection(summary)

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="缠观首页">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <span className="brand-name">缠观</span>
          <span className="brand-tagline">先看结构，再看方向。</span>
        </a>
        <nav className="view-nav" aria-label="页面切换">
          <button type="button" className={view === 'market' ? 'active' : ''} onClick={() => setView('market')}>行情</button>
          <button type="button" className={view === 'rankings' ? 'active' : ''} onClick={() => setView('rankings')}>榜单</button>
        </nav>
        <div className="topbar-actions">
          <span className="data-status"><i />公开行情</span>
          <AccountChip />
          <button
            className="icon-button"
            type="button"
            aria-label="刷新行情"
            title="刷新行情"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            <RefreshIcon spinning={loading || refreshing} />
          </button>
        </div>
      </header>

      <main>
        {view === 'rankings' ? (
          <RankingPage getRankings={useCases.getRankings} onSelectStock={selectStock} reloadKey={reloadKey} />
        ) : (
          <>
        <section className="market-header" aria-labelledby="instrument-name">
          <div className="market-identity">
            <div className="instrument-row">
              <h1 id="instrument-name">{selectedStock.name}</h1>
              <span className="symbol">{selectedStock.symbol}</span>
              <span className="market-label">{selectedStock.market}</span>
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
            <strong>{TIMEFRAMES.find((item) => item.value === timeframe)?.label}</strong>
            <small>{isRealtime ? '' : loading && snapshot ? '正在更新结构…' : snapshot ? `${snapshot.candles.length} 根 K 线 · ${snapshot.instrument.data_source}` : '正在载入'}</small>
          </div>
        </section>

        <section className="stock-tools" aria-label="股票与 ETF 查询">
          <StockSearch selected={selectedStock} onSelect={selectStock} searchStocks={useCases.searchStocks} />
          <p>支持 股票 / ETF<span>A 股 · 港股 · 美股</span></p>
        </section>

        <nav
          className="timeframe-tabs"
          aria-label="K 线周期"
          role="tablist"
          data-active={TIMEFRAMES.findIndex((item) => item.value === timeframe)}
        >
          <span className="timeframe-slider" aria-hidden="true" />
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

        <div className={`workspace ${isRealtime ? 'realtime-workspace' : ''}`}>
          {isRealtime ? <RealtimeQuotePanel quote={quote} series={series} loading={quoteLoading} /> : (
            <>
          <section className="chart-card" aria-label="量价与缠论结构图">
            <div className="card-header">
              <div><h2>量价结构</h2></div>
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
                  macd={snapshot.macd}
                  signals={snapshot.signals}
                  timeframe={snapshot.timeframe}
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
              <div><h2>结构解读</h2></div>
              <span className={`trend-badge ${analysis?.trend || ''}`}>
                {analysis?.trend === 'up' ? '向上笔' : analysis?.trend === 'down' ? '向下笔' : '等待结构'}
                <TermInfo {...GLOSSARY.upPen} />
              </span>
            </div>

            {snapshot?.guidance && (
              <section className={`guidance-card ${snapshot.guidance.bias}`} aria-label="结构观察结论">
                <div className="guidance-heading">
                  <span>结构观察结论<TermInfo {...GLOSSARY.guidance} /></span>
                  <strong>{GUIDANCE_LABELS[snapshot.guidance.bias]}</strong>
                </div>
                <ul>
                  {snapshot.guidance.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                </ul>
                {Number.isFinite(snapshot.guidance.invalidation_price) && (
                  <p className="guidance-invalidation">
                    <span>失效参考价<TermInfo {...GLOSSARY.invalidation} /></span>
                    <b>{formatPrice(snapshot.guidance.invalidation_price)}</b>
                  </p>
                )}
                <small>仅供结构研究，不构成买卖建议</small>
              </section>
            )}

            {snapshot && (
              <SignalPanel
                signals={snapshot.signals}
                timeframeLabel={snapshot.timeframe_label}
                confluence={confluence}
                confluenceLoading={confluenceLoading}
                confluenceError={confluenceError}
              />
            )}

            <div className="structure-stats">
              <div><span>分型<TermInfo {...GLOSSARY.fractal} /></span><strong>{analysis?.fractals.length ?? '—'}</strong><small>入选端点</small></div>
              <div><span>笔<TermInfo {...GLOSSARY.pen} /></span><strong>{analysis?.pens.length ?? '—'}</strong><small>末笔为候选</small></div>
              <div><span>笔中枢<TermInfo {...GLOSSARY.zhongshu} /></span><strong>{analysis?.zhongshu.length ?? '—'}</strong><small>核心不收缩</small></div>
            </div>

            <div className="structure-detail">
              <div className="detail-title">
                <span>最近一笔<TermInfo {...GLOSSARY.pen} /></span>
                <small>{lastPen ? (lastPen.confirmed ? '已确认' : '候选 · 可能修正') : ''}{lastPen && <TermInfo {...GLOSSARY.confirmed} />}</small>
              </div>
              {lastPen ? (
                <>
                  <div className="pen-direction">
                    <span className={lastPen.direction === 'up' ? 'direction-icon up' : 'direction-icon down'}>
                      {lastPen.direction === 'up' ? '↗' : '↘'}
                    </span>
                    <div><strong>{lastPen.direction === 'up' ? '向上笔' : '向下笔'}<TermInfo {...GLOSSARY.upPen} /></strong><small>{formatTime(lastPen.end_time)}</small></div>
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
              <div className="detail-title">
                <span>{snapshot?.timeframe_label || ''}笔中枢<TermInfo {...GLOSSARY.timeframeZone} /></span>
                <small>{lastZone ? (lastZone.confirmed ? '延伸已结束' : '候选 · 仍可延伸') : ''}</small>
              </div>
              {lastZone ? (
                <>
                  <div className="zone-range">
                    <div><span>核心上沿 ZG<TermInfo {...GLOSSARY.coreHigh} /></span><strong>{formatPrice(lastZone.high)}</strong></div>
                    <div><span>核心下沿 ZD<TermInfo {...GLOSSARY.coreLow} /></span><strong>{formatPrice(lastZone.low)}</strong></div>
                  </div>
                  <p><span className="zone-metric-label">核心宽度<TermInfo {...GLOSSARY.coreWidth} /></span><b>{formatPrice(lastZone.high - lastZone.low)}</b><span className="zone-time">{formatTime(lastZone.end_time)}</span></p>
                  <p><span className="zone-metric-label">波动范围<TermInfo {...GLOSSARY.range} /></span><b>{formatPrice(lastZone.dd)}～{formatPrice(lastZone.gg)}</b></p>
                </>
              ) : <p className="empty-copy">连续三笔尚无价格重叠区间</p>}
            </div>

            <div className="market-range">
              <div><span>区间最高</span><strong>{summary ? formatPrice(summary.high) : '—'}</strong></div>
              <div><span>区间最低</span><strong>{summary ? formatPrice(summary.low) : '—'}</strong></div>
              <div><span>近 20 根量能</span><strong>{summary ? formatVolume(summary.volume) : '—'}</strong></div>
            </div>
          </aside>
            </>
          )}
        </div>
          </>
        )}
      </main>
    </div>
  )
}

export default App
