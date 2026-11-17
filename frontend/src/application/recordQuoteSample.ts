import type { RealtimeQuote } from '../domain/types.ts'
import type { MarketGateway } from './ports.ts'

export function createRecordQuoteSample(gateway: MarketGateway) {
  return (quote: RealtimeQuote, signal?: AbortSignal) => gateway.recordQuoteSample(quote, signal)
}
