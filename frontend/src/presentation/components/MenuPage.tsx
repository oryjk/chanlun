import { useState } from 'react'
import type { AuthChannel, ClientEnvironment, ThemeMode, UserIdentity } from '../../domain/types.ts'
import { GLOSSARY } from '../glossary.ts'
import type { AuthContextValue } from './AuthContext.tsx'
import ProfileDefaultAvatar from './ProfileDefaultAvatar.tsx'

const THEME_OPTIONS: ReadonlyArray<{ id: ThemeMode; label: string }> = [
  { id: 'system', label: '跟随系统' },
  { id: 'light', label: '浅色' },
  { id: 'dark', label: '深色' },
]

export interface MenuPageProps {
  user: UserIdentity | null
  authChannel: AuthChannel | null
  environment: ClientEnvironment
  themeMode: ThemeMode
  onThemeModeChange: (mode: ThemeMode) => void
  trackingCount: number
  onOpenTracking: () => void
  onOpenLogin: AuthContextValue['openLoginPrompt']
  onLogout: () => Promise<void>
}

export default function MenuPage({
  user,
  authChannel,
  environment,
  themeMode,
  onThemeModeChange,
  trackingCount,
  onOpenTracking,
  onOpenLogin,
  onLogout,
}: MenuPageProps) {
  const [glossaryOpen, setGlossaryOpen] = useState(false)
  const glossaryEntries = Object.values(GLOSSARY)

  const accountSubtitle = user
    ? (authChannel === 'bearer' ? '邮箱账号，模拟跟踪与 Android 互通' : '微信小程序账号')
    : (environment === 'miniprogram' ? '点击同步小程序登录状态' : '支持邮箱注册登录或微信打开')

  return (
    <section className="menu-page" aria-label="菜单">
      <header className="section-head-hairline">
        <div className="section-head-row">
          <div>
            <h1>菜单</h1>
            <small className="head-subtitle">账号、显示设置与缠论规则说明</small>
          </div>
        </div>
      </header>

      <div className="menu-profile-row">
        <div className="menu-profile-left" onClick={!user ? () => onOpenLogin('登录后可使用模拟跟踪等个人功能', 'email') : undefined}>
          <ProfileDefaultAvatar size={48} alt={user ? user.nickname : '未登录头像'} />
          <div className="menu-profile-text">
            <strong>{user ? user.nickname : '点击登录账号'}</strong>
            <small>{accountSubtitle}</small>
          </div>
        </div>
        {user ? (
          authChannel === 'bearer' ? (
            <button type="button" className="secondary-btn" onClick={() => void onLogout()}>
              退出登录
            </button>
          ) : (
            <span className="menu-channel-tag">微信小程序</span>
          )
        ) : (
          <button
            type="button"
            className="primary-btn-sm"
            onClick={() => onOpenLogin('登录后可使用模拟跟踪等个人功能', 'email')}
          >
            登录
          </button>
        )}
      </div>

      <div className="menu-group">
        <div className="menu-group-label">外观模式</div>
        <div className="menu-row">
          <div className="menu-row-main">
            <strong>界面主题</strong>
            <small>深色模式采用纯黑 OLED 配色</small>
          </div>
          <div className="segmented-switch" role="group" aria-label="外观模式切换">
            {THEME_OPTIONS.map((opt) => (
              <button
                key={opt.id}
                type="button"
                aria-pressed={themeMode === opt.id}
                onClick={() => onThemeModeChange(opt.id)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="menu-group">
        <div className="menu-group-label">个人功能与帮助</div>
        <button type="button" className="menu-row menu-row-action" onClick={onOpenTracking}>
          <div className="menu-row-main">
            <strong>我的模拟跟踪</strong>
            <small>每只标的按 ¥10,000 模拟本金记录</small>
          </div>
          <span className="menu-row-trailing">
            {user ? `已跟踪 ${trackingCount} 只` : '去登录'} ›
          </span>
        </button>

        {!user && (
          <button
            type="button"
            className="menu-row menu-row-action"
            onClick={() => onOpenLogin('使用邮箱验证码注册或密码登录', 'email')}
          >
            <div className="menu-row-main">
              <strong>邮箱注册 / 密码登录</strong>
              <small>验证码 10 分钟有效，与客户端账号互通</small>
            </div>
            <span className="menu-row-trailing">去登录 ›</span>
          </button>
        )}

        <button type="button" className="menu-row menu-row-action" onClick={() => setGlossaryOpen((open) => !open)}>
          <div className="menu-row-main">
            <strong>缠论术语与信号口径</strong>
            <small>笔、线段、中枢、背驰、三类买卖点与失效参考</small>
          </div>
          <span className="menu-row-trailing">{glossaryOpen ? '收起' : '展开'}</span>
        </button>

        {glossaryOpen && (
          <div className="menu-glossary-list">
            {glossaryEntries.map((item) => (
              <div key={item.term} className="menu-glossary-item">
                <strong>{item.term}</strong>
                <p>{item.meaning}</p>
                <small>{item.usage}</small>
              </div>
            ))}
          </div>
        )}
      </div>

      <footer className="menu-footer-note">
        <p>缠观 H5：基于缠论笔、线段、笔中枢与 MACD 自动识别结构，历史统计与信号不构成买卖建议。</p>
      </footer>
    </section>
  )
}
