import { useEffect, useRef, useState } from 'react'
import { isMarketOpen } from '../../domain/marketHours.js'
import { mergeQuoteSeries } from '../../domain/quoteSeries.js'

export function useRealtimeQuote(getQuote, getQuoteSeries, recordQuoteSample, secid, enabled, reloadKey) {
  const [quote, setQuote] = useState(null)
  const [series, setSeries] = useState({ secid: '', points: [] })
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const quoteRef = useRef(null)
  const historyRef = useRef({ secid: '', points: [] })

  useEffect(() => {
    if (!enabled) {
      quoteRef.current = null
      historyRef.current = { secid: '', points: [] }
      setQuote(null)
      setSeries({ secid: '', points: [] })
      setLoading(false)
      setRefreshing(false)
      setError('')
      return
    }

    let active = true
    let inFlight = false
    let timer = null
    const controller = new AbortController()
    if (quoteRef.current?.secid !== secid) {
      quoteRef.current = null
      setQuote(null)
    }
    historyRef.current = { secid, points: [] }
    setSeries({ secid, points: [] })
    setLoading(true)
    setError('')

    const load = async (record = true) => {
      if (inFlight) return
      inFlight = true
      setRefreshing(true)
      try {
        const nextQuote = await getQuote(secid, controller.signal)
        if (active) {
          quoteRef.current = nextQuote
          setQuote(nextQuote)
        }
        if (record && isMarketOpen(secid)) {
          const nextSeries = await recordQuoteSample(nextQuote, controller.signal)
          if (active) {
            const mergedSeries = mergeQuoteSeries(historyRef.current, nextSeries)
            historyRef.current = mergedSeries
            setSeries(mergedSeries)
          }
        }
        if (active) setError('')
      } catch (reason) {
        if (active && reason.name !== 'AbortError') setError(reason.message)
      } finally {
        inFlight = false
        if (active) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    }

    const initialize = async () => {
      try {
        const initialSeries = await getQuoteSeries(secid, controller.signal)
        if (active) {
          historyRef.current = initialSeries
          setSeries(initialSeries)
        }
      } catch (reason) {
        if (active && reason.name !== 'AbortError') setError(reason.message)
      }
      const marketOpen = isMarketOpen(secid)
      await load(marketOpen)
      if (active && marketOpen) {
        timer = setInterval(() => {
          if (isMarketOpen(secid)) load()
        }, 3000)
      }
    }

    initialize()
    return () => {
      active = false
      clearInterval(timer)
      controller.abort()
    }
  }, [getQuote, getQuoteSeries, recordQuoteSample, secid, enabled, reloadKey])

  return { quote, series, loading, refreshing, error }
}
