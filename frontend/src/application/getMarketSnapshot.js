export function createGetMarketSnapshot(gateway) {
  return ({ secid, timeframe, refresh = false }, signal) => (
    gateway.getMarketSnapshot({ secid, timeframe, refresh }, signal)
  )
}
