package com.cardhunt.app.data.repository

import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.remote.CardHuntApi
import com.cardhunt.app.data.remote.dto.AvatarUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val api: CardHuntApi) {
    suspend fun getMe() = safeApiCall { api.me() }
    suspend fun getInventory() = safeApiCall { api.myCards() }
    suspend fun updateAvatar(url: String) = safeApiCall { api.updateAvatar(AvatarUpdateRequest(url)) }
    suspend fun deleteCard(cardId: Int) = safeApiCall { api.deleteMyCard(cardId) }
}