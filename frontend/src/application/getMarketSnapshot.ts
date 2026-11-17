import type { MarketGateway, MarketSnapshotQueryParams } from './ports.ts'

export function createGetMarketSnapshot(gateway: MarketGateway) {
  return ({ secid, timeframe, refresh = false }: MarketSnapshotQueryParams, signal?: AbortSignal) => (
    gateway.getMarketSnapshot({ secid, timeframe, refresh }, signal)
  )
}
