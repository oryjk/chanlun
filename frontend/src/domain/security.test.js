import { expect, test } from 'bun:test'
import { readMarketRoute } from '../presentation/hooks/useMarketRoute.js'
import { DEFAULT_STOCK, isStarMarket } from './market.js'
import { isMarketOpen } from './marketHours.js'
import { normalizeSecid } from './security.js'

test('normalizes legacy and canonical ids like the backend', () => {
  expect(normalizeSecid('1.600519')).toBe('600519.SH')
  expect(normalizeSecid('0.000001')).toBe('000001.SZ')
  expect(normalizeSecid('0.920118')).toBe('920118.BJ')
  expect(normalizeSecid(' 600519.sh ')).toBe('600519.SH')
  expect(normalizeSecid('116.00700')).toBeNull()
  expect(normalizeSecid('105.AAPL')).toBeNull()
  expect(normalizeSecid('600519.XX')).toBeNull()
})

test('exchange decides star market', () => {
  expect(isStarMarket({ secid: '688981.SH', symbol: '688981', market: '' })).toBe(true)
  expect(isStarMarket({ secid: '688981.SZ', symbol: '688981', market: '' })).toBe(false)
  // 2026-11-10 为周二：北京 10:00 开市、15:30 已收市
  expect(isMarketOpen('600519.SH', new Date('2026-11-10T02:00:00Z'))).toBe(true)
  expect(isMarketOpen('600519.SH', new Date('2026-11-10T07:30:00Z'))).toBe(false)
})

test('legacy route links resolve to canonical ids and unsupported ones fall back', () => {
  const legacy = readMarketRoute('?secid=1.600519&timeframe=1d')
  expect(legacy.stock.secid).toBe('600519.SH')
  expect(legacy.stock.symbol).toBe('600519')
  expect(legacy.resolveStock).toBe(true)
  const us = readMarketRoute('?secid=105.AAPL')
  expect(us.stock).toEqual(DEFAULT_STOCK)
  expect(us.resolveStock).toBe(false)
})
