import type { AuthChannel } from '../domain/types.ts'
import type { AuthGateway, EmailLoginPayload, EmailRegisterPayload } from './ports.ts'

export function createSendEmailCode(gateway: AuthGateway) {
  return (email: string, signal?: AbortSignal) => gateway.sendEmailCode(email, signal)
}

export function createRegisterWithEmail(gateway: AuthGateway) {
  return (payload: EmailRegisterPayload, signal?: AbortSignal) => gateway.registerWithEmail(payload, signal)
}

export function createLoginWithEmail(gateway: AuthGateway) {
  return (payload: EmailLoginPayload, signal?: AbortSignal) => gateway.loginWithEmail(payload, signal)
}

export function createLogoutSession(gateway: AuthGateway) {
  return (channel: AuthChannel | null, signal?: AbortSignal) => gateway.logoutSession(channel, signal)
}
