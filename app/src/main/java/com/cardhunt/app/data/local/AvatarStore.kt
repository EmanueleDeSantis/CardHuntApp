package com.cardhunt.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.avatarStore by preferencesDataStore("avatar")

@Singleton
class AvatarStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val KEY = stringPreferencesKey("avatar_url")
    val avatarUrl: Flow<String?> = ctx.avatarStore.data.map { it[KEY] }
    suspend fun save(url: String) { ctx.avatarStore.edit { it[KEY] = url } }
    suspend fun clear() { ctx.avatarStore.edit { it.remove(KEY) } }
}