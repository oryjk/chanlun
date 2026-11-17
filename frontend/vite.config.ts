import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'

export default defineConfig({
  // 生产部署在 match.oryjk.cn/chanlun/ 时以 CHANLUN_BASE_PATH=/chanlun/ 构建
  base: process.env.CHANLUN_BASE_PATH || '/',
  plugins: [react(), {
    name: 'chanlun-dev-startup-diagnostics',
    apply: 'serve',
    transformIndexHtml() {
      return [{
        tag: 'script',
        children: readFileSync(new URL('./dev/startup-diagnostics.js', import.meta.url), 'utf8'),
        injectTo: 'head-prepend',
      }]
    },
  }],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8000',
    },
  },
})
