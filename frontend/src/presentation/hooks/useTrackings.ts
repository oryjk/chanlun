import { useCallback, useEffect, useMemo, useState } from 'react'
import type { AppUseCases, TrackingPreferencesUseCases } from '../../application/ports.ts'
import { sortTrackings, summarizeTrackings, type TrackingPortfolioSummary } from '../../domain/tracking.ts'
import type {
  AuthChannel,
  TrackedAssetItem,
  TrackingOverview,
  TrackingPreferencesData,
  TrackingSortBy,
  UserIdentity,
} from '../../domain/types.ts'

const EMPTY_PREFS: TrackingPreferencesData = { sortBy: 'default', pinnedSecids: [], customOrder: [] }

export interface UseTrackingsInput {
  user: UserIdentity | null
  authChannel: AuthChannel | null
  getTrackings: AppUseCases['getTrackings']
  startTrackingUseCase: AppUseCases['startTracking']
  cancelTrackingsUseCase: AppUseCases['cancelTrackings']
  trackingPreferences: TrackingPreferencesUseCases
}

export interface UseTrackingsResult {
  asOfDate: string
  items: TrackedAssetItem[]
  trackedSecids: Set<string>
  summary: TrackingPortfolioSummary
  sortBy: TrackingSortBy
  pinnedSecids: string[]
  loading: boolean
  mutating: boolean
  error: string
  setSortBy: (sortBy: TrackingSortBy) => void
  togglePin: (secid: string) => void
  moveItem: (secid: string, direction: -1 | 1) => void
  startTracking: (secid: string) => Promise<boolean>
  cancelTrackings: (secids: string[]) => Promise<boolean>
  toggleTracking: (secid: string) => Promise<boolean>
  refresh: () => void
}

export function useTrackings({
  user,
  authChannel,
  getTrackings,
  startTrackingUseCase,
  cancelTrackingsUseCase,
  trackingPreferences,
}: UseTrackingsInput): UseTrackingsResult {
  const userKey = user ? `${authChannel || 'cookie'}:${user.userId}` : ''
  const [overview, setOverview] = useState<TrackingOverview>({ asOfDate: '', items: [] })
  const [loading, setLoading] = useState(false)
  const [mutating, setMutating] = useState(false)
  const [error, setError] = useState('')
  const [prefs, setPrefs] = useState<TrackingPreferencesData>(() => (userKey ? trackingPreferences.read(userKey) : EMPTY_PREFS))
  const [refreshTick, setRefreshTick] = useState(0)

  useEffect(() => {
    if (!userKey) {
      setPrefs(EMPTY_PREFS)
      return
    }
    setPrefs(trackingPreferences.read(userKey))
  }, [trackingPreferences, userKey])

  const persistPrefs = useCallback(
    (updater: TrackingPreferencesData | ((current: TrackingPreferencesData) => TrackingPreferencesData)) => {
      setPrefs((current) => {
        const next = typeof updater === 'function' ? updater(current) : updater
        if (userKey) trackingPreferences.write(userKey, next)
        return next
      })
    },
    [trackingPreferences, userKey],
  )

  const refresh = useCallback(() => {
    setRefreshTick((tick) => tick + 1)
  }, [])

  useEffect(() => {
    if (!user || !authChannel) {
      setOverview({ asOfDate: '', items: [] })
      setError('')
      setLoading(false)
      return undefined
    }
    const controller = new AbortController()
    setLoading(true)
    setError('')
    getTrackings(authChannel, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setOverview(result)
          setLoading(false)
        }
      })
      .catch((reason: unknown) => {
        if (reason instanceof Error && reason.name !== 'AbortError') {
          setError(reason.message || '跟踪列表加载失败')
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [authChannel, getTrackings, refreshTick, user])

  const trackedSecids = useMemo(
    () => new Set(overview.items.map((item) => item.stock.secid)),
    [overview.items],
  )

  const sortedItems = useMemo(
    () => sortTrackings(overview.items, { ...prefs, asOfDate: overview.asOfDate }),
    [overview.asOfDate, overview.items, prefs],
  )

  const summary = useMemo(() => summarizeTrackings(overview.items), [overview.items])

  const startTracking = useCallback(async (secid: string): Promise<boolean> => {
    if (!authChannel) return false
    setMutating(true)
    setError('')
    try {
      await startTrackingUseCase(secid, authChannel)
      refresh()
      return true
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '添加跟踪失败')
      return false
    } finally {
      setMutating(false)
    }
  }, [authChannel, refresh, startTrackingUseCase])

  const cancelTrackings = useCallback(async (secids: string[]): Promise<boolean> => {
    if (!authChannel || !secids?.length) return false
    setMutating(true)
    setError('')
    try {
      await cancelTrackingsUseCase(secids, authChannel)
      persistPrefs((current) => ({
        ...current,
        pinnedSecids: current.pinnedSecids.filter((id) => !secids.includes(id)),
        customOrder: current.customOrder.filter((id) => !secids.includes(id)),
      }))
      refresh()
      return true
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '取消跟踪失败')
      return false
    } finally {
      setMutating(false)
    }
  }, [authChannel, cancelTrackingsUseCase, persistPrefs, refresh])

  const toggleTracking = useCallback(async (secid: string): Promise<boolean> => {
    if (!secid) return false
    if (trackedSecids.has(secid)) {
      return cancelTrackings([secid])
    }
    return startTracking(secid)
  }, [cancelTrackings, startTracking, trackedSecids])

  const setSortBy = useCallback((sortBy: TrackingSortBy) => {
    persistPrefs((current) => ({ ...current, sortBy }))
  }, [persistPrefs])

  const togglePin = useCallback((secid: string) => {
    persistPrefs((current) => {
      const exists = current.pinnedSecids.includes(secid)
      const pinnedSecids = exists
        ? current.pinnedSecids.filter((id) => id !== secid)
        : [secid, ...current.pinnedSecids]
      return { ...current, pinnedSecids }
    })
  }, [persistPrefs])

  const moveItem = useCallback((secid: string, direction: -1 | 1) => {
    const order = sortedItems.map((item) => item.stock.secid)
    const idx = order.indexOf(secid)
    if (idx < 0) return
    const target = idx + direction
    if (target < 0 || target >= order.length) return
    const nextOrder = [...order]
    const swap = nextOrder[target]
    nextOrder[target] = nextOrder[idx]
    nextOrder[idx] = swap
    persistPrefs((current) => ({ ...current, sortBy: 'default', customOrder: nextOrder }))
  }, [persistPrefs, sortedItems])

  return {
    asOfDate: overview.asOfDate,
    items: sortedItems,
    trackedSecids,
    summary,
    sortBy: prefs.sortBy,
    pinnedSecids: prefs.pinnedSecids,
    loading,
    mutating,
    error,
    setSortBy,
    togglePin,
    moveItem,
    startTracking,
    cancelTrackings,
    toggleTracking,
    refresh,
  }
}
