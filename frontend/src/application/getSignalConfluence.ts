import type { MarketGateway } from './ports.ts'

export function createGetSignalConfluence(gateway: MarketGateway) {
  return (secid: string, signal?: AbortSignal) => gateway.getSignalConfluence(secid, signal)
}
