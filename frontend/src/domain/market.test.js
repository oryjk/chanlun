import { describe, expect, test } from 'bun:test'
import { formatPrice, getCandleChange, getPriceDecimals, isStarMarket } from './market.js'

describe('isStarMarket', () => {
  test('识别 688 与 689 开头的科创板股票', () => {
    expect(isStarMarket({ secid: '1.688410', symbol: '688410', market: '沪A' })).toBe(true)
    expect(isStarMarket({ secid: '1.689009', symbol: '689009', market: '沪A' })).toBe(true)
    expect(isStarMarket({ secid: '1.600519', symbol: '600519', market: '沪A' })).toBe(false)
    expect(isStarMarket({ secid: '0.300012', symbol: '300012', market: '深A' })).toBe(false)
  })
})

describe('formatPrice', () => {
  test('默认保留两位小数', () => {
    expect(formatPrice(1480)).toBe('1480.00')
    expect(formatPrice(3.671)).toBe('3.67')
    expect(formatPrice('40.9')).toBe('40.90')
  })

  test('可指定位数', () => {
    expect(formatPrice(3.671, 3)).toBe('3.671')
  })
})

describe('getPriceDecimals', () => {
  test('ETF 保留三位，股票与未识别证券保留两位', () => {
    expect(getPriceDecimals({ market: 'ETF' })).toBe(3)
    expect(getPriceDecimals({ market: '沪A' })).toBe(2)
    expect(getPriceDecimals({ market: '' })).toBe(2)
    expect(getPriceDecimals(null)).toBe(2)
  })
})

describe('getCandleChange', () => {
  const candles = [
    { open: 10, close: 10.5 },
    { open: 10.5, close: 11.025 },
    { open: 11.0, close: 10.47375 },
  ]

  test('相对前一根 K 线收盘价计算涨跌额与涨跌幅', () => {
    expect(getCandleChange(candles, 1)).toEqual({
      change: 0.525,
      changePercent: 5,
      direction: 'up',
      formattedChange: '+0.53',
      formattedPercent: '+5.00%',
    })
    expect(getCandleChange(candles, 2, 3)).toEqual({
      change: -0.5512,
      changePercent: -5,
      direction: 'down',
      formattedChange: '-0.551',
      formattedPercent: '-5.00%',
    })
  })

  test('首根 K 线回退使用自身开盘价作为基准', () => {
    expect(getCandleChange(candles, 0)).toEqual({
      change: 0.5,
      changePercent: 5,
      direction: 'up',
      formattedChange: '+0.50',
      formattedPercent: '+5.00%',
    })
  })
})
