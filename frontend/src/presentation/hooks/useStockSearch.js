import { useEffect, useRef, useState } from 'react'

export function useStockSearch(searchStocks, initialSymbol) {
  const [query, setQuery] = useState(initialSymbol)
  const [items, setItems] = useState([])
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')
  const requestRef = useRef(0)

  useEffect(() => {
    const value = query.trim()
    if (!value) {
      setItems([])
      setSearching(false)
      return
    }

    const controller = new AbortController()
    const requestId = ++requestRef.current
    const timer = setTimeout(() => {
      setSearching(true)
      setError('')
      searchStocks(value, controller.signal)
        .then((results) => {
          if (requestId === requestRef.current) setItems(results)
        })
        .catch((reason) => {
          if (reason.name !== 'AbortError' && requestId === requestRef.current) {
            setItems([])
            setError(reason.message)
          }
        })
        .finally(() => {
          if (!controller.signal.aborted && requestId === requestRef.current) setSearching(false)
        })
    }, 280)

    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query, searchStocks])

  const reset = (symbol) => {
    setQuery(symbol)
    setItems([])
    setOpen(false)
    setError('')
  }

  return { query, items, open, searching, error, setQuery, setOpen, setError, reset }
}
