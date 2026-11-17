export interface ProfileDefaultAvatarProps {
  size?: number
  alt?: string
}

export default function ProfileDefaultAvatar({ size = 48, alt = '默认头像' }: ProfileDefaultAvatarProps) {
  return (
    <span className="profile-default-avatar" style={{ width: size, height: size }} aria-label={alt}>
      <svg viewBox="0 0 48 48" width={size} height={size} aria-hidden="true">
        <circle cx="24" cy="24" r="23" className="avatar-bg" />
        <circle cx="24" cy="18.5" r="7.5" className="avatar-fg" />
        <path
          d="M11 39.5c2.4-6.2 7.3-9.5 13-9.5s10.6 3.3 13 9.5"
          className="avatar-fg"
          strokeLinecap="round"
        />
      </svg>
    </span>
  )
}
