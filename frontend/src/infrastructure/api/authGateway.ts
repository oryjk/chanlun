import type {
  AuthGateway,
  EmailCodeSendResult,
  EmailLoginPayload,
  EmailRegisterPayload,
  PreferencesStorage,
} from '../../application/ports.ts'
import type { AuthChannel, AuthSession } from '../../domain/types.ts'
import { getJson, postJson, type HttpError } from './httpClient.ts'

interface CookieUserDto {
  user_id: number | string
  nickname: string
  avatar_url?: string | null
}

interface CookieSessionDto {
  authenticated?: boolean
  user?: CookieUserDto | null
}

interface BearerUserDto {
  user_id: string
  nickname: string
}

interface BearerSessionDto {
  authenticated?: boolean
  user?: BearerUserDto | null
}

interface BearerLoginDto {
  user?: BearerUserDto | null
  token?: string
}

interface MiniProgramLinkDto {
  url: string
}

interface EmailCodeDto {
  ok?: boolean
  expires_in?: number
  cooldown_seconds?: number
}

function toCookieSession(dto: CookieSessionDto | null | undefined): AuthSession {
  const authenticated = Boolean(dto?.authenticated && dto?.user)
  return {
    authenticated,
    channel: authenticated ? 'cookie' : null,
    user: dto?.user ? { userId: String(dto.user.user_id), nickname: dto.user.nickname } : null,
  }
}

function toBearerSession(dto: BearerSessionDto | null | undefined): AuthSession {
  const authenticated = Boolean(dto?.authenticated && dto?.user)
  return {
    authenticated,
    channel: authenticated ? 'bearer' : null,
    user: dto?.user ? { userId: String(dto.user.user_id), nickname: dto.user.nickname } : null,
  }
}

function toBearerLoginResult(dto: BearerLoginDto | null | undefined, email: string): AuthSession {
  const authenticated = Boolean(dto?.user && dto?.token)
  return {
    authenticated,
    channel: authenticated ? 'bearer' : null,
    token: dto?.token || '',
    user: dto?.user ? { userId: String(dto.user.user_id), nickname: dto.user.nickname, email } : null,
  }
}

export function createAuthGateway(tokenStore?: Pick<PreferencesStorage, 'readEmailToken' | 'writeEmailToken' | 'clearEmailToken'>): AuthGateway {
  return {
    async getSession(signal?: AbortSignal): Promise<AuthSession> {
      return toCookieSession(await getJson<CookieSessionDto>('/api/auth/session', signal))
    },
    async getActiveSession(signal?: AbortSignal): Promise<AuthSession> {
      try {
        const cookieSession = toCookieSession(await getJson<CookieSessionDto>('/api/auth/session', signal))
        if (cookieSession.authenticated) {
          return cookieSession
        }
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') throw error
      }
      const token = tokenStore?.readEmailToken?.() || ''
      if (!token) {
        return { authenticated: false, channel: null, user: null }
      }
      try {
        const bearerSession = toBearerSession(
          await getJson<BearerSessionDto>('/api/app-auth/session', signal, {
            headers: { Authorization: `Bearer ${token}` },
          }),
        )
        if (!bearerSession.authenticated) {
          tokenStore?.clearEmailToken?.()
        }
        return bearerSession
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') throw error
        if ((error as HttpError)?.status === 401) {
          tokenStore?.clearEmailToken?.()
        }
        return { authenticated: false, channel: null, user: null }
      }
    },
    async loginWithWebviewCode(code: string, signal?: AbortSignal): Promise<AuthSession> {
      return toCookieSession(await postJson<CookieSessionDto>('/api/auth/webview-login', { code }, signal))
    },
    async getMiniProgramLink(returnQuery: string, signal?: AbortSignal): Promise<string> {
      const params = new URLSearchParams({ return_query: returnQuery })
      const response = await getJson<MiniProgramLinkDto>(`/api/auth/mini-program-link?${params}`, signal)
      return response.url
    },
    async sendEmailCode(email: string, signal?: AbortSignal): Promise<EmailCodeSendResult> {
      const response = await postJson<EmailCodeDto>('/api/app-auth/email/code', { email }, signal)
      return {
        ok: Boolean(response?.ok),
        expiresIn: Number(response?.expires_in || 600),
        cooldownSeconds: Number(response?.cooldown_seconds || 60),
      }
    },
    async registerWithEmail({ email, code, password, nickname }: EmailRegisterPayload, signal?: AbortSignal): Promise<AuthSession> {
      const payload: EmailRegisterPayload = { email, code, password }
      if (nickname) payload.nickname = nickname
      const response = await postJson<BearerLoginDto>('/api/app-auth/email/register', payload, signal)
      const result = toBearerLoginResult(response, email)
      if (result.token) tokenStore?.writeEmailToken?.(result.token)
      return result
    },
    async loginWithEmail({ email, password }: EmailLoginPayload, signal?: AbortSignal): Promise<AuthSession> {
      const response = await postJson<BearerLoginDto>('/api/app-auth/email/login', { email, password }, signal)
      const result = toBearerLoginResult(response, email)
      if (result.token) tokenStore?.writeEmailToken?.(result.token)
      return result
    },
    async logoutSession(channel: AuthChannel | null, signal?: AbortSignal): Promise<{ ok: boolean }> {
      const token = tokenStore?.readEmailToken?.() || ''
      if (channel === 'bearer' && token) {
        try {
          await postJson('/api/app-auth/logout', {}, signal, {
            headers: { Authorization: `Bearer ${token}` },
          })
        } finally {
          tokenStore?.clearEmailToken?.()
        }
      } else {
        tokenStore?.clearEmailToken?.()
      }
      return { ok: true }
    },
  }
}
