package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.User

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "lhopital_session_prefs"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_FULL_NAME = "key_full_name"
        private const val KEY_ROLE = "key_role"
        private const val KEY_STUDENT_ID = "key_student_id"
        private const val KEY_PHONE = "key_phone"

        // Keys for saved credentials as specified
        const val KEY_SAVED_USERNAME = "saved_username"
        const val KEY_SAVED_PASSWORD = "saved_password"
        const val KEY_IS_SAVED = "is_saved"
    }

    fun saveSession(user: User) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_ROLE, user.role)
            .putLong(KEY_STUDENT_ID, user.studentId ?: -1L)
            .putString(KEY_PHONE, user.phone)
            .apply()
    }

    /**
     * Clears only active session state (is_logged_in = false),
     * PRESERVING saved_username, saved_password, and is_saved.
     */
    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putLong(KEY_USER_ID, -1L)
            .putString(KEY_USERNAME, "")
            .putString(KEY_FULL_NAME, "")
            .putString(KEY_ROLE, "")
            .putLong(KEY_STUDENT_ID, -1L)
            .putString(KEY_PHONE, "")
            .apply()
    }

    /**
     * Saves user and password credentials upon successful login.
     */
    fun saveCredentials(username: String, pass: String) {
        prefs.edit()
            .putString(KEY_SAVED_USERNAME, username)
            .putString(KEY_SAVED_PASSWORD, pass)
            .putBoolean(KEY_IS_SAVED, true)
            .apply()
    }

    /**
     * Clears saved credentials (e.g. when creating a new account).
     */
    fun clearSavedCredentials() {
        prefs.edit()
            .remove(KEY_SAVED_USERNAME)
            .remove(KEY_SAVED_PASSWORD)
            .putBoolean(KEY_IS_SAVED, false)
            .apply()
    }

    val isCredentialsSaved: Boolean
        get() = prefs.getBoolean(KEY_IS_SAVED, false)

    val savedUsername: String
        get() = prefs.getString(KEY_SAVED_USERNAME, "") ?: ""

    val savedPassword: String
        get() = prefs.getString(KEY_SAVED_PASSWORD, "") ?: ""

    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    val userId: Long
        get() = prefs.getLong(KEY_USER_ID, -1L)

    val username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""

    val fullName: String
        get() = prefs.getString(KEY_FULL_NAME, "") ?: ""

    val role: String
        get() = prefs.getString(KEY_ROLE, "") ?: ""

    val studentId: Long
        get() = prefs.getLong(KEY_STUDENT_ID, -1L)

    val phone: String
        get() = prefs.getString(KEY_PHONE, "") ?: ""
}
