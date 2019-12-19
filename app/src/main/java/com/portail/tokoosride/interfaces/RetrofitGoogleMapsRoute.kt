package com.portail.tokoosride.interfaces


import com.portail.tokoosride.models.PolylineMapRoute
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query


public interface RetrofitGoogleMapsRoute {

    @GET("api/directions/json?key=AIzaSyD2mLGusTJZqu7zesBgobnoVIzN6hIayvk")
    fun getDistanceDuration(
        @Query("units") units: String, @Query("origin") origin: String, @Query("destination") destination: String, @Query(
            "mode"
        ) mode: String
    ): Call<PolylineMapRoute>

}