import { useCallback, useEffect, useState } from 'react'

// 带一次性 code 进入时先兑换登录（中间页阶段）；失败或无 code 时按现有 cookie 判断身份，查不到即游客
export function useAuthSession(getSession, loginWithWebviewCode, initialCode) {
  const [user, setUser] = useState(null)
  const [exchanging, setExchanging] = useState(Boolean(initialCode))

  const refresh = useCallback(async (signal) => {
    try {
      const session = await getSession(signal)
      setUser(session.user)
    } catch (reason) {
      if (reason.name !== 'AbortError') setUser(null)
    }
  }, [getSession])

  useEffect(() => {
    const controller = new AbortController()
    const start = async () => {
      if (initialCode) {
        try {
          const session = await loginWithWebviewCode(initialCode, controller.signal)
          setUser(session.user)
          return
        } catch (reason) {
          if (reason.name === 'AbortError') return
        } finally {
          if (!controller.signal.aborted) setExchanging(false)
        }
      }
      await refresh(controller.signal)
    }
    start()
    return () => controller.abort()
  }, [initialCode, loginWithWebviewCode, refresh])

  return { user, exchanging }
}
