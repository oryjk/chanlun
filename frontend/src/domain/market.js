export const DEFAULT_STOCK = {
  secid: '1.600519',
  symbol: '600519',
  name: '贵州茅台',
  market: '沪A',
}

export function getLatestPen(analysis) {
  return analysis?.pens.at(-1) ?? null
}

export function getLatestZhongshu(analysis) {
  return analysis?.zhongshu.at(-1) ?? null
}

export function getQuoteDirection(summary) {
  return (summary?.change ?? 0) >= 0 ? 'up' : 'down'
}
