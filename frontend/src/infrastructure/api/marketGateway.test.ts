import { afterEach, expect, test } from 'bun:test'

process.env.BASE_URL = '/'
if (import.meta.env) {
  (import.meta.env as Record<string, string>).BASE_URL = '/'
}

const { createMarketGateway } = await import('./marketGateway.ts')
const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function mockFetchJson(payload: unknown) {
  globalThis.fetch = (async () => ({
    ok: true,
    json: async () => payload,
  })) as unknown as typeof fetch
}

test('getRankings drops fields the gateway does not map', async () => {
  mockFetchJson({
    generated_at: '2026-11-10T07:35:00Z',
    timeframe: '1d',
    trade_date: '2026-11-10',
    total_count: 100,
    analyzed_count: 98,
    failed_count: 2,
    state: 'ready',
    progress_total: 100,
    progress_processed: 100,
    update_error: null,
    analysis_version: 4,
    internal_note: 'should-be-dropped',
    bullish: [
      {
        rank: 1,
        stock: { secid: '1.600519', symbol: '600519', name: '贵州茅台', market: 'SH', internal_tag: 'x' },
        bias: 'bullish',
        strength: 85,
        latest: 1500,
        change_percent: 1.5,
        invalidation_price: 1450,
        latest_point: {
          kind: 1,
          side: 'buy',
          pen_id: 3,
          candle_index: 50,
          time: '2026-11-08T00:00:00+08:00',
          price: 1460,
          confirmed: true,
          reason: '趋势背驰买点',
          invalidation_price: 1450,
          source_zone_id: 1,
          debug_point: 'drop-me',
        },
        buy_point: null,
        sell_point: null,
        trend: [{ time: '2026-11-10T00:00:00+08:00', price: 1500, extra: 1 }],
        signal_quality: null,
        signal_group: 'actionable',
        internal_debug: 'should-be-dropped',
      },
    ],
    bearish: [],
  })

  const gateway = createMarketGateway()
  const board = await gateway.getRankings(10)
  expect((board.bullish[0] as unknown as Record<string, unknown>).internal_debug).toBeUndefined()
  expect((board as unknown as Record<string, unknown>).internal_note).toBeUndefined()
  expect((board.bullish[0].latest_point as unknown as Record<string, unknown>).debug_point).toBeUndefined()
  expect((board.bullish[0].trend[0] as unknown as Record<string, unknown>).extra).toBeUndefined()
})

test('getRankings defaults fields missing from older backends', async () => {
  mockFetchJson({
    generated_at: '2026-11-10T07:35:00Z',
    timeframe: '1d',
    total_count: 10,
    analyzed_count: 10,
    failed_count: 0,
    state: 'ready',
    progress_total: 10,
    progress_processed: 10,
    bullish: [
      {
        rank: 1,
        stock: { secid: '1.600519', symbol: '600519', name: '贵州茅台', market: 'SH' },
        bias: 'bullish',
        strength: 80,
        latest: 1500,
        change_percent: 1.2,
      },
    ],
  })

  const gateway = createMarketGateway()
  const board = await gateway.getRankings(10)
  expect(board.bullish[0].signal_group).toBe('observation')
  expect(board.bullish[0].signal_quality).toBeNull()
  expect(board.bullish[0].trend).toEqual([])
  expect(board.analysis_version).toBe(1)
})

test('getMarketAssets maps fields cleanly and drops internal debug properties', async () => {
  mockFetchJson({
    category: 'stock',
    as_of_date: '2026-11-17',
    internal_note: 'drop',
    items: [
      {
        stock: { secid: '600519.SH', symbol: '600519', name: '贵州茅台', market: '沪A', extra: 1 },
        latest: 1453.9,
        change: 18.64,
        change_percent: 1.3,
        open: 1435.0,
        high: 1460.0,
        low: 1430.0,
        previous_close: 1435.26,
        volume: 3996700,
        amount: 5767305714,
        sparkline: [1435.26, 1453.9],
        signal_label: '二类买点',
        debug_flag: true,
      },
    ],
  })

  const gateway = createMarketGateway()
  const list = await gateway.getMarketAssets({ category: 'stock', sort: 'amount', order: 'desc', limit: 60 })
  expect(list.asOfDate).toBe('2026-11-17')
  expect(list.items).toHaveLength(1)
  expect(list.items[0].changePercent).toBe(1.3)
  expect(list.items[0].signalLabel).toBe('二类买点')
  expect((list.items[0] as unknown as Record<string, unknown>).debug_flag).toBeUndefined()
  expect((list.items[0].stock as unknown as Record<string, unknown>).extra).toBeUndefined()
})
