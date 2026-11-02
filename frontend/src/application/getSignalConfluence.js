export function createGetSignalConfluence(gateway) {
  return (secid, signal) => gateway.getSignalConfluence(secid, signal)
}
