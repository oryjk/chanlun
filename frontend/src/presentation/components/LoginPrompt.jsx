import { useEffect, useState } from 'react'
import { buildReturnQuery } from '../../domain/auth.js'

const TITLES = {
  miniprogram: '登录报名系统账号',
  wechat: '请在小程序中打开',
  browser: '请在微信中打开',
}

export default function LoginPrompt({ reason, environment, requestLogin, onClose }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [link, setLink] = useState('')
  const [copied, setCopied] = useState(false)
  const inMiniProgram = environment === 'miniprogram'

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const start = async () => {
    setBusy(true)
    setError('')
    try {
      const result = await requestLogin(environment, buildReturnQuery(window.location.search))
      if (result.kind === 'link') setLink(result.url)
    } catch (reason) {
      setError(reason.message)
    } finally {
      setBusy(false)
    }
  }

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
    } catch {
      setError('复制失败，请长按链接手动复制')
    }
  }

  return (
    <div className="login-prompt-backdrop" onClick={onClose}>
      <section className="login-prompt" role="dialog" aria-modal="true" aria-labelledby="login-prompt-title" onClick={(event) => event.stopPropagation()}>
        <h2 id="login-prompt-title">{TITLES[environment]}</h2>
        <p>{reason}。</p>
        <p className="login-prompt-detail">
          {inMiniProgram
            ? '将回到报名系统小程序完成登录，登录后自动返回当前页面。'
            : '缠观的账号来自报名系统小程序，需要在微信中通过小程序登录。'}
        </p>
        {link && (
          <div className="login-prompt-link">
            <a href={link}>{link}</a>
            <button type="button" onClick={copy}>{copied ? '已复制' : '复制链接'}</button>
            <small>{environment === 'browser' ? '在手机上点击链接会自动打开微信；电脑上请复制到微信中打开。' : '点击链接即可打开报名系统小程序。'}</small>
          </div>
        )}
        {error && <p className="login-prompt-error" role="alert">{error}</p>}
        <div className="login-prompt-actions">
          <button type="button" className="secondary" onClick={onClose}>暂不登录</button>
          {!link && (
            <button type="button" onClick={start} disabled={busy}>
              {busy ? '请稍候…' : inMiniProgram ? '去登录' : '获取小程序链接'}
            </button>
          )}
        </div>
      </section>
    </div>
  )
}
