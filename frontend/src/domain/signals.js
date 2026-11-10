const KIND_LABELS = { 1: '一', 2: '二', 3: '三' }

export const TREND_TYPE_LABELS = {
  up_trend: '上涨趋势',
  down_trend: '下跌趋势',
  consolidation: '盘整',
  none: '结构不足',
}

export function pointLabel(point) {
  return `${KIND_LABELS[point.kind]}类${point.side === 'buy' ? '买' : '卖'}点`
}

export function pointShortLabel(point) {
  return `${point.side === 'buy' ? 'B' : 'S'}${point.kind}`
}

export function getRecentPoints(signals, count = 4) {
  return signals ? signals.points.slice(-count).reverse() : []
}

export function getLatestDivergence(signals) {
  return signals?.divergences[signals.divergences.length - 1] ?? null
}

export function divergenceLabel(divergence) {
  return `${divergence.kind === 'trend' ? '趋势' : '盘整'}${divergence.direction === 'bottom' ? '底' : '顶'}背驰`
}

function pad2(number) {
  return String(number).padStart(2, '0')
}

export function formatShortDate(value) {
  if (!value) return ''
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (match) return `${match[2]}-${match[3]}`
  const date = new Date(value)
  return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function formatSignalDate(value, timeframe = '1d') {
  if (!value) return ''
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (timeframe === '1d' && match) {
    return `${match[1]}-${match[2]}-${match[3]}`
  }
  const date = new Date(value)
  if (timeframe === '1d') {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
  }
  return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

export function getPriceChangeFromPoint(point, latestPrice) {
  if (!point || !Number.isFinite(point.price) || point.price <= 0 || !Number.isFinite(latestPrice)) {
    return null
  }
  const percent = ((latestPrice - point.price) / point.price) * 100
  const rounded = Number(percent.toFixed(2))
  const confirmed = Boolean(point.confirmed)
  return {
    date: formatSignalDate(point.time, '1d'),
    shortDate: formatShortDate(point.time),
    confirmed,
    statusLabel: confirmed ? '已确认' : '待确认',
    percent: rounded,
    formatted: `${rounded >= 0 ? '+' : ''}${rounded.toFixed(2)}%`,
  }
}

export function getLatestPointsBySide(points = []) {
  let buyPoint = null
  let sellPoint = null
  for (let index = points.length - 1; index >= 0; index -= 1) {
    const point = points[index]
    if (!buyPoint && point?.side === 'buy') buyPoint = point
    if (!sellPoint && point?.side === 'sell') sellPoint = point
    if (buyPoint && sellPoint) break
  }
  return { buyPoint, sellPoint }
}

export function formatInvalidationDistance(bias, latestPrice, invalidationPrice, formattedInvalidation) {
  if (!Number.isFinite(invalidationPrice) || !Number.isFinite(latestPrice) || latestPrice <= 0) {
    return '—'
  }
  const diffPercent = ((invalidationPrice - latestPrice) / latestPrice) * 100
  const signed = `${diffPercent >= 0 ? '+' : ''}${diffPercent.toFixed(1)}%`
  const action = bias === 'bearish' ? '收盘突破' : '收盘跌破'
  return `${action} ${formattedInvalidation} 失效 (${signed})`
}

export function evaluateActionConclusion({ bias, latestPrice, latestPoint, signalQuality }) {
  if (latestPoint && (signalQuality?.state === 'invalidated' || signalQuality?.history_status === 'invalidated'
      || signalQuality?.first_reference_crossed_at)) {
    return {
      label: `${latestPoint.side === 'buy' ? '买' : '卖'}点结构已失效`,
      tone: latestPoint.side === 'buy' ? 'neutral' : 'down',
      summary: '已有收盘价越过该信号对应的失效参考，即使价格恢复也不继续沿用原信号',
    }
  }
  if (latestPoint && !latestPoint.confirmed) {
    return {
      label: latestPoint.side === 'buy' ? '候选买点，等待确认' : '候选卖点，注意风险',
      tone: latestPoint.side === 'sell' ? 'down' : 'neutral',
      summary: '结构端点仍可能修正，候选状态不能作为已确认的买卖依据',
    }
  }
  const aligned = latestPoint?.side === (bias === 'bullish' ? 'buy' : bias === 'bearish' ? 'sell' : '')
  if (aligned && signalQuality?.state !== 'confirmed') {
    return {
      label: '结构已确认，证据待补齐',
      tone: bias === 'bearish' ? 'down' : 'neutral',
      summary: '尚不能核对该信号的有效性或当前 K 线未收盘，等待完整评价',
    }
  }
  if (aligned && signalQuality?.history_status !== 'checked_from_pivot') {
    return {
      label: '结构已确认，历史待核对',
      tone: bias === 'bearish' ? 'down' : 'neutral',
      summary: '尚未核对端点起的已收盘 K 线是否曾越界，暂列观察',
    }
  }
  if (aligned) {
    return {
      label: latestPoint.side === 'buy' ? '买点结构已确认' : '卖点结构已确认',
      tone: latestPoint.side === 'buy' ? 'neutral' : 'down',
      summary: '仅代表结构确认，量价辅助条件尚未验证；需另行制定入场、退出与仓位规则',
    }
  }
  if (bias === 'bullish') {
    if (latestPoint?.side === 'sell') {
      if (Number.isFinite(latestPrice) && Number.isFinite(latestPoint.price) && latestPrice > latestPoint.price) {
        return {
          label: '刚突破中枢，等回踩买点',
          tone: 'neutral',
          summary: '现价已站上中枢上沿并突破前期卖点，等待回踩不破确认买点',
        }
      }
      return {
        label: '出现卖点，暂不买入',
        tone: 'down',
        summary: '价格虽在中枢上方，但最近信号为卖点，注意冲高回落风险',
      }
    }
    return {
      label: '偏多结构，等待买点',
      tone: 'neutral',
      summary: '价格位于中枢上方，等待回抽出现买点信号',
    }
  }

  if (bias === 'bearish') {
    if (latestPoint?.side === 'buy') {
      if (Number.isFinite(latestPrice) && Number.isFinite(latestPoint.price) && latestPrice < latestPoint.price) {
        return {
          label: '刚跌破中枢，等反抽确认',
          tone: 'down',
          summary: '现价已跌破中枢下沿及前期买点，结构转弱，暂宜规避',
        }
      }
      return {
        label: '低位买点，观察筑底',
        tone: 'neutral',
        summary: '中枢下方出现买点信号，需收复中枢下沿确认转强',
      }
    }
    return {
      label: '偏空结构，暂不买入',
      tone: 'down',
      summary: '价格位于中枢下方，尚未出现明确买点信号',
    }
  }

  return {
    label: '中枢震荡，暂不操作',
    tone: 'neutral',
    summary: '价格尚未离开笔中枢核心区间或方向未达成一致，等待突破信号',
  }
}
