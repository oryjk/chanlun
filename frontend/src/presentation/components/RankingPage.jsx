import { useEffect, useState } from 'react'
import { formatPrice } from '../../domain/market.js'
import {
  DEFAULT_RANKING_PAGE_SIZE,
  describeRankingEntry,
  filterRankingEntries,
  getRankingState,
} from '../../domain/rankings.js'
import { useRankings } from '../hooks/useRankings.js'
import DragonLoader from './DragonLoader.jsx'

function formatTime(value) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
  )
}

function RankingTrendChart({ chart }) {
  if (!chart) {
    return <span className="ranking-trend-empty">暂无走势样本</span>
  }
  return (
    <svg
      viewBox={`0 0 ${chart.width} ${chart.height}`}
      className="ranking-trend-svg"
      aria-label="近期价格走势与买卖点"
    >
      <path d={chart.areaPath} className="ranking-trend-area" />
      <polyline points={chart.polyline} className="ranking-trend-line" />
      <circle cx={chart.lastX} cy={chart.lastY} r="2.2" className="ranking-trend-last" />
      {chart.sellMarker && (
        <g>
          <line
            x1={chart.sellMarker.x}
            y1={chart.sellMarker.y}
            x2={chart.sellMarker.x}
            y2={chart.sellMarker.lineY}
            className="ranking-marker-stem sell"
          />
          <circle cx={chart.sellMarker.x} cy={chart.sellMarker.y} r="2.8" className="ranking-marker-dot sell" />
          <text
            x={chart.sellMarker.labelX}
            y={chart.sellMarker.labelY}
            textAnchor={chart.sellMarker.textAnchor}
            className="ranking-marker-text sell"
          >
            {chart.sellMarker.text}
          </text>
        </g>
      )}
      {chart.buyMarker && (
        <g>
          <line
            x1={chart.buyMarker.x}
            y1={chart.buyMarker.y}
            x2={chart.buyMarker.x}
            y2={chart.buyMarker.lineY}
            className="ranking-marker-stem buy"
          />
          <circle cx={chart.buyMarker.x} cy={chart.buyMarker.y} r="2.8" className="ranking-marker-dot buy" />
          <text
            x={chart.buyMarker.labelX}
            y={chart.buyMarker.labelY}
            textAnchor={chart.buyMarker.textAnchor}
            className="ranking-marker-text buy"
          >
            {chart.buyMarker.text}
          </text>
        </g>
      )}
    </svg>
  )
}

function RankingList({ title, entries, direction, onSelect, hasResult, query }) {
  const [visibleCount, setVisibleCount] = useState(DEFAULT_RANKING_PAGE_SIZE)

  useEffect(() => {
    setVisibleCount(DEFAULT_RANKING_PAGE_SIZE)
  }, [query])

  const visibleEntries = entries.slice(0, visibleCount)
  const remaining = Math.max(0, entries.length - visibleEntries.length)

  return (
    <section className={`ranking-card ${direction}`}>
      <div className="ranking-card-heading">
        <h2>{title}</h2>
        <span>
          {entries.length > visibleEntries.length
            ? `已显示 ${visibleEntries.length} / ${entries.length} 只`
            : `${entries.length} 只`}
        </span>
      </div>
      <div className="ranking-list">
        {visibleEntries.map((entry) => {
          const info = describeRankingEntry(entry)
          return (
            <button type="button" className="ranking-row" key={entry.stock.secid} onClick={() => onSelect(entry.stock)}>
              <span className="ranking-number">{entry.rank}</span>
              <span className="ranking-name">
                <strong>
                  {entry.stock.name}
                  <em className={`ranking-conclusion ${info.conclusion.tone}`}>{info.conclusion.label}</em>
                </strong>
                <small className="ranking-meta">
                  <span>{entry.stock.symbol}</span>
                  {info.isStar && <i className="board-tag">科创</i>}
                  <span>{entry.stock.market}</span>
                  <span>强度 {entry.strength}</span>
                </small>
                <span className="ranking-point-changes">
                  {info.buyChange && (
                    <span>
                      买点 {info.buyChange.date}
                      <i className={`point-confirm-tag ${info.buyChange.confirmed ? 'confirmed' : 'pending'}`}>
                        {info.buyChange.statusLabel}
                      </i>{' '}
                      较该日{' '}
                      <b className={info.buyChange.percent >= 0 ? 'up' : 'down'}>{info.buyChange.formatted}</b>
                    </span>
                  )}
                  {info.sellChange && (
                    <span>
                      卖点 {info.sellChange.date}
                      <i className={`point-confirm-tag ${info.sellChange.confirmed ? 'confirmed' : 'pending'}`}>
                        {info.sellChange.statusLabel}
                      </i>{' '}
                      较该日{' '}
                      <b className={info.sellChange.percent >= 0 ? 'up' : 'down'}>{info.sellChange.formatted}</b>
                    </span>
                  )}
                </span>
              </span>
              <span className="ranking-trend">
                <RankingTrendChart chart={info.chart} />
              </span>
              <span className="ranking-price">
                <strong>{formatPrice(entry.latest, info.decimals)}</strong>
                <small className={entry.change_percent >= 0 ? 'up' : 'down'}>
                  {entry.change_percent >= 0 ? '+' : ''}{entry.change_percent}%
                </small>
                <span className="ranking-invalidation">{info.invalidationText}</span>
              </span>
            </button>
          )
        })}
        {entries.length === 0 && (
          <p className="ranking-empty">
            {query.trim()
              ? '当前榜单未匹配到该证券，请尝试其他关键词'
              : hasResult ? '暂无可列入的结构' : '首次收盘榜单尚未生成'}
          </p>
        )}
      </div>
      {remaining > 0 && (
        <button
          type="button"
          className="ranking-more"
          onClick={() => setVisibleCount((count) => count + DEFAULT_RANKING_PAGE_SIZE)}
        >
          展开更多（剩余 {remaining} 只）
        </button>
      )}
    </section>
  )
}

