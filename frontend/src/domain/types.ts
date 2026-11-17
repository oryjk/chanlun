export type Exchange = 'SH' | 'SZ' | 'BJ'

export interface Stock {
  secid: string
  symbol: string
  name: string
  market: string
}

export interface Instrument {
  secid: string
  symbol: string
  name: string
  decimal: number
  data_source: string
  currency: string | null
  exchange: string | null
}

export interface Candle {
  time: string
  open: number
  close: number
  high: number
  low: number
  volume: number
}

export interface Fractal {
  type: 'top' | 'bottom' | string
  merged_index: number
  candle_index: number
  time: string
  price: number
}

export interface Pen {
  id: number
  direction: 'up' | 'down' | string
  start_index: number
  end_index: number
  start_time: string
  end_time: string
  start_price: number
  end_price: number
  high: number
  low: number
  confirmed: boolean
}

export interface Segment {
  id: number
  direction: 'up' | 'down' | string
  start_pen_index: number
  end_pen_index: number
  start_index: number
  end_index: number
  start_time: string
  end_time: string
  start_price: number
  end_price: number
  high: number
  low: number
  confirmed: boolean
}

export interface Zhongshu {
  id: number
  start_index: number
  end_index: number
  start_time: string
  end_time: string
  low: number
  high: number
  gg: number
  dd: number
  confirmed: boolean
}

export interface ChanAnalysis {
  fractals: Fractal[]
  pens: Pen[]
  segments: Segment[]
  zhongshu: Zhongshu[]
  merged_count: number
  trend: string
}

export interface MacdPoint {
  dif: number
  dea: number
  histogram: number
}

export interface Divergence {
  kind: 'trend' | 'consolidation' | string
  direction: 'bottom' | 'top' | string
  zhongshu_id: number
  entering_pen_id: number
  leaving_pen_id: number
  entering_area: number
  leaving_area: number
  candle_index: number
  time: string
  price: number
  confirmed: boolean
}

export interface BuySellPoint {
  kind: number
  side: 'buy' | 'sell'
  pen_id?: number
  candle_index?: number
  time?: string
  price: number
  confirmed: boolean
  reason?: string
  invalidation_price?: number | null
  source_zone_id?: number | null
}

export interface SignalQuality {
  state: 'confirmed' | 'candidate' | 'invalidated' | 'insufficient' | string
  invalidation_price: number | null
  momentum_status?: string
  volume_status?: string
  macd_histogram?: number | null
  macd_area_ratio?: number | null
  pullback_macd_ratio?: number | null
  reference_touched?: boolean | null
  bars_since_pivot?: number | null
  first_reference_crossed_at?: string | null
  first_reference_crossed_close?: number | null
  history_checked_through?: string | null
  breakout_volume_ratio?: number | null
  pullback_volume_ratio?: number | null
  history_status?: 'checked_from_pivot' | 'invalidated' | 'unknown' | string
  first_detected_at?: string | null
  confirmed_at?: string | null
  reasons?: string[]
}

export interface ChanSignals {
  trend_type: 'up_trend' | 'down_trend' | 'consolidation' | 'none' | string
  divergences: Divergence[]
  points: BuySellPoint[]
}

export interface MarketSummary {
  latest: number
  change: number
  change_percent: number
  high: number
  low: number
  volume: number
}

export interface MarketGuidance {
  bias: 'bullish' | 'bearish' | 'neutral' | string
  reasons: string[]
  invalidation_price: number | null
  strength: number
}

export interface MarketSnapshot {
  instrument: Instrument
  timeframe: TimeframeValue | string
  timeframe_label: string
  candles: Candle[]
  analysis: ChanAnalysis
  summary: MarketSummary
  guidance: MarketGuidance
  macd: MacdPoint[]
  signals: ChanSignals
  signal_quality: SignalQuality | null
}

export interface LevelSignal {
  timeframe: string
  timeframe_label: string
  trend_type: string
  latest_point: BuySellPoint | null
  lower_confirmation: BuySellPoint | null
}

export interface SignalConfluence {
  secid: string
  levels: LevelSignal[]
  conclusion: string
}

export interface QuotePoint {
  time: string
  price: number
}

export interface RealtimeQuote {
  secid: string
  symbol: string
  name: string
  time: string
  open: number
  high: number
  low: number
  previous_close: number
  latest: number
  change: number
  change_percent: number
  volume: number
  amount: number
}

export interface QuoteSeries {
  secid: string
  points: QuotePoint[]
}

export interface RankingEntry {
  rank: number
  stock: Stock
  bias: 'bullish' | 'bearish' | string
  strength: number
  latest: number
  change_percent: number
  invalidation_price: number | null
  latest_point: BuySellPoint | null
  buy_point: BuySellPoint | null
  sell_point: BuySellPoint | null
  trend: QuotePoint[]
  signal_quality: SignalQuality | null
  signal_group: 'confirmed' | 'observation' | string
}

export interface RankingBoard {
  generated_at: string | null
  timeframe: string
  trade_date: string | null
  total_count: number
  analyzed_count: number
  failed_count: number
  state: 'pending' | 'preparing' | 'running' | 'ready' | 'error' | string
  progress_total: number
  progress_processed: number
  update_error: string | null
  analysis_version: number
  bullish: RankingEntry[]
  bearish: RankingEntry[]
}

export type AssetCategory = 'stock' | 'etf'
export type AssetSortField = 'amount' | 'change_percent' | 'latest'
export type SortOrder = 'asc' | 'desc'

export interface MarketAssetItem {
  stock: Stock
  latest: number
  change: number
  changePercent: number
  open: number
  high: number
  low: number
  previousClose: number
  volume: number
  amount: number
  sparkline: number[]
  signalLabel: string | null
}

export interface MarketAssetList {
  category: AssetCategory
  asOfDate: string | null
  items: MarketAssetItem[]
}

export interface TrackedAssetItem {
  stock: Stock
  startDate: string
  startPrice: number
  latestPrice: number
  dayReturnPct: number
  dayProfit: number
  cumulativeReturnPct: number
  cumulativeProfit: number
  currentValue: number
  sparkline: number[]
}

export interface TrackedAssetRecord {
  secid: string
  symbol: string
  name: string
  market: string
  startDate: string
  startPrice: number
}

export interface TrackingOverview {
  asOfDate: string
  items: TrackedAssetItem[]
}

export type TrackingSortBy = 'default' | 'cumulative' | 'day' | 'days'

export interface TrackingPreferencesData {
  sortBy: TrackingSortBy
  pinnedSecids: string[]
  customOrder: string[]
}

export interface UserIdentity {
  userId: string
  nickname: string
  email?: string
}

export type AuthChannel = 'cookie' | 'bearer'

export interface AuthSession {
  authenticated: boolean
  channel: AuthChannel | null
  user: UserIdentity | null
  token?: string
}

export type ClientEnvironment = 'miniprogram' | 'wechat' | 'browser'
export type ThemeMode = 'system' | 'light' | 'dark'
export type ResolvedTheme = 'light' | 'dark'

export type TimeframeValue = '5m' | '30m' | '1h' | '1d'
export type ViewTabValue = TimeframeValue | 'realtime'

export interface TimeframeOption {
  value: ViewTabValue
  label: string
  short: string
}

export type RouteView = 'market' | 'detail' | 'tracking' | 'rankings' | 'menu'
export type PercentTone = 'up' | 'down' | 'flat'
export type ConclusionTone = 'up' | 'down' | 'caution' | 'neutral'
