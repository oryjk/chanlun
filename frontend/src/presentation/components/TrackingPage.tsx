import { useMemo, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import { formatPrice, formatSignedPercent, getPriceDecimals } from '../../domain/market.ts'
import {
  formatSignedProfit,
  getGrowthStage,
  getTrackingDays,
  TRACKING_PRINCIPAL,
} from '../../domain/tracking.ts'
import type { Stock, TrackingSortBy, UserIdentity } from '../../domain/types.ts'
import type { UseTrackingsResult } from '../hooks/useTrackings.ts'
import type { AuthContextValue } from './AuthContext.tsx'
import BotanicalStageIcon from './BotanicalStageIcon.tsx'
import DragonLoader from './DragonLoader.tsx'
import SparklineSvg from './SparklineSvg.tsx'
import TrackingPickerModal from './TrackingPickerModal.tsx'

const SORT_OPTIONS: ReadonlyArray<{ id: TrackingSortBy; label: string }> = [
  { id: 'default', label: '默认' },
  { id: 'cumulative', label: '累计收益' },
  { id: 'day', label: '当日涨跌' },
  { id: 'days', label: '跟踪天数' },
]

export interface TrackingPageProps {
  user: UserIdentity | null
  onRequireLogin: AuthContextValue['requireLogin']
  trackings: UseTrackingsResult
  searchStocks: AppUseCases['searchStocks']
  onSelectStock: (stock: Stock) => void
}

export default function TrackingPage({
  user,
  onRequireLogin,
  trackings,
  searchStocks,
  onSelectStock,
}: TrackingPageProps) {
  const [pickerOpen, setPickerOpen] = useState(false)
  const [managing, setManaging] = useState(false)
  const [selectedBatch, setSelectedBatch] = useState<string[]>([])

  const selectedSet = useMemo(() => new Set(selectedBatch), [selectedBatch])

  const openPicker = () => {
    onRequireLogin(() => setPickerOpen(true), '登录后可将关注的标的加入模拟跟踪')
  }

  const toggleBatchItem = (secid: string) => {
    setSelectedBatch((current) => (
      current.includes(secid) ? current.filter((id) => id !== secid) : [...current, secid]
    ))
  }

  const handleBatchCancel = async () => {
    if (!selectedBatch.length) return
    const ok = await trackings.cancelTrackings(selectedBatch)
    if (ok) {
      setSelectedBatch([])
      setManaging(false)
    }
  }

  if (!user) {
    return (
      <section className="tracking-page" aria-label="模拟跟踪">
        <header className="section-head-hairline">
          <div className="section-head-row">
            <div>
              <h1>跟踪</h1>
              <small className="head-subtitle">¥10,000 模拟本金记账与成长阶段记录</small>
            </div>
          </div>
        </header>
        <div className="guest-hairline-empty">
          <BotanicalStageIcon stageKey="sprout" />
          <h2>登录后开启模拟跟踪</h2>
          <p>每只跟踪标的自动按 ¥{TRACKING_PRINCIPAL.toLocaleString('zh-CN')} 模拟本金起始记账，追踪当日与累计表现，不构成买卖建议。</p>
          <button
            type="button"
            className="primary-cta-btn"
            onClick={() => onRequireLogin(() => {}, '登录后可管理你的模拟跟踪列表')}
          >
            登录账号
          </button>
        </div>
      </section>
    )
  }

  const dayProfit = formatSignedProfit(trackings.summary.dayProfit)
  const totalProfit = formatSignedProfit(trackings.summary.cumulativeProfit)
  const totalReturnPct = formatSignedPercent(trackings.summary.cumulativeReturnPct)

  return (
    <section className="tracking-page" aria-label="模拟跟踪">
      <header className="section-head-hairline">
        <div className="section-head-row">
          <div>
            <h1>跟踪</h1>
            <small className="head-subtitle">
              {trackings.asOfDate ? `账期截至 ${trackings.asOfDate}` : '模拟本金记账'}
            </small>
          </div>
          <div className="head-actions">
            {trackings.items.length > 0 && (
              <button
                type="button"
                className="text-action-btn"
                onClick={() => {
                  setManaging((value) => !value)
                  setSelectedBatch([])
                }}
              >
                {managing ? '完成' : '管理'}
              </button>
            )}
            <button type="button" className="primary-btn-sm" onClick={openPicker}>
              + 跟踪
            </button>
          </div>
        </div>
      </header>

      <div className="tracking-summary-strip">
        <div className="summary-cell">
          <span>模拟总市值（{trackings.summary.count} 只）</span>
          <strong className="num-tabular">
            ¥{trackings.summary.totalValue.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </strong>
        </div>
        <div className="summary-cell">
          <span>当日盈亏</span>
          <strong className={`num-tabular pct-${dayProfit.tone}`}>{dayProfit.text}</strong>
        </div>
        <div className="summary-cell">
          <span>累计盈亏</span>
          <strong className={`num-tabular pct-${totalProfit.tone}`}>
            {totalProfit.text} ({totalReturnPct.text})
          </strong>
        </div>
      </div>

      {trackings.items.length > 0 && (
        <div className="tracking-sort-bar" role="toolbar" aria-label="跟踪排序">
          <div className="sort-pills">
            {SORT_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                className={trackings.sortBy === option.id ? 'is-active' : ''}
                onClick={() => trackings.setSortBy(option.id)}
              >
                {option.label}
              </button>
            ))}
          </div>
          {managing && selectedBatch.length > 0 && (
            <button
              type="button"
              className="danger-text-btn"
              disabled={trackings.mutating}
              onClick={() => void handleBatchCancel()}
            >
              取消跟踪 ({selectedBatch.length})
            </button>
          )}
        </div>
      )}

      {trackings.error && (
        <div className="inline-error-row" role="alert">
          <span>{trackings.error}</span>
          <button type="button" onClick={trackings.refresh}>重试</button>
        </div>
      )}

      {trackings.loading && trackings.items.length === 0 ? (
        <div className="list-loading-state">
          <DragonLoader title="正在核对模拟跟踪账本" label="同步最新行情估值…" />
        </div>
      ) : trackings.items.length === 0 ? (
        <div className="guest-hairline-empty">
          <BotanicalStageIcon stageKey="branch" />
          <h2>还没有跟踪中的标的</h2>
          <p>点击右上角「+ 跟踪」，或直接在行情列表点击书签，即可开始记录模拟盈亏与成长天数。</p>
          <button type="button" className="primary-cta-btn" onClick={openPicker}>
            + 添加首只标的
          </button>
        </div>
      ) : (
        <ul className="tracking-list">
          {trackings.items.map((item, index) => {
            const { stock } = item
            const decimals = getPriceDecimals(stock)
            const days = getTrackingDays(item.startDate, trackings.asOfDate)
            const stage = getGrowthStage(days)
            const cumPct = formatSignedPercent(item.cumulativeReturnPct)
            const dayPct = formatSignedPercent(item.dayReturnPct)
            const cumProfit = formatSignedProfit(item.cumulativeProfit)
            const pinned = trackings.pinnedSecids.includes(stock.secid)

            return (
              <li
                key={stock.secid}
                className={`tracking-row${pinned ? ' is-pinned' : ''}`}
                onClick={() => {
                  if (managing) {
                    toggleBatchItem(stock.secid)
                  } else {
                    onSelectStock(stock)
                  }
                }}
              >
                {managing && (
                  <input
                    type="checkbox"
                    className="batch-checkbox"
                    aria-label={`选择 ${stock.name}`}
                    checked={selectedSet.has(stock.secid)}
                    onChange={() => toggleBatchItem(stock.secid)}
                    onClick={(event) => event.stopPropagation()}
                  />
                )}

                <div className="tracking-row-main">
                  <div className="tracking-title-row">
                    <span className="tracking-name">
                      {pinned && <em className="pin-mark">置顶</em>}
                      <strong>{stock.name}</strong>
                    </span>
                    <span className={`growth-badge stage-${stage.key}`} title={stage.daysText}>
                      <BotanicalStageIcon stageKey={stage.key} />
                      <span>{stage.label}</span>
                      <small>{days === 0 ? '今日' : `${days}天`}</small>
                    </span>
                  </div>
                  <div className="tracking-sub-row">
                    <span>{stock.symbol} {stock.market}</span>
                    <span>起始 {formatPrice(item.startPrice, decimals)}，{item.startDate}</span>
                  </div>
                </div>

                {!managing && (
                  <div className="tracking-spark">
                    <SparklineSvg values={item.sparkline} width={56} height={22} label={`${stock.name} 走势`} />
                  </div>
                )}

                <div className="tracking-metrics-col">
                  <div className="metric-top">
                    <span className="latest-val num-tabular">{formatPrice(item.latestPrice, decimals)}</span>
                    <span className={`cum-pct num-tabular pct-${cumPct.tone}`}>{cumPct.text}</span>
                  </div>
                  <div className="metric-sub num-tabular">
                    <span className={`pct-${dayPct.tone}`}>今日 {dayPct.text}</span>
                    <span className={`pct-${cumProfit.tone}`}>{cumProfit.text}</span>
                  </div>
                </div>

                {managing && (
                  <div className="tracking-manage-actions" onClick={(event) => event.stopPropagation()}>
                    <button
                      type="button"
                      className={pinned ? 'is-active' : ''}
                      onClick={() => trackings.togglePin(stock.secid)}
                    >
                      {pinned ? '取消置顶' : '置顶'}
                    </button>
                    <button
                      type="button"
                      disabled={index === 0}
                      aria-label={`上移 ${stock.name}`}
                      onClick={() => trackings.moveItem(stock.secid, -1)}
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      disabled={index === trackings.items.length - 1}
                      aria-label={`下移 ${stock.name}`}
                      onClick={() => trackings.moveItem(stock.secid, 1)}
                    >
                      ↓
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {pickerOpen && (
        <TrackingPickerModal
          searchStocks={searchStocks}
          trackedSecids={trackings.trackedSecids}
          onStartTracking={(secid) => trackings.startTracking(secid)}
          onClose={() => setPickerOpen(false)}
        />
      )}
    </section>
  )
}
