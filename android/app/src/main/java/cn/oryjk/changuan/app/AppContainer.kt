package cn.oryjk.changuan.app

import android.content.Context
import cn.oryjk.changuan.BuildConfig
import cn.oryjk.changuan.application.AppUpdateUseCases
import cn.oryjk.changuan.application.AuthUseCases
import cn.oryjk.changuan.application.MarketUseCases
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.SessionStore
import cn.oryjk.changuan.infrastructure.ApiClient
import cn.oryjk.changuan.infrastructure.AppUpdateGateway
import cn.oryjk.changuan.infrastructure.AuthGateway
import cn.oryjk.changuan.infrastructure.MarketGateway
import cn.oryjk.changuan.infrastructure.SharedPreferencesRouteStore
import cn.oryjk.changuan.infrastructure.SharedPreferencesSessionStore
import cn.oryjk.changuan.presentation.AppViewModel

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val sessionStore: SessionStore = SharedPreferencesSessionStore(appContext)
    val routeStore: RouteStore = SharedPreferencesRouteStore(appContext)
    private val apiClient = ApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        store = sessionStore,
    )
    private val marketGateway = MarketGateway(apiClient)
    private val authGateway = AuthGateway(apiClient)
    private val updateGateway = AppUpdateGateway(context = appContext)

    val marketUseCases = MarketUseCases(
        reader = marketGateway,
        writer = marketGateway,
    )
    val authUseCases = AuthUseCases(
        reader = authGateway,
        commands = authGateway,
        store = sessionStore,
    )
    val updateUseCases = AppUpdateUseCases(
        reader = updateGateway,
        downloader = updateGateway,
        installer = updateGateway,
    )

    val viewModelFactory = AppViewModel.Factory(
        market = marketUseCases,
        auth = authUseCases,
        routes = routeStore,
        updates = updateUseCases,
    )
}
