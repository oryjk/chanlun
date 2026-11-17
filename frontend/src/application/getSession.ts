import type { AuthGateway } from './ports.ts'

export function createGetSession(gateway: AuthGateway) {
  return (signal?: AbortSignal) => gateway.getActiveSession(signal)
}
