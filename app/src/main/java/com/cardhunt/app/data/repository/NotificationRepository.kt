package com.cardhunt.app.data.repository

import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.remote.CardHuntApi
import com.cardhunt.app.data.remote.dto.CardNearbyRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(private val api: CardHuntApi) {
    suspend fun fetch() = safeApiCall { api.notifications() }
    suspend fun markAllRead() = safeApiCall { api.markNotificationsRead() }
    suspend fun reportCardNearby(cardId: Int) =
        safeApiCall { api.reportCardNearby(CardNearbyRequest(cardId)) }
    suspend fun delete(id: Int) = safeApiCall { api.deleteNotification(id) }
    suspend fun clearAll() = safeApiCall { api.clearAllNotifications() }
}