package com.cardhunt.app.data.repository

import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.local.Session
import com.cardhunt.app.data.local.TokenStore
import com.cardhunt.app.data.remote.CardHuntApi
import com.cardhunt.app.data.remote.dto.AuthResponse
import com.cardhunt.app.data.remote.dto.FcmTokenRequest
import com.cardhunt.app.data.remote.dto.LoginRequest
import com.cardhunt.app.data.remote.dto.RegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: CardHuntApi,
    private val tokenStore: TokenStore,
) {
    val session = tokenStore.session
    val isAdmin: Boolean get() = tokenStore.cached?.role == "ADMIN"
    fun snapshot(): Session? = tokenStore.cached

    suspend fun register(username: String, email: String, password: String): NetworkResult<AuthResponse> =
        safeApiCall { api.register(RegisterRequest(username, email, password)) }.also { store(it) }

    suspend fun login(identifier: String, password: String): NetworkResult<AuthResponse> =
        safeApiCall { api.login(LoginRequest(identifier, password)) }.also { store(it) }

    suspend fun logout() = tokenStore.clear()

    private suspend fun store(r: NetworkResult<AuthResponse>) {
        if (r is NetworkResult.Success) tokenStore.save(r.data.token, r.data.user)
    }
    suspend fun registerFcmToken(token: String) = safeApiCall { api.registerFcmToken(FcmTokenRequest(token)) }
}