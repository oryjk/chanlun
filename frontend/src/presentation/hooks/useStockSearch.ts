import { useEffect, useRef, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { Stock } from '../../domain/types.ts'

export interface UseStockSearchResult {
  query: string
  items: Stock[]
  open: boolean
  searching: boolean
  error: string
  setQuery: (query: string) => void
  setOpen: (open: boolean) => void
  setError: (error: string) => void
  reset: (symbol: string) => void
  searchNow: () => Promise<Stock[] | null>
}

export function useStockSearch(
  searchStocks: AppUseCases['searchStocks'],
  initialSymbol: string,
): UseStockSearchResult {
  const [query, setQuery] = useState(initialSymbol)
  const [items, setItems] = useState<Stock[]>([])
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const controllerRef = useRef<AbortController | null>(null)

  const runSearch = async (value: string): Promise<Stock[] | null> => {
    clearTimeout(timerRef.current)
    controllerRef.current?.abort()
    if (!value) return []

    const controller = new AbortController()
    controllerRef.current = controller
    const requestId = ++requestRef.current
    setSearching(true)
    setError('')

    try {
      const results = await searchStocks(value, controller.signal)
      if (requestId !== requestRef.current) return null
      setItems(results)
      return results
    } catch (reason) {
      if (reason instanceof Error && (reason.name === 'AbortError' || requestId !== requestRef.current)) return null
      setItems([])
      setError(reason instanceof Error ? reason.message : '搜索失败')
      return null
    } finally {
      if (!controller.signal.aborted && requestId === requestRef.current) setSearching(false)
    }
  }

  useEffect(() => {
    const value = query.trim()
    controllerRef.current?.abort()
    requestRef.current += 1
    if (!value) {
      setItems([])
      setSearching(false)
      return undefined
    }

    timerRef.current = setTimeout(() => {
      void runSearch(value)
    }, 280)
    return () => clearTimeout(timerRef.current)
  }, [query, searchStocks])

  useEffect(() => () => controllerRef.current?.abort(), [])

  const reset = (symbol: string) => {
    clearTimeout(timerRef.current)
    controllerRef.current?.abort()
    requestRef.current += 1
    setQuery(symbol)
    setItems([])
    setOpen(false)
    setError('')
  }

  const searchNow = () => runSearch(query.trim())

  return { query, items, open, searching, error, setQuery, setOpen, setError, reset, searchNow }
}
