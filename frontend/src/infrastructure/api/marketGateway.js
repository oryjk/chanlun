import { getJson } from './httpClient.js'

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
      zhongshu: dto.analysis.zhongshu.map((zone) => ({ ...zone })),
      merged_count: dto.analysis.merged_count,
      trend: dto.analysis.trend,
    },
    summary: { ...dto.summary },
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
  }
}
