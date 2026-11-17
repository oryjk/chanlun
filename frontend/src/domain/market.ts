import { exchangeOf } from './security.ts'
import type { ChanAnalysis, Pen, PercentTone, Stock, Zhongshu } from './types.ts'

export const DEFAULT_STOCK: Stock = {
  secid: '600519.SH',
  symbol: '600519',
  name: '贵州茅台',
  market: '沪A',
}

export function getLatestPen(analysis?: Pick<ChanAnalysis, 'pens'> | null): Pen | null {
  return analysis?.pens[analysis.pens.length - 1] ?? null
}

export function getLatestZhongshu(analysis?: Pick<ChanAnalysis, 'zhongshu'> | null): Zhongshu | null {
  return analysis?.zhongshu[analysis.zhongshu.length - 1] ?? null
}

export function getQuoteDirection(summary?: { change?: number } | null): 'up' | 'down' {
  return (summary?.change ?? 0) >= 0 ? 'up' : 'down'
}

export const PRICE_DECIMALS = 2
export const ETF_PRICE_DECIMALS = 3

// 股票报价保留 2 位，ETF 最小变动单位为 0.001，保留 3 位
export function getPriceDecimals(stock?: Partial<Stock> | null): number {
  return stock?.market === 'ETF' ? ETF_PRICE_DECIMALS : PRICE_DECIMALS
}

export function formatPrice(value: number | string, decimals = PRICE_DECIMALS): string {
  return Number(value).toFixed(decimals)
}

export interface CandleChangeInfo {
  change: number
  changePercent: number
  direction: 'up' | 'down'
  formattedChange: string
  formattedPercent: string
}

export function getCandleChange(
  candles: ReadonlyArray<{ open: number; close: number }> | undefined,
  index: number,
  decimals = PRICE_DECIMALS,
): CandleChangeInfo | null {
  const candle = candles?.[index]
  if (!candle || !Number.isFinite(candle.close)) {
    return null
  }
  const previous = index > 0 ? candles?.[index - 1] : null
  const basePrice = previous && Number.isFinite(previous.close) && previous.close > 0 ? previous.close : candle.open
  if (!Number.isFinite(basePrice) || basePrice <= 0) {
    return null
  }
  const rawChange = candle.close - basePrice
  const change = Math.abs(rawChange) < 1e-9 ? 0 : Number(rawChange.toFixed(4))
  const rawPercent = (rawChange / basePrice) * 100
  const changePercent = Math.abs(rawPercent) < 1e-9 ? 0 : Number(rawPercent.toFixed(2))
  const direction: 'up' | 'down' = change >= 0 ? 'up' : 'down'
  const sign = change >= 0 ? '+' : ''
  return {
    change,
    changePercent,
    direction,
    formattedChange: `${sign}${formatPrice(change, decimals)}`,
    formattedPercent: `${sign}${changePercent.toFixed(2)}%`,
  }
}

export function isStarMarket(stock?: Partial<Stock> | null): boolean {
  const symbol = String(stock?.symbol || '')
  const secid = String(stock?.secid || '')
  return exchangeOf(secid) === 'SH' && (symbol.startsWith('688') || symbol.startsWith('689'))
}

export function formatTurnover(amount: number | null | undefined): string {
  if (amount == null || !Number.isFinite(amount) || amount <= 0) {
    return '—'
  }
  if (amount >= 1e8) {
    return `${(amount / 1e8).toFixed(2)} 亿`
  }
  if (amount >= 1e4) {
    return `${(amount / 1e4).toFixed(1)} 万`
  }
  return `${Math.round(amount)}`
}

export interface SignedPercentDisplay {
  text: string
  tone: PercentTone
}

export function formatSignedPercent(value: number | null | undefined): SignedPercentDisplay {
  if (value == null || !Number.isFinite(value)) {
    return { text: '—', tone: 'flat' }
  }
  if (Math.abs(value) < 0.005) {
    return { text: '0.00%', tone: 'flat' }
  }
  if (value > 0) {
    return { text: `+${value.toFixed(2)}%`, tone: 'up' }
  }
  return { text: `${value.toFixed(2)}%`, tone: 'down' }
}

export interface SparklineGeometry {
  width: number
  height: number
  tone: PercentTone
  polyline: string
  areaPath: string
  lastX: number
  lastY: number
}

export function buildSparklineGeometry(
  values: ReadonlyArray<number> = [],
  width = 68,
  height = 24,
): SparklineGeometry | null {
  const numeric = (values || []).filter((item) => Number.isFinite(item))
  if (numeric.length < 2) {
    return null
  }
  const padX = 2
  const padY = 3
  const plotWidth = width - padX * 2
  const plotHeight = height - padY * 2
  const minVal = Math.min(...numeric)
  const maxVal = Math.max(...numeric)
  const span = Math.max(maxVal - minVal, 1e-6)
  const coords = numeric.map((value, index): [number, number] => {
    const x = padX + (index / (numeric.length - 1)) * plotWidth
    const y = padY + (1 - (value - minVal) / span) * plotHeight
    return [Number(x.toFixed(1)), Number(y.toFixed(1))]
  })
  const first = coords[0]
  const last = coords[coords.length - 1]
  const firstVal = numeric[0]
  const lastVal = numeric[numeric.length - 1]
  const diff = lastVal - firstVal
  const tone: PercentTone = Math.abs(diff) < 1e-6 ? 'flat' : diff > 0 ? 'up' : 'down'
  return {
    width,
    height,
    tone,
    polyline: coords.map(([x, y]) => `${x},${y}`).join(' '),
    areaPath: `M ${first[0]} ${(padY + plotHeight).toFixed(1)} L ${coords.map(([x, y]) => `${x} ${y}`).join(' L ')} L ${last[0]} ${(padY + plotHeight).toFixed(1)} Z`,
    lastX: last[0],
    lastY: last[1],
  }
}
