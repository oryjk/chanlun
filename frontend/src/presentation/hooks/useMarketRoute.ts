import { type SetStateAction, useCallback, useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import { DEFAULT_STOCK } from '../../domain/market.ts'
import { codeOf, normalizeSecid } from '../../domain/security.ts'
import { VIEW_TABS } from '../../domain/timeframes.ts'
import type { RouteView, Stock, ViewTabValue } from '../../domain/types.ts'

const DEFAULT_TIMEFRAME: ViewTabValue = '30m'
const VALID_VIEWS: RouteView[] = ['market', 'detail', 'tracking', 'rankings', 'menu']

export interface InitialMarketRoute {
  stock: Stock
  timeframe: ViewTabValue
  view: RouteView
  resolveStock: boolean
}

function isValidView(value: string): value is RouteView {
  return (VALID_VIEWS as string[]).includes(value)
}

export function readMarketRoute(search: string): InitialMarketRoute {
  const params = new URLSearchParams(search)
  const secid = normalizeSecid(params.get('secid'))
  const requestedTimeframe = params.get('timeframe') || ''
  const matchedTab = VIEW_TABS.find((item) => item.value === requestedTimeframe)
  const timeframe: ViewTabValue = matchedTab ? matchedTab.value : DEFAULT_TIMEFRAME
  const rawView = params.get('view') || ''

  let view: RouteView = 'market'
  if (isValidView(rawView)) {
    view = rawView
  } else if (secid) {
    // 兼容仅携带 secid 的深链与小程序回跳直接打开详情页
    view = 'detail'
  }

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

interface RouteState {
  view: RouteView
  returnView: RouteView
  selected: Stock
  timeframe: ViewTabValue
}

export interface UseMarketRouteResult {
  view: RouteView
  setView: (nextView: RouteView) => void
  selected: Stock
  setSelected: (stockOrUpdater: SetStateAction<Stock>) => void
  openStockDetail: (stock: Stock, fromView?: RouteView) => void
  goBackFromDetail: () => void
  timeframe: ViewTabValue
  setTimeframe: (nextTimeframe: ViewTabValue) => void
}

export function useMarketRoute(searchStocks?: AppUseCases['searchStocks']): UseMarketRouteResult {
  const [initialRoute] = useState<InitialMarketRoute>(() => readMarketRoute(window.location.search))
  const [route, setRoute] = useState<RouteState>(() => ({
    view: initialRoute.view,
    returnView: initialRoute.view === 'detail' ? 'market' : initialRoute.view,
    selected: initialRoute.stock,
    timeframe: initialRoute.timeframe,
  }))

  const setView = useCallback((nextView: RouteView) => {
    if (!isValidView(nextView)) return
    setRoute((current) => ({
      ...current,
      view: nextView,
      returnView: nextView === 'detail' ? current.returnView : nextView,
    }))
  }, [])

  const openStockDetail = useCallback((stock: Stock, fromView?: RouteView) => {
    if (!stock?.secid) return
    const normalized = normalizeSecid(stock.secid) || stock.secid
    const resolvedStock: Stock = normalized === stock.secid ? stock : { ...stock, secid: normalized }
    setRoute((current) => ({
      ...current,
      selected: resolvedStock,
      returnView: fromView && fromView !== 'detail'
        ? fromView
        : (current.view === 'detail' ? current.returnView : current.view),
      view: 'detail',
    }))
  }, [])

  const goBackFromDetail = useCallback(() => {
    setRoute((current) => ({
      ...current,
      view: isValidView(current.returnView) && current.returnView !== 'detail'
        ? current.returnView
        : 'market',
    }))
  }, [])

  const setSelected = useCallback((stockOrUpdater: SetStateAction<Stock>) => {
    setRoute((current) => ({
      ...current,
      selected: typeof stockOrUpdater === 'function' ? stockOrUpdater(current.selected) : stockOrUpdater,
    }))
  }, [])

  const setTimeframe = useCallback((nextTimeframe: ViewTabValue) => {
    if (!VIEW_TABS.some((item) => item.value === nextTimeframe)) return
    setRoute((current) => ({ ...current, timeframe: nextTimeframe }))
  }, [])

  useEffect(() => {
    const params = new URLSearchParams()
    if (route.view === 'detail') {
      params.set('secid', route.selected.secid)
      params.set('timeframe', route.timeframe)
      params.set('view', 'detail')
    } else {
      params.set('view', route.view)
    }
    const nextUrl = `${window.location.pathname}?${params}${window.location.hash}`
    if (`${window.location.pathname}${window.location.search}${window.location.hash}` !== nextUrl) {
      window.history.replaceState(window.history.state, '', nextUrl)
    }
  }, [route.selected.secid, route.timeframe, route.view])

  useEffect(() => {
    if (!initialRoute.resolveStock || typeof searchStocks !== 'function') return undefined
    const controller = new AbortController()

    searchStocks(initialRoute.stock.symbol, controller.signal)
      .then((items) => {
        const match = items.find((item) => normalizeSecid(item.secid) === initialRoute.stock.secid)
        if (match) {
          const canonical: Stock = { ...match, secid: normalizeSecid(match.secid) || match.secid }
          setRoute((current) => (
            current.selected.secid === canonical.secid
              ? { ...current, selected: canonical }
              : current
          ))
        }
      })
      .catch(() => {})

    return () => controller.abort()
  }, [initialRoute, searchStocks])

  return {
    view: route.view,
    setView,
    selected: route.selected,
    setSelected,
    openStockDetail,
    goBackFromDetail,
    timeframe: route.timeframe,
    setTimeframe,
  }
}
