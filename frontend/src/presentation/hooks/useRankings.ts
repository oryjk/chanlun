import { useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { RankingBoard } from '../../domain/types.ts'

export interface UseRankingsResult {
  board: RankingBoard | null
  loading: boolean
  error: string
}

export function useRankings(
  getRankings: AppUseCases['getRankings'],
  reloadKey: number,
  enabled = true,
): UseRankingsResult {
  const [board, setBoard] = useState<RankingBoard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) return undefined
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    setLoading(true)
    setError('')
    const read = () => getRankings(500, controller.signal)
      .then((nextBoard) => {
        setBoard(nextBoard)
        setError('')
      })
      .catch((reason: unknown) => {
        if (reason instanceof Error && reason.name !== 'AbortError') setError(reason.message)
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false)
          timer = setTimeout(read, 60000)
        }
      })
    read()
    return () => {
      controller.abort()
      clearTimeout(timer)
    }
  }, [enabled, getRankings, reloadKey])

  return { board, loading, error }
}
