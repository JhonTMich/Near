package com.example.findme.data.local

import android.content.Context

/**
 * A recovery link signs the person in before they choose the new password. This remembers that,
 * so closing or killing the app does not leave them inside the account with the old password unchanged.
 * Backups exclude every SharedPreferences file, so the mark never moves to another device.
 */
object PendingPasswordRecovery {
    private const val FILE = "password_recovery"
    private const val USER = "user_id"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mark(context: Context, userId: String) { prefs(context).edit().putString(USER, userId).commit() }

    fun isPending(context: Context, userId: String) = prefs(context).getString(USER, null) == userId

    fun clear(context: Context) { prefs(context).edit().clear().commit() }
}
