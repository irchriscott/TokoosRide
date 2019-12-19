package com.portail.tokoosride.models

class PolylineMapRoute(
    val routes: List<Route>
){}

class Distance(
    val text: String,
    val value: Int
){}

class Duration(
    val text: String,
    val value: Int
){}

class Leg(
    val duration: Duration,
    val distance: Distance
){}

class OverviewPolyline(
    val points: String
){}

class Route(
    val legs: List<Leg>,
    val overview_polyline: OverviewPolyline
){}