package com.cardhunt.app.data.repository

import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.remote.CardHuntApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(private val api: CardHuntApi) {
    suspend fun getFriends() = safeApiCall { api.friends() }
    suspend fun removeFriend(userId: Int) = safeApiCall { api.removeFriend(userId) }
    suspend fun getPlayers() = safeApiCall { api.getPlayers() }
    suspend fun getMyProfile() = safeApiCall { api.me() }
    suspend fun search(q: String) = safeApiCall { api.searchUsers(q) }
    suspend fun sendRequest(userId: Int) = safeApiCall { api.friendRequest(userId) }
    suspend fun accept(requestId: Int) = safeApiCall { api.acceptRequest(requestId) }
    suspend fun decline(requestId: Int) = safeApiCall { api.declineRequest(requestId) }
    suspend fun cancelRequest(requestId: Int) = safeApiCall { api.cancelFriendRequest(requestId) }
}