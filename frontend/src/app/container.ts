import { createCancelTrackings } from '../application/cancelTrackings.ts'
import {
  createLoginWithEmail,
  createLogoutSession,
  createRegisterWithEmail,
  createSendEmailCode,
} from '../application/emailAuth.ts'
import { createGetMarketAssets } from '../application/getMarketAssets.ts'
import { createGetMarketSnapshot } from '../application/getMarketSnapshot.ts'
import { createGetOvernightSnapshot } from '../application/getOvernightSnapshot.ts'
import { createGetQuote } from '../application/getQuote.ts'
import { createGetQuoteSeries } from '../application/getQuoteSeries.ts'
import { createGetRankings } from '../application/getRankings.ts'
import { createGetSession } from '../application/getSession.ts'
import { createGetSignalConfluence } from '../application/getSignalConfluence.ts'
import { createGetTrackings } from '../application/getTrackings.ts'
import { createLoginWithWebviewCode } from '../application/loginWithWebviewCode.ts'
import type { AppUseCases } from '../application/ports.ts'
import { createThemePreferences, createTrackingPreferences } from '../application/preferences.ts'
import { createRecordQuoteSample } from '../application/recordQuoteSample.ts'
import { createRequestLogin } from '../application/requestLogin.ts'
import { createSearchStocks } from '../application/searchStocks.ts'
import { createStartTracking } from '../application/startTracking.ts'
import { createAuthGateway } from '../infrastructure/api/authGateway.ts'
import { createMarketGateway } from '../infrastructure/api/marketGateway.ts'
import { createMethodsGateway } from '../infrastructure/api/methodsGateway.ts'
import { createTrackingGateway } from '../infrastructure/api/trackingGateway.ts'
import { createLocalPreferences } from '../infrastructure/storage/localPreferences.ts'
import { createMiniProgramBridge } from '../infrastructure/wechat/miniProgramBridge.ts'

const localPreferences = createLocalPreferences()
const marketGateway = createMarketGateway()
const methodsGateway = createMethodsGateway()
const authGateway = createAuthGateway(localPreferences)
const trackingGateway = createTrackingGateway(localPreferences)
const miniProgramBridge = createMiniProgramBridge()

export const useCases: AppUseCases = {
  searchStocks: createSearchStocks(marketGateway),
  getMarketAssets: createGetMarketAssets(marketGateway),
  getMarketSnapshot: createGetMarketSnapshot(marketGateway),
  getSignalConfluence: createGetSignalConfluence(marketGateway),
  getQuote: createGetQuote(marketGateway),
  getQuoteSeries: createGetQuoteSeries(marketGateway),
  getRankings: createGetRankings(marketGateway),
  getOvernightSnapshot: createGetOvernightSnapshot(methodsGateway),
  recordQuoteSample: createRecordQuoteSample(marketGateway),
  getTrackings: createGetTrackings(trackingGateway),
  startTracking: createStartTracking(trackingGateway),
  cancelTrackings: createCancelTrackings(trackingGateway),
  getSession: createGetSession(authGateway),
  loginWithWebviewCode: createLoginWithWebviewCode(authGateway),
  requestLogin: createRequestLogin(authGateway, miniProgramBridge),
  sendEmailCode: createSendEmailCode(authGateway),
  registerWithEmail: createRegisterWithEmail(authGateway),
  loginWithEmail: createLoginWithEmail(authGateway),
  logoutSession: createLogoutSession(authGateway),
  themePreferences: createThemePreferences(localPreferences),
  trackingPreferences: createTrackingPreferences(localPreferences),
}
