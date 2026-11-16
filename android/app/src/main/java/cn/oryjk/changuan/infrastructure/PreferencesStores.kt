package cn.oryjk.changuan.infrastructure

import android.content.Context
import cn.oryjk.changuan.application.EnvironmentStore
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.SessionStore
import cn.oryjk.changuan.application.ThemePreferenceStore
import cn.oryjk.changuan.application.TrackingPreferenceStore
import cn.oryjk.changuan.domain.AppThemeMode
import cn.oryjk.changuan.domain.BackendEnvironment
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesSessionStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun token(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val DEFAULT_PREFS_NAME = "changuan_session"
        private const val KEY_TOKEN = "bearer_token"

        fun prefsNameFor(environment: BackendEnvironment): String = when (environment) {
            BackendEnvironment.Development -> DEFAULT_PREFS_NAME
            BackendEnvironment.DevelopmentProxy -> "${DEFAULT_PREFS_NAME}_development_proxy"
            BackendEnvironment.Production -> "${DEFAULT_PREFS_NAME}_production"
        }
    }
}

class SharedPreferencesEnvironmentStore(
    context: Context,
    private val defaultEnvironment: BackendEnvironment = BackendEnvironment.DEFAULT,
) : EnvironmentStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _environment = MutableStateFlow(readInitial())
    override val environment: StateFlow<BackendEnvironment> = _environment.asStateFlow()

    override fun select(environment: BackendEnvironment) {
        if (_environment.value == environment) return
        val committed = prefs.edit()
            .putString(KEY_ENVIRONMENT, environment.id)
            .commit()
        check(committed) { "无法保存后端环境，请重试" }
        _environment.value = environment
    }

    private fun readInitial(): BackendEnvironment {
        val raw = runCatching { prefs.getString(KEY_ENVIRONMENT, null) }.getOrNull()
        return if (raw.isNullOrBlank()) defaultEnvironment else BackendEnvironment.fromId(raw)
    }

    companion object {
        private const val PREFS_NAME = "changuan_environment"
        private const val KEY_ENVIRONMENT = "environment"
    }
}

class SharedPreferencesThemeStore(
    context: Context,
) : ThemePreferenceStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        AppThemeMode.fromRaw(runCatching { prefs.getString(KEY_THEME_MODE, null) }.getOrNull()),
    )
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) {
        if (_themeMode.value == mode) return
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "changuan_theme"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

class SharedPreferencesRouteStore(
    context: Context,
) : RouteStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun restore(): Triple<Stock?, Timeframe, RootTab> {
        val secid = prefs.getString(KEY_SECID, null)
        val symbol = prefs.getString(KEY_SYMBOL, null)
        val name = prefs.getString(KEY_NAME, null)
        val market = prefs.getString(KEY_MARKET, null)
        val stock = if (!secid.isNullOrBlank() && !symbol.isNullOrBlank() && !name.isNullOrBlank()) {
            Stock(
                secid = secid,
                symbol = symbol,
                name = name,
                market = market ?: "沪深",
            )
        } else {
            null
        }
        val timeframe = Timeframe.fromRaw(prefs.getString(KEY_TIMEFRAME, null))
        val tab = RootTab.fromRaw(prefs.getString(KEY_TAB, null))
        return Triple(stock, timeframe, tab)
    }

    override fun save(stock: Stock?, timeframe: Timeframe, tab: RootTab) {
        val editor = prefs.edit()
            .putString(KEY_TIMEFRAME, timeframe.rawValue)
            .putString(KEY_TAB, tab.rawValue)
        if (stock != null) {
            editor
                .putString(KEY_SECID, stock.secid)
                .putString(KEY_SYMBOL, stock.symbol)
                .putString(KEY_NAME, stock.name)
                .putString(KEY_MARKET, stock.market)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "changuan_route"
        private const val KEY_SECID = "secid"
        private const val KEY_SYMBOL = "symbol"
        private const val KEY_NAME = "name"
        private const val KEY_MARKET = "market"
        private const val KEY_TIMEFRAME = "timeframe"
        private const val KEY_TAB = "tab"
    }
}

class SharedPreferencesTrackingPreferenceStore(
    context: Context,
    prefsName: String,
) : TrackingPreferenceStore {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun pinnedSecids(userId: String): Set<String> =
        prefs.getString("pinned_$userId", null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    override fun savePinnedSecids(userId: String, secids: Set<String>) {
        prefs.edit().putString("pinned_$userId", secids.joinToString(",")).apply()
    }

    override fun customOrder(userId: String): List<String> =
        prefs.getString("order_$userId", null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    override fun saveCustomOrder(userId: String, order: List<String>) {
        prefs.edit().putString("order_$userId", order.joinToString(",")).apply()
    }

    companion object {
        fun prefsNameFor(environment: BackendEnvironment): String = when (environment) {
            BackendEnvironment.Development -> "changuan_tracking_prefs"
            BackendEnvironment.DevelopmentProxy -> "changuan_tracking_prefs_proxy"
            BackendEnvironment.Production -> "changuan_tracking_prefs_prod"
        }
    }
}
