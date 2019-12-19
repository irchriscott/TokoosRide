package com.portail.tokoosride.models

class Vehicle (
    val id: Int,
    val type: VehicleType,
    val brand: String,
    val name: String,
    val plateNumber: String,
    val productionYear: Int,
    val mileage: Int,
    val power: Int,
    val maxPeople: Int
){}