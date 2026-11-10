import {
  divergenceLabel,
  formatSignalDate,
  getLatestDivergence,
  getPriceChangeFromPoint,
  getRecentPoints,
  pointLabel,
  pointShortLabel,
  TREND_TYPE_LABELS,
} from '../../domain/signals.js'
import { GLOSSARY } from '../glossary.js'
import TermInfo from './TermInfo.jsx'
import { formatPrice } from '../../domain/market.js'
import SignalEvidence from './SignalEvidence.jsx'

function PointBadge({ point }) {
  return <span className={`point-badge ${point.side} ${point.confirmed ? '' : 'candidate'}`}>{pointShortLabel(point)}</span>
}

function ConfluenceSection({ confluence, loading, error }) {
  return (
    <div className="confluence">
      <div className="detail-title">
        <span>多级别联立<TermInfo {...GLOSSARY.confluence} /></span>
        <small>{loading ? '正在比对四个周期…' : ''}</small>
      </div>
      {error && <p className="empty-copy">{error}</p>}
      {confluence && (
        <>
          <p className="confluence-conclusion">{confluence.conclusion}</p>
          <div className="confluence-levels">
            {confluence.levels.map((level) => (
              <div key={level.timeframe}>
                <span>{level.timeframe_label}</span>
                <small>{TREND_TYPE_LABELS[level.trend_type]}</small>
                {level.latest_point ? <PointBadge point={level.latest_point} /> : <em>—</em>}
                <i className={level.lower_confirmation ? 'confirmed' : ''} title={level.lower_confirmation ? '次级别同向印证' : '次级别未印证'}>
                  {level.lower_confirmation ? '✓' : '–'}
                </i>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

export default function SignalPanel({
  signals,
  quality,
  timeframe = '1d',
  timeframeLabel,
  latestPrice,
  confluence,
  confluenceLoading,
  confluenceError,
  decimals,
}) {
  const points = getRecentPoints(signals)
  const groups = [
    { name: '已确认结构', points: points.filter((point) => point.confirmed) },
    { name: '候选，等待确认', points: points.filter((point) => !point.confirmed) },
  ]
  const divergence = getLatestDivergence(signals)

  return (
    <section className="structure-detail signal-detail" aria-label="缠论买卖点">
      <div className="detail-title">
        <span>{timeframeLabel}买卖点<TermInfo {...GLOSSARY.buySellPoint} /></span>
        <small>{signals ? TREND_TYPE_LABELS[signals.trend_type] : ''}<TermInfo {...GLOSSARY.trendType} /></small>
      </div>

      {points.length > 0 ? (
        <>
        <p className="empty-copy">日期为结构端点，首次识别与确认时间未追溯；结构确认不等于应立即交易。</p>
        {groups.map((group) => group.points.length > 0 && (
        <div className="signal-subgroup" key={group.name}>
        <h3>{group.name}</h3>
        <ul className="signal-list">
          {group.points.map((point) => {
            const change = getPriceChangeFromPoint(point, latestPrice)
            return (
              <li key={`${point.kind}-${point.side}-${point.pen_id}`}>
                <PointBadge point={point} />
                <div>
                  <strong>{pointLabel(point)}<b>{formatPrice(point.price, decimals)}</b></strong>
                  <small>
                    <span>结构端点 {formatSignalDate(point.time, timeframe)}</span>
                    {change && (
                      <span>
                        较端点价{' '}
                        <b className={change.percent >= 0 ? 'up' : 'down'}>{change.formatted}</b>
                      </span>
                    )}
                    <span>{point.confirmed ? '已确认' : '候选，可能修正'}</span>
                  </small>
                  <p>{point.reason}</p>
                </div>
              </li>
            )
          })}
        </ul>
        </div>
        ))}
        {Number.isFinite(quality?.invalidation_price) && (
          <p className="empty-copy">最近信号失效参考 {formatPrice(quality.invalidation_price, decimals)}，以完成 K 线收盘严格越界判断。</p>
        )}
        <SignalEvidence quality={quality} decimals={decimals} timeframeLabel={timeframeLabel} structureConfirmed={Boolean(points[0]?.confirmed)} />
        </>
      ) : <p className="empty-copy">当前周期尚未出现买卖点</p>}

      {divergence && (
        <p className="divergence-line">
          <span>最近背驰<TermInfo {...GLOSSARY.divergence} /></span>
          <b className={divergence.direction === 'bottom' ? 'up' : 'down'}>{divergenceLabel(divergence)}</b>
          <small>MACD 面积 {divergence.leaving_area.toFixed(2)} / {divergence.entering_area.toFixed(2)}</small>
        </p>
      )}

      <ConfluenceSection confluence={confluence} loading={confluenceLoading} error={confluenceError} />
      <small className="signal-disclaimer">按固定规则自动识别，存在主观性与滞后性，不构成买卖建议</small>
    </section>
  )
}
