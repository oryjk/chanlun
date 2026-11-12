package cn.oryjk.changuan.application

import cn.oryjk.changuan.domain.AppLoginResult
import cn.oryjk.changuan.domain.AppSession
import cn.oryjk.changuan.domain.AppUpdateInfo
import cn.oryjk.changuan.domain.EmailCodeSent
import cn.oryjk.changuan.domain.MarketQuote
import cn.oryjk.changuan.domain.MarketSnapshot
import cn.oryjk.changuan.domain.QuoteSeries
import cn.oryjk.changuan.domain.RankingBoard
import cn.oryjk.changuan.domain.RootTab
import cn.oryjk.changuan.domain.SignalConfluence
import cn.oryjk.changuan.domain.Stock
import cn.oryjk.changuan.domain.Timeframe

interface MarketReader {
    suspend fun search(query: String): List<Stock>
    suspend fun snapshot(secid: String, timeframe: Timeframe, refresh: Boolean = false): MarketSnapshot
    suspend fun quote(secid: String): MarketQuote
    suspend fun series(secid: String): QuoteSeries
    suspend fun rankings(): RankingBoard
    suspend fun confluence(secid: String): SignalConfluence
}

interface QuoteWriter {
    suspend fun record(quote: MarketQuote): QuoteSeries
}

interface SessionStore {
    fun token(): String?
    fun save(token: String)
    fun clear()
}

interface RouteStore {
    fun restore(): Triple<Stock?, Timeframe, RootTab>
    fun save(stock: Stock?, timeframe: Timeframe, tab: RootTab)
}

interface AuthReader {
    suspend fun session(): AppSession
}

interface AuthCommands {
    suspend fun sendEmailCode(email: String): EmailCodeSent
    suspend fun registerWithEmail(
        email: String,
        code: String,
        password: String,
        nickname: String? = null,
    ): AppLoginResult

    suspend fun loginWithEmail(
        email: String,
        password: String,
    ): AppLoginResult

    suspend fun logout()
    suspend fun deleteAccount()
}

interface AppUpdateReader {
    suspend fun checkLatest(): AppUpdateInfo?
}

interface AppUpdateDownloader {
    suspend fun download(
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): String
}

interface AppUpdateInstaller {
    fun canInstallPackages(): Boolean
    fun openInstallPermissionSettings()
    fun installApk(apkPath: String)
}

