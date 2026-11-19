import type { OvernightSnapshot } from '../domain/overnight.ts'
import type {
  AssetCategory,
  AssetSortField,
  AuthChannel,
  AuthSession,
  ClientEnvironment,
  MarketAssetList,
  MarketSnapshot,
  QuoteSeries,
  RankingBoard,
  RealtimeQuote,
  SignalConfluence,
  SortOrder,
  Stock,
  ThemeMode,
  TrackedAssetRecord,
  TrackingOverview,
  TrackingPreferencesData,
} from '../domain/types.ts'

export interface MarketAssetsQueryParams {
  category?: AssetCategory
  sort?: AssetSortField
  order?: SortOrder
  limit?: number
}

export interface MarketSnapshotQueryParams {
  secid: string
  timeframe: string
  refresh?: boolean
}

export interface EmailCodeSendResult {
  ok: boolean
  expiresIn: number
  cooldownSeconds: number
}

export interface EmailRegisterPayload {
  email: string
  code: string
  password: string
  nickname?: string
}

export interface EmailLoginPayload {
  email: string
  password: string
}

export type LoginRequestResult =
  | { kind: 'redirected' }
  | { kind: 'link'; url: string }

export interface MarketGateway {
  searchStocks: (query: string, signal?: AbortSignal) => Promise<Stock[]>
  getMarketAssets: (params?: MarketAssetsQueryParams, signal?: AbortSignal) => Promise<MarketAssetList>
  getMarketSnapshot: (params: MarketSnapshotQueryParams, signal?: AbortSignal) => Promise<MarketSnapshot>
  getSignalConfluence: (secid: string, signal?: AbortSignal) => Promise<SignalConfluence>
  getQuote: (secid: string, signal?: AbortSignal) => Promise<RealtimeQuote>
  getQuoteSeries: (secid: string, signal?: AbortSignal) => Promise<QuoteSeries>
  getRankings: (limit: number, signal?: AbortSignal) => Promise<RankingBoard>
  recordQuoteSample: (quote: RealtimeQuote, signal?: AbortSignal) => Promise<QuoteSeries>
}

export type GetOvernightSnapshot = (signal?: AbortSignal) => Promise<OvernightSnapshot>

export interface MethodsReader {
  getOvernightSnapshot: GetOvernightSnapshot
}

export interface AuthGateway {
  getSession: (signal?: AbortSignal) => Promise<AuthSession>
  getActiveSession: (signal?: AbortSignal) => Promise<AuthSession>
  loginWithWebviewCode: (code: string, signal?: AbortSignal) => Promise<AuthSession>
  getMiniProgramLink: (returnQuery: string, signal?: AbortSignal) => Promise<string>
  sendEmailCode: (email: string, signal?: AbortSignal) => Promise<EmailCodeSendResult>
  registerWithEmail: (payload: EmailRegisterPayload, signal?: AbortSignal) => Promise<AuthSession>
  loginWithEmail: (payload: EmailLoginPayload, signal?: AbortSignal) => Promise<AuthSession>
  logoutSession: (channel: AuthChannel | null, signal?: AbortSignal) => Promise<{ ok: boolean }>
}

export interface TrackingGateway {
  getTrackings: (channel: AuthChannel, signal?: AbortSignal) => Promise<TrackingOverview>
  startTracking: (
    secid: string,
    channel: AuthChannel,
    signal?: AbortSignal,
  ) => Promise<{ ok: boolean; item: TrackedAssetRecord | null }>
  cancelTrackings: (
    secids: string[],
    channel: AuthChannel,
    signal?: AbortSignal,
  ) => Promise<{ ok: boolean; removed: number }>
}

export interface MiniProgramBridge {
  redirectToLanding: (returnQuery: string) => Promise<void>
}

export interface PreferencesStorage {
  readThemeMode: () => ThemeMode
  writeThemeMode: (mode: ThemeMode) => void
  readEmailToken: () => string
  writeEmailToken: (token: string) => void
  clearEmailToken: () => void
  readTrackingPrefs: (userKey: string) => TrackingPreferencesData
  writeTrackingPrefs: (userKey: string, prefs: TrackingPreferencesData) => void
}

export interface ThemePreferencesUseCases {
  read: () => ThemeMode
  write: (mode: ThemeMode) => void
}

export interface TrackingPreferencesUseCases {
  read: (userKey: string) => TrackingPreferencesData
  write: (userKey: string, prefs: TrackingPreferencesData) => void
}

export type RequestLoginUseCase = (
  environment: ClientEnvironment,
  returnQuery: string,
  signal?: AbortSignal,
) => Promise<LoginRequestResult>

export interface AppUseCases {
  searchStocks: (query: string, signal?: AbortSignal) => Promise<Stock[]>
  getMarketAssets: (params?: MarketAssetsQueryParams, signal?: AbortSignal) => Promise<MarketAssetList>
  getMarketSnapshot: (params: MarketSnapshotQueryParams, signal?: AbortSignal) => Promise<MarketSnapshot>
  getSignalConfluence: (secid: string, signal?: AbortSignal) => Promise<SignalConfluence>
  getQuote: (secid: string, signal?: AbortSignal) => Promise<RealtimeQuote>
  getQuoteSeries: (secid: string, signal?: AbortSignal) => Promise<QuoteSeries>
  getRankings: (limit: number, signal?: AbortSignal) => Promise<RankingBoard>
  getOvernightSnapshot: GetOvernightSnapshot
  recordQuoteSample: (quote: RealtimeQuote, signal?: AbortSignal) => Promise<QuoteSeries>
  getTrackings: (channel: AuthChannel, signal?: AbortSignal) => Promise<TrackingOverview>
  startTracking: (
    secid: string,
    channel: AuthChannel,
    signal?: AbortSignal,
  ) => Promise<{ ok: boolean; item: TrackedAssetRecord | null }>
  cancelTrackings: (
    secids: string[],
    channel: AuthChannel,
    signal?: AbortSignal,
  ) => Promise<{ ok: boolean; removed: number }>
  getSession: (signal?: AbortSignal) => Promise<AuthSession>
  loginWithWebviewCode: (code: string, signal?: AbortSignal) => Promise<AuthSession>
  requestLogin: RequestLoginUseCase
  sendEmailCode: (email: string, signal?: AbortSignal) => Promise<EmailCodeSendResult>
  registerWithEmail: (payload: EmailRegisterPayload, signal?: AbortSignal) => Promise<AuthSession>
  loginWithEmail: (payload: EmailLoginPayload, signal?: AbortSignal) => Promise<AuthSession>
  logoutSession: (channel: AuthChannel | null, signal?: AbortSignal) => Promise<{ ok: boolean }>
  themePreferences: ThemePreferencesUseCases
  trackingPreferences: TrackingPreferencesUseCases
}
