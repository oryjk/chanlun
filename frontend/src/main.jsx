import { createRoot } from 'react-dom/client'
import { useCases } from './app/container.js'
import App from './App.jsx'
import { detectClientEnvironment, splitWebviewParams } from './domain/auth.js'
import { AuthProvider } from './presentation/components/AuthContext.jsx'
import './styles.css'

// 渲染前取出报名系统小程序带来的一次性 code，并立即从地址栏抹掉，避免留在历史记录和分享链接里
const { code, search } = splitWebviewParams(window.location.search)
if (search !== window.location.search) {
  window.history.replaceState(window.history.state, '', `${window.location.pathname}${search}${window.location.hash}`)
}
const environment = detectClientEnvironment({
  userAgent: navigator.userAgent,
  wxjsEnvironment: window.__wxjs_environment,
})

createRoot(document.getElementById('root')).render(
  <AuthProvider useCases={useCases} initialCode={code} environment={environment}>
    <App useCases={useCases} />
  </AuthProvider>,
)
