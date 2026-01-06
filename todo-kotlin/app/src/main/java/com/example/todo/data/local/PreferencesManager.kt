package com.example.todo.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "todo_prefs")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val LAST_SYNC_KEY = stringPreferencesKey("last_sync")
    }
    
    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }
    
    val userIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_ID_KEY]
    }
    
    val lastSyncFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LAST_SYNC_KEY]
    }
    
    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }
    
    suspend fun getUserId(): String? {
        return context.dataStore.data.first()[USER_ID_KEY]
    }
    
    suspend fun getLastSync(): String? {
        return context.dataStore.data.first()[LAST_SYNC_KEY]
    }
    
    suspend fun saveAuthData(token: String, userId: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_ID_KEY] = userId
        }
    }
    
    suspend fun saveLastSync(timestamp: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SYNC_KEY] = timestamp
        }
    }
    
    suspend fun updateToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
    }
    
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return getToken() != null
    }
}
