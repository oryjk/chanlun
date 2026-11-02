import { getJson, postJson } from './httpClient.js'

function toStock(dto) {
  return { secid: dto.secid, symbol: dto.symbol, name: dto.name, market: dto.market }
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
      return { ...response }
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
