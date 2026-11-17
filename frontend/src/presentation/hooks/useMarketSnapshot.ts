import { useEffect, useRef, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { MarketSnapshot, Stock, ViewTabValue } from '../../domain/types.ts'

export interface UseMarketSnapshotResult {
  snapshot: MarketSnapshot | null
  loading: boolean
  error: string
}

export function useMarketSnapshot(
  getMarketSnapshot: AppUseCases['getMarketSnapshot'],
  selectedStock: Stock,
  timeframe: ViewTabValue | string,
  reloadKey: number,
  enabled = true,
): UseMarketSnapshotResult {
  const [snapshot, setSnapshot] = useState<MarketSnapshot | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const snapshotRef = useRef<MarketSnapshot | null>(null)

  useEffect(() => {
    if (!enabled) {
      setLoading(false)
      setError('')
      return undefined
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
      .catch((reason: unknown) => {
        if (reason instanceof Error && reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [getMarketSnapshot, selectedStock.secid, timeframe, reloadKey, enabled])

  return { snapshot, loading, error }
}
