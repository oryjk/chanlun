import type { PercentTone, TrackedAssetItem, TrackingSortBy } from './types.ts'

export const TRACKING_PRINCIPAL = 10000

const MS_PER_DAY = 24 * 60 * 60 * 1000

export type BotanicalStageKey = 'sprout' | 'branch' | 'lush' | 'rooted' | 'canopy' | 'rings'

export interface GrowthStageInfo {
  key: BotanicalStageKey
  label: string
  daysText: string
}

export interface SignedProfitDisplay {
  text: string
  tone: PercentTone
}

export interface TrackingPortfolioSummary {
  count: number
  totalPrincipal: number
  totalValue: number
  dayProfit: number
  cumulativeProfit: number
  cumulativeReturnPct: number
}

export interface SortTrackingsOptions {
  sortBy?: TrackingSortBy
  pinnedSecids?: ReadonlyArray<string>
  customOrder?: ReadonlyArray<string>
  asOfDate?: string
}

function parseIsoDayUtc(value: string | null | undefined): number | null {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return null
  return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

export function getTrackingDays(startDate: string, asOfDate: string): number {
  const startMs = parseIsoDayUtc(startDate)
  const endMs = parseIsoDayUtc(asOfDate)
  if (startMs == null || endMs == null) return 0
  return Math.max(0, Math.floor((endMs - startMs) / MS_PER_DAY))
}

export function getGrowthStage(days: number): GrowthStageInfo {
  const safeDays = Math.max(0, Number(days) || 0)
  const daysText = safeDays === 0 ? '今日跟踪' : `已跟踪 ${safeDays} 天`
  if (safeDays < 7) return { key: 'sprout', label: '萌芽', daysText }
  if (safeDays < 30) return { key: 'branch', label: '抽枝', daysText }
  if (safeDays < 90) return { key: 'lush', label: '繁茂', daysText }
  if (safeDays < 180) return { key: 'rooted', label: '扎根', daysText }
  if (safeDays < 365) return { key: 'canopy', label: '成荫', daysText }
  return { key: 'rings', label: '年轮', daysText }
}

export function formatSignedProfit(value: number | null | undefined): SignedProfitDisplay {
  if (value == null || !Number.isFinite(value)) return { text: '—', tone: 'flat' }
  if (Math.abs(value) < 0.005) return { text: '¥0.00', tone: 'flat' }
  if (value > 0) return { text: `+¥${value.toFixed(2)}`, tone: 'up' }
  return { text: `-¥${Math.abs(value).toFixed(2)}`, tone: 'down' }
}

export function summarizeTrackings(
  items: ReadonlyArray<Pick<TrackedAssetItem, 'currentValue' | 'dayProfit' | 'cumulativeProfit'>> = [],
): TrackingPortfolioSummary {
  const count = items.length
  const totalPrincipal = count * TRACKING_PRINCIPAL
  let totalValue = 0
  let dayProfit = 0
  let cumulativeProfit = 0
  for (const item of items) {
    totalValue += Number(item.currentValue) || TRACKING_PRINCIPAL
    dayProfit += Number(item.dayProfit) || 0
    cumulativeProfit += Number(item.cumulativeProfit) || 0
  }
  const cumulativeReturnPct = totalPrincipal > 0 ? (cumulativeProfit / totalPrincipal) * 100 : 0
  return {
    count,
    totalPrincipal,
    totalValue: Number(totalValue.toFixed(2)),
    dayProfit: Number(dayProfit.toFixed(2)),
    cumulativeProfit: Number(cumulativeProfit.toFixed(2)),
    cumulativeReturnPct: Number(cumulativeReturnPct.toFixed(2)),
  }
}

export type SortableTrackedItem = Pick<
  TrackedAssetItem,
  'stock' | 'startDate' | 'cumulativeReturnPct' | 'dayReturnPct'
>

export function sortTrackings<T extends SortableTrackedItem>(
  items: ReadonlyArray<T> = [],
  { sortBy = 'default', pinnedSecids = [], customOrder = [], asOfDate = '' }: SortTrackingsOptions = {},
): T[] {
  const pinSet = new Set(pinnedSecids)
  const orderIndex = new Map<string, number>()
  customOrder.forEach((secid, index) => orderIndex.set(secid, index))
  const originalIndex = new Map<string, number>()
  items.forEach((item, index) => originalIndex.set(item.stock.secid, index))

  const compareWithinGroup = (a: T, b: T): number => {
    if (sortBy === 'cumulative') {
      const diff = (b.cumulativeReturnPct ?? 0) - (a.cumulativeReturnPct ?? 0)
      if (diff !== 0) return diff
    } else if (sortBy === 'day') {
      const diff = (b.dayReturnPct ?? 0) - (a.dayReturnPct ?? 0)
      if (diff !== 0) return diff
    } else if (sortBy === 'days') {
      const diff = getTrackingDays(b.startDate, asOfDate) - getTrackingDays(a.startDate, asOfDate)
      if (diff !== 0) return diff
    }
    const orderA = orderIndex.has(a.stock.secid) ? orderIndex.get(a.stock.secid)! : Number.MAX_SAFE_INTEGER
    const orderB = orderIndex.has(b.stock.secid) ? orderIndex.get(b.stock.secid)! : Number.MAX_SAFE_INTEGER
    if (orderA !== orderB) return orderA - orderB
    return (originalIndex.get(a.stock.secid) ?? 0) - (originalIndex.get(b.stock.secid) ?? 0)
  }

  return [...items].sort((a, b) => {
    const pinnedA = pinSet.has(a.stock.secid) ? 0 : 1
    const pinnedB = pinSet.has(b.stock.secid) ? 0 : 1
    if (pinnedA !== pinnedB) return pinnedA - pinnedB
    return compareWithinGroup(a, b)
  })
}
