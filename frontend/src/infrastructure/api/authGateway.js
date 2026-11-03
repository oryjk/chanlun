import { getJson, postJson } from './httpClient.js'

function toSession(dto) {
  return {
    authenticated: dto.authenticated,
    user: dto.user && { userId: dto.user.user_id, nickname: dto.user.nickname },
  }
}

export function createAuthGateway() {
  return {
    async getSession(signal) {
      return toSession(await getJson('/api/auth/session', signal))
    },
    async loginWithWebviewCode(code, signal) {
      return toSession(await postJson('/api/auth/webview-login', { code }, signal))
    },
    async getMiniProgramLink(returnQuery, signal) {
      const params = new URLSearchParams({ return_query: returnQuery })
      const response = await getJson(`/api/auth/mini-program-link?${params}`, signal)
      return response.url
    },
  }
}
