package com.portail.tokoosride.models

class Rider (
    val id: Int,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    private val image: String,
    val country: String,
    val city: String,
    val rideNumber: String,
    val rate: Double,
    val token: String,
    val channel: String,
    val isAvailable: Int,
    val isAuthenticated: Int,
    val vehicle: Vehicle
){}