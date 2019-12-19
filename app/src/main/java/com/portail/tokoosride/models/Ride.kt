package com.portail.tokoosride.models

class Ride (
    val id: Int,
    val rider: Rider,
    val user: User,
    val type: String,
    val origin: Location,
    val destination: Location,
    val packageRide: Package?,
    val status: Int,
    val priceRange: String,
    val currency: String,
    val numberOfPeople: Int,
    val amountPaid: Double,
    val distance: Double,
    val duration: Int,
    val timeDeparture: String,
    val timeArrive: String,
    val review: RideReview?,
    val createdAt: String,
    val updatedAt: String
){}