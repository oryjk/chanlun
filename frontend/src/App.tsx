import { useState } from 'react'
import type { AppUseCases } from './application/ports.ts'
import brandMark from './assets/chan-guan-mark.svg'
import {
  formatPrice,
  formatSignedPercent,
  getLatestPen,
  getLatestZhongshu,
  getPriceDecimals,
  getQuoteDirection,
  isStarMarket,
} from './domain/market.ts'
import {
  evaluateActionConclusion,
  formatInvalidationDistance,
  getLatestPointsBySide,
  getPriceChangeFromPoint,
  pointLabel,
} from './domain/signals.ts'
import { VIEW_TABS as TIMEFRAMES } from './domain/timeframes.ts'
import type { RouteView, Stock } from './domain/types.ts'
import AccountChip from './presentation/components/AccountChip.tsx'
import { useAuth } from './presentation/components/AuthContext.tsx'
import DragonLoader from './presentation/components/DragonLoader.tsx'
import MarketChart, { type ChartLayersState } from './presentation/components/MarketChart.tsx'
import MarketListPage from './presentation/components/MarketListPage.tsx'
import MenuPage from './presentation/components/MenuPage.tsx'
import RankingPage from './presentation/components/RankingPage.tsx'
import RealtimeQuotePanel from './presentation/components/RealtimeQuotePanel.tsx'
import SignalPanel from './presentation/components/SignalPanel.tsx'
import StockSearch from './presentation/components/StockSearch.tsx'
import TermInfo from './presentation/components/TermInfo.tsx'
import TrackingPage from './presentation/components/TrackingPage.tsx'
import { GLOSSARY } from './presentation/glossary.ts'
import { useMarketAssets } from './presentation/hooks/useMarketAssets.ts'
import { useMarketRoute } from './presentation/hooks/useMarketRoute.ts'
import { useMarketSnapshot } from './presentation/hooks/useMarketSnapshot.ts'
import { useRankings } from './presentation/hooks/useRankings.ts'
import { useRealtimeQuote } from './presentation/hooks/useRealtimeQuote.ts'
import { useSignalConfluence } from './presentation/hooks/useSignalConfluence.ts'
import { useThemeMode } from './presentation/hooks/useThemeMode.ts'
import { useTrackings } from './presentation/hooks/useTrackings.ts'

const LAYERS: ReadonlyArray<{ key: keyof ChartLayersState; label: string }> = [
  { key: 'fractals', label: '分型' },
  { key: 'pens', label: '笔' },
  { key: 'segments', label: '线段' },
  { key: 'zhongshu', label: '笔中枢' },
  { key: 'points', label: '买卖点' },
]

const GUIDANCE_LABELS: Record<string, string> = {
  bullish: '偏多观察',
  bearish: '偏空观察',
  neutral: '暂时等待',
}

function formatVolume(value: number): string {
  if (value >= 100000000) return `${(value / 100000000).toFixed(2)} 亿`
  if (value >= 10000) return `${(value / 10000).toFixed(1)} 万`
  return String(value)
}

function formatTime(value: string, includeDate = true): string {
  const date = new Date(value)
  const pad = (number: number) => String(number).padStart(2, '0')
  const day = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  return includeDate ? `${day} ${time}` : time
}

function RefreshIcon({ spinning }: { spinning: boolean }) {
  return (
    <svg className={spinning ? 'spin' : ''} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M20 11a8 8 0 0 0-14.9-4M4 3v5h5M4 13a8 8 0 0 0 14.9 4M20 21v-5h-5" />
    </svg>
  )
}

export interface AppProps {
  useCases: AppUseCases
}

