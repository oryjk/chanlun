package cn.oryjk.changuan.application

import cn.oryjk.changuan.domain.AppLoginResult
import cn.oryjk.changuan.domain.AppSession
import cn.oryjk.changuan.domain.EmailCodeSent

class MarketUseCases(
    val reader: MarketReader,
    val writer: QuoteWriter,
)

class AppUpdateUseCases(
    val reader: AppUpdateReader,
    val downloader: AppUpdateDownloader,
    val installer: AppUpdateInstaller,
)

class AuthUseCases(
    val reader: AuthReader,
    val commands: AuthCommands,
    val store: SessionStore,
) {
    suspend fun currentSession(): AppSession {
        val currentToken = store.token()
        if (currentToken.isNullOrBlank()) {
            return AppSession(authenticated = false, user = null)
        }
        val session = reader.session()
        if (!session.authenticated) {
            store.clear()
        }
        return session
    }

    suspend fun sendEmailCode(email: String): EmailCodeSent =
        commands.sendEmailCode(email.trim())

    suspend fun registerWithEmail(
        email: String,
        code: String,
        password: String,
        nickname: String? = null,
    ): AppLoginResult {
        val result = commands.registerWithEmail(
            email = email.trim(),
            code = code.trim(),
            password = password,
            nickname = nickname?.trim()?.takeIf { it.isNotEmpty() },
        )
        store.save(result.token)
        return result
    }

    suspend fun loginWithEmail(
        email: String,
        password: String,
    ): AppLoginResult {
        val result = commands.loginWithEmail(
            email = email.trim(),
            password = password,
        )
        store.save(result.token)
        return result
    }

    fun persist(result: AppLoginResult) {
        store.save(result.token)
    }

    suspend fun logout() {
        try {
            commands.logout()
        } finally {
            store.clear()
        }
    }

    suspend fun deleteAccount() {
        commands.deleteAccount()
        store.clear()
    }
}
