import { useEffect, type FormEvent } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import { isStarMarket } from '../../domain/market.ts'
import { useStockSearch } from '../hooks/useStockSearch.ts'

export interface TrackingPickerModalProps {
  searchStocks: AppUseCases['searchStocks']
  trackedSecids: Set<string>
  onStartTracking: (secid: string) => void | Promise<boolean>
  onClose: () => void
}

export default function TrackingPickerModal({
  searchStocks,
  trackedSecids,
  onStartTracking,
  onClose,
}: TrackingPickerModalProps) {
  const { query, items, searching, error, setQuery, searchNow } = useStockSearch(searchStocks, '')

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (query.trim()) searchNow()
  }

  return (
    <div className="login-prompt-backdrop" onClick={onClose}>
      <section
        className="login-prompt picker-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="tracking-picker-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="login-prompt-head">
          <h2 id="tracking-picker-title">添加模拟跟踪标的</h2>
          <button type="button" className="modal-close-btn" aria-label="关闭添加弹窗" onClick={onClose}>×</button>
        </div>
        <p className="login-prompt-reason">每只标按 ¥10,000 模拟本金起始记账，追踪当日与累计结构表现。</p>

        <form className="picker-search-form" onSubmit={handleSubmit} role="search">
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="输入股票 / ETF 代码、名称或拼音"
            aria-label="搜索待跟踪证券"
            autoFocus
          />
          <button type="submit" disabled={searching}>{searching ? '搜索中' : '搜索'}</button>
        </form>

        {error && <p className="login-prompt-error">{error}</p>}

        <ul className="picker-result-list">
          {items.map((stock) => {
            const added = trackedSecids.has(stock.secid)
            return (
              <li key={stock.secid} className="picker-result-row">
                <div className="picker-stock-info">
                  <strong>{stock.name}</strong>
                  <small>
                    {stock.symbol} {stock.market}
                    {isStarMarket(stock) ? ' 科创' : ''}
                  </small>
                </div>
                <button
                  type="button"
                  className={added ? 'secondary-btn is-tracked' : 'primary-btn-sm'}
                  disabled={added}
                  onClick={() => void onStartTracking(stock.secid)}
                >
                  {added ? '跟踪中' : '+ 跟踪'}
                </button>
              </li>
            )
          })}
          {!searching && !error && query.trim() && items.length === 0 && (
            <li className="picker-empty">未找到匹配证券，请尝试输入代码或简称</li>
          )}
          {!query.trim() && (
            <li className="picker-empty">支持沪深北 A 股及境内场内 ETF</li>
          )}
        </ul>
      </section>
    </div>
  )
}
