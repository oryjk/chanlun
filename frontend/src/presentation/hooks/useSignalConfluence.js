import { useEffect, useState } from 'react'

export function useSignalConfluence(getSignalConfluence, secid, enabled, reloadKey) {
  const [confluence, setConfluence] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    setConfluence((current) => current?.secid === secid ? current : null)
    setLoading(true)
    setError('')
    getSignalConfluence(secid, controller.signal)
      .then(setConfluence)
      .catch((reason) => {
        if (reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [getSignalConfluence, secid, enabled, reloadKey])

  return { confluence, loading, error }
}
