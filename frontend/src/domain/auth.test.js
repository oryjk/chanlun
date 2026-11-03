import { describe, expect, test } from 'bun:test'
import { buildReturnQuery, detectClientEnvironment, splitWebviewParams } from './auth.js'

describe('客户端环境', () => {
  test('小程序 web-view、微信浏览器与普通浏览器', () => {
    expect(detectClientEnvironment({ userAgent: 'x MicroMessenger/8.0 miniProgram', wxjsEnvironment: undefined })).toBe('miniprogram')
    expect(detectClientEnvironment({ userAgent: 'x MicroMessenger/8.0', wxjsEnvironment: 'miniprogram' })).toBe('miniprogram')
    expect(detectClientEnvironment({ userAgent: 'x MicroMessenger/8.0', wxjsEnvironment: undefined })).toBe('wechat')
    expect(detectClientEnvironment({ userAgent: 'Mozilla/5.0 Safari', wxjsEnvironment: undefined })).toBe('browser')
  })
})

describe('web-view 桥接参数', () => {
  test('取出一次性 code 并从地址中抹掉全部桥接参数', () => {
    const result = splitWebviewParams('?secid=1.600519&webview_code=abc&webview_identity_kind=team&webview_identity_team_id=3&timeframe=1d')
    expect(result.code).toBe('abc')
    expect(result.search).toBe('?secid=1.600519&timeframe=1d')
  })

  test('没有桥接参数时原样返回', () => {
    expect(splitWebviewParams('?secid=1.600519')).toEqual({ code: '', search: '?secid=1.600519' })
    expect(splitWebviewParams('')).toEqual({ code: '', search: '' })
  })
})

describe('登录回跳参数', () => {
  test('只保留缠观自身的页面状态', () => {
    expect(buildReturnQuery('?secid=1.600519&timeframe=30m&view=market&utm=x')).toBe('secid=1.600519&timeframe=30m&view=market')
  })

  test('不合法的值被丢弃', () => {
    expect(buildReturnQuery('?secid=<x>&timeframe=1d')).toBe('timeframe=1d')
    expect(buildReturnQuery('')).toBe('')
  })
})
