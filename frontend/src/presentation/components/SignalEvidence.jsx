import { formatSignalDate } from '../../domain/signals.js'
import { formatPrice } from '../../domain/market.js'

function ratio(value) {
  return Number.isFinite(value) ? `${value.toFixed(2)} 倍` : '数据不足'
}

export default function SignalEvidence({ quality, compact = false, structureConfirmed = false, timeframeLabel = '日线', decimals }) {
  if (!quality) return <span className="signal-evidence">辅助证据尚未计算</span>
  return (
    <span className={`signal-evidence ${compact ? 'compact' : ''}`}>
      <span className="signal-pivot-age">
        {Number.isInteger(quality.bars_since_pivot) && quality.bars_since_pivot >= 0
          ? <>距结构端点 {quality.bars_since_pivot} 根已收盘 K 线（{timeframeLabel}）</>
          : '距结构端点的 K 线数尚未计算'}
      </span>
      <span>{compact ? '量价证据待验证' : '量价辅助规则待验证，不代表成功概率'}</span>
      {Number.isFinite(quality.macd_histogram) && (
        <span>MACD 柱 {quality.macd_histogram.toFixed(3)}
          {Number.isFinite(quality.macd_area_ratio) && <>，背驰面积比 {quality.macd_area_ratio.toFixed(2)}</>}
          {Number.isFinite(quality.pullback_macd_ratio) && <span title="本笔与上一个同方向笔的同号MACD柱面积之比">，同向动能比 {quality.pullback_macd_ratio.toFixed(2)}</span>}
        </span>
      )}
      <span title="前段量比：前一笔均量 / 该笔开始前20根均量；回抽量比：本笔均量 / 前一笔均量">
        {quality.volume_status === 'insufficient'
          ? '量能数据不足，未作有效性判断'
          : <>前段量比 {ratio(quality.breakout_volume_ratio)}，回抽量比 {ratio(quality.pullback_volume_ratio)}</>}
      </span>
      <span>{quality.history_status === 'invalidated'
        ? '端点起曾收盘越界，价格恢复不重用该信号'
        : quality.history_status === 'checked_from_pivot'
          ? '已核对端点起已收盘 K 线，未见收盘越界'
          : '端点起收盘越界尚未核对，暂列观察'}</span>
      {quality.first_reference_crossed_at && <span>
        首次越界 {formatSignalDate(quality.first_reference_crossed_at, timeframeLabel === '日线' ? '1d' : '30m')}
        {Number.isFinite(quality.first_reference_crossed_close) && <>，收盘价 {formatPrice(quality.first_reference_crossed_close, decimals)}</>}
      </span>}
      {!compact && quality.history_checked_through && <span>
        区间核对截至 {formatSignalDate(quality.history_checked_through, timeframeLabel === '日线' ? '1d' : '30m')}，不代表当时已识别该信号
      </span>}
      {quality.reference_touched && quality.state !== 'invalidated' && <span>
        {quality.state === 'insufficient' ? '本根 K 线盘中已触及参考，等待完整收盘评价' : '本根 K 线盘中触及参考，收盘未严格越界'}
      </span>}
      {!compact && (
        <span>
          首次识别：{quality.first_detected_at ? formatSignalDate(quality.first_detected_at, '30m') : '未追溯'}
          {'　'}首次确认：{quality.confirmed_at ? formatSignalDate(quality.confirmed_at, '30m') : structureConfirmed ? '未追溯' : '尚未确认'}
        </span>
      )}
    </span>
  )
}
