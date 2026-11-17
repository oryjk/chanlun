import { useAuth } from './AuthContext.tsx'
import ProfileDefaultAvatar from './ProfileDefaultAvatar.tsx'

export interface AccountChipProps {
  onOpenMenu?: () => void
}

export default function AccountChip({ onOpenMenu }: AccountChipProps) {
  const { user, openLoginPrompt } = useAuth()
  if (user) {
    return (
      <button
        type="button"
        className="account-chip signed-in"
        title={`已登录：${user.nickname}`}
        onClick={onOpenMenu}
      >
        <ProfileDefaultAvatar size={20} alt={user.nickname} />
        <span>{user.nickname}</span>
      </button>
    )
  }
  return (
    <button type="button" className="account-chip" onClick={() => openLoginPrompt()}>
      <ProfileDefaultAvatar size={20} alt="游客" />
      <span>登录</span>
    </button>
  )
}
