// 微信 JS-SDK 仅用于 web-view 内调用 wx.miniProgram 跳回小程序页面
const JSSDK_URL = 'https://res.wx.qq.com/open/js/jweixin-1.6.0.js'
const LANDING_PAGE = '/pages/chanlun/index'

let sdkPromise = null

function loadSdk() {
  if (window.wx?.miniProgram) return Promise.resolve(window.wx)
  sdkPromise ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = JSSDK_URL
    script.onload = () => (window.wx?.miniProgram ? resolve(window.wx) : reject(new Error('微信 JS-SDK 不可用')))
    script.onerror = () => {
      sdkPromise = null
      reject(new Error('微信 JS-SDK 加载失败，请检查网络'))
    }
    document.head.appendChild(script)
  })
  return sdkPromise
}

export function createMiniProgramBridge() {
  return {
    async redirectToLanding(returnQuery) {
      const wx = await loadSdk()
      const url = returnQuery ? `${LANDING_PAGE}?path=${encodeURIComponent(returnQuery)}` : LANDING_PAGE
      await new Promise((resolve, reject) => {
        wx.miniProgram.redirectTo({
          url,
          success: resolve,
          fail: () => reject(new Error('无法跳转到小程序登录，请返回后重试')),
        })
      })
    },
  }
}
