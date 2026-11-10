import { formatPrice, getPriceDecimals, isStarMarket } from './market.js'
import {
  evaluateActionConclusion,
  formatInvalidationDistance,
  formatShortDate,
  formatSignalDate,
  getPriceChangeFromPoint,
  pointLabel,
} from './signals.js'

export const DEFAULT_RANKING_PAGE_SIZE = 10

export function splitRankingGroups(entries = []) {
  const confirmed = []
  const observation = []
  for (const entry of entries) {
    (entry.signal_group === 'confirmed' ? confirmed : observation).push(entry)
  }
  return { confirmed, observation }
}

export function getRankingState(board) {
  const hasResult = Boolean(board?.generated_at)
  const updating = board?.state === 'preparing' || board?.state === 'running'
  return {
    hasResult,
    updating,
    progress: board?.progress_total > 0 ? `已处理 ${board.progress_processed} / ${board.progress_total} 只` : '正在准备证券清单',
    coverage: hasResult ? `已分析 ${board.analyzed_count} / ${board.total_count} 只${board.failed_count ? `，${board.failed_count} 只暂无有效收盘行情` : ''}` : '',
  }
}

function buildMarker(point, side, trend, toX, toY, width, height, decimals) {
  if (!point || !Number.isFinite(point.price)) return null
  const targetDate = formatSignalDate(point.time, '1d')
  let index = trend.findIndex((item) => formatSignalDate(item.time, '1d') === targetDate)
  if (index < 0) {
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
  let textAnchor = 'middle'
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

export function buildEntryTrendChart(entry, decimals = 2) {
  const trend = entry?.trend || []
  if (trend.length < 2) return null
  const width = 220
  const height = 54
  const padX = 8
  const padTop = 15
  const padBottom = 15
  const plotWidth = width - padX * 2
  const plotHeight = height - padTop - padBottom

  const buyPoint = entry.buy_point || (entry.latest_point?.side === 'buy' ? entry.latest_point : null)
  const sellPoint = entry.sell_point || (entry.latest_point?.side === 'sell' ? entry.latest_point : null)

  const prices = trend.map((item) => item.price)
  if (buyPoint && Number.isFinite(buyPoint.price)) prices.push(buyPoint.price)
  if (sellPoint && Number.isFinite(sellPoint.price)) prices.push(sellPoint.price)

  const minPrice = Math.min(...prices)
  const maxPrice = Math.max(...prices)
  const span = Math.max(maxPrice - minPrice, 1e-6)

  const toX = (index) => padX + (index / (trend.length - 1)) * plotWidth
  const toY = (price) => padTop + (1 - (price - minPrice) / span) * plotHeight

  const coords = trend.map((item, index) => [Number(toX(index).toFixed(1)), Number(toY(item.price).toFixed(1))])
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

function buildPointChangeDetail(point, latestPrice, decimals) {
  const change = getPriceChangeFromPoint(point, latestPrice)
  if (!change) return null
  return {
    ...change,
    kindLabel: pointLabel(point),
    confirmed: Boolean(point.confirmed),
    priceText: formatPrice(point.price, decimals),
  }
}

export function describeRankingEntry(entry) {
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
  const reference = Number.isFinite(signalInvalidation) ? signalInvalidation : entry.invalidation_price
  const referenceBias = Number.isFinite(signalInvalidation) && entry.latest_point
    ? (entry.latest_point.side === 'sell' ? 'bearish' : 'bullish') : entry.bias
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

export function filterRankingEntries(entries = [], query = '') {
  const keyword = String(query || '').trim().toLowerCase()
  if (!keyword) return entries
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
