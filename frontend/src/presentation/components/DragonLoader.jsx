export default function DragonLoader({
  title = '游龙巡盘中',
  label = '正在读取结构数据，请稍候…',
  compact = false,
}) {
  return (
    <div className={`dragon-loader ${compact ? 'compact' : ''}`} role="status" aria-live="polite">
      <div className="dragon-stage" aria-hidden="true">
        <svg viewBox="0 0 280 96" className="dragon-svg">
          <defs>
            <linearGradient id="dragon-body-grad" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#2e4a6e" stopOpacity="0.15" />
              <stop offset="35%" stopColor="#2e4a6e" stopOpacity="0.85" />
              <stop offset="75%" stopColor="#2e4a6e" stopOpacity="1" />
              <stop offset="100%" stopColor="#b48a42" stopOpacity="1" />
            </linearGradient>
            <linearGradient id="dragon-spine-grad" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#b48a42" stopOpacity="0.1" />
              <stop offset="50%" stopColor="#b48a42" stopOpacity="0.95" />
              <stop offset="100%" stopColor="#d94f5e" stopOpacity="0.9" />
            </linearGradient>
            <radialGradient id="dragon-pearl-grad" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#fff6d6" />
              <stop offset="45%" stopColor="#b48a42" />
              <stop offset="100%" stopColor="#b48a42" stopOpacity="0" />
            </radialGradient>
          </defs>

          {/* 背景流云与缠论笔折线 */}
          <g className="dragon-clouds">
            <path d="M16 74 H54 L70 58 L92 72 H132" className="dragon-kline-trail" />
            <path d="M148 24 H182 L198 14 L218 26 H246" className="dragon-kline-trail delay" />
            <path d="M36 28 Q48 22 60 28 T84 28" className="dragon-cloud-wisp" />
            <path d="M150 76 Q164 70 178 76 T206 76" className="dragon-cloud-wisp delay" />
          </g>

          {/* 游龙整体起伏组 */}
          <g className="dragon-wave-group">
            {/* 龙尾焰羽 */}
            <g className="dragon-tail">
              <path
                d="M28 56 C18 48, 10 52, 6 62 C14 60, 20 62, 26 58 Z"
                fill="#2e4a6e"
                opacity="0.75"
              />
              <path
                d="M27 54 C16 40, 8 42, 4 49 C12 49, 19 52, 26 56 Z"
                fill="#b48a42"
                opacity="0.85"
              />
              <path
                d="M28 58 C19 64, 13 69, 9 74 C17 71, 23 66, 29 60 Z"
                fill="#2e4a6e"
                opacity="0.55"
              />
            </g>

            {/* 龙爪（前后双爪） */}
            <g className="dragon-claws" stroke="#2e4a6e" strokeWidth="1.6" strokeLinecap="round" fill="none">
              <path d="M84 41 L78 52 M78 52 L73 55 M78 52 L78 57 M78 52 L83 55" />
              <path d="M168 57 L164 69 M164 69 L159 72 M164 69 L164 74 M164 69 L169 72" />
            </g>

            {/* 龙身主干与流动鳞脊 */}
            <path
              d="M26 56 C52 22, 86 22, 114 50 C142 78, 174 74, 202 38"
              className="dragon-body-shadow"
            />
            <path
              d="M26 56 C52 22, 86 22, 114 50 C142 78, 174 74, 202 38"
              className="dragon-body-main"
            />
            <path
              d="M26 56 C52 22, 86 22, 114 50 C142 78, 174 74, 202 38"
              className="dragon-body-scales"
            />

            {/* 背部龙鳍 */}
            <g className="dragon-fins" fill="#b48a42">
              <polygon points="50,34 54,24 58,32" />
              <polygon points="68,28 73,18 77,28" />
              <polygon points="88,32 94,23 96,35" />
              <polygon points="112,46 118,37 120,49" />
              <polygon points="136,61 142,52 144,64" />
              <polygon points="158,64 165,55 166,65" />
              <polygon points="180,54 186,43 188,52" />
            </g>

            {/* 昂首龙头 */}
            <g className="dragon-head" transform="translate(196, 20)">
              {/* 龙角 */}
              <path
                d="M6 12 C0 3, -9 0, -16 2 C-9 5, -3 9, 2 15 Z"
                fill="#b48a42"
              />
              <path
                d="M10 10 C6 0, -2 -5, -10 -4 C-3 -1, 2 5, 6 12 Z"
                fill="#b48a42"
                opacity="0.8"
              />
              {/* 龙鬃 */}
              <path
                d="M2 16 C-6 14, -12 17, -16 22 C-9 21, -3 20, 3 21 Z"
                fill="#2e4a6e"
                opacity="0.85"
              />
              {/* 头颅与上下颚 */}
              <path
                d="M0 16 L10 9 L24 10 L31 6 L29 14 L20 17 L28 21 L24 25 L12 23 L3 24 Z"
                fill="#2e4a6e"
              />
              {/* 龙眼 */}
              <circle cx="15.5" cy="13.5" r="1.8" fill="#ffffff" />
              <circle cx="16" cy="13.5" r="1" fill="#d94f5e" />
              {/* 飘动龙须 */}
              <path
                d="M25 12 C34 4, 42 6, 48 2"
                className="dragon-whisker"
              />
              <path
                d="M24 22 C33 28, 40 27, 46 31"
                className="dragon-whisker lower"
              />
            </g>
          </g>

          {/* 龙戏金珠（脉冲能量珠） */}
          <g className="dragon-pearl" transform="translate(250, 34)">
            <circle cx="0" cy="0" r="14" fill="url(#dragon-pearl-grad)" className="dragon-pearl-aura" />
            <circle cx="0" cy="0" r="9" className="dragon-pearl-ring" />
            <circle cx="0" cy="0" r="5" fill="#b48a42" />
            <circle cx="-1.5" cy="-1.5" r="1.6" fill="#fff8e1" />
          </g>
        </svg>
      </div>
      <div className="dragon-copy">
        {title && <strong>{title}</strong>}
        <span>{label}</span>
      </div>
    </div>
  )
}
