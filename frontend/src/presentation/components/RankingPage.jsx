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

const updateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function formatUpdateTime(value) {
  return updateTimeFormatter.format(new Date(value)).replace(/\//g, '-')
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
                      <span className="ranking-signal-date">
                        <span>买点信号出现于</span>
                        <time dateTime={info.buyChange.date}>{info.buyChange.date}</time>
                      </span>
                      <i className={`point-confirm-tag ${info.buyChange.confirmed ? 'confirmed' : 'pending'}`}>
                        {info.buyChange.statusLabel}
                      </i>{' '}
                      较信号价{' '}
                      <b className={info.buyChange.percent >= 0 ? 'up' : 'down'}>{info.buyChange.formatted}</b>
                    </span>
                  )}
                  {info.sellChange && (
                    <span>
                      <span className="ranking-signal-date">
                        <span>卖点信号出现于</span>
                        <time dateTime={info.sellChange.date}>{info.sellChange.date}</time>
                      </span>
                      <i className={`point-confirm-tag ${info.sellChange.confirmed ? 'confirmed' : 'pending'}`}>
                        {info.sellChange.statusLabel}
                      </i>{' '}
                      较信号价{' '}
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
      <div className="ranking-status" role="status">
        {initialLoading
          ? '正在读取全市场收盘结构榜单…'
          : state.hasResult
            ? <>
                <div className="ranking-dates">
                  <p className="ranking-close-date">
                    <span>榜单收盘日期</span>
                    <time dateTime={board.trade_date}>{board.trade_date}</time>
                  </p>
                  <p className="ranking-update-time">
                    <span>榜单更新时间（北京时间）</span>
                    <time dateTime={board.generated_at}>{formatUpdateTime(board.generated_at)}</time>
                  </p>
                </div>
                <p className="ranking-coverage">{state.coverage}</p>
              </>
            : '收盘榜单准备中，请稍后查看'}
        {!initialLoading && state.updating && <p className="ranking-progress">后台正在更新，{state.progress}</p>}
        {board?.update_error && <p className="ranking-update-error">{board.update_error}{state.hasResult ? '，当前展示上次完成的结果' : ''}</p>}
        {state.hasResult && <p className="ranking-date-hint">买卖点日期为信号出现日期，行情与涨跌幅截至上述收盘日。</p>}
      </div>
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
      </div>
    </section>
  )
}
