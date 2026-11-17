import type { AuthChannel } from '../domain/types.ts'
import type { TrackingGateway } from './ports.ts'

export function createStartTracking(gateway: TrackingGateway) {
  return (secid: string, channel: AuthChannel, signal?: AbortSignal) => (
    gateway.startTracking(secid, channel, signal)
  )
}