function App({ useCases }: AppProps) {
  const { user, authChannel, environment, openLoginPrompt, requireLogin, logout } = useAuth()
  const { themeMode, resolvedTheme, setThemeMode } = useThemeMode(useCases.themePreferences)
  const {
    view,
    setView,
    selected: selectedStock,
    setSelected,
    openStockDetail,
    goBackFromDetail,
    timeframe,
    setTimeframe,
  } = useMarketRoute(useCases.searchStocks)

  const decimals = getPriceDecimals(selectedStock)
  const price = (value: number | string) => formatPrice(value, decimals)
  const [reloadKey, setReloadKey] = useState(0)
  const [layers, setLayers] = useState<ChartLayersState>({
    fractals: true,
    pens: true,
    segments: true,
    zhongshu: true,
    points: true,
  })
  const isDetail = view === 'detail'
  const isRealtime = timeframe === 'realtime'

  const assetsState = useMarketAssets(useCases.getMarketAssets, { enabled: view === 'market' })
  const rankingsState = useRankings(useCases.getRankings, reloadKey, view === 'rankings')
  const trackings = useTrackings({
    user,
    authChannel,
    getTrackings: useCases.getTrackings,
    startTrackingUseCase: useCases.startTracking,
    cancelTrackingsUseCase: useCases.cancelTrackings,
    trackingPreferences: useCases.trackingPreferences,
  })

  const { snapshot, loading: snapshotLoading, error: snapshotError } = useMarketSnapshot(
    useCases.getMarketSnapshot,
    selectedStock,
    timeframe,
    reloadKey,
    isDetail && !isRealtime,
  )
  const { quote, series, loading: quoteLoading, refreshing, error: quoteError } = useRealtimeQuote(
    useCases.getQuote,
    useCases.getQuoteSeries,
    useCases.recordQuoteSample,
    selectedStock.secid,
    isDetail && isRealtime,
    reloadKey,
  )
  const { confluence, loading: confluenceLoading, error: confluenceError } = useSignalConfluence(
    useCases.getSignalConfluence,
    selectedStock.secid,
    isDetail && !isRealtime,
    reloadKey,
  )

  const navigateToView = (nextView: RouteView) => {
    setView(nextView)
    window.scrollTo(0, 0)
  }

  const handleSelectStock = (stock: Stock | null | undefined, fromView?: RouteView) => {
    if (!stock?.secid) return
    if (fromView === 'rankings') {
      setTimeframe('1d')
    }
    if (view === 'detail' && stock.secid === selectedStock.secid) {
      setReloadKey((value) => value + 1)
      return
    }
    openStockDetail(stock, fromView || view)
    window.scrollTo(0, 0)
  }

  const handleToggleTrack = (stock: Stock) => {
    requireLogin(
      () => trackings.toggleTracking(stock.secid),
      `登录后可将 ${stock.name} 加入模拟跟踪`,
    )
  }

  const handleRefreshAction = () => {
    if (view === 'market') {
      assetsState.refresh()
    } else if (view === 'tracking') {
      trackings.refresh()
    } else {
      setReloadKey((value) => value + 1)
    }
  }

  const summary = isRealtime && quote ? quote : snapshot?.summary
  const loading = isRealtime ? quoteLoading : snapshotLoading
  const error = isRealtime ? quoteError : snapshotError
  const analysis = snapshot?.analysis
  const lastPen = getLatestPen(analysis)
  const lastZone = getLatestZhongshu(analysis)
  const summaryPct = summary ? formatSignedPercent(summary.change_percent) : { text: '等待行情', tone: 'flat' as const }
  const direction = summaryPct.tone === 'flat' ? 'flat' : getQuoteDirection(summary)
  const isDetailTracked = trackings.trackedSecids.has(selectedStock.secid)

  const isRefreshSpinning = view === 'market'
    ? assetsState.loading
    : view === 'rankings'
      ? rankingsState.loading
      : view === 'tracking'
        ? trackings.loading
        : (loading || refreshing)

  const refreshLabel = view === 'rankings' ? '刷新榜单' : '刷新行情'

  return (
    <div className="app-shell">
      <header className="topbar">
        <a
          className="brand"
          href={`${import.meta.env.BASE_URL}?view=market`}
          aria-label="缠观行情首页"
          onClick={(event) => {
            event.preventDefault()
            navigateToView('market')
          }}
        >
          <img className="brand-mark" src={brandMark} width="42" height="30" alt="" aria-hidden="true" />
          <span className="brand-name">缠观</span>
          <span className="brand-tagline">先看结构，再看方向。</span>
        </a>

        <nav className="bottom-tabbar" aria-label="主导航">
          <button
            type="button"
            aria-current={view === 'market' || view === 'detail' ? 'page' : undefined}
            onClick={() => navigateToView('market')}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 4v16h16M7 14l4-4 4 3 5-7" />
            </svg>
            <span>行情</span>
          </button>
          <button
            type="button"
            aria-current={view === 'tracking' ? 'page' : undefined}
            onClick={() => navigateToView('tracking')}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="8" />
              <circle cx="12" cy="12" r="3" />
              <path d="M12 2v3M12 19v3" />
            </svg>
            <span>跟踪</span>
          </button>
          <button
            type="button"
            aria-current={view === 'rankings' ? 'page' : undefined}
            onClick={() => navigateToView('rankings')}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 20V11h5v9M9 20V4h6v16M15 20v-7h5v7M3 20h18" />
            </svg>
            <span>榜单</span>
          </button>
          <button
            type="button"
            aria-current={view === 'menu' ? 'page' : undefined}
            onClick={() => navigateToView('menu')}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" />
            </svg>
            <span>菜单</span>
          </button>
        </nav>

        <div className="topbar-actions">
          <AccountChip onOpenMenu={() => navigateToView('menu')} />
          {view !== 'menu' && (
            <button
              className="icon-button"
              type="button"
              aria-label={refreshLabel}
              title={refreshLabel}
              onClick={handleRefreshAction}
            >
              <RefreshIcon spinning={isRefreshSpinning} />
            </button>
          )}
        </div>
      </header>

      <main>
        {view === 'market' && (
          <MarketListPage
            selectedStock={selectedStock}
            category={assetsState.category}
            onCategoryChange={assetsState.setCategory}
            sort={assetsState.sort}
            order={assetsState.order}
            onToggleSort={assetsState.toggleSort}
            asOfDate={assetsState.asOfDate}
            items={assetsState.items}
            loading={assetsState.loading}
            error={assetsState.error}
            onRefresh={assetsState.refresh}
            searchStocks={useCases.searchStocks}
            onSelectStock={(stock) => handleSelectStock(stock, 'market')}
            trackedSecids={trackings.trackedSecids}
            onToggleTrack={handleToggleTrack}
          />
        )}

        {view === 'tracking' && (
          <TrackingPage
            user={user}
            onRequireLogin={requireLogin}
            trackings={trackings}
            searchStocks={useCases.searchStocks}
            onSelectStock={(stock) => handleSelectStock(stock, 'tracking')}
          />
        )}

        {view === 'rankings' && (
          <RankingPage
            board={rankingsState.board}
            loading={rankingsState.loading}
            error={rankingsState.error}
            onRefresh={() => setReloadKey((value) => value + 1)}
            onSelectStock={(stock) => handleSelectStock(stock, 'rankings')}
          />
        )}

        {view === 'menu' && (
          <MenuPage
            user={user}
            authChannel={authChannel}
            environment={environment}
            themeMode={themeMode}
            onThemeModeChange={setThemeMode}
            trackingCount={trackings.items.length}
            onOpenTracking={() => navigateToView('tracking')}
            onOpenLogin={openLoginPrompt}
            onLogout={logout}
          />
        )}

        {isDetail && (
          <>
            <div className="detail-nav-bar">
              <button type="button" className="back-link-btn" onClick={goBackFromDetail}>
                <svg viewBox="0 0 20 20" aria-hidden="true">
                  <path d="M12.5 4.5 7 10l5.5 5.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <span>返回</span>
              </button>
              <button
                type="button"
                className={isDetailTracked ? 'secondary-btn is-tracked' : 'primary-btn-sm'}
                onClick={() => handleToggleTrack(selectedStock)}
              >
                {isDetailTracked ? '跟踪中' : '+ 跟踪'}
              </button>
            </div>

            <section className="market-header" aria-labelledby="instrument-name">
              <div className="market-identity">
                <div className="instrument-row">
                  <h1 id="instrument-name" title={selectedStock.name}>{selectedStock.name}</h1>
                  <span className="symbol">{selectedStock.symbol}</span>
                  {isStarMarket(selectedStock) && <span className="board-tag">科创</span>}
                  {selectedStock.market && <span className="market-label">{selectedStock.market}</span>}
                </div>
                <div className="quote-row">
                  <strong className={direction}>{summary ? price(summary.latest) : '—'}</strong>
                  <span className={`quote-change ${direction}`}>
                    {summary
                      ? `${Math.abs(summary.change) < 1e-6 ? formatPrice(0, decimals) : `${summary.change > 0 ? '+' : ''}${price(summary.change)}`}  ${summaryPct.text}`
                      : '等待行情'}
                  </span>
                </div>
              </div>
              <div className="session-meta">
                <span>分析周期</span>
                <strong>{TIMEFRAMES.find((item) => item.value === timeframe)?.label}</strong>
              </div>
            </section>

            <section className="stock-tools" aria-label="股票与 ETF 查询">
              <StockSearch
                selected={selectedStock}
                onSelect={(stock) => setSelected(stock)}
                searchStocks={useCases.searchStocks}
              />
              <p>可查沪深北 A 股与境内 ETF 实时结构</p>
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
              {isRealtime ? (
                <RealtimeQuotePanel
                  quote={quote}
                  series={series}
                  loading={quoteLoading}
                  decimals={decimals}
                  theme={resolvedTheme}
                />
              ) : (
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
                          theme={resolvedTheme}
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
                        signalQuality: snapshot.signal_quality,
                      })
                      const buyChange = getPriceChangeFromPoint(buyPoint, summary?.latest)
                      const sellChange = getPriceChangeFromPoint(sellPoint, summary?.latest)
                      const guidanceInvalidationPrice = snapshot.guidance.invalidation_price
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
                              {buyChange && buyPoint && (
                                <p>
                                  <span>
                                    最近{pointLabel(buyPoint)}，结构端点 {buyChange.date} ({price(buyPoint.price)})，{buyChange.statusLabel}
                                  </span>
                                  <b>现价较端点价 <em className={buyChange.percent >= 0 ? 'up' : 'down'}>{buyChange.formatted}</em></b>
                                </p>
                              )}
                              {sellChange && sellPoint && (
                                <p>
                                  <span>
                                    最近{pointLabel(sellPoint)}，结构端点 {sellChange.date} ({price(sellPoint.price)})，{sellChange.statusLabel}
                                  </span>
                                  <b>现价较端点价 <em className={sellChange.percent >= 0 ? 'up' : 'down'}>{sellChange.formatted}</em></b>
                                </p>
                              )}
                            </div>
                          )}
                          <ul>
                            {snapshot.guidance.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                          </ul>
                          {guidanceInvalidationPrice != null && Number.isFinite(guidanceInvalidationPrice) && (
                            <p className="guidance-invalidation">
                              <span>中枢方向失效参考<TermInfo {...GLOSSARY.invalidation} /></span>
                              <b>
                                {formatInvalidationDistance(
                                  snapshot.guidance.bias,
                                  summary?.latest,
                                  guidanceInvalidationPrice,
                                  price(guidanceInvalidationPrice),
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
                        quality={snapshot.signal_quality}
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
    </div>
  )
}

export default App
