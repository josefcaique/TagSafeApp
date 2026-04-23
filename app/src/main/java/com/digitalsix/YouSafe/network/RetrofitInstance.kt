package com.digitalsix.YouSafe.network

import android.content.Context
import com.digitalsix.YouSafe.BuildConfig
import com.digitalsix.YouSafe.utils.SessionManager
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    // ✅ Agora a URL vem automaticamente do Gradle (Produção ou Staging)
    private val BASE_URL = BuildConfig.BASE_URL
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val refreshRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val refreshApi: ApiService by lazy {
        refreshRetrofit.create(ApiService::class.java)
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .authenticator(TokenAuthenticator())
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    private class TokenAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val requestOriginal = response.request()
            if (requestOriginal.url().encodedPath().contains("/auth/refresh")) return null
            if (responseCount(response) >= 2) return null

            val context = appContext ?: return null
            val sessionManager = SessionManager(context)
            val refreshToken = sessionManager.getRefreshToken()?.takeIf { it.isNotBlank() } ?: return null
            val tokenUsado = requestOriginal.header("Authorization")
            val tokenAtual = sessionManager.getTokenWithBearer()
            if (tokenUsado != null && tokenAtual != null && tokenUsado != tokenAtual) {
                return requestOriginal.newBuilder()
                    .header("Authorization", tokenAtual)
                    .build()
            }

            val refreshResponse = try {
                refreshApi.refreshTokenSync(RefreshTokenRequest(refreshToken)).execute()
            } catch (e: Exception) {
                return null
            }

            if (!refreshResponse.isSuccessful) return null
            val body = refreshResponse.body() ?: return null
            sessionManager.atualizarTokens(
                token = body.token,
                refreshToken = body.refreshToken,
                refreshExpiresAt = body.refreshExpiresAt
            )

            return requestOriginal.newBuilder()
                .header("Authorization", "Bearer ${body.token}")
                .build()
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var priorResponse = response.priorResponse()
            while (priorResponse != null) {
                count++
                priorResponse = priorResponse.priorResponse()
            }
            return count
        }
    }
}
