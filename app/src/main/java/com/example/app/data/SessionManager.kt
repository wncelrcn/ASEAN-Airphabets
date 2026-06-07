package com.example.app.data

import android.content.Context
import android.content.SharedPreferences
import com.example.app.data.entity.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        loadSavedUser()
    }

    fun saveUserSession(user: User, staySignedIn: Boolean = true) {
        _currentUser.value = user

        if (staySignedIn) {
            prefs.edit().apply {
                putLong(KEY_USER_ID, user.id)
                putString(KEY_USERNAME, user.username)
                putString(KEY_NAME, user.name)
                putBoolean(KEY_IS_LOGGED_IN, true)
                putBoolean(KEY_STAY_SIGNED_IN, true)
                apply()
            }
        } else {
            prefs.edit().clear().apply()
        }
    }

    private fun loadSavedUser() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val staySignedIn = prefs.getBoolean(KEY_STAY_SIGNED_IN, false)

        if (isLoggedIn && staySignedIn) {
            val userId = prefs.getLong(KEY_USER_ID, 0)
            val username = prefs.getString(KEY_USERNAME, null)
            val name = prefs.getString(KEY_NAME, null)

            if (userId != 0L && username != null && name != null) {
                _currentUser.value = User(
                    id = userId,
                    username = username,
                    name = name,
                    passwordHash = "",
                    salt = ""
                )
            }
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    fun isLoggedIn(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val staySignedIn = prefs.getBoolean(KEY_STAY_SIGNED_IN, false)
        return isLoggedIn && staySignedIn
    }

    fun getUserId(): Long {
        return prefs.getLong(KEY_USER_ID, 0)
    }

    fun getUserName(): String {
        return prefs.getString(KEY_NAME, "Guest") ?: "Guest"
    }

    companion object {
        private const val PREFS_NAME = "airphabets_user_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_NAME = "name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_STAY_SIGNED_IN = "stay_signed_in"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
