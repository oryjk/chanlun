import { createGetMarketSnapshot } from '../application/getMarketSnapshot.js'
import { createGetQuote } from '../application/getQuote.js'
import { createGetQuoteSeries } from '../application/getQuoteSeries.js'
import { createGetRankings } from '../application/getRankings.js'
import { createGetSignalConfluence } from '../application/getSignalConfluence.js'
import { createRecordQuoteSample } from '../application/recordQuoteSample.js'
import { createSearchStocks } from '../application/searchStocks.js'
import { createMarketGateway } from '../infrastructure/api/marketGateway.js'

const marketGateway = createMarketGateway()

export const useCases = {
  searchStocks: createSearchStocks(marketGateway),
  getMarketSnapshot: createGetMarketSnapshot(marketGateway),
  getQuote: createGetQuote(marketGateway),
  getQuoteSeries: createGetQuoteSeries(marketGateway),
  getRankings: createGetRankings(marketGateway),
  getSignalConfluence: createGetSignalConfluence(marketGateway),
  recordQuoteSample: createRecordQuoteSample(marketGateway),
}
