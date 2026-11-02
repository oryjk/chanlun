export function createGetRankings(gateway) {
  return (limit, signal) => gateway.getRankings(limit, signal)
}
