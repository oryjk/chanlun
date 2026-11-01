import { useEffect, useState } from 'react'

export function useMarketSnapshot(getMarketSnapshot, selectedStock, timeframe, reloadKey) {
  const [snapshot, setSnapshot] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    setSnapshot(null)

    getMarketSnapshot(
      { secid: selectedStock.secid, timeframe, refresh: reloadKey > 0 },
      controller.signal,
    )
      .then(setSnapshot)
      .catch((reason) => {
        if (reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [getMarketSnapshot, selectedStock.secid, timeframe, reloadKey])

  return { snapshot, loading, error }
}
