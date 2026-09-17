package com.cardhunt.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardhunt.app.data.remote.dto.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.get

private val Context.dataStore by preferencesDataStore("auth")

data class Session(val token: String, val userId: Int, val username: String, val role: String)

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val TOKEN = stringPreferencesKey("jwt")
    private val USER_ID = intPreferencesKey("user_id")
    private val USERNAME = stringPreferencesKey("username")
    private val ROLE = stringPreferencesKey("role")

    @Volatile var cached: Session? = null
        private set

    val session: Flow<Session?> = ctx.dataStore.data.map { p ->
        p[TOKEN]?.let { Session(it, p[USER_ID] ?: 0, p[USERNAME] ?: "", p[ROLE] ?: "USER") }
    }

    suspend fun warmUp() {
        cached = session.first()
    }

    suspend fun save(token: String, user: UserDto) {
        cached = Session(token, user.id, user.username, user.role)
        ctx.dataStore.edit {
            it[TOKEN] = token; it[USER_ID] = user.id
            it[USERNAME] = user.username; it[ROLE] = user.role
        }
    }

    suspend fun clear() {
        cached = null
        ctx.dataStore.edit { it.clear() }
    }
}