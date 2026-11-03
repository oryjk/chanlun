// 报名系统 web-view 桥接协议：小程序把一次性 code 与身份选择拼在 URL query 上
export const WEBVIEW_CODE_PARAM = 'webview_code'
const WEBVIEW_PARAMS = [WEBVIEW_CODE_PARAM, 'webview_identity_kind', 'webview_identity_team_id']

// 登录后回到缠观时需要恢复的页面状态，与小程序和后端的回跳参数规则一致
const RETURN_PARAMS = ['secid', 'timeframe', 'view']
const SAFE_VALUE = /^[A-Za-z0-9._-]+$/

export function detectClientEnvironment({ userAgent, wxjsEnvironment }) {
  if (wxjsEnvironment === 'miniprogram' || /miniProgram/i.test(userAgent)) return 'miniprogram'
  if (/MicroMessenger/i.test(userAgent)) return 'wechat'
  return 'browser'
}

export function splitWebviewParams(search) {
  const params = new URLSearchParams(search)
  const code = params.get(WEBVIEW_CODE_PARAM) || ''
  if (!WEBVIEW_PARAMS.some((name) => params.has(name))) return { code, search }
  WEBVIEW_PARAMS.forEach((name) => params.delete(name))
  const rest = params.toString()
  return { code, search: rest ? `?${rest}` : '' }
}

export function buildReturnQuery(search) {
  const params = new URLSearchParams(search)
  return RETURN_PARAMS
    .filter((name) => SAFE_VALUE.test(params.get(name) || ''))
    .map((name) => `${name}=${params.get(name)}`)
    .join('&')
}
