import { formatPrice, getPriceDecimals, isStarMarket } from './market.ts'
import {
  evaluateActionConclusion,
  formatInvalidationDistance,
  formatShortDate,
  formatSignalDate,
  getPriceChangeFromPoint,
  pointLabel,
  type ActionConclusion,
  type PointChangeDetail,
} from './signals.ts'
import type { BuySellPoint, RankingBoard, RankingEntry, SignalQuality } from './types.ts'

export const DEFAULT_RANKING_PAGE_SIZE = 10

export type RankingMarketFilter = 'all' | 'a_share' | 'etf' | 'star'

export interface RankingGroupSplit<T extends { signal_group?: string }> {
  confirmed: T[]
  observation: T[]
}

export function splitRankingGroups<T extends { signal_group?: string }>(entries: ReadonlyArray<T> = []): RankingGroupSplit<T> {
  const confirmed: T[] = []
  const observation: T[] = []
  for (const entry of entries) {
    ;(entry.signal_group === 'confirmed' ? confirmed : observation).push(entry)
  }
  return { confirmed, observation }
}

export interface RankingStateView {
  hasResult: boolean
  updating: boolean
  progress: string
  coverage: string
}

export function getRankingState(board: Partial<RankingBoard> | null | undefined): RankingStateView {
  const hasResult = Boolean(board?.generated_at)
  const updating = board?.state === 'preparing' || board?.state === 'running'
  return {
    hasResult,
    updating,
    progress: (board?.progress_total ?? 0) > 0
      ? `已处理 ${board?.progress_processed ?? 0} / ${board?.progress_total} 只`
      : '正在准备证券清单',
    coverage: hasResult
      ? `已分析 ${board?.analyzed_count ?? 0} / ${board?.total_count ?? 0} 只${board?.failed_count ? `，${board.failed_count} 只暂无有效收盘行情` : ''}`
      : '',
  }
}

export interface TrendChartMarker {
  side: 'buy' | 'sell'
  x: number
  y: number
  labelX: number
  labelY: number
  lineY: number
  textAnchor: 'start' | 'middle' | 'end'
  text: string
  date: string
  shortDate: string
  priceText: string
}

export interface EntryTrendChartGeometry {
  width: number
  height: number
  polyline: string
  areaPath: string
  lastX: number
  lastY: number
  buyMarker: TrendChartMarker | null
  sellMarker: TrendChartMarker | null
}

function buildMarker(
  point: Partial<BuySellPoint> | null | undefined,
  side: 'buy' | 'sell',
  trend: ReadonlyArray<{ time: string; price: number }>,
  toX: (index: number) => number,
  toY: (price: number) => number,
  width: number,
  height: number,
  decimals: number,
): TrendChartMarker | null {
  if (!point || point.price == null || !Number.isFinite(point.price)) return null
  const targetDate = formatSignalDate(point.time, '1d')
  let index = trend.findIndex((item) => formatSignalDate(item.time, '1d') === targetDate)
  if (index < 0 && point.time) {
    const targetMs = new Date(point.time).getTime()
    let bestDiff = Infinity
    trend.forEach((item, idx) => {
      const diff = Math.abs(new Date(item.time).getTime() - targetMs)
      if (diff < bestDiff) {
        bestDiff = diff
        index = idx
      }
    })
  }
  if (index < 0) return null
  const x = Number(toX(index).toFixed(1))
  const y = Number(toY(point.price).toFixed(1))
  const shortDate = formatShortDate(point.time)
  const priceText = formatPrice(point.price, decimals)
  const prefix = side === 'buy' ? '买' : '卖'
  const text = `${prefix} ${priceText} (${shortDate})`
  let textAnchor: 'start' | 'middle' | 'end' = 'middle'
  let labelX = x
  if (x < 62) {
    textAnchor = 'start'
    labelX = Math.max(2, x - 3)
  } else if (x > width - 62) {
    textAnchor = 'end'
    labelX = Math.min(width - 2, x + 3)
  }
  const labelY = side === 'sell' ? 10 : height - 3
  const lineY = side === 'sell' ? 12 : height - 12
  return {
    side,
    x,
    y,
    labelX: Number(labelX.toFixed(1)),
    labelY,
    lineY,
    textAnchor,
    text,
    date: targetDate,
    shortDate,
    priceText,
  }
}

export type RankingChartInput = Pick<Partial<RankingEntry>, 'trend' | 'buy_point' | 'sell_point' | 'latest_point'>

export function buildEntryTrendChart(entry: RankingChartInput | null | undefined, decimals = 2): EntryTrendChartGeometry | null {
  const trend = entry?.trend || []
  if (trend.length < 2) return null
  const width = 220
  const height = 54
  const padX = 8
  const padTop = 15
  const padBottom = 15
  const plotWidth = width - padX * 2
  const plotHeight = height - padTop - padBottom

  const buyPoint = entry?.buy_point || (entry?.latest_point?.side === 'buy' ? entry.latest_point : null)
  const sellPoint = entry?.sell_point || (entry?.latest_point?.side === 'sell' ? entry.latest_point : null)

  const prices = trend.map((item) => item.price)
  if (buyPoint && buyPoint.price != null && Number.isFinite(buyPoint.price)) prices.push(buyPoint.price)
  if (sellPoint && sellPoint.price != null && Number.isFinite(sellPoint.price)) prices.push(sellPoint.price)

  const minPrice = Math.min(...prices)
  const maxPrice = Math.max(...prices)
  const span = Math.max(maxPrice - minPrice, 1e-6)

  const toX = (index: number) => padX + (index / (trend.length - 1)) * plotWidth
  const toY = (price: number) => padTop + (1 - (price - minPrice) / span) * plotHeight

  const coords = trend.map((item, index): [number, number] => [
    Number(toX(index).toFixed(1)),
    Number(toY(item.price).toFixed(1)),
  ])
  const polyline = coords.map(([x, y]) => `${x},${y}`).join(' ')
  const first = coords[0]
  const last = coords[coords.length - 1]
  const areaPath = `M ${first[0]} ${padTop + plotHeight} L ${coords.map(([x, y]) => `${x} ${y}`).join(' L ')} L ${last[0]} ${padTop + plotHeight} Z`

  return {
    width,
    height,
    polyline,
    areaPath,
    lastX: last[0],
    lastY: last[1],
    buyMarker: buildMarker(buyPoint, 'buy', trend, toX, toY, width, height, decimals),
    sellMarker: buildMarker(sellPoint, 'sell', trend, toX, toY, width, height, decimals),
  }
}

