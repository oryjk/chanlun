import { expect, test } from 'bun:test'
import { getLatestPen, getLatestZhongshu } from './market.ts'
import { getLatestDivergence } from './signals.ts'
import { mergeQuoteSeries } from './quoteSeries.ts'
import type { Divergence, Pen, Zhongshu } from './types.ts'

test('不支持 Array.at 的浏览器仍可读取最新行情结构与合并分时', () => {
  const proto = Array.prototype as unknown as { at?: unknown }
  const originalAt = proto.at
  try {
    proto.at = undefined
    const pen = { id: 2 } as Pen
    const zone = { id: 3 } as Zhongshu
    const divergence = { kind: 'trend' } as Divergence
    expect(getLatestPen({ pens: [{ id: 1 } as Pen, pen] })).toBe(pen)
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
    proto.at = originalAt
  }
})
