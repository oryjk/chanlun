export function createGetQuoteSeries(gateway) {
  return (secid, signal) => gateway.getQuoteSeries(secid, signal)
}
