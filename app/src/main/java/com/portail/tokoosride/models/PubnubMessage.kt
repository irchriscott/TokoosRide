package com.portail.tokoosride.models

class PubnubMessage (
    var type: String,
    var message: String,
    var channel: String,
    var location: PubnubLocation?,
    var distance: Double?,
    var data: MutableMap<String, Any>?
) {}

class PubnubLocation (
    var latitude: Double,
    var longitude:Double
) {}