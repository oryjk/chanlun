import { test, expect } from 'bun:test'
import { buildEntryTrendChart, DEFAULT_RANKING_PAGE_SIZE, describeRankingEntry, filterRankingEntries, getRankingState } from './rankings.js'

test('an initial pending response is not a completed empty ranking', () => {
  const state = getRankingState({ generated_at: null, state: 'preparing', progress_total: 7000, progress_processed: 100 })
  expect(state.hasResult).toBe(false)
  expect(state.updating).toBe(true)
  expect(state.progress).toBe('已处理 100 / 7000 只')
})

test('a previous completed ranking remains visible while the next day is calculated', () => {
  const state = getRankingState({ generated_at: '2026-11-03T08:30:00Z', state: 'running', trade_date: '2026-11-03', total_count: 7000, analyzed_count: 6990, failed_count: 10 })
  expect(state.hasResult).toBe(true)
  expect(state.updating).toBe(true)
  expect(state.coverage).toBe('已分析 6990 / 7000 只，10 只暂无有效收盘行情')
})

test('describeRankingEntry produces direct conclusion, buy/sell dates, relative changes, and trend chart', () => {
  const entry = {
    rank: 1,
    stock: { secid: '1.688410', symbol: '688410', name: '山外山', market: '沪A' },
    bias: 'bullish',
    strength: 4,
    latest: 10.36,
    change_percent: 2.07,
    invalidation_price: 9.932,
    latest_point: { kind: 3, side: 'buy', time: '2026-11-03T00:00:00+08:00', price: 10.04, confirmed: false },
    buy_point: { kind: 3, side: 'buy', time: '2026-11-03T00:00:00+08:00', price: 10.04, confirmed: false },
    sell_point: { kind: 3, side: 'sell', time: '2026-09-23T00:00:00+08:00', price: 9.932, confirmed: true },
    trend: [
      { time: '2026-09-23T00:00:00+08:00', price: 9.932 },
      { time: '2026-10-15T00:00:00+08:00', price: 9.6 },
      { time: '2026-11-03T00:00:00+08:00', price: 10.04 },
      { time: '2026-11-04T00:00:00+08:00', price: 10.36 },
    ],
  }
  const info = describeRankingEntry(entry)
  expect(info.conclusion.label).toBe('可关注买入')
  expect(info.buyChange.date).toBe('2026-11-03')
  expect(info.buyChange.statusLabel).toBe('待确认')
  expect(info.buyChange.formatted).toBe('+3.19%')
  expect(info.sellChange.date).toBe('2026-09-23')
  expect(info.sellChange.statusLabel).toBe('已确认')
  expect(info.sellChange.formatted).toBe('+4.31%')
  expect(info.invalidationText).toBe('跌破 9.93 失效 (-4.1%)')
  expect(info.chart.buyMarker.text).toBe('买 10.04 (11-03)')
  expect(info.chart.sellMarker.text).toBe('卖 9.93 (09-23)')
})

test('describeRankingEntry warns against chasing when price is far above buy point', () => {
  const entry = {
    rank: 1,
    stock: { secid: '1.688220', symbol: '688220', name: '翱捷科技', market: '沪A' },
    bias: 'bullish',
    strength: 4,
    latest: 109.75,
    change_percent: 16.93,
    invalidation_price: 92.8,
    latest_point: { kind: 1, side: 'buy', time: '2026-10-27T00:00:00+08:00', price: 81.0, confirmed: false },
    buy_point: { kind: 1, side: 'buy', time: '2026-10-27T00:00:00+08:00', price: 81.0, confirmed: false },
    sell_point: null,
    trend: [],
  }
  const info = describeRankingEntry(entry)
  expect(info.conclusion.label).toBe('已涨高，不宜追买')
  expect(info.buyChange.formatted).toBe('+35.49%')
  expect(info.isStar).toBe(true)
  expect(buildEntryTrendChart(entry)).toBeNull()
})

test('filterRankingEntries matches symbol, name, STAR market tag, and conclusion label', () => {
  expect(DEFAULT_RANKING_PAGE_SIZE).toBe(10)
  const entries = [
    {
      rank: 1,
      stock: { secid: '1.688410', symbol: '688410', name: '山外山', market: '沪A' },
      bias: 'bullish',
      strength: 4,
      latest: 10.36,
      change_percent: 2.07,
      invalidation_price: 9.932,
      latest_point: { kind: 3, side: 'buy', time: '2026-11-03T00:00:00+08:00', price: 10.04, confirmed: false },
    },
    {
      rank: 2,
      stock: { secid: '0.300012', symbol: '300012', name: '华测检测', market: '深A' },
      bias: 'bullish',
      strength: 4,
      latest: 15.91,
      change_percent: 1.73,
      invalidation_price: 14.3425,
      latest_point: { kind: 3, side: 'buy', time: '2026-11-02T00:00:00+08:00', price: 14.95, confirmed: false },
    },
  ]
  expect(filterRankingEntries(entries, '688')).toHaveLength(1)
  expect(filterRankingEntries(entries, '科创')).toHaveLength(1)
  expect(filterRankingEntries(entries, '华测')).toHaveLength(1)
  expect(filterRankingEntries(entries, '可关注')).toHaveLength(2)
})

test('describeRankingEntry explains breakout waiting for pullback when bullish entry already reclaimed old sell point', () => {
  const entry = {
    rank: 90,
    stock: { secid: '1.512290', symbol: '512290', name: '生物医药ETF国泰', market: 'ETF' },
    bias: 'bullish',
    strength: 4,
    latest: 1.015,
    change_percent: 3.15,
    invalidation_price: 1.006,
    latest_point: { kind: 3, side: 'sell', time: '2026-04-02T00:00:00+08:00', price: 1.006, confirmed: true },
    buy_point: null,
    sell_point: { kind: 3, side: 'sell', time: '2026-04-02T00:00:00+08:00', price: 1.006, confirmed: true },
    trend: [],
  }
  const info = describeRankingEntry(entry)
  expect(info.conclusion.label).toBe('刚突破中枢，等回踩买点')
  expect(info.conclusion.tone).toBe('neutral')
})
