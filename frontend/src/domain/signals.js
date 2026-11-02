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
  return signals?.divergences.at(-1) ?? null
}

export function divergenceLabel(divergence) {
  return `${divergence.kind === 'trend' ? '趋势' : '盘整'}${divergence.direction === 'bottom' ? '底' : '顶'}背驰`
}
