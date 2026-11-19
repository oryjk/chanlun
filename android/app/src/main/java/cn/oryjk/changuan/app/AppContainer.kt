package cn.oryjk.changuan.app

import android.content.Context
import cn.oryjk.changuan.application.AppUpdateUseCases
import cn.oryjk.changuan.application.AuthUseCases
import cn.oryjk.changuan.application.EnvironmentServices
import cn.oryjk.changuan.application.EnvironmentServicesFactory
import cn.oryjk.changuan.application.EnvironmentStore
import cn.oryjk.changuan.application.MarketUseCases
import cn.oryjk.changuan.application.OvernightQuery
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.ThemePreferenceStore
import cn.oryjk.changuan.application.TrackingUseCases
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.infrastructure.ApiClient
import cn.oryjk.changuan.infrastructure.AuthGateway
import cn.oryjk.changuan.infrastructure.HttpTransport
import cn.oryjk.changuan.infrastructure.MarketGateway
import cn.oryjk.changuan.infrastructure.OkHttpTransport
import cn.oryjk.changuan.infrastructure.OvernightGateway
import cn.oryjk.changuan.infrastructure.SharedPreferencesEnvironmentStore
import cn.oryjk.changuan.infrastructure.SharedPreferencesRouteStore
import cn.oryjk.changuan.infrastructure.SharedPreferencesSessionStore
import cn.oryjk.changuan.infrastructure.SharedPreferencesThemeStore
import cn.oryjk.changuan.infrastructure.SharedPreferencesTrackingPreferenceStore
import cn.oryjk.changuan.infrastructure.TrackingGateway
import cn.oryjk.changuan.infrastructure.update.AndroidUpdateGateway
import cn.oryjk.changuan.presentation.AppViewModel
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val routeStore: RouteStore = SharedPreferencesRouteStore(appContext)
    val environmentStore: EnvironmentStore = SharedPreferencesEnvironmentStore(appContext)
    val themeStore: ThemePreferenceStore = SharedPreferencesThemeStore(appContext)

    private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val transport: HttpTransport = OkHttpTransport(sharedHttpClient)
    private val updateGateway = AndroidUpdateGateway(
        context = appContext,
        checkerClient = sharedHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build(),
        downloaderClient = sharedHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    )

    private val servicesByEnv = ConcurrentHashMap<BackendEnvironment, EnvironmentServices>()

    val servicesFactory = EnvironmentServicesFactory { environment ->
        servicesByEnv.getOrPut(environment) {
            val sessionStore = SharedPreferencesSessionStore(
                context = appContext,
                prefsName = SharedPreferencesSessionStore.prefsNameFor(environment),
            )
            val apiClient = ApiClient(
                baseUrl = environment.baseUrl,
                store = sessionStore,
                transport = transport,
            )
            val marketGateway = MarketGateway(apiClient)
            val authGateway = AuthGateway(apiClient)
            val trackingGateway = TrackingGateway(apiClient)
            val trackingPrefs = SharedPreferencesTrackingPreferenceStore(
                context = appContext,
                prefsName = SharedPreferencesTrackingPreferenceStore.prefsNameFor(environment),
            )
            EnvironmentServices(
                market = MarketUseCases(reader = marketGateway, writer = marketGateway),
                auth = AuthUseCases(reader = authGateway, commands = authGateway, store = sessionStore),
                overnight = OvernightQuery(OvernightGateway(apiClient)),
                tracking = TrackingUseCases(
                    reader = trackingGateway,
                    commands = trackingGateway,
                    prefs = trackingPrefs,
                ),
            )
        }
    }

    val updateUseCases = AppUpdateUseCases(
        reader = updateGateway,
        downloader = updateGateway,
        installer = updateGateway,
    )

    val viewModelFactory = AppViewModel.Factory(
        servicesFactory = servicesFactory,
        environmentStore = environmentStore,
        themeStore = themeStore,
        routes = routeStore,
        updates = updateUseCases,
    )
}
