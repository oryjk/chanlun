import { useEffect, useState } from 'react'
import type { GetOvernightSnapshot } from '../../application/ports.ts'
import type { OvernightSnapshot } from '../../domain/overnight.ts'

const POLL_INTERVAL_MS = 15_000

export function useOvernightSnapshot(
  getSnapshot: GetOvernightSnapshot,
  reloadKey: number,
  enabled: boolean,
) {
  const [snapshot, setSnapshot] = useState<OvernightSnapshot | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) return

    let active: AbortController | undefined
    let timer: ReturnType<typeof setTimeout> | undefined

    async function read(signal: AbortSignal) {
      try {
        const next = await getSnapshot(signal)
        if (signal.aborted) return
        setSnapshot(next)
        setError('')
      } catch (reason) {
        if (signal.aborted) return
        setError(reason instanceof Error ? reason.message : '无法读取隔夜扫描结果')
      } finally {
        if (!signal.aborted) {
          setLoading(false)
          timer = setTimeout(() => {
            if (document.visibilityState !== 'hidden') void read(signal)
          }, POLL_INTERVAL_MS)
        }
      }
    }

    function stop() {
      active?.abort()
      clearTimeout(timer)
    }

    function onVisibilityChange() {
      stop()
      if (document.visibilityState === 'hidden') return
      active = new AbortController()
      setLoading(true)
      void read(active.signal)
    }

    document.addEventListener('visibilitychange', onVisibilityChange)
    onVisibilityChange()
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [enabled, getSnapshot, reloadKey])

  return { snapshot, loading, error }
}
