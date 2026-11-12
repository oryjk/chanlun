import { useEffect, useState } from 'react'
import { DEFAULT_STOCK } from '../../domain/market.js'
import { codeOf, normalizeSecid } from '../../domain/security.js'
import { VIEW_TABS } from '../../domain/timeframes.js'

const DEFAULT_TIMEFRAME = '30m'

export function readMarketRoute(search) {
  const params = new URLSearchParams(search)
  const secid = normalizeSecid(params.get('secid'))
  const requestedTimeframe = params.get('timeframe') || ''
  const timeframe = VIEW_TABS.some((item) => item.value === requestedTimeframe)
    ? requestedTimeframe
    : DEFAULT_TIMEFRAME
  const view = params.get('view') === 'rankings' ? 'rankings' : 'market'

  if (!secid) {
    return { stock: DEFAULT_STOCK, timeframe, view, resolveStock: false }
  }

  const symbol = codeOf(secid)
  return {
    stock: { secid, symbol, name: symbol, market: '' },
    timeframe,
    view,
    resolveStock: true,
  }
}

export function useMarketRoute(searchStocks) {
  const [initialRoute] = useState(() => readMarketRoute(window.location.search))
  const [selectedStock, setSelectedStock] = useState(initialRoute.stock)
  const [timeframe, setTimeframe] = useState(initialRoute.timeframe)
  const [view, setView] = useState(initialRoute.view)

  useEffect(() => {
    const url = new URL(window.location.href)
    url.searchParams.set('secid', selectedStock.secid)
    url.searchParams.set('timeframe', timeframe)
    url.searchParams.set('view', view)
    window.history.replaceState(window.history.state, '', url)
  }, [selectedStock.secid, timeframe, view])

  useEffect(() => {
    if (!initialRoute.resolveStock) return
    const controller = new AbortController()

    searchStocks(initialRoute.stock.symbol, controller.signal)
      .then((items) => {
        const match = items.find((item) => item.secid === initialRoute.stock.secid)
        if (match) {
          setSelectedStock((current) => current.secid === match.secid ? match : current)
        }
      })
      .catch(() => {})

    return () => controller.abort()
  }, [initialRoute, searchStocks])

  return { selectedStock, setSelectedStock, timeframe, setTimeframe, view, setView }
}
