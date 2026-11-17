import { useEffect, useState } from 'react'
import { formatPrice, formatSignedPercent } from '../../domain/market.ts'
import {
  DEFAULT_RANKING_PAGE_SIZE,
  describeRankingEntry,
  filterRankingByMarket,
  filterRankingEntries,
  getRankingState,
  splitRankingGroups,
  type EntryTrendChartGeometry,
  type PointChangeWithLabel,
  type RankingMarketFilter,
} from '../../domain/rankings.ts'
import type { RankingBoard, RankingEntry, Stock } from '../../domain/types.ts'
import { GLOSSARY } from '../glossary.ts'
import DragonLoader from './DragonLoader.tsx'
import SignalEvidence from './SignalEvidence.tsx'
import TermInfo from './TermInfo.tsx'

const MARKET_FILTERS: ReadonlyArray<{ id: RankingMarketFilter; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'a_share', label: 'A股' },
  { id: 'etf', label: 'ETF' },
  { id: 'star', label: '科创' },
]

function formatTime(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const map = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${map.year}-${map.month}-${map.day} ${map.hour}:${map.minute}`
}

function PointSummary({ label, change }: { label: string; change: PointChangeWithLabel | null }) {
  if (!change) return null
  return (
    <span className="point-line">
      <span>{label}结构端点 {change.date}</span>
      <span>
        较端点价 <b className={change.percent >= 0 ? 'pct-up' : 'pct-down'}>{change.formatted}</b>
      </span>
    </span>
  )
}

function EntryTrendChart({ chart }: { chart: EntryTrendChartGeometry | null }) {
  if (!chart) return null
  return (
    <div className="ranking-spark" aria-label="日线与买卖点示意">
      <svg viewBox={`0 0 ${chart.width} ${chart.height}`} role="img" aria-label="近 20 日收盘走势">
        <path className="ranking-spark-area" d={chart.areaPath} />
        <polyline className="ranking-spark-line" points={chart.polyline} />
        <circle className="ranking-spark-last" cx={chart.lastX} cy={chart.lastY} r="2.2" />
        {chart.sellMarker && (
          <g className="ranking-marker sell">
            <line x1={chart.sellMarker.x} y1={chart.sellMarker.y} x2={chart.sellMarker.x} y2={chart.sellMarker.lineY} />
            <circle cx={chart.sellMarker.x} cy={chart.sellMarker.y} r="3" />
            <text x={chart.sellMarker.labelX} y={chart.sellMarker.labelY} textAnchor={chart.sellMarker.textAnchor}>
              {chart.sellMarker.text}
            </text>
          </g>
        )}
        {chart.buyMarker && (
          <g className="ranking-marker buy">
            <line x1={chart.buyMarker.x} y1={chart.buyMarker.y} x2={chart.buyMarker.x} y2={chart.buyMarker.lineY} />
            <circle cx={chart.buyMarker.x} cy={chart.buyMarker.y} r="3" />
            <text x={chart.buyMarker.labelX} y={chart.buyMarker.labelY} textAnchor={chart.buyMarker.textAnchor}>
              {chart.buyMarker.text}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}

function RankingRow({ entry, onSelect }: { entry: RankingEntry; onSelect: (stock: Stock) => void }) {
  const { stock } = entry
  const info = describeRankingEntry(entry)
  const pct = formatSignedPercent(entry.change_percent)
  const latestPoint = entry.latest_point
  const latestSideLabel = latestPoint ? (latestPoint.side === 'buy' ? '买点' : '卖点') : ''
  const latestChange = latestPoint ? (latestPoint.side === 'buy' ? info.buyChange : info.sellChange) : null

  return (
    <li className="ranking-row">
      <button type="button" onClick={() => onSelect(stock)}>
        <div className="ranking-row-head">
          <div className="ranking-stock">
            <span className="ranking-rank">{entry.rank}</span>
            <div className="ranking-name-block">
              <div className="ranking-name-line">
                <strong>{stock.name}</strong>
              </div>
              <small>
                <span>{stock.symbol}</span>
                <span>{stock.market}</span>
                {info.isStar && <span className="meta-note">科创</span>}
              </small>
            </div>
          </div>
          <div className="ranking-quote">
            <strong className="num-tabular">{formatPrice(entry.latest, info.decimals)}</strong>
            <span className={`num-tabular pct-${pct.tone}`}>{pct.text}</span>
          </div>
        </div>

        <div className="ranking-conclusion-row">
          <span className={`conclusion-badge ${info.conclusion.tone}`}>{info.conclusion.label}</span>
          <small className="invalidation-note num-tabular">{info.invalidationText}</small>
        </div>

        <div className="ranking-row-body">
          <div className="ranking-points">
            {latestChange && <PointSummary label={latestSideLabel} change={latestChange} />}
            <SignalEvidence
              quality={entry.signal_quality}
              compact
              structureConfirmed={Boolean(latestPoint?.confirmed)}
              timeframeLabel="日线"
              decimals={info.decimals}
            />
          </div>
          <EntryTrendChart chart={info.chart} />
        </div>
      </button>
    </li>
  )
}

export interface RankingPageProps {
  board: RankingBoard | null
  loading: boolean
  error: string
  onRefresh: () => void
  onSelectStock: (stock: Stock) => void
}

export default function RankingPage({ board, loading, error, onRefresh: _onRefresh, onSelectStock }: RankingPageProps) {
  const [groupTab, setGroupTab] = useState<'confirmed' | 'observation'>('confirmed')
  const [biasTab, setBiasTab] = useState<'bullish' | 'bearish'>('bullish')
  const [marketFilter, setMarketFilter] = useState<RankingMarketFilter>('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [visibleLimit, setVisibleLimit] = useState(DEFAULT_RANKING_PAGE_SIZE)

  useEffect(() => {
    setVisibleLimit(DEFAULT_RANKING_PAGE_SIZE)
  }, [board?.generated_at, groupTab, biasTab, marketFilter, searchQuery])

  const state = getRankingState(board)
  const rawEntries = biasTab === 'bullish' ? (board?.bullish || []) : (board?.bearish || [])
  const marketFiltered = filterRankingByMarket(rawEntries, marketFilter)
  const { confirmed, observation } = splitRankingGroups(marketFiltered)
  const activeGroupEntries = groupTab === 'confirmed' ? confirmed : observation
  const filteredEntries = filterRankingEntries(activeGroupEntries, searchQuery)
  const visibleEntries = searchQuery ? filteredEntries : filteredEntries.slice(0, visibleLimit)
  const remaining = Math.max(filteredEntries.length - visibleEntries.length, 0)

  const legacySnapshot = Boolean(board && (board.analysis_version == null || board.analysis_version < 2))
  const unverifiedHistory = Boolean(
    !legacySnapshot && board && (
      board.analysis_version < 4 || rawEntries.some((entry) => (
        entry.latest_point?.confirmed
        && entry.signal_quality?.state === 'confirmed'
        && entry.signal_quality?.history_status === 'unknown'
      ))
    ),
  )

  return (
    <section className="ranking-page" aria-label="每日榜单">
      <header className="section-head-hairline">
        <div className="section-head-row">
          <div>
            <h1>
              日线结构榜
              <TermInfo {...GLOSSARY.ranking} />
            </h1>
            <small className="head-subtitle">
              {board?.trade_date ? `收盘日 ${board.trade_date}，仅作结构归纳` : '全市场日线收盘结构归纳，不构成买卖建议'}
            </small>
          </div>
        </div>

        <div className="ranking-meta-strip">
          {board?.generated_at && (
            <span className="ranking-update-time">
              更新于 <time dateTime={board.generated_at}>{formatTime(board.generated_at)}</time>（北京时间）
            </span>
          )}
          {state.coverage && <span>{state.coverage}</span>}
          {state.updating && <span className="ranking-updating">{state.progress}</span>}
        </div>
      </header>

      <div className="underline-tabs" role="tablist" aria-label="信号状态分组">
        <button
          type="button"
          role="tab"
          aria-selected={groupTab === 'confirmed'}
          onClick={() => setGroupTab('confirmed')}
        >
          已确认同向（{confirmed.length}）
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={groupTab === 'observation'}
          onClick={() => setGroupTab('observation')}
        >
          候选观察（{observation.length}）
        </button>
      </div>

      <div className="ranking-toolbar">
        <div className="ranking-toolbar-top">
          <div className="segmented-switch" role="tablist" aria-label="结构方向">
            <button
              type="button"
              role="tab"
              aria-selected={biasTab === 'bullish'}
              onClick={() => setBiasTab('bullish')}
            >
              偏多结构
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={biasTab === 'bearish'}
              onClick={() => setBiasTab('bearish')}
            >
              偏空结构
            </button>
          </div>

          <div className="filter-pills" role="group" aria-label="板块筛选">
            {MARKET_FILTERS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={marketFilter === item.id ? 'is-active' : ''}
                onClick={() => setMarketFilter(item.id)}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>

        <div className="ranking-search-bar">
          <input
            type="search"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            placeholder="在榜单中搜索代码、名称、结论或科创"
            aria-label="搜索榜单证券"
          />
          {searchQuery && (
            <button type="button" className="text-action-btn" onClick={() => setSearchQuery('')}>清空</button>
          )}
        </div>
      </div>

      {state.hasResult && (
        <div className="ranking-status" role="status">
          <p className="ranking-date-note">
            列表中买/卖点日期为结构端点日期，首次识别及确认时间尚未追溯，且信号可能失效。
          </p>
        </div>
      )}

      {board?.update_error && <p className="inline-notice">{board.update_error}</p>}
      {error && !state.hasResult && <div className="empty-state error">{error}</div>}
      {loading && !state.hasResult && (
        <div className="list-loading-state">
          <DragonLoader title="正在读取收盘榜单" label={state.updating ? state.progress : '加载全市场排行…'} />
        </div>
      )}

      {!loading && !error && !state.hasResult && (
        <div className="empty-state">
          <strong>{state.updating ? '收盘榜单正在计算中' : '暂无收盘榜单'}</strong>
          <p>{state.updating ? state.progress : '每日 15:30 收盘后自动生成全市场 A 股与 ETF 日线榜单。'}</p>
        </div>
      )}

      {state.hasResult && (
        <section className={`ranking-list-section ${biasTab}`} data-group={groupTab}>
          <div className="ranking-group-caption">
            <strong>{groupTab === 'confirmed' ? '已确认且端点起未见越界' : '候选结构、待核查或已失效'}</strong>
            <small>
              {groupTab === 'confirmed'
                ? '最新结构端点已确认、同向且历史未曾收盘越界；候选不会补入此组'
                : '包含未确认候选结构、历史核对不足或收盘越界失效的观察条目'}
            </small>
          </div>

          {groupTab === 'confirmed' && legacySnapshot && (
            <p className="ranking-legacy-note">当前为旧版快照，辅助证据尚未计算，暂列观察组，等待后台更新。</p>
          )}
          {groupTab === 'confirmed' && unverifiedHistory && (
            <p className="ranking-legacy-note">当前快照尚未核对端点起的历史收盘越界，暂列观察组，等待后台更新。</p>
          )}

          {visibleEntries.length > 0 ? (
            <ul className="ranking-list">
              {visibleEntries.map((entry) => (
                <RankingRow key={`${biasTab}-${entry.stock.secid}`} entry={entry} onSelect={onSelectStock} />
              ))}
            </ul>
          ) : (
            <p className="empty-copy">
              {searchQuery ? '当前筛选下未匹配到证券' : `当前暂无线下符合「${groupTab === 'confirmed' ? '已确认同向' : '候选观察'}」的条目`}
            </p>
          )}

          {!searchQuery && remaining > 0 && (
            <div className="ranking-more">
              <button
                type="button"
                onClick={() => setVisibleLimit((limit) => limit + DEFAULT_RANKING_PAGE_SIZE)}
              >
                展开更多（剩余 {remaining} 只）
              </button>
            </div>
          )}
        </section>
      )}
    </section>
  )
}
