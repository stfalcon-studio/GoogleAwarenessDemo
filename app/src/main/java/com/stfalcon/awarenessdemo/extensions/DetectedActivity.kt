package com.stfalcon.awarenessdemo.extensions

import com.google.android.gms.location.DetectedActivity

fun DetectedActivity.stateString(): String {
    return when (type) {
        0 -> "IN_VEHICLE"
        1 -> "ON_BICYCLE"
        2 -> "ON_FOOT"
        3 -> "STILL"
        4 -> "UNKNOWN"
        5 -> "TILTING"
        6, 9, 10, 11, 12, 13, 14, 15 -> type.toString()
        7 -> "WALKING"
        8 -> "RUNNING"
        16 -> "IN_ROAD_VEHICLE"
        17 -> "IN_RAIL_VEHICLE"
        18 -> "IN_TWO_WHEELER_VEHICLE"
        19 -> "IN_FOUR_WHEELER_VEHICLE"
        else -> type.toString()
    }
}