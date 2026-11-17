import { describe, expect, test } from 'bun:test'
import {
  formatSignedProfit,
  getGrowthStage,
  getTrackingDays,
  sortTrackings,
  summarizeTrackings,
  TRACKING_PRINCIPAL,
} from './tracking.ts'

describe('tracking domain rules', () => {
  test('6-stage botanical growth progression matches day thresholds', () => {
    expect(TRACKING_PRINCIPAL).toBe(10000)
    expect(getTrackingDays('2026-11-17', '2026-11-17')).toBe(0)
    expect(getGrowthStage(0)).toEqual({ key: 'sprout', label: '萌芽', daysText: '今日跟踪' })
    expect(getGrowthStage(6).key).toBe('sprout')
    expect(getGrowthStage(7)).toEqual({ key: 'branch', label: '抽枝', daysText: '已跟踪 7 天' })
    expect(getGrowthStage(30).key).toBe('lush')
    expect(getGrowthStage(90).key).toBe('rooted')
    expect(getGrowthStage(180).key).toBe('canopy')
    expect(getGrowthStage(365).key).toBe('rings')
  })

  test('summarizeTrackings aggregates ¥10,000 simulated principal portfolio', () => {
    const summary = summarizeTrackings([
      { currentValue: 10500, dayProfit: 120, cumulativeProfit: 500 },
      { currentValue: 9800, dayProfit: -50, cumulativeProfit: -200 },
    ])
    expect(summary).toEqual({
      count: 2,
      totalPrincipal: 20000,
      totalValue: 20300,
      dayProfit: 70,
      cumulativeProfit: 300,
      cumulativeReturnPct: 1.5,
    })
    expect(formatSignedProfit(0)).toEqual({ text: '¥0.00', tone: 'flat' })
    expect(formatSignedProfit(120.5)).toEqual({ text: '+¥120.50', tone: 'up' })
    expect(formatSignedProfit(-30)).toEqual({ text: '-¥30.00', tone: 'down' })
  })

  test('sortTrackings keeps pinned assets on top and honors customOrder and metrics', () => {
    const stockStub = (secid: string) => ({ secid, symbol: secid, name: secid, market: '沪A' })
    const items = [
      { stock: stockStub('A'), startDate: '2026-11-15', cumulativeReturnPct: 2.0, dayReturnPct: 1.0 },
      { stock: stockStub('B'), startDate: '2026-10-01', cumulativeReturnPct: 8.5, dayReturnPct: -0.5 },
      { stock: stockStub('C'), startDate: '2026-11-16', cumulativeReturnPct: -1.0, dayReturnPct: 3.0 },
    ]
    expect(sortTrackings(items, { pinnedSecids: ['C'], sortBy: 'cumulative' }).map((i) => i.stock.secid)).toEqual(['C', 'B', 'A'])
    expect(sortTrackings(items, { sortBy: 'day' }).map((i) => i.stock.secid)).toEqual(['C', 'A', 'B'])
    expect(sortTrackings(items, { sortBy: 'days', asOfDate: '2026-11-17' }).map((i) => i.stock.secid)).toEqual(['B', 'A', 'C'])
    expect(sortTrackings(items, { customOrder: ['B', 'C', 'A'] }).map((i) => i.stock.secid)).toEqual(['B', 'C', 'A'])
  })
})
