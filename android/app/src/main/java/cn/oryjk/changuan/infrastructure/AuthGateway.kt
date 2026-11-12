package cn.oryjk.changuan.infrastructure

import cn.oryjk.changuan.application.AuthCommands
import cn.oryjk.changuan.application.AuthReader
import cn.oryjk.changuan.domain.AppLoginResult
import cn.oryjk.changuan.domain.AppSession
import cn.oryjk.changuan.domain.AppUser
import cn.oryjk.changuan.domain.EmailCodeSent
import cn.oryjk.changuan.domain.MarketTime
import com.google.gson.JsonObject

class AuthGateway(
    private val api: ApiClient,
) : AuthReader, AuthCommands {
    override suspend fun session(): AppSession =
        api.send(
            path = "api/app-auth/session",
            authenticated = true,
            mapper = ::parseSession,
        )

    override suspend fun sendEmailCode(email: String): EmailCodeSent {
        val body = JsonObject().apply {
            addProperty("email", email)
        }
        return api.send(
            path = "api/app-auth/email/code",
            body = body,
            mapper = ::parseEmailCodeSent,
        )
    }

    override suspend fun registerWithEmail(
        email: String,
        code: String,
        password: String,
        nickname: String?,
    ): AppLoginResult {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("code", code)
            addProperty("password", password)
            if (!nickname.isNullOrBlank()) {
                addProperty("nickname", nickname)
            }
        }
        return api.send(
            path = "api/app-auth/email/register",
            body = body,
            mapper = ::parseLoginResult,
        )
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String,
    ): AppLoginResult {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
        }
        return api.send(
            path = "api/app-auth/email/login",
            body = body,
            mapper = ::parseLoginResult,
        )
    }

    override suspend fun logout() {
        api.send(
            path = "api/app-auth/logout",
            body = JsonObject(),
            authenticated = true,
        ) { it.optBoolean("ok") ?: true }
    }

    override suspend fun deleteAccount() {
        api.send(
            path = "api/app-auth/delete-account",
            body = JsonObject(),
            authenticated = true,
        ) { it.optBoolean("ok") ?: true }
    }

    companion object {
        internal fun parseUser(obj: JsonObject): AppUser =
            AppUser(
                userId = obj.reqString("user_id"),
                nickname = obj.reqString("nickname"),
            )

        internal fun parseSession(obj: JsonObject): AppSession {
            val authenticated = obj.optBoolean("authenticated") ?: false
            val userObj = obj.optElement("user")?.takeIf { it.isJsonObject }?.asJsonObject
            return AppSession(
                authenticated = authenticated && userObj != null,
                user = userObj?.let(::parseUser),
            )
        }

        internal fun parseLoginResult(obj: JsonObject): AppLoginResult {
            val userObj = obj.optElement("user")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw ApiError.InvalidData()
            val expiresAt = obj.optString("expires_at")?.let(MarketTime::parseInstant)
                ?: throw ApiError.InvalidData()
            return AppLoginResult(
                user = parseUser(userObj),
                token = obj.reqString("token"),
                expiresAt = expiresAt,
            )
        }

        internal fun parseEmailCodeSent(obj: JsonObject): EmailCodeSent =
            EmailCodeSent(
                expiresInSeconds = obj.optInt("expires_in") ?: 600,
                cooldownSeconds = obj.optInt("cooldown_seconds") ?: 60,
            )
    }
}
