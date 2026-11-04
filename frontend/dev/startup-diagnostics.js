// 仅由 Vite 开发服务注入；使用普通脚本，在应用模块启动前捕获旧内核错误。
(function () {
  var lastError = ''

  function showFailure(detail) {
    var root = document.getElementById('root')
    if (!root || root.childElementCount) return
    var panel = document.getElementById('chanlun-startup-error')
    if (!panel) {
      panel = document.createElement('pre')
      panel.id = 'chanlun-startup-error'
      panel.setAttribute('role', 'alert')
      panel.style.cssText = 'margin:24px;padding:20px;white-space:pre-wrap;overflow-wrap:anywhere;font:14px/1.6 monospace;background:#fff;color:#1c2533;border:1px solid #dfe5ee;'
      document.body.appendChild(panel)
    }
    // 不显示一次性 code 或其他桥接参数。
    var safeDetail = String(detail).replace(/([?&]webview_[^=\s]*=)[^&#\s]*/g, '$1[已隐藏]')
    panel.textContent = '缠观开发页启动失败\n\n' + safeDetail + '\n\n浏览器：' + navigator.userAgent
  }

  function report(detail) {
    lastError = detail
    setTimeout(function () { showFailure(detail) }, 0)
  }

  window.addEventListener('error', function (event) {
    if (event.error) report(event.error.stack || event.message)
    else if (event.target && event.target.tagName === 'SCRIPT') {
      report('脚本加载失败：' + (event.target.getAttribute('src') || '内联脚本').split('?')[0])
    }
  }, true)
  window.addEventListener('unhandledrejection', function (event) {
    report(event.reason && event.reason.stack || String(event.reason))
  })
  setTimeout(function () {
    showFailure(lastError || '应用脚本尚未完成启动，请检查 Network 中失败或一直等待的脚本请求。')
  }, 15000)
})()
