import { createGetMarketSnapshot } from '../application/getMarketSnapshot.js'
import { createGetQuote } from '../application/getQuote.js'
import { createGetQuoteSeries } from '../application/getQuoteSeries.js'
import { createGetRankings } from '../application/getRankings.js'
import { createGetSession } from '../application/getSession.js'
import { createGetSignalConfluence } from '../application/getSignalConfluence.js'
import { createLoginWithWebviewCode } from '../application/loginWithWebviewCode.js'
import { createRecordQuoteSample } from '../application/recordQuoteSample.js'
import { createRequestLogin } from '../application/requestLogin.js'
import { createSearchStocks } from '../application/searchStocks.js'
import { createAuthGateway } from '../infrastructure/api/authGateway.js'
import { createMarketGateway } from '../infrastructure/api/marketGateway.js'
import { createMiniProgramBridge } from '../infrastructure/wechat/miniProgramBridge.js'

const marketGateway = createMarketGateway()
const authGateway = createAuthGateway()

export const useCases = {
  searchStocks: createSearchStocks(marketGateway),
  getMarketSnapshot: createGetMarketSnapshot(marketGateway),
  getQuote: createGetQuote(marketGateway),
  getQuoteSeries: createGetQuoteSeries(marketGateway),
  getRankings: createGetRankings(marketGateway),
  getSignalConfluence: createGetSignalConfluence(marketGateway),
  recordQuoteSample: createRecordQuoteSample(marketGateway),
  getSession: createGetSession(authGateway),
  loginWithWebviewCode: createLoginWithWebviewCode(authGateway),
  requestLogin: createRequestLogin(authGateway, createMiniProgramBridge()),
}
