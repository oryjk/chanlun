package cn.oryjk.changuan.infrastructure

import android.content.Context
import cn.oryjk.changuan.application.RouteStore
import cn.oryjk.changuan.application.SessionStore
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe

class SharedPreferencesSessionStore(
    context: Context,
) : SessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun token(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "changuan_session"
        private const val KEY_TOKEN = "bearer_token"
    }
}

class SharedPreferencesRouteStore(
    context: Context,
) : RouteStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
