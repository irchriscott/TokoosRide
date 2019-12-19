package com.portail.tokoosride.providers

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.portail.tokoosride.models.Ride

class LocalData (contxt: Context) {

    private val context = contxt
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPreferencesEditor = sharedPreferences.edit()

    private val CURRENT_RIDE_KEY = "current_ride"

    public fun saveCurrentRide(ride: String) {
        sharedPreferencesEditor.putString(CURRENT_RIDE_KEY, ride)
        sharedPreferencesEditor.apply()
        sharedPreferencesEditor.commit()
    }

    public fun getCurrentRide() : Ride? {
        val ride = sharedPreferences.getString(CURRENT_RIDE_KEY, "")
        return if (ride != "") {
            Gson().fromJson(ride, Ride::class.java)
        } else { null }
    }

    public fun hasActiveRide(): Boolean = this.getCurrentRide() != null

    public fun deleteCurrentRide() {
        sharedPreferencesEditor.remove(CURRENT_RIDE_KEY)
        sharedPreferencesEditor.apply()
        sharedPreferencesEditor.commit()
    }
}