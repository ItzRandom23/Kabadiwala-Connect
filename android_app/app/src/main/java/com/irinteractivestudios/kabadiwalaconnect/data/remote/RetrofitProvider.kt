package com.irinteractivestudios.kabadiwalaconnect.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Retrofit construction point. The base URL is supplied at build time. */
object RetrofitProvider {

    const val PLACEHOLDER_BASE_URL = "https://api.kabadiwalaconnect.invalid/api/v1/"

    fun create(baseUrl: String = PLACEHOLDER_BASE_URL, tokenProvider: () -> String? = { null }): ApiService {
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
}
