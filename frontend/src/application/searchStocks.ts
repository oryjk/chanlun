import type { MarketGateway } from './ports.ts'

export function createSearchStocks(gateway: MarketGateway) {
  return (query: string, signal?: AbortSignal) => {
    const normalized = query.trim()
    if (!normalized) return Promise.resolve([])
    return gateway.searchStocks(normalized, signal)
  }
}
