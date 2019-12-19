package com.portail.tokoosride.models

class Package (
    val id: Int,
    val name: Int,
    val weight: Int,
    val description: Int,
    val recipients: List<Recipient>
){}