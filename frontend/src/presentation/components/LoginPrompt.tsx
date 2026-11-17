import { type FormEvent, useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import { buildReturnQuery } from '../../domain/auth.ts'
import type { AuthSession, ClientEnvironment } from '../../domain/types.ts'
import type { PreferredLoginMode } from './AuthContext.tsx'

const MINI_TITLES: Record<ClientEnvironment, string> = {
  miniprogram: '登录报名系统账号',
  wechat: '通过小程序登录',
  browser: '登录缠观账号',
}

export interface LoginPromptProps {
  reason: string
  preferredMode?: PreferredLoginMode
  environment: ClientEnvironment
  requestLogin: AppUseCases['requestLogin']
  sendEmailCode: AppUseCases['sendEmailCode']
  registerWithEmail: AppUseCases['registerWithEmail']
  loginWithEmail: AppUseCases['loginWithEmail']
  onEmailAuthenticated?: (session: AuthSession) => void
  onClose: () => void
}

export default function LoginPrompt({
  reason,
  preferredMode = 'email',
  environment,
  requestLogin,
  sendEmailCode,
  registerWithEmail,
  loginWithEmail,
  onEmailAuthenticated,
  onClose,
}: LoginPromptProps) {
  const inMiniProgram = environment === 'miniprogram'
  const [channelTab, setChannelTab] = useState<PreferredLoginMode>(
    inMiniProgram ? 'wechat' : (preferredMode === 'wechat' ? 'wechat' : 'email'),
  )
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [nickname, setNickname] = useState('')
  const [cooldown, setCooldown] = useState(0)
  const [codeNotice, setCodeNotice] = useState('')

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [link, setLink] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  useEffect(() => {
    if (cooldown <= 0) return undefined
    const timer = window.setInterval(() => {
      setCooldown((prev) => (prev > 1 ? prev - 1 : 0))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [cooldown])

  const startMiniProgram = async () => {
    setBusy(true)
    setError('')
    try {
      const result = await requestLogin(environment, buildReturnQuery(window.location.search))
      if (result.kind === 'link') setLink(result.url)
    } catch (reasonError) {
      setError(reasonError instanceof Error ? reasonError.message : '请求失败')
    } finally {
      setBusy(false)
    }
  }

  const handleSendCode = async () => {
    if (!email.trim()) {
      setError('请先输入邮箱地址')
      return
    }
    setBusy(true)
    setError('')
    setCodeNotice('')
    try {
      const result = await sendEmailCode(email.trim())
      setCooldown(result.cooldownSeconds || 60)
      setCodeNotice('6 位验证码已发送至邮箱，10 分钟内有效')
    } catch (reasonError) {
      setError(reasonError instanceof Error ? reasonError.message : '发送验证码失败')
    } finally {
      setBusy(false)
    }
  }

  const handleEmailSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!email.trim() || !password) {
      setError('请输入邮箱与密码')
      return
    }
    if (authMode === 'register' && !code.trim()) {
      setError('请输入 6 位邮箱验证码')
      return
    }
    setBusy(true)
    setError('')
    try {
      const session = authMode === 'register'
        ? await registerWithEmail({
            email: email.trim(),
            code: code.trim(),
            password,
            nickname: nickname.trim() || undefined,
          })
        : await loginWithEmail({ email: email.trim(), password })
      if (session?.authenticated) {
        onEmailAuthenticated?.(session)
      } else {
        setError('登录响应无效，请稍后重试')
      }
    } catch (reasonError) {
      setError(reasonError instanceof Error ? reasonError.message : '登录失败')
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
      <section
        className="login-prompt"
        role="dialog"
        aria-modal="true"
        aria-labelledby="login-prompt-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="login-prompt-head">
          <h2 id="login-prompt-title">{MINI_TITLES[environment]}</h2>
          <button type="button" className="modal-close-btn" aria-label="关闭登录弹窗" onClick={onClose}>×</button>
        </div>
        <p className="login-prompt-reason">{reason}。</p>

        {!inMiniProgram && (
          <div className="underline-tabs login-channel-tabs" role="tablist" aria-label="登录方式">
            <button
              type="button"
              role="tab"
              aria-selected={channelTab === 'email'}
              onClick={() => { setChannelTab('email'); setError('') }}
            >
              邮箱账号
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={channelTab === 'wechat'}
              onClick={() => { setChannelTab('wechat'); setError('') }}
            >
              微信小程序
            </button>
          </div>
        )}

        {channelTab === 'email' && !inMiniProgram ? (
          <form className="email-auth-form" onSubmit={handleEmailSubmit}>
            <div className="segmented-switch" role="tablist" aria-label="邮箱登录或注册">
              <button
                type="button"
                role="tab"
                aria-selected={authMode === 'login'}
                onClick={() => { setAuthMode('login'); setError('') }}
              >
                密码登录
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={authMode === 'register'}
                onClick={() => { setAuthMode('register'); setError('') }}
              >
                邮箱注册
              </button>
            </div>

            <label className="field-group">
              <span>邮箱地址</span>
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="name@example.com"
                autoComplete="email"
                required
              />
            </label>

            {authMode === 'register' && (
              <>
                <div className="field-group">
                  <span>邮箱验证码</span>
                  <div className="code-row">
                    <input
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      value={code}
                      onChange={(event) => setCode(event.target.value)}
                      placeholder="6 位数字"
                      required
                    />
                    <button
                      type="button"
                      className="secondary-btn"
                      onClick={handleSendCode}
                      disabled={busy || cooldown > 0}
                    >
                      {cooldown > 0 ? `${cooldown}s` : '发送验证码'}
                    </button>
                  </div>
                </div>
                {codeNotice && <p className="field-hint">{codeNotice}</p>}
              </>
            )}

            <label className="field-group">
              <span>密码{authMode === 'register' ? '（至少 8 位）' : ''}</span>
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="请输入密码"
                autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
                required
              />
            </label>

            {authMode === 'register' && (
              <label className="field-group">
                <span>昵称（可选）</span>
                <input
                  type="text"
                  maxLength={32}
                  value={nickname}
                  onChange={(event) => setNickname(event.target.value)}
                  placeholder="默认取邮箱前缀"
                />
              </label>
            )}

            {error && <p className="login-prompt-error" role="alert">{error}</p>}

            <div className="login-prompt-actions">
              <button type="button" className="secondary" onClick={onClose}>取消</button>
              <button type="submit" disabled={busy}>
                {busy ? '提交中…' : authMode === 'register' ? '注册并登录' : '登录'}
              </button>
            </div>
          </form>
        ) : (
          <div className="wechat-auth-panel">
            <p className="login-prompt-detail">
              {inMiniProgram
                ? '将回到报名系统小程序完成登录，登录后自动返回当前页面。'
                : '使用报名系统小程序账号时，请通过微信小程序授权打开缠观；与网页邮箱账号各自独立保存数据。'}
            </p>
            {link && (
              <div className="login-prompt-link">
                <a href={link}>{link}</a>
                <button type="button" onClick={copy}>{copied ? '已复制' : '复制链接'}</button>
                <small>{environment === 'browser' ? '手机点击链接可打开微信；电脑请复制到微信中打开。' : '点击链接即可打开报名系统小程序。'}</small>
              </div>
            )}
            {error && <p className="login-prompt-error" role="alert">{error}</p>}
            <div className="login-prompt-actions">
              <button type="button" className="secondary" onClick={onClose}>暂不登录</button>
              {!link && (
                <button type="button" onClick={startMiniProgram} disabled={busy}>
                  {busy ? '请稍候…' : inMiniProgram ? '去登录' : '获取小程序链接'}
                </button>
              )}
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
