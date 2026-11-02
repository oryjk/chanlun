import { describe, expect, test } from 'bun:test'
import {
  getSessionBreaks,
  getTradingDuration,
  isMarketOpen,
  toTradingMinute,
  tradingMinuteToClock,
} from './marketHours.js'

// 2026-11-02 为周一；上海时间 = UTC+8
const shanghai = (hour, minute) => new Date(Date.UTC(2026, 10, 2, hour - 8, minute))

describe('A 股交易分钟轴', () => {
  test('午休被压缩：11:30 与 13:00 落在同一位置', () => {
    expect(toTradingMinute('1.600028', shanghai(9, 30))).toBe(0)
    expect(toTradingMinute('1.600028', shanghai(11, 30))).toBe(120)
    expect(toTradingMinute('1.600028', shanghai(13, 0))).toBe(120)
    expect(toTradingMinute('1.600028', shanghai(14, 0))).toBe(180)
    expect(toTradingMinute('1.600028', shanghai(15, 0))).toBe(240)
  })

  test('午休期间与收盘后的点贴到边界', () => {
    expect(toTradingMinute('1.600028', shanghai(12, 15))).toBe(120)
    expect(toTradingMinute('1.600028', shanghai(15, 30))).toBe(240)
    expect(toTradingMinute('1.600028', shanghai(9, 20))).toBe(0)
  })

  test('交易分钟换回时钟，休市线在 11:30', () => {
    expect(getTradingDuration('1.600028')).toBe(240)
    expect(getSessionBreaks('1.600028')).toEqual([120])
    expect(tradingMinuteToClock('1.600028', 120)).toBe('11:30')
    expect(tradingMinuteToClock('1.600028', 150)).toBe('13:30')
    expect(tradingMinuteToClock('1.600028', 240)).toBe('15:00')
  })
})

describe('其他市场', () => {
  test('港股午休 12:00–13:00', () => {
    expect(getTradingDuration('116.00700')).toBe(330)
    expect(getSessionBreaks('116.00700')).toEqual([150])
    expect(tradingMinuteToClock('116.00700', 150)).toBe('12:00')
  })

  test('美股无午休', () => {
    expect(getTradingDuration('105.AAPL')).toBe(390)
    expect(getSessionBreaks('105.AAPL')).toEqual([])
  })

  test('开市判断不变', () => {
    expect(isMarketOpen('1.600028', shanghai(10, 0))).toBe(true)
    expect(isMarketOpen('1.600028', shanghai(12, 0))).toBe(false)
    expect(isMarketOpen('1.600028', shanghai(14, 59))).toBe(true)
    expect(isMarketOpen('1.600028', shanghai(15, 0))).toBe(false)
  })
})
