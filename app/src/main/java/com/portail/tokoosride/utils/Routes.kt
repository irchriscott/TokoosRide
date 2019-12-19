package com.portail.tokoosride.utils

class Routes {

    val END_POINT = "http://192.168.43.138:8009/api/v1/"

    val registerPhoneNumber = "${END_POINT}user/register/phone-number"
    val authPhoneNumber = "${END_POINT}user/auth/phone-number"
    val registerIdentity = "${END_POINT}user/register/identity"

    val faresRequest = "${END_POINT}ride/request/fares"
    val ridersRequest = "${END_POINT}ride/request/riders"
    val rideInitiate = "${END_POINT}ride/request/initiate"
}