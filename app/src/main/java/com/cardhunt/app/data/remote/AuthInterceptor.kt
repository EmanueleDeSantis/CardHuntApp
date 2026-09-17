package com.cardhunt.app.data.remote

import com.cardhunt.app.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = tokenStore.cached?.token?.let {
            chain.request().newBuilder().header("Authorization", "Bearer $it").build()
        } ?: chain.request()
        return chain.proceed(request)
    }
}