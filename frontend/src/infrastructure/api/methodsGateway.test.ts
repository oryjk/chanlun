import { afterEach, expect, test } from 'bun:test'

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

test('reads the overnight snapshot for guests and maps signal, evidence and reminder data', async () => {
  const { createMethodsGateway } = await import('./methodsGateway.ts')
  const controller = new AbortController()
  globalThis.fetch = (async (url, options) => {
    expect(url).toBe('/api/methods/overnight')
    expect(options?.method).toBe('GET')
    expect(options?.signal).toBe(controller.signal)
    return Response.json({
      trade_date: '2026-11-19',
      phase: 'exit',
      observed_at: '2026-11-19T10:00:00+08:00',
      history_sessions: 20,
      universe_count: 5612,
      fresh_count: 5390,
      matched_count: 2,
      minute_checked_count: 1,
      elapsed_ms: 4500,
      max_quote_age_seconds: 15,
      reminders_until: '2026-11-19T11:30:00+08:00',
      status: 'partial',
      candidates: [{
        secid: '000001.SZ',
        symbol: '000001',
        name: '平安银行',
        price: 12.35,
        change_percent: 3.67,
        volume_ratio: 1.44,
        turnover_rate: 8.24,
        total_market_cap: 12_140_000_000,
        limit_up_date: '2026-11-04',
        as_of: '2026-11-18T14:54:45+08:00',
        average_price: 12.29,
        minute_change_percent: 3.42,
        minute_at: '2026-11-18T14:54:00+08:00',
        benchmark_change_percent: 0,
        pullback_at: null,
        status: 'watching',
        reason: null,
        debug: 'drop-me',
      }],
      reminders: [{
        secid: '600519.SH',
        symbol: '600519',
        name: '贵州茅台',
        signal_date: '2026-11-18',
        signal_at: '2026-11-18T14:50:00+08:00',
        exception_observed: true,
        exception_as_of: '2026-11-19T09:59:00+08:00',
        exception_valid_until: '2026-11-19T10:01:00+08:00',
      }],
    })
  }) as typeof fetch

  const snapshot = await createMethodsGateway().getOvernightSnapshot(controller.signal)
  expect(snapshot.tradeDate).toBe('2026-11-19')
  expect(snapshot.freshCount).toBe(5390)
  expect(snapshot.maxQuoteAgeSeconds).toBe(15)
  expect(snapshot.remindersUntil).toBe('2026-11-19T11:30:00+08:00')
  expect(snapshot.status).toBe('partial')
  expect(snapshot.candidates).toEqual([{
    stock: { secid: '000001.SZ', symbol: '000001', name: '平安银行', market: '深A' },
    price: 12.35,
    changePercent: 3.67,
    volumeRatio: 1.44,
    turnoverRate: 8.24,
    totalMarketCap: 12_140_000_000,
    limitUpDate: '2026-11-04',
    asOf: '2026-11-18T14:54:45+08:00',
    averagePrice: 12.29,
    minuteChangePercent: 3.42,
    minuteAt: '2026-11-18T14:54:00+08:00',
    benchmarkChangePercent: 0,
    pullbackAt: null,
    status: 'watching',
    reason: null,
  }])
  expect(snapshot.reminders[0]).toMatchObject({
    signalDate: '2026-11-18',
    signalAt: '2026-11-18T14:50:00+08:00',
    exceptionObserved: true,
    exceptionAsOf: '2026-11-19T09:59:00+08:00',
    exceptionValidUntil: '2026-11-19T10:01:00+08:00',
  })
})
