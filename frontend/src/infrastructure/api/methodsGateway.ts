import type { MethodsReader } from '../../application/ports.ts'
import type {
  OvernightCandidate,
  OvernightCandidateStatus,
  OvernightPhase,
  OvernightReminder,
  OvernightScanStatus,
  OvernightSnapshot,
} from '../../domain/overnight.ts'
import { exchangeOf } from '../../domain/security.ts'
import type { Stock } from '../../domain/types.ts'
import { getJson } from './httpClient.ts'

interface StockDto {
  secid: string
  symbol: string
  name: string
}

interface OvernightCandidateDto extends StockDto {
  price: number
  change_percent: number
  volume_ratio: number
  turnover_rate: number
  total_market_cap: number
  limit_up_date: string
  as_of: string
  average_price: number | null
  minute_change_percent?: number | null
  minute_at?: string | null
  benchmark_change_percent: number | null
  pullback_at: string | null
  status: OvernightCandidateStatus
  reason: string | null
}

interface OvernightReminderDto extends StockDto {
  signal_date: string
  signal_at: string
  exception_observed?: boolean | null
  exception_as_of?: string | null
  exception_valid_until?: string | null
}

interface OvernightSnapshotDto {
  trade_date: string | null
  phase: OvernightPhase
  observed_at: string | null
  history_sessions: number
  universe_count: number
  fresh_count: number
  matched_count: number
  minute_checked_count: number
  elapsed_ms: number | null
  max_quote_age_seconds: number | null
  status: OvernightScanStatus
  candidates: OvernightCandidateDto[]
  reminders: OvernightReminderDto[]
  reminders_until?: string | null
}

const MARKET_LABELS = { SH: '沪A', SZ: '深A', BJ: '北A' }

function toStock({ secid, symbol, name }: StockDto): Stock {
  const exchange = exchangeOf(secid)
  return { secid, symbol, name, market: exchange ? MARKET_LABELS[exchange] : '' }
}

function toCandidate(dto: OvernightCandidateDto): OvernightCandidate {
  return {
    stock: toStock(dto),
    price: dto.price,
    changePercent: dto.change_percent,
    volumeRatio: dto.volume_ratio,
    turnoverRate: dto.turnover_rate,
    totalMarketCap: dto.total_market_cap,
    limitUpDate: dto.limit_up_date,
    asOf: dto.as_of,
    averagePrice: dto.average_price,
    minuteChangePercent: dto.minute_change_percent ?? null,
    minuteAt: dto.minute_at ?? null,
    benchmarkChangePercent: dto.benchmark_change_percent,
    pullbackAt: dto.pullback_at,
    status: dto.status,
    reason: dto.reason,
  }
}

function toReminder(dto: OvernightReminderDto): OvernightReminder {
  return {
    stock: toStock(dto),
    signalDate: dto.signal_date,
    signalAt: dto.signal_at,
    exceptionObserved: dto.exception_observed ?? null,
    exceptionAsOf: dto.exception_as_of ?? null,
    exceptionValidUntil: dto.exception_valid_until ?? null,
  }
}

function toSnapshot(dto: OvernightSnapshotDto): OvernightSnapshot {
  return {
    tradeDate: dto.trade_date,
    phase: dto.phase,
    observedAt: dto.observed_at,
    historySessions: dto.history_sessions,
    universeCount: dto.universe_count,
    freshCount: dto.fresh_count,
    matchedCount: dto.matched_count,
    minuteCheckedCount: dto.minute_checked_count,
    elapsedMs: dto.elapsed_ms,
    maxQuoteAgeSeconds: dto.max_quote_age_seconds,
    status: dto.status,
    candidates: dto.candidates.map(toCandidate),
    reminders: dto.reminders.map(toReminder),
    remindersUntil: dto.reminders_until ?? null,
  }
}

export function createMethodsGateway(): MethodsReader {
  return {
    async getOvernightSnapshot(signal) {
      return toSnapshot(await getJson<OvernightSnapshotDto>('/api/methods/overnight', signal))
    },
  }
}
