import { formatPrice, formatSignedPercent, formatTurnover, getPriceDecimals } from '../../domain/market.ts'
import type {
  OvernightCandidate,
  OvernightCandidateStatus,
  OvernightPhase,
  OvernightReminder,
  OvernightScanStatus,
  OvernightSnapshot,
} from '../../domain/overnight.ts'
import type { Stock } from '../../domain/types.ts'
import { useBeforeDeadline } from '../hooks/useBeforeDeadline.ts'
import DragonLoader from './DragonLoader.tsx'

const PHASE_LABELS: Record<OvernightPhase, string> = {
  waiting: '等待交易时段',
  preparing: '准备尾盘扫描',
  scanning: '尾盘扫描时段',
  closed: '尾盘扫描已结束',
  exit: '次日上午提醒时段',
}

const SCAN_STATUS_LABELS: Record<OvernightScanStatus, string> = {
  pending: '等待扫描结果',
  ready: '扫描结果已更新',
  partial: '部分数据未覆盖',
  unavailable: '扫描数据暂不可用',
}

const CANDIDATE_LABELS: Record<OvernightCandidateStatus, string> = {
  watching: '观察回踩',
  pullback: '已识别回踩',
  filtered: '已排除',
  unavailable: '缺少分时证据',
}

const beijingTime = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

