import { pointLabel } from '../../domain/signals.js'
import { useRankings } from '../hooks/useRankings.js'

function formatPrice(value) {
  return Number(value).toFixed(3)
}

function formatTime(value) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function RankingList({ title, entries, direction, onSelect }) {
  return (
    <section className={`ranking-card ${direction}`}>
      <div className="ranking-card-heading">
        <h2>{title}</h2>
        <span>{entries.length} 只</span>
      </div>
      <div className="ranking-list">
        {entries.map((entry) => (
          <button type="button" className="ranking-row" key={entry.stock.secid} onClick={() => onSelect(entry.stock)}>
            <span className="ranking-number">{entry.rank}</span>
            <span className="ranking-name">
              <strong>{entry.stock.name}</strong>
              <small>{entry.stock.symbol} · {entry.stock.market}</small>
            </span>
            <span className="ranking-price">
              <strong>{formatPrice(entry.latest)}</strong>
              <small className={entry.change_percent >= 0 ? 'up' : 'down'}>{entry.change_percent >= 0 ? '+' : ''}{entry.change_percent}%</small>
            </span>
            <span className="ranking-strength">强度 {entry.strength}</span>
            <span className="ranking-invalidation">
              失效参考 {entry.invalidation_price === null ? '—' : formatPrice(entry.invalidation_price)}
              {entry.latest_point && <> · 最近<b className={entry.latest_point.side === 'buy' ? 'up' : 'down'}>{pointLabel(entry.latest_point)}</b></>}
            </span>
          </button>
        ))}
        {entries.length === 0 && <p className="ranking-empty">暂无可列入的结构</p>}
      </div>
    </section>
  )
}

export default function RankingPage({ getRankings, onSelectStock, reloadKey }) {
  const { board, loading, error, reload } = useRankings(getRankings, reloadKey)

  return (
    <section className="ranking-page">
      <div className="ranking-page-heading">
        <div>
          <h1>结构榜单</h1>
          <p>日线结构 · 按成交额选取候选池</p>
        </div>
        <button type="button" className="ranking-refresh" onClick={reload} disabled={loading}>
          {loading ? '正在计算' : '刷新榜单'}
        </button>
      </div>
      {error && <div className="error-banner" role="alert">{error}</div>}
      <div className="ranking-grid">
        <RankingList title="结构偏多榜" entries={board?.bullish || []} direction="bullish" onSelect={onSelectStock} />
        <RankingList title="结构偏空榜" entries={board?.bearish || []} direction="bearish" onSelect={onSelectStock} />
      </div>
      <div className="ranking-note">
        <span>按价格位置、确认笔和确认线段的同向强度排序，同强度时最近买卖点与方向一致者优先，中性结构不入榜。仅供结构研究，不构成买卖建议。</span>
        {board && <time dateTime={board.generated_at}>生成于 {formatTime(board.generated_at)}</time>}
      </div>
    </section>
  )
}
