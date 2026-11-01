import { createGetMarketSnapshot } from '../application/getMarketSnapshot.js'
import { createSearchStocks } from '../application/searchStocks.js'
import { createMarketGateway } from '../infrastructure/api/marketGateway.js'

const marketGateway = createMarketGateway()

export const useCases = {
  searchStocks: createSearchStocks(marketGateway),
  getMarketSnapshot: createGetMarketSnapshot(marketGateway),
}