function Timestamp({ value }: { value: string }) {
  const date = new Date(value)
  return <time dateTime={value}>{Number.isFinite(date.getTime()) ? beijingTime.format(date) : '—'}</time>
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

function StockName({ stock }: { stock: Stock }) {
  return (
    <div className="ranking-name-block">
      <div className="ranking-name-line"><strong>{stock.name}</strong></div>
      <small><span>{stock.symbol}</span><span>{stock.market}</span></small>
    </div>
  )
}

function CandidateRow({ candidate, onSelect }: { candidate: OvernightCandidate; onSelect: (stock: Stock) => void }) {
  const { stock } = candidate
  const decimals = getPriceDecimals(stock)
  const pct = formatSignedPercent(candidate.changePercent)
  return (
    <li className="ranking-row overnight-row">
      <button type="button" onClick={() => onSelect(stock)}>
        <div className="ranking-row-head">
          <StockName stock={stock} />
          <div className="ranking-quote">
            <strong className="num-tabular">{formatPrice(candidate.price, decimals)}</strong>
            <span className={`num-tabular pct-${pct.tone}`}>{pct.text}</span>
          </div>
        </div>
        <div className="overnight-row-status">
          <strong>{CANDIDATE_LABELS[candidate.status]}</strong>
          {candidate.reason && <span>{candidate.reason}</span>}
        </div>
        <div className="overnight-evidence num-tabular">
          <span>量比 {candidate.volumeRatio.toFixed(2)}</span>
          <span>换手率 {candidate.turnoverRate.toFixed(2)}%</span>
          <span>总市值 {formatTurnover(candidate.totalMarketCap)}元</span>
          <span>最近涨停 {candidate.limitUpDate}</span>
          <span>报价 <Timestamp value={candidate.asOf} /></span>
        </div>
        {(candidate.minuteAt || candidate.pullbackAt) && (
          <div className="overnight-evidence num-tabular">
            {candidate.minuteAt && <span>分时数据 <Timestamp value={candidate.minuteAt} /></span>}
            {candidate.minuteChangePercent != null && <span>个股涨跌幅 {formatSignedPercent(candidate.minuteChangePercent).text}</span>}
            {candidate.benchmarkChangePercent != null && <span>沪深300涨跌幅 {formatSignedPercent(candidate.benchmarkChangePercent).text}</span>}
            {candidate.averagePrice != null && <span>分时均价 {formatPrice(candidate.averagePrice, decimals)}</span>}
            {candidate.pullbackAt && <span>回踩分钟 <Timestamp value={candidate.pullbackAt} /></span>}
          </div>
        )}
      </button>
    </li>
  )
}

function ReminderRow({ reminder, onSelect }: { reminder: OvernightReminder; onSelect: (stock: Stock) => void }) {
  const showException = useBeforeDeadline(reminder.exceptionObserved ? reminder.exceptionValidUntil : null)
  return (
    <li className="ranking-row overnight-row">
      <button type="button" onClick={() => onSelect(reminder.stock)}>
        <StockName stock={reminder.stock} />
        <div className="overnight-row-status">
          <strong>{showException ? '缩量一字板，复核清仓提醒' : '清仓提醒'}</strong>
          {showException && reminder.exceptionAsOf && (
            <span>截至 <Timestamp value={reminder.exceptionAsOf} /></span>
          )}
        </div>
        <div className="overnight-evidence num-tabular">
          <span>尾盘信号 <Timestamp value={reminder.signalAt} /></span>
        </div>
      </button>
    </li>
  )
}

function ReminderList({ reminders, onSelect }: {
  reminders: readonly OvernightReminder[]
  onSelect: (stock: Stock) => void
}) {
  return (
    <section aria-labelledby="overnight-reminders">
      <div className="overnight-section-head">
        <h2 id="overnight-reminders">次日上午清仓提醒</h2>
        <small>09:30–11:30（北京时间）</small>
      </div>
      {reminders.length > 0 ? (
        <ul className="ranking-list">
          {reminders.map((reminder) => (
            <ReminderRow key={`${reminder.stock.secid}-${reminder.signalAt}`} reminder={reminder} onSelect={onSelect} />
          ))}
        </ul>
      ) : <p className="empty-copy">当前暂无提醒</p>}
    </section>
  )
}

interface OvernightPageProps {
  snapshot: OvernightSnapshot | null
  loading: boolean
  error: string
  onRefresh: () => void
  onSelectStock: (stock: Stock) => void
}

export default function OvernightPage({ snapshot, loading, error, onRefresh, onSelectStock }: OvernightPageProps) {
  const scanned = snapshot && (snapshot.universeCount > 0 || snapshot.elapsedMs != null)
  const showReminders = useBeforeDeadline(snapshot?.phase === 'exit' ? snapshot.remindersUntil : null)
  return (
    <section className="ranking-page" aria-labelledby="overnight-title">
      <header className="section-head-hairline">
        <div className="section-head-row">
          <div>
            <h1 id="overnight-title">隔夜套利</h1>
            <small className="head-subtitle">尾盘筛选与次日上午提醒，不构成买卖建议</small>
          </div>
        </div>
        {snapshot && (
          <div className="ranking-meta-strip">
            {snapshot.tradeDate && <strong className="overnight-trade-date">交易日 {snapshot.tradeDate}</strong>}
            <span>{snapshot.phase === 'exit' && snapshot.remindersUntil && !showReminders
              ? '次日上午提醒时段已结束'
              : PHASE_LABELS[snapshot.phase]}</span>
            <span className="ranking-updating">{SCAN_STATUS_LABELS[snapshot.status]}</span>
            {snapshot.observedAt && (
              <span className="ranking-update-time">更新于 <Timestamp value={snapshot.observedAt} />（北京时间）</span>
            )}
          </div>
        )}
      </header>

      {error && (
        <div className="inline-error-row" role="status">
          <span>{snapshot ? `更新失败，显示上次结果。 ${error}` : error}</span>
          <button type="button" onClick={onRefresh}>重试</button>
        </div>
      )}
      {loading && !snapshot && !error && (
        <div className="list-loading-state">
          <DragonLoader title="正在读取隔夜扫描" label="获取最新候选与提醒…" />
        </div>
      )}

      {snapshot && (
        <>
          {(scanned || snapshot.historySessions > 0) && (
            <dl className="overnight-metrics num-tabular" aria-label="扫描覆盖与耗时">
              {scanned && <Metric label="新鲜报价" value={snapshot.universeCount > 0 ? `${snapshot.freshCount} / ${snapshot.universeCount} 只` : '—'} />}
              <Metric label="历史覆盖" value={`${snapshot.historySessions} / 20 个交易日`} />
              {scanned && (
                <>
                  <Metric label="满足初筛" value={`${snapshot.matchedCount} 只`} />
                  <Metric label="已核对分时" value={`${snapshot.minuteCheckedCount} 只`} />
                  <Metric label="扫描耗时" value={snapshot.elapsedMs == null ? '—' : `${(snapshot.elapsedMs / 1_000).toFixed(1)} 秒`} />
                  <Metric label="最长报价延迟" value={snapshot.maxQuoteAgeSeconds == null ? '—' : `${snapshot.maxQuoteAgeSeconds.toFixed(1)} 秒`} />
                </>
              )}
            </dl>
          )}

          {showReminders && <ReminderList reminders={snapshot.reminders} onSelect={onSelectStock} />}

          <section aria-labelledby="overnight-candidates">
            <div className="overnight-section-head">
              <h2 id="overnight-candidates">尾盘候选</h2>
              <small>14:30–15:00（北京时间）</small>
            </div>
            {snapshot.candidates.length > 0 ? (
              <ul className="ranking-list">
                {snapshot.candidates.map((candidate) => (
                  <CandidateRow key={candidate.stock.secid} candidate={candidate} onSelect={onSelectStock} />
                ))}
              </ul>
            ) : (
              <p className="empty-copy">
                {snapshot.status === 'ready' ? '本次扫描暂无匹配候选' : SCAN_STATUS_LABELS[snapshot.status]}
              </p>
            )}
          </section>
        </>
      )}
    </section>
  )
}
