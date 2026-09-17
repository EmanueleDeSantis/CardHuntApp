package com.cardhunt.app.data.repository

import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.remote.CardHuntApi
import com.cardhunt.app.data.remote.dto.ShareRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepository @Inject constructor(private val api: CardHuntApi) {
    suspend fun share(cardId: Int, friendId: Int) = safeApiCall { api.createShare(ShareRequest(cardId, friendId)) }
    suspend fun redeem(token: String) = safeApiCall { api.redeemShare(token) }
    suspend fun getAvailableFriends(cardId: Int) = safeApiCall { api.getAvailableFriends(cardId) }
}