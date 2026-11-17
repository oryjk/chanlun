import { useEffect, useState } from 'react'
import type { AppUseCases } from '../../application/ports.ts'
import {
  formatPrice,
  formatSignedPercent,
  formatTurnover,
  getPriceDecimals,
  isStarMarket,
} from '../../domain/market.ts'
import type { AssetCategory, AssetSortField, MarketAssetItem, SortOrder, Stock } from '../../domain/types.ts'
import DragonLoader from './DragonLoader.tsx'
import SparklineSvg from './SparklineSvg.tsx'
import StockSearch from './StockSearch.tsx'

function useIsWideScreen(query = '(min-width: 768px)'): boolean {
  const [matches, setMatches] = useState<boolean>(() => (
    typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(query).matches : false
  ))
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined
    const media = window.matchMedia(query)
    setMatches(media.matches)
    const listener = (event: MediaQueryListEvent) => setMatches(event.matches)
    media.addEventListener?.('change', listener)
    return () => media.removeEventListener?.('change', listener)
  }, [query])
  return matches
}

function SortCaret({ active, order }: { active: boolean; order: SortOrder }) {
  return (
    <svg className={`sort-caret${active ? ' is-active' : ''}`} viewBox="0 0 12 14" aria-hidden="true">
      <path
        d="M6 2 3 5.5h6L6 2z"
        className={active && order === 'asc' ? 'caret-on' : 'caret-off'}
      />
      <path
        d="M6 12 3 8.5h6L6 12z"
        className={active && order === 'desc' ? 'caret-on' : 'caret-off'}
      />
    </svg>
  )
}

function BookmarkIcon({ tracked }: { tracked: boolean }) {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true">
      <path
        d="M5 3.5h10a.5.5 0 0 1 .5.5v12.8l-5.5-3.3-5.5 3.3V4a.5.5 0 0 1 .5-.5z"
        fill={tracked ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export interface MarketListPageProps {
  selectedStock: Stock
  category: AssetCategory
  onCategoryChange: (category: AssetCategory) => void
  sort: AssetSortField
  order: SortOrder
  onToggleSort: (field: AssetSortField) => void
  asOfDate: string | null
  items: MarketAssetItem[]
  loading: boolean
  error: string
  onRefresh: () => void
  searchStocks: AppUseCases['searchStocks']
  onSelectStock: (stock: Stock) => void
  trackedSecids: Set<string>
  onToggleTrack: (stock: Stock) => void
}

export default function MarketListPage({
  selectedStock,
  category,
  onCategoryChange,
  sort,
  order,
  onToggleSort,
  asOfDate,
  items,
  loading,
  error,
  onRefresh,
  searchStocks,
  onSelectStock,
  trackedSecids,
  onToggleTrack,
}: MarketListPageProps) {
  const isWide = useIsWideScreen()
  const countLabel = `共 ${items.length} 只${category === 'etf' ? 'ETF' : '股票'}`
  const isZeroPriceFallback = (item: MarketAssetItem) => item.latest <= 0

  const renderSortHeader = (field: AssetSortField, label: string) => {
    const active = sort === field
    const ariaSort = active ? (order === 'asc' ? 'ascending' : 'descending') : undefined
    return (
      <th scope="col" className={`col-num col-${field}`} aria-sort={ariaSort}>
        <button type="button" onClick={() => onToggleSort(field)}>
          <SortCaret active={active} order={order} />
          <span>{label}</span>
        </button>
      </th>
    )
  }

  return (
    <section className="market-list-page" aria-label="行情列表">
      <header className="section-head-hairline">
        <div className="section-head-row">
          <div>
            <h1>行情</h1>
            <small className="head-subtitle">
              {asOfDate ? `行情截至 ${asOfDate}` : '沪深北及 ETF 实时活跃行情'}
            </small>
          </div>
        </div>
        <div className="market-search-bar">
          <StockSearch selected={selectedStock} onSelect={onSelectStock} searchStocks={searchStocks} initialQuery="" />
        </div>
      </header>

      <div className="underline-tabs category-tabs" role="tablist" aria-label="资产类别">
        <button
          type="button"
          role="tab"
          aria-selected={category === 'stock'}
          onClick={() => onCategoryChange('stock')}
        >
          股票
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={category === 'etf'}
          onClick={() => onCategoryChange('etf')}
        >
          ETF
        </button>
      </div>

      {error && (
        <div className="inline-error-row" role="alert">
          <span>{error}</span>
          <button type="button" onClick={onRefresh}>重试</button>
        </div>
      )}

      <div className="market-table-wrap" role="region" aria-label="行情数据表">
        <table className="market-asset-table" data-category={category}>
          <colgroup>
            <col className="col-asset" />
            {isWide && <col className="col-trend" />}
            <col className="col-amount" />
            <col className="col-change_percent" />
            <col className="col-latest" />
          </colgroup>
          <thead>
            <tr>
              <th scope="col" className="col-asset">{countLabel}</th>
              {isWide && <th scope="col" className="col-trend">近20日</th>}
              {renderSortHeader('amount', '成交额')}
              {renderSortHeader('change_percent', '涨跌幅')}
              {renderSortHeader('latest', '最新价')}
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const { stock } = item
              const decimals = getPriceDecimals(stock)
              const pct = formatSignedPercent(item.changePercent)
              const tracked = trackedSecids.has(stock.secid)
              const priceText = isZeroPriceFallback(item) ? '—' : formatPrice(item.latest, decimals)

              return (
                <tr
                  key={stock.secid}
                  className="market-asset-row"
                  onClick={() => onSelectStock(stock)}
                >
                  <td className="col-asset">
                    <div className="asset-cell">
                      <div className="asset-name-line">
                        <button
                          type="button"
                          className="asset-name-btn"
                          onClick={(event) => {
                            event.stopPropagation()
                            onSelectStock(stock)
                          }}
                        >
                          {stock.name}
                        </button>
                        <button
                          type="button"
                          className={`row-bookmark-btn${tracked ? ' is-tracked' : ''}`}
                          aria-label={tracked ? `取消跟踪 ${stock.name}` : `跟踪 ${stock.name}`}
                          aria-pressed={tracked}
                          onClick={(event) => {
                            event.stopPropagation()
                            onToggleTrack(stock)
                          }}
                        >
                          <BookmarkIcon tracked={tracked} />
                        </button>
                      </div>
                      <div className="asset-meta-line">
                        <span>{stock.symbol}</span>
                        <span>{stock.market}</span>
                        {isStarMarket(stock) && <span className="meta-note">科创</span>}
                        {item.signalLabel && <span className="meta-signal">{item.signalLabel}</span>}
                      </div>
                    </div>
                  </td>
                  {isWide && (
                    <td className="col-trend">
                      <SparklineSvg values={item.sparkline} label={`${stock.name} 近20日走势`} />
                    </td>
                  )}
                  <td className="col-num col-amount" data-col="amount">
                    <span className="num-primary">{formatTurnover(item.amount)}</span>
                  </td>
                  <td className="col-num col-change_percent" data-col="change_percent">
                    <span className={`num-primary pct-${pct.tone}`}>{pct.text}</span>
                  </td>
                  <td className="col-num col-latest" data-col="latest">
                    <span className={`num-price pct-${pct.tone}`}>{priceText}</span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>

        {loading && items.length === 0 && (
          <div className="list-loading-state">
            <DragonLoader title="正在读取实时活跃清单" label="加载沪深北及 ETF 行情…" />
          </div>
        )}

        {!loading && !error && items.length === 0 && (
          <div className="empty-state">暂无符合条件的资产行情</div>
        )}
      </div>
    </section>
  )
}
