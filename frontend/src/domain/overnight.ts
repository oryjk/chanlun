import type { Stock } from './types.ts'

export type OvernightPhase = 'waiting' | 'preparing' | 'scanning' | 'closed' | 'exit'
export type OvernightScanStatus = 'pending' | 'ready' | 'partial' | 'unavailable'
export type OvernightCandidateStatus = 'watching' | 'pullback' | 'filtered' | 'unavailable'

export interface OvernightCandidate {
  readonly stock: Stock
  readonly price: number
  readonly changePercent: number
  readonly volumeRatio: number
  readonly turnoverRate: number
  readonly totalMarketCap: number
  readonly limitUpDate: string
  readonly asOf: string
  readonly averagePrice: number | null
  readonly minuteChangePercent: number | null
  readonly minuteAt: string | null
  readonly benchmarkChangePercent: number | null
  readonly pullbackAt: string | null
  readonly status: OvernightCandidateStatus
  readonly reason: string | null
}

export interface OvernightReminder {
  readonly stock: Stock
  readonly signalDate: string
  readonly signalAt: string
  readonly exceptionObserved: boolean | null
  readonly exceptionAsOf: string | null
  readonly exceptionValidUntil: string | null
}

export interface OvernightSnapshot {
  readonly tradeDate: string | null
  readonly phase: OvernightPhase
  readonly observedAt: string | null
  readonly historySessions: number
  readonly universeCount: number
  readonly freshCount: number
  readonly matchedCount: number
  readonly minuteCheckedCount: number
  readonly elapsedMs: number | null
  readonly maxQuoteAgeSeconds: number | null
  readonly status: OvernightScanStatus
  readonly candidates: readonly OvernightCandidate[]
  readonly reminders: readonly OvernightReminder[]
  readonly remindersUntil: string | null
}
