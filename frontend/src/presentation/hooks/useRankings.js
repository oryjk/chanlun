import { useEffect, useState } from 'react'

export function useRankings(getRankings, reloadKey) {
  const [board, setBoard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    let timer
    setLoading(true)
    setError('')
    const read = () => getRankings(500, controller.signal)
      .then((nextBoard) => {
        setBoard(nextBoard)
        setError('')
      })
      .catch((reason) => {
        if (reason.name !== 'AbortError') setError(reason.message)
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
  }, [getRankings, reloadKey])

  return { board, loading, error }
}
