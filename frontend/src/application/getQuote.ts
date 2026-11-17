import type { MarketGateway } from './ports.ts'

export function createGetQuote(gateway: MarketGateway) {
  return (secid: string, signal?: AbortSignal) => gateway.getQuote(secid, signal)
}
