import { useEffect, useRef, useState } from 'react'

export function useStockSearch(searchStocks, initialSymbol) {
  const [query, setQuery] = useState(initialSymbol)
  const [items, setItems] = useState([])
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef(0)
  const timerRef = useRef(null)
  const controllerRef = useRef(null)

  const runSearch = async (value) => {
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
      if (reason.name === 'AbortError' || requestId !== requestRef.current) return null
      setItems([])
      setError(reason.message)
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
      return
    }

    timerRef.current = setTimeout(() => runSearch(value), 280)
    return () => clearTimeout(timerRef.current)
  }, [query, searchStocks])

  useEffect(() => () => controllerRef.current?.abort(), [])

  const reset = (symbol) => {
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
