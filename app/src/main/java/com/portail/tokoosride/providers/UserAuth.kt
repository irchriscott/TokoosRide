package com.portail.tokoosride.providers

import android.content.Context
import android.preference.PreferenceManager
import com.portail.tokoosride.models.User
import com.google.gson.Gson

class UserAuth (contxt: Context) {

    private val context = contxt
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPreferencesEditor = sharedPreferences.edit()

    private val SESSION_USER_KEY = "session_user"
    private val AUTH_STAGE = "auth_stage"

    public fun saveUser(user: String) {
        sharedPreferencesEditor.putString(SESSION_USER_KEY, user)
        sharedPreferencesEditor.apply()
        sharedPreferencesEditor.commit()
    }

    public fun getUser() : User? {
        val user = sharedPreferences.getString(SESSION_USER_KEY, "")
        return if (user != "") {
            Gson().fromJson(user, User::class.java)
        } else { null }
    }

    public fun isLoggedIn(): Boolean = this.getUser() != null

    public fun saveAuthStage(stage: Int) {
        sharedPreferencesEditor.putInt(AUTH_STAGE, stage)
        sharedPreferencesEditor.apply()
        sharedPreferencesEditor.commit()
    }

    public fun getAuthStage() : Int = sharedPreferences.getInt(AUTH_STAGE, 0)
}