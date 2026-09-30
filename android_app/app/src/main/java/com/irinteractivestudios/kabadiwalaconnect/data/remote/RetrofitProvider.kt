package com.irinteractivestudios.kabadiwalaconnect.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import android.os.SystemClock
import android.util.Log
import com.irinteractivestudios.kabadiwalaconnect.BuildConfig
import retrofit2.Retrofit
import retrofit2.Invocation
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Retrofit construction point. The base URL is supplied at build time. */
object RetrofitProvider {

    const val PLACEHOLDER_BASE_URL = "https://api.kabadiwalaconnect.invalid/api/v1/"

    fun create(
        baseUrl: String = PLACEHOLDER_BASE_URL,
        tokenProvider: () -> String? = { null },
        tokenRefresher: ((failedAccessToken: String?) -> String?)? = null,
        onAuthenticationFailure: ((failedToken: String?) -> Unit)? = null,
        sessionGenerationProvider: (() -> Long)? = null,
        requestSessionGenerationProvider: (() -> Long)? = sessionGenerationProvider,
        accountIdProvider: () -> String? = { null }
    ): ApiService {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenProvider()
            // Only the credential/session endpoints are public.  `/auth/profile`
            // is protected and must carry the bearer token; treating every
            // `/auth/*` path as public caused the startup profile request to
            // return `Bearer token required` immediately after sign-in.
            val isPublicAuthEndpoint = isPublicAuthEndpoint(original.url.encodedPath)
            // A protected call without a token is a client-side session race,
            // not a request the backend should have to reject. This guard is
            // deliberately below the public-auth check so login, signup,
            // OTP, refresh, and logout can still run without an access token.
            if (token.isNullOrBlank() && !isPublicAuthEndpoint) {
                throw ProtectedRequestBlockedException
            }
            val request: Request = if (token.isNullOrBlank() || isPublicAuthEndpoint) {
                original
            } else {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .tag(SessionGeneration::class.java, requestSessionGenerationProvider?.invoke()?.let(::SessionGeneration))
                    .build()
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestId = original.header("X-Request-ID") ?: UUID.randomUUID().toString()
                val request = original.newBuilder().header("X-Request-ID", requestId).build()
                val operation = original.tag(Invocation::class.java)?.method()?.name ?: "unknown"
                val started = SystemClock.elapsedRealtimeNanos()
                val requestOwner = accountIdProvider()
                val requestGeneration = requestSessionGenerationProvider?.invoke()
                try {
                    val response = chain.proceed(request)
                    if (response.isSuccessful && requestOwner == accountIdProvider() &&
                        sessionRetryAllowed(requestGeneration, sessionGenerationProvider?.invoke()) &&
                        DataChangeEvents.invalidatesData(request.method, request.url.encodedPath)) {
                        DataChangeEvents.publish(requestOwner, request.url.encodedPath)
                    }
                    if (BuildConfig.DEBUG) Log.d("KcApiTiming", "requestId=$requestId operation=$operation durationMs=${(SystemClock.elapsedRealtimeNanos() - started) / 1_000_000} status=${response.code}")
                    response
                } catch (error: IOException) {
                    if (BuildConfig.DEBUG) Log.w("KcApiTiming", "requestId=$requestId operation=$operation durationMs=${(SystemClock.elapsedRealtimeNanos() - started) / 1_000_000} failure=${error.javaClass.simpleName}")
                    throw error
                }
            }
            .addInterceptor(authInterceptor)
            .addInterceptor { chain ->
                // A photo classification may need one additional Lite-model
                // request to estimate the scrap range after identifying the item.
                val request = chain.request()
                val photoDetection = request.url.encodedPath.endsWith("/future/lots/material-suggestion")
                (if (photoDetection) chain.withReadTimeout(45, TimeUnit.SECONDS) else chain).proceed(request)
            }
            .authenticator { _, response ->
                if (tokenRefresher == null || isPublicAuthEndpoint(response.request.url.encodedPath)) {
                    null
                } else if (!response.request.belongsToCurrentSession(sessionGenerationProvider)) {
                    // An old request can finish after logout or another
                    // account signs in. It must never borrow that account's
                    // fresh bearer token in an OkHttp retry.
                    null
                } else if (response.retryCount() >= 2) {
                    // The refreshed credential was also rejected. Clear the
                    // session, but let the owner compare the failed token
                    // with the current token before doing so; a late response
                    // from an old account must not log out a new account.
                    onAuthenticationFailure?.invoke(response.request.bearerToken())
                    null
                } else {
                    // Multiple requests may fail together when a token expires.
                    // Reuse a token already refreshed by another request first.
                    synchronized(this) {
                        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                        val current = tokenProvider()
                        val fresh = if (!current.isNullOrBlank() && current != requestToken) current else tokenRefresher(requestToken)
                        if (!response.request.belongsToCurrentSession(sessionGenerationProvider)) return@synchronized null
                        fresh?.let { response.request.newBuilder().header("Authorization", "Bearer $it").build() }
                        ?: run {
                            // A rotated refresh token can be rejected when a
                            // stale process/device presents it after another
                            // session has already advanced the token family.
                            // Do not keep replaying the same credential or
                            // leave the UI in a protected state with a dead
                            // session. The owner clears the local session and
                            // returns the user to authentication.
                            if (tokenProvider().isNullOrBlank()) onAuthenticationFailure?.invoke(requestToken)
                            null
                        }
                }
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun Response.retryCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun Request.bearerToken(): String? =
        header("Authorization")?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }

    private fun Request.belongsToCurrentSession(generationProvider: (() -> Long)?): Boolean =
        sessionRetryAllowed(tag(SessionGeneration::class.java)?.value, generationProvider?.invoke())

    internal data class SessionGeneration(val value: Long)

    internal fun sessionRetryAllowed(requestGeneration: Long?, currentGeneration: Long?): Boolean =
        requestGeneration == currentGeneration

    internal fun isPublicAuthEndpoint(path: String): Boolean =
            path.endsWith("/materials/categories") ||
            path.endsWith("/auth/request-otp") ||
            path.endsWith("/auth/verify-otp") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/logout") ||
            path.endsWith("/auth/signup") ||
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/admin-login")

}

internal object ProtectedRequestBlockedException : IOException(
    "Protected request blocked until an authenticated session is available"
)
