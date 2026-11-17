import { useCallback, useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { AuthChannel, AuthSession, UserIdentity } from '../../domain/types.ts'

export interface UseAuthSessionResult {
  user: UserIdentity | null
  authChannel: AuthChannel | null
  exchanging: boolean
  setAuthenticatedSession: (session: AuthSession | null | undefined) => void
  clearSession: () => void
}

// 带一次性 code 进入时先兑换登录（中间页阶段）；失败或无 code 时判断 cookie 与本地邮箱令牌，查不到即游客
export function useAuthSession(
  getSession: AppUseCases['getSession'],
  loginWithWebviewCode: AppUseCases['loginWithWebviewCode'],
  initialCode: string,
): UseAuthSessionResult {
  const [sessionState, setSessionState] = useState<{ user: UserIdentity | null; channel: AuthChannel | null }>({
    user: null,
    channel: null,
  })
  const [exchanging, setExchanging] = useState(Boolean(initialCode))

  const applySession = useCallback((session: AuthSession | null | undefined) => {
    if (session?.authenticated && session?.user) {
      setSessionState({ user: session.user, channel: session.channel || 'cookie' })
    } else {
      setSessionState({ user: null, channel: null })
    }
  }, [])

  const refresh = useCallback(async (signal?: AbortSignal) => {
    try {
      const session = await getSession(signal)
      applySession(session)
    } catch (reason) {
      if (reason instanceof Error && reason.name !== 'AbortError') {
        setSessionState({ user: null, channel: null })
      }
    }
  }, [applySession, getSession])

  useEffect(() => {
    const controller = new AbortController()
    const start = async () => {
      if (initialCode) {
        try {
          const session = await loginWithWebviewCode(initialCode, controller.signal)
          applySession(session)
          return
        } catch (reason) {
          if (reason instanceof Error && reason.name === 'AbortError') return
        } finally {
          if (!controller.signal.aborted) setExchanging(false)
        }
      }
      await refresh(controller.signal)
    }
    start()
    return () => controller.abort()
  }, [applySession, initialCode, loginWithWebviewCode, refresh])

  return {
    user: sessionState.user,
    authChannel: sessionState.channel,
    exchanging,
    setAuthenticatedSession: applySession,
    clearSession: () => setSessionState({ user: null, channel: null }),
  }
}
