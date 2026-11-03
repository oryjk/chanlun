export function createGetSession(gateway) {
  return (signal) => gateway.getSession(signal)
}
