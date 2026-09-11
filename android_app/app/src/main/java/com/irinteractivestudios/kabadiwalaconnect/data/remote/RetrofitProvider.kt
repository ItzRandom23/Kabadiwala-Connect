package com.irinteractivestudios.kabadiwalaconnect.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Retrofit construction point. The base URL is supplied at build time. */
object RetrofitProvider {

    const val PLACEHOLDER_BASE_URL = "https://api.kabadiwalaconnect.invalid/api/v1/"

    fun create(
        baseUrl: String = PLACEHOLDER_BASE_URL,
        tokenProvider: () -> String? = { null },
        tokenRefresher: (() -> String?)? = null
    ): ApiService {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenProvider()
            val request: Request = if (token.isNullOrBlank() || original.url.encodedPath.contains("/auth/")) {
                original
            } else {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator { _, response ->
                if (tokenRefresher == null || response.request.url.encodedPath.contains("/auth/") || response.retryCount() >= 2) {
                    null
                } else {
                    // Multiple requests may fail together when a token expires.
                    // Reuse a token already refreshed by another request first.
                    synchronized(this) {
                        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                        val current = tokenProvider()
                        val fresh = if (!current.isNullOrBlank() && current != requestToken) current else tokenRefresher()
                        fresh?.let { response.request.newBuilder().header("Authorization", "Bearer $it").build() }
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
}
