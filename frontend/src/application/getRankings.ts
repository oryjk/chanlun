import type { MarketGateway } from './ports.ts'

export function createGetRankings(gateway: MarketGateway) {
  return (limit: number, signal?: AbortSignal) => gateway.getRankings(limit, signal)
}
