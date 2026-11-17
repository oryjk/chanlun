import type { PreferencesStorage, TrackingGateway } from '../../application/ports.ts'
import type {
  AuthChannel,
  Stock,
  TrackedAssetItem,
  TrackedAssetRecord,
  TrackingOverview,
} from '../../domain/types.ts'
import { getJson, postJson } from './httpClient.ts'

interface StockDto {
  secid: string
  symbol: string
  name: string
  market: string
}

interface TrackedAssetDto {
  stock: StockDto
  start_date: string
  start_price: number
  latest_price: number
  day_return_pct: number
  day_profit: number
  cumulative_return_pct: number
  cumulative_profit: number
  current_value: number
  sparkline?: number[]
}

interface TrackingOverviewDto {
  as_of_date?: string
  items?: TrackedAssetDto[]
}

interface StartTrackingDto {
  ok?: boolean
  item?: {
    secid: string
    symbol: string
    name: string
    market: string
    start_date: string
    start_price: number
  } | null
}

interface CancelTrackingDto {
  ok?: boolean
  removed?: number
}

function toStock(dto: StockDto): Stock {
  return { secid: dto.secid, symbol: dto.symbol, name: dto.name, market: dto.market }
}

function toTrackedAsset(dto: TrackedAssetDto): TrackedAssetItem {
  return {
    stock: toStock(dto.stock),
    startDate: dto.start_date,
    startPrice: Number(dto.start_price),
    latestPrice: Number(dto.latest_price),
    dayReturnPct: Number(dto.day_return_pct),
    dayProfit: Number(dto.day_profit),
    cumulativeReturnPct: Number(dto.cumulative_return_pct),
    cumulativeProfit: Number(dto.cumulative_profit),
    currentValue: Number(dto.current_value),
    sparkline: Array.isArray(dto.sparkline) ? dto.sparkline.map(Number) : [],
  }
}

export function createTrackingGateway(tokenStore?: Pick<PreferencesStorage, 'readEmailToken'>): TrackingGateway {
  const resolveEndpoint = (channel: AuthChannel, suffix = '') => {
    if (channel === 'bearer') {
      const token = tokenStore?.readEmailToken?.() || ''
      return {
        path: `/api/app-tracking${suffix}`,
        options: token ? { headers: { Authorization: `Bearer ${token}` } } : {},
      }
    }
    return {
      path: `/api/tracking${suffix}`,
      options: {},
    }
  }

  return {
    async getTrackings(channel: AuthChannel, signal?: AbortSignal): Promise<TrackingOverview> {
      const { path, options } = resolveEndpoint(channel, '')
      const dto = await getJson<TrackingOverviewDto>(path, signal, options)
      return {
        asOfDate: dto.as_of_date || '',
        items: (dto.items || []).map(toTrackedAsset),
      }
    },
    async startTracking(
      secid: string,
      channel: AuthChannel,
      signal?: AbortSignal,
    ): Promise<{ ok: boolean; item: TrackedAssetRecord | null }> {
      const { path, options } = resolveEndpoint(channel, '/start')
      const dto = await postJson<StartTrackingDto>(path, { secid }, signal, options)
      return {
        ok: Boolean(dto?.ok),
        item: dto?.item
          ? {
              secid: dto.item.secid,
              symbol: dto.item.symbol,
              name: dto.item.name,
              market: dto.item.market,
              startDate: dto.item.start_date,
              startPrice: Number(dto.item.start_price),
            }
          : null,
      }
    },
    async cancelTrackings(
      secids: string[],
      channel: AuthChannel,
      signal?: AbortSignal,
    ): Promise<{ ok: boolean; removed: number }> {
      const { path, options } = resolveEndpoint(channel, '/cancel')
      const dto = await postJson<CancelTrackingDto>(path, { secids }, signal, options)
      return {
        ok: Boolean(dto?.ok),
        removed: Number(dto?.removed || 0),
      }
    },
  }
}
