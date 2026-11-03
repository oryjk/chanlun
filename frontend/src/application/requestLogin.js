// 小程序 web-view 内跳回小程序落地页完成默认登录；其他环境只能引导用户到微信里打开
export function createRequestLogin(gateway, bridge) {
  return async (environment, returnQuery, signal) => {
    if (environment === 'miniprogram') {
      await bridge.redirectToLanding(returnQuery)
      return { kind: 'redirected' }
    }
    return { kind: 'link', url: await gateway.getMiniProgramLink(returnQuery, signal) }
  }
}
