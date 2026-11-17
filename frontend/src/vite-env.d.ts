/// <reference types="vite/client" />

interface WechatMiniProgramRedirectOptions {
  url: string
  success?: () => void
  fail?: () => void
}

interface WechatJsSdk {
  miniProgram?: {
    redirectTo: (options: WechatMiniProgramRedirectOptions) => void
  }
}

interface Window {
  __wxjs_environment?: string
  wx?: WechatJsSdk
}
