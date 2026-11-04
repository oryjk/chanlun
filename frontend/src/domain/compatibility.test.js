import { expect, test } from 'bun:test'
import { getLatestPen, getLatestZhongshu } from './market.js'
import { getLatestDivergence } from './signals.js'
import { mergeQuoteSeries } from './quoteSeries.js'

test('不支持 Array.at 的浏览器仍可读取最新行情结构与合并分时', () => {
  const originalAt = Array.prototype.at
  try {
    Array.prototype.at = undefined
    const pen = { id: 2 }
    const zone = { id: 3 }
    const divergence = { kind: 'trend' }
    expect(getLatestPen({ pens: [{ id: 1 }, pen] })).toBe(pen)
    expect(getLatestZhongshu({ zhongshu: [zone] })).toBe(zone)
    expect(getLatestDivergence({ divergences: [divergence] })).toBe(divergence)
    expect(getLatestPen({ pens: [] })).toBe(null)
    expect(getLatestZhongshu(undefined)).toBe(null)
    expect(getLatestDivergence({ divergences: [] })).toBe(null)
    expect(mergeQuoteSeries(
      { secid: '1.600519', points: [{ time: '2026-11-03T01:30:00Z', price: 1 }] },
      { secid: '1.600519', points: [{ time: '2026-11-03T01:30:00Z', price: 2 }] },
    )).toEqual({ secid: '1.600519', points: [{ time: '2026-11-03T01:30:00Z', price: 2 }] })
  } finally {
    Array.prototype.at = originalAt
  }
})
