import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { useAuthSession } from '../hooks/useAuthSession.js'
import LoginPrompt from './LoginPrompt.jsx'

const AuthContext = createContext(null)

export function AuthProvider({ useCases, initialCode, environment, children }) {
  const { user, exchanging } = useAuthSession(useCases.getSession, useCases.loginWithWebviewCode, initialCode)
  const [prompt, setPrompt] = useState(null)

  const openLoginPrompt = useCallback((reason = '登录后可使用收藏、跟踪等个人功能') => setPrompt({ reason }), [])
  // 收藏、跟踪等个人功能统一经此入口：已登录直接执行，游客弹出登录引导
  const requireLogin = useCallback((action, reason) => {
    if (user) return action()
    openLoginPrompt(reason)
    return undefined
  }, [openLoginPrompt, user])

  const value = useMemo(() => ({ user, environment, openLoginPrompt, requireLogin }), [environment, openLoginPrompt, requireLogin, user])

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
          environment={environment}
          requestLogin={useCases.requestLogin}
          onClose={() => setPrompt(null)}
        />
      )}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
