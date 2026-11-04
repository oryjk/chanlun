import { useState } from 'react'
import brandMark from './assets/chan-guan-mark.svg'
import { formatPrice, getLatestPen, getPriceDecimals, getLatestZhongshu, getQuoteDirection, isStarMarket } from './domain/market.js'
import {
  evaluateActionConclusion,
  formatInvalidationDistance,
  getLatestPointsBySide,
  getPriceChangeFromPoint,
  pointLabel,
} from './domain/signals.js'
import { VIEW_TABS as TIMEFRAMES } from './domain/timeframes.js'
import AccountChip from './presentation/components/AccountChip.jsx'
import DragonLoader from './presentation/components/DragonLoader.jsx'
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
  const decimals = getPriceDecimals(selectedStock)
  const price = (value) => formatPrice(value, decimals)
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

  const navigateToView = (nextView) => {
    setView(nextView)
    window.scrollTo(0, 0)
  }

  const selectStock = (stock) => {
    if (view === 'rankings') {
      navigateToView('market')
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
        <a className="brand" href={import.meta.env.BASE_URL} aria-label="缠观首页">
          <img className="brand-mark" src={brandMark} width="42" height="30" alt="" aria-hidden="true" />
          <span className="brand-name">缠观</span>
          <span className="brand-tagline">先看结构，再看方向。</span>
        </a>
        <div className="topbar-actions">
          <AccountChip />
          <button
            className="icon-button"
            type="button"
            aria-label={view === 'rankings' ? '刷新榜单' : '刷新行情'}
            title={view === 'rankings' ? '刷新榜单' : '刷新行情'}
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
              <h1 id="instrument-name" title={selectedStock.name}>{selectedStock.name}</h1>
              <span className="symbol">{selectedStock.symbol}</span>
              {isStarMarket(selectedStock) && <span className="board-tag">科创</span>}
              <span className="market-label">{selectedStock.market}</span>
            </div>
            <div className="quote-row">
              <strong className={direction}>{summary ? price(summary.latest) : '—'}</strong>
              <span className={`quote-change ${direction}`}>
                {summary ? `${direction === 'up' ? '+' : ''}${price(summary.change)}  ${direction === 'up' ? '+' : ''}${summary.change_percent}%` : '等待行情'}
              </span>
            </div>
          </div>
          <div className="session-meta">
            <span>分析周期</span>
            <strong>{TIMEFRAMES.find((item) => item.value === timeframe)?.label}</strong>
          </div>
        </section>

        <section className="stock-tools" aria-label="股票与 ETF 查询">
          <StockSearch selected={selectedStock} onSelect={selectStock} searchStocks={useCases.searchStocks} />
          <p>可查 A 股、港股、美股的股票和 ETF</p>
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
          {isRealtime ? <RealtimeQuotePanel quote={quote} series={series} loading={quoteLoading} decimals={decimals} /> : (
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
                  decimals={decimals}
                />
              )}
              {loading && (
                <div className="chart-state">
                  <DragonLoader title="游龙识势中" label="正在获取行情并解析结构…" />
                </div>
              )}
              {!loading && !snapshot && <div className="chart-state">暂无图表数据，请重新查询</div>}
            </div>
            <div className="chart-legend">
              <span><i className="legend-candle up-bg" />上涨</span>
              <span><i className="legend-candle down-bg" />下跌</span>
              <span className="chart-hint">拖动查看，双指或滚轮缩放</span>
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

            {snapshot?.guidance && (() => {
              const signalPoints = snapshot.signals?.points || []
              const latestPoint = signalPoints.length > 0 ? signalPoints[signalPoints.length - 1] : null
              const { buyPoint, sellPoint } = getLatestPointsBySide(signalPoints)
              const conclusion = evaluateActionConclusion({
                bias: snapshot.guidance.bias,
                latestPrice: summary?.latest,
                invalidationPrice: snapshot.guidance.invalidation_price,
                latestPoint,
              })
              const buyChange = getPriceChangeFromPoint(buyPoint, summary?.latest)
              const sellChange = getPriceChangeFromPoint(sellPoint, summary?.latest)
              return (
                <section className={`guidance-card ${snapshot.guidance.bias}`} aria-label="结构观察结论">
                  <div className="guidance-heading">
                    <span>结构观察结论<TermInfo {...GLOSSARY.guidance} /></span>
                    <strong>{conclusion.label}</strong>
                  </div>
                  <p className="guidance-conclusion-summary">
                    {GUIDANCE_LABELS[snapshot.guidance.bias]}，{conclusion.summary}
                  </p>
                  {(buyChange || sellChange) && (
                    <div className="guidance-point-changes">
                      {buyChange && (
                        <p>
                          <span>
                            最近{pointLabel(buyPoint)} {buyChange.date} ({price(buyPoint.price)})
                            <i className={`point-confirm-tag ${buyChange.confirmed ? 'confirmed' : 'pending'}`}>
                              {buyChange.statusLabel}
                            </i>
                          </span>
                          <b>现价较该日 <em className={buyChange.percent >= 0 ? 'up' : 'down'}>{buyChange.formatted}</em></b>
                        </p>
                      )}
                      {sellChange && (
                        <p>
                          <span>
                            最近{pointLabel(sellPoint)} {sellChange.date} ({price(sellPoint.price)})
                            <i className={`point-confirm-tag ${sellChange.confirmed ? 'confirmed' : 'pending'}`}>
                              {sellChange.statusLabel}
                            </i>
                          </span>
                          <b>现价较该日 <em className={sellChange.percent >= 0 ? 'up' : 'down'}>{sellChange.formatted}</em></b>
                        </p>
                      )}
                    </div>
                  )}
                  <ul>
                    {snapshot.guidance.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                  </ul>
                  {Number.isFinite(snapshot.guidance.invalidation_price) && (
                    <p className="guidance-invalidation">
                      <span>失效参考价<TermInfo {...GLOSSARY.invalidation} /></span>
                      <b>
                        {formatInvalidationDistance(
                          snapshot.guidance.bias,
                          summary?.latest,
                          snapshot.guidance.invalidation_price,
                          price(snapshot.guidance.invalidation_price),
                        )}
                      </b>
                    </p>
                  )}
                  <small>仅供结构研究，不构成买卖建议</small>
                </section>
              )
            })()}

            {snapshot && (
              <SignalPanel
                signals={snapshot.signals}
                timeframe={snapshot.timeframe}
                timeframeLabel={snapshot.timeframe_label}
                latestPrice={summary?.latest}
                confluence={confluence}
                confluenceLoading={confluenceLoading}
                confluenceError={confluenceError}
                decimals={decimals}
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
                <small>{lastPen ? (lastPen.confirmed ? '已确认' : '候选，可能修正') : ''}{lastPen && <TermInfo {...GLOSSARY.confirmed} />}</small>
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
                    <span>{price(lastPen.start_price)}</span><i><em className={lastPen.direction} /></i><span>{price(lastPen.end_price)}</span>
                  </div>
                </>
              ) : <p className="empty-copy">当前样本尚未形成有效的笔</p>}
            </div>

            <div className="structure-detail zone-detail">
              <div className="detail-title">
                <span>{snapshot?.timeframe_label || ''}笔中枢<TermInfo {...GLOSSARY.timeframeZone} /></span>
                <small>{lastZone ? (lastZone.confirmed ? '延伸已结束' : '候选，仍可延伸') : ''}</small>
              </div>
              {lastZone ? (
                <>
                  <div className="zone-range">
                    <div><span>核心上沿 ZG<TermInfo {...GLOSSARY.coreHigh} /></span><strong>{price(lastZone.high)}</strong></div>
                    <div><span>核心下沿 ZD<TermInfo {...GLOSSARY.coreLow} /></span><strong>{price(lastZone.low)}</strong></div>
                  </div>
                  <p><span className="zone-metric-label">核心宽度<TermInfo {...GLOSSARY.coreWidth} /></span><b>{price(lastZone.high - lastZone.low)}</b><span className="zone-time">{formatTime(lastZone.end_time)}</span></p>
                  <p><span className="zone-metric-label">波动范围<TermInfo {...GLOSSARY.range} /></span><b>{price(lastZone.dd)}～{price(lastZone.gg)}</b></p>
                </>
              ) : <p className="empty-copy">连续三笔尚无价格重叠区间</p>}
            </div>

            <div className="market-range">
              <div><span>区间最高</span><strong>{summary ? price(summary.high) : '—'}</strong></div>
              <div><span>区间最低</span><strong>{summary ? price(summary.low) : '—'}</strong></div>
              <div><span>近 20 根量能</span><strong>{summary ? formatVolume(summary.volume) : '—'}</strong></div>
            </div>
          </aside>
            </>
          )}
        </div>
          </>
        )}
      </main>

      <nav className="bottom-tabbar" aria-label="主导航">
        <button type="button" aria-current={view === 'market' ? 'page' : undefined} onClick={() => navigateToView('market')}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 4v16h16M7 14l4-4 4 3 5-7" />
          </svg>
          <span>行情</span>
        </button>
        <button type="button" aria-current={view === 'rankings' ? 'page' : undefined} onClick={() => navigateToView('rankings')}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 20V11h5v9M9 20V4h6v16M15 20v-7h5v7M3 20h18" />
          </svg>
          <span>榜单</span>
        </button>
      </nav>
    </div>
  )
}

export default App
