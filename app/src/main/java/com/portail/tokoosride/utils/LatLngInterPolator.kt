package com.portail.tokoosride.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.sign

public interface LatLngInterpolator {

    fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng

    class LinearFixed : LatLngInterpolator {
        override fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
            val lat = (b.latitude - a.latitude) * fraction + a.latitude
            var lngDelta = b.longitude - a.longitude
            if (abs(lngDelta) > 180) {
                lngDelta -= sign(lngDelta) * 360
            }
            val lng = lngDelta * fraction + a.longitude
            return LatLng(lat, lng)
        }
    }
}