export interface PointChangeWithLabel extends PointChangeDetail {
  kindLabel: string
  priceText: string
}

function buildPointChangeDetail(
  point: Partial<BuySellPoint> | null | undefined,
  latestPrice: number | null | undefined,
  decimals: number,
): PointChangeWithLabel | null {
  const change = getPriceChangeFromPoint(point, latestPrice)
  if (!change || !point || point.kind == null || !point.side || point.price == null) return null
  return {
    ...change,
    kindLabel: pointLabel({ kind: point.kind, side: point.side }),
    confirmed: Boolean(point.confirmed),
    priceText: formatPrice(point.price, decimals),
  }
}

export interface RankingEntryInput {
  rank?: number
  stock?: Partial<RankingEntry['stock']>
  bias: string
  strength?: number
  latest: number
  change_percent?: number
  invalidation_price: number | null
  latest_point: BuySellPoint | null
  buy_point?: BuySellPoint | null
  sell_point?: BuySellPoint | null
  trend?: Array<{ time: string; price: number }>
  signal_quality?: Partial<SignalQuality> | null
}

export interface RankingEntryDescription {
  decimals: number
  isStar: boolean
  conclusion: ActionConclusion
  buyChange: PointChangeWithLabel | null
  sellChange: PointChangeWithLabel | null
  invalidationText: string
  chart: EntryTrendChartGeometry | null
}

export function describeRankingEntry(entry: RankingEntryInput): RankingEntryDescription {
  const decimals = getPriceDecimals(entry.stock)
  const buyPoint = entry.buy_point || (entry.latest_point?.side === 'buy' ? entry.latest_point : null)
  const sellPoint = entry.sell_point || (entry.latest_point?.side === 'sell' ? entry.latest_point : null)
  const conclusion = evaluateActionConclusion({
    bias: entry.bias,
    latestPrice: entry.latest,
    invalidationPrice: entry.invalidation_price,
    latestPoint: entry.latest_point,
    signalQuality: entry.signal_quality,
  })
  const signalInvalidation = entry.signal_quality?.invalidation_price
  const reference = signalInvalidation != null && Number.isFinite(signalInvalidation)
    ? signalInvalidation
    : entry.invalidation_price
  const referenceBias = signalInvalidation != null && Number.isFinite(signalInvalidation) && entry.latest_point
    ? (entry.latest_point.side === 'sell' ? 'bearish' : 'bullish')
    : entry.bias
  const formattedInvalidation = reference == null
    ? '—'
    : formatPrice(reference, decimals)
  return {
    decimals,
    isStar: isStarMarket(entry.stock),
    conclusion,
    buyChange: buildPointChangeDetail(buyPoint, entry.latest, decimals),
    sellChange: buildPointChangeDetail(sellPoint, entry.latest, decimals),
    invalidationText: formatInvalidationDistance(
      referenceBias,
      entry.latest,
      reference,
      formattedInvalidation,
    ),
    chart: buildEntryTrendChart(entry, decimals),
  }
}

export function filterRankingEntries<T extends RankingEntryInput>(entries: ReadonlyArray<T> = [], query = ''): T[] {
  const keyword = String(query || '').trim().toLowerCase()
  if (!keyword) return [...entries]
  return entries.filter((entry) => {
    const stock = entry.stock || {}
    const conclusion = evaluateActionConclusion({
      bias: entry.bias,
      latestPrice: entry.latest,
      invalidationPrice: entry.invalidation_price,
      latestPoint: entry.latest_point,
      signalQuality: entry.signal_quality,
    })
    const starLabel = isStarMarket(stock) ? '科创板' : ''
    const confirmLabel = entry.latest_point ? (entry.latest_point.confirmed ? '已确认' : '待确认') : ''
    const haystack = `${stock.symbol || ''} ${stock.name || ''} ${stock.market || ''} ${starLabel} ${confirmLabel} ${conclusion.label}`.toLowerCase()
    return haystack.includes(keyword)
  })
}

export function filterRankingByMarket<T extends { stock?: Partial<RankingEntry['stock']> }>(
  entries: ReadonlyArray<T> = [],
  marketFilter: RankingMarketFilter = 'all',
): T[] {
  if (!marketFilter || marketFilter === 'all') return [...entries]
  return entries.filter((entry) => {
    const stock = entry?.stock
    if (marketFilter === 'etf') return stock?.market === 'ETF'
    if (marketFilter === 'star') return isStarMarket(stock)
    if (marketFilter === 'a_share') return stock?.market !== 'ETF'
    return true
  })
}
