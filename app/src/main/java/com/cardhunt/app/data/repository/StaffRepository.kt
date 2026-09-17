package com.cardhunt.app.data.repository

import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.remote.CardHuntApi
import com.cardhunt.app.data.remote.dto.CreateCardRequest
import com.cardhunt.app.data.remote.dto.UpdateUserRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffRepository @Inject constructor(private val api: CardHuntApi) {
    suspend fun getUsers() = safeApiCall { api.getAllUsers() }
    suspend fun updateUserStatus(id: Int, status: String) = safeApiCall { api.updateUserStatus(id, UpdateUserRequest(status = status)) }
    suspend fun updateUserRole(id: Int, role: String) = safeApiCall { api.updateUserRole(id, UpdateUserRequest(role = role)) }
    suspend fun getAllCards() = safeApiCall { api.getAllCards() }
    suspend fun deleteCard(id: Int) = safeApiCall { api.deleteCard(id) }
}