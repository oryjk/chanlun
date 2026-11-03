import { useAuth } from './AuthContext.jsx'

export default function AccountChip() {
  const { user, openLoginPrompt } = useAuth()
  if (user) {
    return (
      <span className="account-chip signed-in" title={`已登录：${user.nickname}`}>
        <i aria-hidden="true">{user.nickname.slice(0, 1)}</i>
        <span>{user.nickname}</span>
      </span>
    )
  }
  return (
    <button type="button" className="account-chip" onClick={() => openLoginPrompt()}>
      <i aria-hidden="true">客</i>
      <span>游客 · 登录</span>
    </button>
  )
}
