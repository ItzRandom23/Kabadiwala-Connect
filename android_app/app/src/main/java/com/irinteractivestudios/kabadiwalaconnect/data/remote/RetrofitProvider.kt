package com.irinteractivestudios.kabadiwalaconnect.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Retrofit construction point. The base URL is supplied at build time. */
object RetrofitProvider {

    const val PLACEHOLDER_BASE_URL = "https://api.kabadiwalaconnect.invalid/api/v1/"

    fun create(
        baseUrl: String = PLACEHOLDER_BASE_URL,
        tokenProvider: () -> String? = { null },
        tokenRefresher: ((failedAccessToken: String?) -> String?)? = null,
        onAuthenticationFailure: ((failedToken: String?) -> Unit)? = null
    ): ApiService {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenProvider()
            // Only the credential/session endpoints are public.  `/auth/profile`
            // is protected and must carry the bearer token; treating every
            // `/auth/*` path as public caused the startup profile request to
            // return `Bearer token required` immediately after sign-in.
            val isPublicAuthEndpoint = original.url.encodedPath.isPublicAuthEndpoint()
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
                original.newBuilder().header("Authorization", "Bearer $token").build()
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor { chain ->
                // A photo classification may need one additional Lite-model
                // request to estimate the scrap range after identifying the item.
                val request = chain.request()
                val photoDetection = request.url.encodedPath.endsWith("/future/lots/material-suggestion")
                (if (photoDetection) chain.withReadTimeout(45, TimeUnit.SECONDS) else chain).proceed(request)
            }
            .authenticator { _, response ->
                if (tokenRefresher == null || response.request.url.encodedPath.isPublicAuthEndpoint()) {
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

    private fun String.isPublicAuthEndpoint(): Boolean =
            endsWith("/auth/request-otp") ||
            endsWith("/auth/verify-otp") ||
            endsWith("/auth/refresh") ||
            endsWith("/auth/logout") ||
            endsWith("/auth/signup") ||
            endsWith("/auth/login") ||
            endsWith("/auth/admin-login")

    private object ProtectedRequestBlockedException : IOException(
        "Protected request blocked until an authenticated session is available"
    )
}
