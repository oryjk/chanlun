export const DEFAULT_STOCK = {
  secid: '1.600519',
  symbol: '600519',
  name: '贵州茅台',
  market: '沪A',
}

export function getLatestPen(analysis) {
  return analysis?.pens[analysis.pens.length - 1] ?? null
}

export function getLatestZhongshu(analysis) {
  return analysis?.zhongshu[analysis.zhongshu.length - 1] ?? null
}

export function getQuoteDirection(summary) {
  return (summary?.change ?? 0) >= 0 ? 'up' : 'down'
}

export const PRICE_DECIMALS = 2
export const ETF_PRICE_DECIMALS = 3

// 股票报价保留 2 位，ETF 最小变动单位为 0.001，保留 3 位
export function getPriceDecimals(stock) {
  return stock?.market === 'ETF' ? ETF_PRICE_DECIMALS : PRICE_DECIMALS
}

export function formatPrice(value, decimals = PRICE_DECIMALS) {
  return Number(value).toFixed(decimals)
}

export function isStarMarket(stock) {
  const symbol = String(stock?.symbol || '')
  const secid = String(stock?.secid || '')
  const isShanghai = stock?.market === '沪A' || secid.startsWith('1.') || !stock?.market
  return isShanghai && (symbol.startsWith('688') || symbol.startsWith('689'))
}
