import { useCallback, useSyncExternalStore } from 'react'

export function useBeforeDeadline(value: string | null | undefined): boolean {
  const deadline = value ? Date.parse(value) : NaN
  const getSnapshot = useCallback(() => Date.now() < deadline, [deadline])
  const subscribe = useCallback((onChange: () => void) => {
    const remaining = deadline - Date.now()
    if (!(remaining > 0)) return () => {}
    const timer = setTimeout(onChange, remaining)
    document.addEventListener('visibilitychange', onChange)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('visibilitychange', onChange)
    }
  }, [deadline])

  return useSyncExternalStore(subscribe, getSnapshot)
}