export default function RankingPage({ getRankings, onSelectStock, reloadKey }) {
  const { board, loading, error } = useRankings(getRankings, reloadKey)
  const [query, setQuery] = useState('')
  const state = getRankingState(board)
  const bullish = filterRankingEntries(board?.bullish || [], query)
  const bearish = filterRankingEntries(board?.bearish || [], query)
  const initialLoading = loading && !board
  const buildingInitialBoard = !initialLoading && !state.hasResult && state.updating

  return (
    <section className="ranking-page" aria-busy={loading}>
      <div className="ranking-page-heading">
        <div>
          <h1>结构榜单</h1>
          <p>沪深 A 股和境内 ETF，每个交易日收盘后更新{loading && <span role="status">，正在读取…</span>}</p>
        </div>
        <div className="ranking-search" role="search">
          <SearchIcon />
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索代码、名称、科创或结论"
            aria-label="搜索榜单证券"
            autoComplete="off"
            spellCheck="false"
          />
          {query && (
            <button type="button" onClick={() => setQuery('')}>
              清空
            </button>
          )}
        </div>
      </div>
      {error && <div className="error-banner" role="alert">{error}</div>}
      <p className="ranking-status" role="status">
        {initialLoading
          ? '正在读取全市场收盘结构榜单…'
          : state.hasResult
            ? <><time dateTime={board.trade_date}>{board.trade_date} 收盘</time>　{state.coverage}</>
            : '收盘榜单准备中，请稍后查看'}
        {!initialLoading && state.updating && <>，后台正在更新，{state.progress}</>}
        {board?.update_error && <>。{board.update_error}{state.hasResult ? '，当前展示上次完成的结果' : ''}</>}
      </p>
      {initialLoading || buildingInitialBoard ? (
        <div className="ranking-loading-panel">
          <DragonLoader
            title="游龙巡盘中"
            label={
              initialLoading
                ? '正在读取沪深 A 股与 ETF 收盘结构榜单，请稍候…'
                : `后台正在计算今日全市场日线结构（${state.progress}），请稍候…`
            }
          />
        </div>
      ) : (
        <>
          {loading && (
            <div className="ranking-refresh-banner">
              <DragonLoader compact title="游龙巡盘中" label="正在同步最新收盘结构榜单…" />
            </div>
          )}
          <div className="ranking-grid">
            <RankingList title="结构偏多榜" entries={bullish} direction="bullish" onSelect={onSelectStock} hasResult={state.hasResult} query={query} />
            <RankingList title="结构偏空榜" entries={bearish} direction="bearish" onSelect={onSelectStock} hasResult={state.hasResult} query={query} />
          </div>
        </>
      )}
      <div className="ranking-note">
        <span>优先展示近期出现同向买卖点且距失效位较近的标的，中性结构不入榜。仅供结构研究，不构成买卖建议。</span>
        {state.hasResult && <time dateTime={board.generated_at}>生成于 {formatTime(board.generated_at)}</time>}
      </div>
    </section>
  )
}
