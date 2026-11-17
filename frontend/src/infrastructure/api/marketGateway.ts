import type { MarketAssetsQueryParams, MarketGateway, MarketSnapshotQueryParams } from '../../application/ports.ts'
import type {
  AssetCategory,
  BuySellPoint,
  Candle,
  Divergence,
  Fractal,
  Instrument,
  LevelSignal,
  MacdPoint,
  MarketAssetItem,
  MarketAssetList,
  MarketGuidance,
  MarketSnapshot,
  MarketSummary,
  Pen,
  QuotePoint,
  QuoteSeries,
  RankingBoard,
  RankingEntry,
  RealtimeQuote,
  Segment,
  SignalConfluence,
  SignalQuality,
  Stock,
  Zhongshu,
} from '../../domain/types.ts'
import { getJson, postJson } from './httpClient.ts'

interface StockDto {
  secid: string
  symbol: string
  name: string
  market: string
}

interface BuySellPointDto {
  kind: number
  side: 'buy' | 'sell'
  pen_id: number
  candle_index: number
  time: string
  price: number
  confirmed: boolean
  reason: string
  invalidation_price?: number | null
  source_zone_id?: number | null
}

interface SignalQualityDto {
  state: string
  invalidation_price: number | null
  momentum_status: string
  volume_status: string
  macd_histogram: number | null
  macd_area_ratio: number | null
  pullback_macd_ratio: number | null
  reference_touched?: boolean | null
  bars_since_pivot?: number | null
  first_reference_crossed_at?: string | null
  first_reference_crossed_close?: number | null
  history_checked_through?: string | null
  breakout_volume_ratio: number | null
  pullback_volume_ratio: number | null
  history_status?: string
  first_detected_at?: string | null
  confirmed_at?: string | null
  reasons?: string[]
}

interface QuotePointDto {
  time: string
  price: number
}

interface RankingEntryDto {
  rank: number
  stock: StockDto
  bias: string
  strength: number
  latest: number
  change_percent: number
  invalidation_price?: number | null
  latest_point?: BuySellPointDto | null
  buy_point?: BuySellPointDto | null
  sell_point?: BuySellPointDto | null
  trend?: QuotePointDto[]
  signal_quality?: SignalQualityDto | null
  signal_group?: string
}

interface RankingBoardDto {
  generated_at?: string | null
  timeframe: string
  trade_date?: string | null
  total_count: number
  analyzed_count: number
  failed_count: number
  state: string
  progress_total: number
  progress_processed: number
  update_error?: string | null
  analysis_version?: number
  bullish?: RankingEntryDto[]
  bearish?: RankingEntryDto[]
}

interface MarketSnapshotDto {
  instrument: Instrument
  timeframe: string
  timeframe_label: string
  candles: Candle[]
  analysis: {
    fractals: Fractal[]
    pens: Pen[]
    segments: Segment[]
    zhongshu: Zhongshu[]
    merged_count: number
    trend: string
  }
  summary: MarketSummary
  guidance: MarketGuidance
  macd: MacdPoint[]
  signals: {
    trend_type: string
    divergences: Divergence[]
    points?: BuySellPointDto[]
  }
  signal_quality?: SignalQualityDto | null
}

interface SignalConfluenceDto {
  secid: string
  conclusion: string
  levels: Array<{
    timeframe: string
    timeframe_label: string
    trend_type: string
    latest_point: BuySellPointDto | null
    lower_confirmation: BuySellPointDto | null
  }>
}

interface MarketAssetItemDto {
  stock: StockDto
  latest: number
  change: number
  change_percent: number
  open: number
  high: number
  low: number
  previous_close: number
  volume: number
  amount: number
  sparkline?: number[]
  signal_label?: string | null
}

interface MarketAssetListDto {
  category?: AssetCategory
  as_of_date?: string | null
  items?: MarketAssetItemDto[]
}

function toStock(dto: StockDto): Stock {
  return { secid: dto.secid, symbol: dto.symbol, name: dto.name, market: dto.market }
}

function toBuySellPoint(dto: BuySellPointDto | null | undefined): BuySellPoint | null {
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

function toSignalQuality(dto: SignalQualityDto | null | undefined): SignalQuality | null {
  if (!dto) return null
  return {
    state: dto.state,
    invalidation_price: dto.invalidation_price,
    momentum_status: dto.momentum_status,
    volume_status: dto.volume_status,
    macd_histogram: dto.macd_histogram,
    macd_area_ratio: dto.macd_area_ratio,
    pullback_macd_ratio: dto.pullback_macd_ratio,
    reference_touched: dto.reference_touched ?? null,
    bars_since_pivot: dto.bars_since_pivot ?? null,
    first_reference_crossed_at: dto.first_reference_crossed_at || null,
    first_reference_crossed_close: dto.first_reference_crossed_close ?? null,
    history_checked_through: dto.history_checked_through || null,
    breakout_volume_ratio: dto.breakout_volume_ratio,
    pullback_volume_ratio: dto.pullback_volume_ratio,
    history_status: dto.history_status || 'unknown',
    first_detected_at: dto.first_detected_at || null,
    confirmed_at: dto.confirmed_at || null,
    reasons: [...(dto.reasons || [])],
  }
}

function toRankingEntry(dto: RankingEntryDto): RankingEntry {
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
    trend: (dto.trend || []).map((point): QuotePoint => ({ time: point.time, price: point.price })),
    signal_quality: toSignalQuality(dto.signal_quality),
    signal_group: dto.signal_group ?? 'observation',
  }
}

