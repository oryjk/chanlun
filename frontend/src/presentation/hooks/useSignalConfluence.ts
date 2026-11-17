import { useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { SignalConfluence } from '../../domain/types.ts'

export interface UseSignalConfluenceResult {
  confluence: SignalConfluence | null
  loading: boolean
  error: string
}

export function useSignalConfluence(
  getSignalConfluence: AppUseCases['getSignalConfluence'],
  secid: string,
  enabled: boolean,
  reloadKey: number,
): UseSignalConfluenceResult {
  const [confluence, setConfluence] = useState<SignalConfluence | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) return undefined
    const controller = new AbortController()
    setConfluence((current) => (current?.secid === secid ? current : null))
    setLoading(true)
    setError('')
    getSignalConfluence(secid, controller.signal)
      .then(setConfluence)
      .catch((reason: unknown) => {
        if (reason instanceof Error && reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [getSignalConfluence, secid, enabled, reloadKey])

  return { confluence, loading, error }
}
