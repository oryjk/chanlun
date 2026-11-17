import type { MarketGateway } from './ports.ts'

export function createGetQuoteSeries(gateway: MarketGateway) {
  return (secid: string, signal?: AbortSignal) => gateway.getQuoteSeries(secid, signal)
}
