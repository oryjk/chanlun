import { useEffect, useRef, useState } from 'react'

export function useMarketSnapshot(getMarketSnapshot, selectedStock, timeframe, reloadKey, enabled = true) {
  const [snapshot, setSnapshot] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const snapshotRef = useRef(null)

  useEffect(() => {
    if (!enabled) {
      setLoading(false)
      setError('')
      return
    }

    const controller = new AbortController()
    const preserveSnapshot = snapshotRef.current?.instrument.secid === selectedStock.secid
    setLoading(true)
    setError('')
    if (!preserveSnapshot) {
      snapshotRef.current = null
      setSnapshot(null)
    }

    getMarketSnapshot(
      { secid: selectedStock.secid, timeframe, refresh: reloadKey > 0 },
      controller.signal,
    )
      .then((nextSnapshot) => {
        snapshotRef.current = nextSnapshot
        setSnapshot(nextSnapshot)
      })
      .catch((reason) => {
        if (reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [getMarketSnapshot, selectedStock.secid, timeframe, reloadKey, enabled])

  return { snapshot, loading, error }
}
