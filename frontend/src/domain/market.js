import { exchangeOf } from './security.js'

export const DEFAULT_STOCK = {
  secid: '600519.SH',
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

export function getCandleChange(candles, index, decimals = PRICE_DECIMALS) {
  const candle = candles?.[index]
  if (!candle || !Number.isFinite(candle.close)) {
    return null
  }
  const previous = index > 0 ? candles[index - 1] : null
  const basePrice = Number.isFinite(previous?.close) && previous.close > 0 ? previous.close : candle.open
  if (!Number.isFinite(basePrice) || basePrice <= 0) {
    return null
  }
  const rawChange = candle.close - basePrice
  const change = Math.abs(rawChange) < 1e-9 ? 0 : Number(rawChange.toFixed(4))
  const rawPercent = (rawChange / basePrice) * 100
  const changePercent = Math.abs(rawPercent) < 1e-9 ? 0 : Number(rawPercent.toFixed(2))
  const direction = change >= 0 ? 'up' : 'down'
  const sign = change >= 0 ? '+' : ''
  return {
    change,
    changePercent,
    direction,
    formattedChange: `${sign}${formatPrice(change, decimals)}`,
    formattedPercent: `${sign}${changePercent.toFixed(2)}%`,
  }
}

export function isStarMarket(stock) {
  const symbol = String(stock?.symbol || '')
  const secid = String(stock?.secid || '')
  return exchangeOf(secid) === 'SH' && (symbol.startsWith('688') || symbol.startsWith('689'))
}
