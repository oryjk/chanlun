export function createSearchStocks(gateway) {
  return (query, signal) => {
    const normalized = query.trim()
    if (!normalized) return Promise.resolve([])
    return gateway.searchStocks(normalized, signal)
  }
}
