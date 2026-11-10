import { getJson, postJson } from './httpClient.js'

function toStock(dto) {
  return { secid: dto.secid, symbol: dto.symbol, name: dto.name, market: dto.market }
}

function toSignalQuality(dto) {
  if (!dto) return null
  return {
    state: dto.state, invalidation_price: dto.invalidation_price,
    momentum_status: dto.momentum_status, volume_status: dto.volume_status,
    macd_histogram: dto.macd_histogram, macd_area_ratio: dto.macd_area_ratio,
    pullback_macd_ratio: dto.pullback_macd_ratio,
    reference_touched: dto.reference_touched ?? null,
    bars_since_pivot: dto.bars_since_pivot ?? null,
    first_reference_crossed_at: dto.first_reference_crossed_at || null,
    first_reference_crossed_close: dto.first_reference_crossed_close ?? null,
    history_checked_through: dto.history_checked_through || null,
    breakout_volume_ratio: dto.breakout_volume_ratio, pullback_volume_ratio: dto.pullback_volume_ratio,
    history_status: dto.history_status || 'unknown', first_detected_at: dto.first_detected_at || null,
    confirmed_at: dto.confirmed_at || null, reasons: [...(dto.reasons || [])],
  }
}

function toMarketSnapshot(dto) {
  return {
    instrument: { ...dto.instrument },
    timeframe: dto.timeframe,
    timeframe_label: dto.timeframe_label,
    candles: dto.candles.map((candle) => ({ ...candle })),
    analysis: {
      fractals: dto.analysis.fractals.map((fractal) => ({ ...fractal })),
      pens: dto.analysis.pens.map((pen) => ({ ...pen })),
      segments: dto.analysis.segments.map((segment) => ({ ...segment })),
      zhongshu: dto.analysis.zhongshu.map((zone) => ({ ...zone })),
      merged_count: dto.analysis.merged_count,
      trend: dto.analysis.trend,
    },
    summary: { ...dto.summary },
    guidance: { ...dto.guidance },
    macd: dto.macd.map((point) => ({ ...point })),
    signals: {
      trend_type: dto.signals.trend_type,
      divergences: dto.signals.divergences.map((divergence) => ({ ...divergence })),
      points: dto.signals.points.map((point) => ({ ...point })),
    },
    signal_quality: toSignalQuality(dto.signal_quality),
  }
}

function toSignalConfluence(dto) {
  return {
    secid: dto.secid,
    conclusion: dto.conclusion,
    levels: dto.levels.map((level) => ({
      ...level,
      latest_point: level.latest_point && { ...level.latest_point },
      lower_confirmation: level.lower_confirmation && { ...level.lower_confirmation },
    })),
  }
}

export function createMarketGateway() {
  return {
    async searchStocks(query, signal) {
      const response = await getJson(`/api/stocks/search?q=${encodeURIComponent(query)}`, signal)
      return response.items.map(toStock)
    },
    async getMarketSnapshot({ secid, timeframe, refresh }, signal) {
      const params = new URLSearchParams({ secid, timeframe, refresh: String(refresh) })
      const response = await getJson(`/api/chan?${params}`, signal)
      return toMarketSnapshot(response)
    },
    async getSignalConfluence(secid, signal) {
      const response = await getJson(`/api/chan/confluence?secid=${encodeURIComponent(secid)}`, signal)
      return toSignalConfluence(response)
    },
    async getQuote(secid, signal) {
      const response = await getJson(`/api/quote?secid=${encodeURIComponent(secid)}`, signal)
      return { ...response }
    },
    async getQuoteSeries(secid, signal) {
      const response = await getJson(`/api/quote/series?secid=${encodeURIComponent(secid)}`, signal)
      return { ...response }
    },
    async getRankings(limit, signal) {
      const response = await getJson(`/api/rankings?limit=${limit}`, signal)
      const entries = (items) => (items || []).map((entry) => ({
        ...entry, stock: toStock(entry.stock), signal_group: entry.signal_group || 'observation',
        signal_quality: toSignalQuality(entry.signal_quality),
      }))
      return { ...response, bullish: entries(response.bullish), bearish: entries(response.bearish) }
    },
    async recordQuoteSample(quote, signal) {
      const response = await postJson('/api/quote/samples', {
        secid: quote.secid,
        time: quote.time,
        price: quote.latest,
      }, signal)
      return { ...response }
    },
  }
}
