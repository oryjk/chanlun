import { describe, expect, test } from 'bun:test'
import { evaluateActionConclusion } from './signals.js'
import { splitRankingGroups } from './rankings.js'

describe('conservative signal conclusions', () => {
  const input = {
    bias: 'bullish', latestPrice: 10.3, invalidationPrice: 9.8,
    latestPoint: { kind: 3, side: 'buy', price: 10, confirmed: false },
  }

  test('a candidate near the buy price remains an observation even if quality claims confirmed', () => {
    const result = evaluateActionConclusion({ ...input, signalQuality: { state: 'confirmed', invalidation_price: 9.8 } })
    expect(result.label).toBe('候选买点，等待确认')
    expect(result.tone).toBe('neutral')
  })

  test('candidate sell points keep risk warnings without becoming confirmed opportunities', () => {
    const result = evaluateActionConclusion({ ...input, bias: 'bearish', latestPoint: { ...input.latestPoint, side: 'sell' } })
    expect(result.label).toBe('候选卖点，注意风险')
    expect(result.tone).toBe('down')
  })

  test('confirmed structure alone never claims calibrated entry quality', () => {
    const result = evaluateActionConclusion({ ...input, latestPoint: { ...input.latestPoint, confirmed: true },
      signalQuality: { state: 'confirmed', invalidation_price: 9.8, history_status: 'checked_from_pivot' } })
    expect(result.label).toBe('买点结构已确认')
    expect(result.summary).toContain('尚未验证')
  })

  test('a known earlier crossing is not revived by recovered price or a stale confirmed state', () => {
    for (const evidence of [{ history_status: 'invalidated' },
      { history_status: 'checked_from_pivot', first_reference_crossed_at: '2026-10-01T00:00:00+08:00' }]) {
      const result = evaluateActionConclusion({ ...input, latestPoint: { ...input.latestPoint, confirmed: true },
        signalQuality: { state: 'confirmed', invalidation_price: 9.8, ...evidence } })
      expect(result.label).toBe('买点结构已失效')
    }
  })

  test('legacy confirmed quality without a checked interval never implies history validity', () => {
    const result = evaluateActionConclusion({ ...input, latestPoint: { ...input.latestPoint, confirmed: true },
      signalQuality: { state: 'confirmed', invalidation_price: 9.8, history_status: 'unknown' } })
    expect(result.label).toBe('结构已确认，历史待核对')
  })

  test('a broken reference overrides attractive old prices and confirmation', () => {
    const result = evaluateActionConclusion({ ...input, signalQuality: { state: 'invalidated', invalidation_price: 10.5 } })
    expect(result.label).toBe('买点结构已失效')
  })

  test('legacy confirmed points remain observations until evidence is available', () => {
    const result = evaluateActionConclusion({ ...input, latestPoint: { ...input.latestPoint, confirmed: true } })
    expect(result.label).toBe('结构已确认，证据待补齐')
  })
})

test('ranking groups use backend classification and never fill the confirmed group from candidates', () => {
  const confirmed = { signal_group: 'confirmed' }
  const candidate = { signal_group: 'observation', latest_point: { confirmed: false } }
  const legacy = { latest_point: { confirmed: true } }
  expect(splitRankingGroups([candidate, legacy, confirmed])).toEqual({ confirmed: [confirmed], observation: [candidate, legacy] })
  expect(splitRankingGroups([candidate]).confirmed).toHaveLength(0)
})
