package com.example.findme.data.local

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

internal val accountCacheKey = stringPreferencesKey("account_cache_v1")

/**
 * The copy of the account, in a file of its own: it is the largest value and is rewritten on every sync,
 * so small settings changes no longer rewrite it, nor it them. Up to 1.9.4 it lived in [dataStore].
 */
val Context.accountCacheStore: DataStore<Preferences> by preferencesDataStore(name = "account_cache",
    produceMigrations = { context -> listOf(MoveAccountCache(context)) })

private class MoveAccountCache(private val context: Context) : DataMigration<Preferences> {
    private suspend fun old() = context.dataStore.data.first()[accountCacheKey]
    override suspend fun shouldMigrate(currentData: Preferences) = old() != null
    override suspend fun migrate(currentData: Preferences): Preferences {
        val moved = old() ?: return currentData
        return currentData.toMutablePreferences().apply { if (accountCacheKey !in this) this[accountCacheKey] = moved }
    }
    override suspend fun cleanUp() { context.dataStore.edit { it.remove(accountCacheKey) } }
}
