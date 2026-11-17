import type { MarketAssetsQueryParams, MarketGateway } from './ports.ts'

export function createGetMarketAssets(gateway: MarketGateway) {
  return (params?: MarketAssetsQueryParams, signal?: AbortSignal) => gateway.getMarketAssets(params, signal)
}
