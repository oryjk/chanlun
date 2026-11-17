import type { AuthGateway } from './ports.ts'

export function createLoginWithWebviewCode(gateway: AuthGateway) {
  return (code: string, signal?: AbortSignal) => gateway.loginWithWebviewCode(code, signal)
}
