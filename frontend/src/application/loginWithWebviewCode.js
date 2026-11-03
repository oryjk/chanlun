export function createLoginWithWebviewCode(gateway) {
  return (code, signal) => gateway.loginWithWebviewCode(code, signal)
}
