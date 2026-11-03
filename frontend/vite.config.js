import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  // 生产部署在 match.oryjk.cn/chanlun/ 时以 CHANLUN_BASE_PATH=/chanlun/ 构建
  base: process.env.CHANLUN_BASE_PATH || '/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8000',
    },
  },
})
