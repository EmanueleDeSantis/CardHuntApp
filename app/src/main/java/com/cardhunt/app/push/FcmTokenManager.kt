package com.cardhunt.app.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import androidx.core.content.edit

object FcmTokenManager {
    private const val PREFS_NAME = "fcm_prefs"
    private const val KEY_TOKEN = "fcm_token"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_TOKEN, token)
            }
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    suspend fun getFreshToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }
}