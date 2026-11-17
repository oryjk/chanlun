import type { AuthChannel } from '../domain/types.ts'
import type { TrackingGateway } from './ports.ts'

export function createCancelTrackings(gateway: TrackingGateway) {
  return (secids: string[], channel: AuthChannel, signal?: AbortSignal) => (
    gateway.cancelTrackings(secids, channel, signal)
  )
}
