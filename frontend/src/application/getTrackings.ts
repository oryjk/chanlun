import type { AuthChannel } from '../domain/types.ts'
import type { TrackingGateway } from './ports.ts'

export function createGetTrackings(gateway: TrackingGateway) {
  return (channel: AuthChannel, signal?: AbortSignal) => gateway.getTrackings(channel, signal)
}