function toRankingBoard(dto: RankingBoardDto): RankingBoard {
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

function toMarketSnapshot(dto: MarketSnapshotDto): MarketSnapshot {
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
      points: (dto.signals?.points || []).map((point) => toBuySellPoint(point)!),
    },
    signal_quality: toSignalQuality(dto.signal_quality),
  }
}

function toSignalConfluence(dto: SignalConfluenceDto): SignalConfluence {
  return {
    secid: dto.secid,
    conclusion: dto.conclusion,
    levels: dto.levels.map((level): LevelSignal => ({
      timeframe: level.timeframe,
      timeframe_label: level.timeframe_label,
      trend_type: level.trend_type,
      latest_point: toBuySellPoint(level.latest_point),
      lower_confirmation: toBuySellPoint(level.lower_confirmation),
    })),
  }
}

function toMarketAssetItem(dto: MarketAssetItemDto): MarketAssetItem {
  return {
    stock: toStock(dto.stock),
    latest: Number(dto.latest),
    change: Number(dto.change),
    changePercent: Number(dto.change_percent),
    open: Number(dto.open),
    high: Number(dto.high),
    low: Number(dto.low),
    previousClose: Number(dto.previous_close),
    volume: Number(dto.volume),
    amount: Number(dto.amount),
    sparkline: Array.isArray(dto.sparkline) ? dto.sparkline.map(Number) : [],
    signalLabel: dto.signal_label ?? null,
  }
}

function toMarketAssetList(dto: MarketAssetListDto): MarketAssetList {
  return {
    category: dto.category || 'stock',
    asOfDate: dto.as_of_date ?? null,
    items: (dto.items || []).map(toMarketAssetItem),
  }
}

export function createMarketGateway(): MarketGateway {
  return {
    async searchStocks(query: string, signal?: AbortSignal): Promise<Stock[]> {
      const response = await getJson<{ items: StockDto[] }>(`/api/stocks/search?q=${encodeURIComponent(query)}`, signal)
      return response.items.map(toStock)
    },
    async getMarketAssets(
      { category = 'stock', sort = 'amount', order = 'desc', limit = 80 }: MarketAssetsQueryParams = {},
      signal?: AbortSignal,
    ): Promise<MarketAssetList> {
      const params = new URLSearchParams({
        category,
        sort,
        order,
        limit: String(limit),
      })
      const response = await getJson<MarketAssetListDto>(`/api/market/assets?${params}`, signal)
      return toMarketAssetList(response)
    },
    async getMarketSnapshot({ secid, timeframe, refresh = false }: MarketSnapshotQueryParams, signal?: AbortSignal): Promise<MarketSnapshot> {
      const params = new URLSearchParams({ secid, timeframe, refresh: String(refresh) })
      const response = await getJson<MarketSnapshotDto>(`/api/chan?${params}`, signal)
      return toMarketSnapshot(response)
    },
    async getSignalConfluence(secid: string, signal?: AbortSignal): Promise<SignalConfluence> {
      const response = await getJson<SignalConfluenceDto>(`/api/chan/confluence?secid=${encodeURIComponent(secid)}`, signal)
      return toSignalConfluence(response)
    },
    async getQuote(secid: string, signal?: AbortSignal): Promise<RealtimeQuote> {
      const response = await getJson<RealtimeQuote>(`/api/quote?secid=${encodeURIComponent(secid)}`, signal)
      return { ...response }
    },
    async getQuoteSeries(secid: string, signal?: AbortSignal): Promise<QuoteSeries> {
      const response = await getJson<QuoteSeries>(`/api/quote/series?secid=${encodeURIComponent(secid)}`, signal)
      return { ...response }
    },
    async getRankings(limit: number, signal?: AbortSignal): Promise<RankingBoard> {
      const response = await getJson<RankingBoardDto>(`/api/rankings?limit=${limit}`, signal)
      return toRankingBoard(response)
    },
    async recordQuoteSample(quote: RealtimeQuote, signal?: AbortSignal): Promise<QuoteSeries> {
      const response = await postJson<QuoteSeries>('/api/quote/samples', {
        secid: quote.secid,
        time: quote.time,
        price: quote.latest,
      }, signal)
      return { ...response }
    },
  }
}
