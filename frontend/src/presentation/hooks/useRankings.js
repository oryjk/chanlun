import { useEffect, useState } from 'react'

export function useRankings(getRankings, externalReloadKey) {
  const [board, setBoard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getRankings(6, controller.signal)
      .then(setBoard)
      .catch((reason) => {
        if (reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [getRankings, reloadKey, externalReloadKey])

  return { board, loading, error, reload: () => setReloadKey((value) => value + 1) }
}
