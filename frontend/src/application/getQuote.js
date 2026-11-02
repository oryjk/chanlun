export function createGetQuote(gateway) {
  return (secid, signal) => gateway.getQuote(secid, signal)
}
