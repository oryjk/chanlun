import { useCallback, useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import type { AssetCategory, AssetSortField, MarketAssetItem, MarketAssetList, SortOrder } from '../../domain/types.ts'

interface CachedCategoryEntry extends MarketAssetList {
  sort: AssetSortField
  order: SortOrder
}

export interface UseMarketAssetsResult {
  category: AssetCategory
  setCategory: (category: AssetCategory) => void
  sort: AssetSortField
  order: SortOrder
  toggleSort: (nextSort: AssetSortField) => void
  asOfDate: string | null
  items: MarketAssetItem[]
  loading: boolean
  error: string
  refresh: () => void
}

export function useMarketAssets(
  getMarketAssets: AppUseCases['getMarketAssets'],
  { enabled = true }: { enabled?: boolean } = {},
): UseMarketAssetsResult {
  const [category, setCategory] = useState<AssetCategory>('stock')
  const [sort, setSort] = useState<AssetSortField>('amount')
  const [order, setOrder] = useState<SortOrder>('desc')
  const [cacheByCategory, setCacheByCategory] = useState<Partial<Record<AssetCategory, CachedCategoryEntry>>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refreshTick, setRefreshTick] = useState(0)

  const toggleSort = useCallback((nextSort: AssetSortField) => {
    setSort((currentSort) => {
      if (currentSort === nextSort) {
        setOrder((currentOrder) => (currentOrder === 'desc' ? 'asc' : 'desc'))
        return currentSort
      }
      setOrder('desc')
      return nextSort
    })
  }, [])

  const refresh = useCallback(() => {
    setRefreshTick((tick) => tick + 1)
  }, [])

  useEffect(() => {
    if (!enabled) return undefined
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getMarketAssets({ category, sort, order, limit: 80 }, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setCacheByCategory((prev) => ({
            ...prev,
            [category]: { ...result, sort, order },
          }))
          setLoading(false)
        }
      })
      .catch((reason: unknown) => {
        if (reason instanceof Error && reason.name !== 'AbortError') {
          setError(reason.message || '行情清单加载失败')
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [category, enabled, getMarketAssets, order, refreshTick, sort])

  const activeData = cacheByCategory[category]
  const items = activeData ? activeData.items : []
  const asOfDate = activeData?.asOfDate ?? null

  return {
    category,
    setCategory,
    sort,
    order,
    toggleSort,
    asOfDate,
    items,
    loading,
    error,
    refresh,
  }
}
