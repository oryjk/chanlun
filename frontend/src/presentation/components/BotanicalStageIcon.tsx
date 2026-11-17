import type { BotanicalStageKey } from '../../domain/tracking.ts'

export interface BotanicalStageIconProps {
  stageKey: BotanicalStageKey
}

// 6 档植物成长阶段图标：萌芽 -> 抽枝 -> 繁茂 -> 扎根 -> 成荫 -> 年轮
export default function BotanicalStageIcon({ stageKey }: BotanicalStageIconProps) {
  return (
    <svg className={`stage-icon stage-${stageKey}`} viewBox="0 0 20 20" aria-hidden="true">
      {stageKey === 'sprout' && (
        <>
          <path d="M10 17V9" />
          <path d="M10 11c0-4-3.5-6-6-5 0 3.2 2.4 5 6 5z" />
          <path d="M10 9c0-3.5 3-5.2 5.5-4.4 0 2.8-2.2 4.4-5.5 4.4z" />
        </>
      )}
      {stageKey === 'branch' && (
        <>
          <path d="M10 17V5" />
          <path d="M10 12 6 8.5" />
          <path d="M10 9.5 14 6.5" />
          <circle cx="5.5" cy="8" r="1.6" />
          <circle cx="14.5" cy="6" r="1.6" />
        </>
      )}
      {stageKey === 'lush' && (
        <>
          <path d="M10 17v-5" />
          <path d="M10 13c-4 0-6-2.8-5.5-6 3.2.2 5 2.4 5.5 6z" />
          <path d="M10 13c4 0 6-2.8 5.5-6-3.2.2-5 2.4-5.5 6z" />
          <path d="M10 11c-2.5-2.5-2-5.8 0-7.5 2 1.7 2.5 5 0 7.5z" />
        </>
      )}
      {stageKey === 'rooted' && (
        <>
          <path d="M10 13V4" />
          <path d="M10 7 6.5 4.5M10 9l3.5-2.5" />
          <path d="M4 13h12M7 13l-2 4M10 13v4.5M13 13l2 4" />
        </>
      )}
      {stageKey === 'canopy' && (
        <>
          <path d="M10 17v-5" />
          <path d="M5 12.5a5 5 0 0 1 10 0H5z" />
          <path d="M7.5 12.5a2.5 2.5 0 0 1 5 0" />
        </>
      )}
      {stageKey === 'rings' && (
        <>
          <circle cx="10" cy="10" r="7.5" />
          <circle cx="10" cy="10" r="4.5" />
          <circle cx="10" cy="10" r="1.6" />
        </>
      )}
    </svg>
  )
}
