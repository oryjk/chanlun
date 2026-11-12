import { getJson, postJson } from './httpClient.js'

function toStock(dto) {
  return { secid: dto.secid, symbol: dto.symbol, name: dto.name, market: dto.market }
}

function toBuySellPoint(dto) {
  if (!dto) return null
  return {
    kind: dto.kind,
    side: dto.side,
    pen_id: dto.pen_id,
    candle_index: dto.candle_index,
    time: dto.time,
    price: dto.price,
    confirmed: dto.confirmed,
    reason: dto.reason,
    invalidation_price: dto.invalidation_price ?? null,
    source_zone_id: dto.source_zone_id ?? null,
  }
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

function toRankingEntry(dto) {
  return {
    rank: dto.rank,
    stock: toStock(dto.stock),
    bias: dto.bias,
    strength: dto.strength,
    latest: dto.latest,
    change_percent: dto.change_percent,
    invalidation_price: dto.invalidation_price ?? null,
    latest_point: toBuySellPoint(dto.latest_point),
    buy_point: toBuySellPoint(dto.buy_point),
    sell_point: toBuySellPoint(dto.sell_point),
    trend: (dto.trend || []).map((point) => ({ time: point.time, price: point.price })),
    signal_quality: toSignalQuality(dto.signal_quality),
    signal_group: dto.signal_group ?? 'observation',
  }
}

function toRankingBoard(dto) {
  return {
    generated_at: dto.generated_at ?? null,
    timeframe: dto.timeframe,
    trade_date: dto.trade_date ?? null,
    total_count: dto.total_count,
    analyzed_count: dto.analyzed_count,
    failed_count: dto.failed_count,
    state: dto.state,
    progress_total: dto.progress_total,
    progress_processed: dto.progress_processed,
    update_error: dto.update_error ?? null,
    analysis_version: dto.analysis_version ?? 1,
    bullish: (dto.bullish || []).map(toRankingEntry),
    bearish: (dto.bearish || []).map(toRankingEntry),
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
      points: (dto.signals?.points || []).map(toBuySellPoint),
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
      return toRankingBoard(response)
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
