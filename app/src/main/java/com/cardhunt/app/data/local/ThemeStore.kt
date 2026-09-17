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

private val Context.themeStore by preferencesDataStore("theme")

@Singleton
class ThemeStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    companion object {
        const val SYSTEM = "system"
        const val LIGHT = "light"
        const val DARK = "dark"
    }
    private val KEY = stringPreferencesKey("mode")
    val mode: Flow<String> = ctx.themeStore.data.map { it[KEY] ?: SYSTEM }
    suspend fun setMode(mode: String) { ctx.themeStore.edit { it[KEY] = mode } }
}