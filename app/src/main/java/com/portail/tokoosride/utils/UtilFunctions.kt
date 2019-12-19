package com.portail.tokoosride.utils

import android.util.Log
import com.portail.tokoosride.models.Distance
import com.portail.tokoosride.models.Duration
import com.portail.tokoosride.models.VehicleType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.roundToInt

class UtilFunctions {

    public fun decodePoly(encoded: String): List<LatLng> {

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5,
                lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }

    public fun calculatePriceTransport(distance: Distance, duration: Duration, vehicleType: VehicleType, ratio: Double) : Int {
        val totalDuration = (duration.value / 60) * vehicleType.fare.time
        val totalDistance = (distance.value / 1000) * vehicleType.fare.distance
        val fare = (vehicleType.fare.baseFare + totalDuration + totalDistance) * ratio
        return fare.roundToInt()
    }

    public fun calculateDistance(from: LatLng, to: LatLng) : Double {
        return "%.2f".format((SphericalUtil.computeDistanceBetween(from, to) / 1000)).toDouble()
    }
}