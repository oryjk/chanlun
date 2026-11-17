import { createContext, type ReactNode, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { AuthChannel, AuthSession, ClientEnvironment, UserIdentity } from '../../domain/types.ts'
import { useAuthSession } from '../hooks/useAuthSession.ts'
import LoginPrompt from './LoginPrompt.tsx'

export type PreferredLoginMode = 'email' | 'wechat'

export interface AuthContextValue {
  user: UserIdentity | null
  authChannel: AuthChannel | null
  environment: ClientEnvironment
  openLoginPrompt: (reason?: string, preferredMode?: PreferredLoginMode) => void
  requireLogin: <T>(action: () => T, reason?: string) => T | undefined
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export interface AuthProviderProps {
  useCases: AppUseCases
  initialCode: string
  environment: ClientEnvironment
  children: ReactNode
}

export function AuthProvider({ useCases, initialCode, environment, children }: AuthProviderProps) {
  const {
    user,
    authChannel,
    exchanging,
    setAuthenticatedSession,
    clearSession,
  } = useAuthSession(useCases.getSession, useCases.loginWithWebviewCode, initialCode)
  const [prompt, setPrompt] = useState<{ reason: string; preferredMode: PreferredLoginMode } | null>(null)
  const pendingActionRef = useRef<(() => unknown) | null>(null)

  const openLoginPrompt = useCallback(
    (reason = '登录后可使用模拟跟踪等个人功能', preferredMode: PreferredLoginMode = 'email') => {
      pendingActionRef.current = null
      setPrompt({ reason, preferredMode })
    },
    [],
  )

  // 收藏、跟踪等个人功能统一经此入口：已登录直接执行，游客弹出登录引导
  const requireLogin = useCallback(
    <T,>(action: () => T, reason = '登录后可使用模拟跟踪等个人功能'): T | undefined => {
      if (user) return action()
      pendingActionRef.current = typeof action === 'function' ? action : null
      setPrompt({ reason, preferredMode: 'email' })
      return undefined
    },
    [user],
  )

  const handleEmailSuccess = useCallback((session: AuthSession) => {
    setAuthenticatedSession(session)
    setPrompt(null)
    const pending = pendingActionRef.current
    pendingActionRef.current = null
    if (pending) {
      Promise.resolve().then(() => pending())
    }
  }, [setAuthenticatedSession])

  const logout = useCallback(async () => {
    await useCases.logoutSession?.(authChannel)
    clearSession()
  }, [authChannel, clearSession, useCases])

  const value = useMemo<AuthContextValue>(
    () => ({ user, authChannel, environment, openLoginPrompt, requireLogin, logout }),
    [authChannel, environment, logout, openLoginPrompt, requireLogin, user],
  )

  if (exchanging) {
    return (
      <div className="auth-gate" role="status">
        <span className="loader" />
        <strong>正在登录缠观</strong>
        <small>正在同步报名系统小程序的登录状态…</small>
      </div>
    )
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
      {prompt && (
        <LoginPrompt
          reason={prompt.reason}
          preferredMode={prompt.preferredMode}
          environment={environment}
          requestLogin={useCases.requestLogin}
          sendEmailCode={useCases.sendEmailCode}
          registerWithEmail={useCases.registerWithEmail}
          loginWithEmail={useCases.loginWithEmail}
          onEmailAuthenticated={handleEmailSuccess}
          onClose={() => {
            pendingActionRef.current = null
            setPrompt(null)
          }}
        />
      )}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内使用')
  }
  return ctx
}
