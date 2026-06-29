package com.spacemishka.app.ft_81xcompanion.utils

import java.util.Locale

object MaidenheadLocator {

    /**
     * Converts Latitude and Longitude to a 6-digit Maidenhead Grid Locator (e.g. "FN31ub").
     */
    fun latLonToGrid(latitude: Double, longitude: Double): String {
        // Enforce bounds
        val lat = maxOf(-90.0, minOf(90.0, latitude))
        val lon = maxOf(-180.0, minOf(180.0, longitude))

        // Longitude offset and characters
        val lonOffset = lon + 180.0
        val lonFieldChar = ('A'.code + (lonOffset / 20.0).toInt()).toChar()
        val lonSquareDigit = ('0'.code + ((lonOffset % 20.0) / 2.0).toInt()).toChar()
        val lonSubsquareChar = ('a'.code + (((lonOffset % 20.0) % 2.0) * 12.0).toInt()).toChar()

        // Latitude offset and characters
        val latOffset = lat + 90.0
        val latFieldChar = ('A'.code + (latOffset / 10.0).toInt()).toChar()
        val latSquareDigit = ('0'.code + (latOffset % 10.0).toInt()).toChar()
        val latSubsquareChar = ('a'.code + (((latOffset % 10.0) % 1.0) * 24.0).toInt()).toChar()

        return "${lonFieldChar}${latFieldChar}${lonSquareDigit}${latSquareDigit}${lonSubsquareChar}${latSubsquareChar}"
    }

    /**
     * Converts a Maidenhead Grid Locator (4 or 6 digits) back to a Pair of (Latitude, Longitude) representing the center of the grid cell.
     * Returns null if grid format is invalid.
     */
    fun gridToLatLon(grid: String): Pair<Double, Double>? {
        val cleanGrid = grid.trim().uppercase(Locale.US)
        if (cleanGrid.length < 4 || cleanGrid.length > 6 || cleanGrid.length % 2 != 0) {
            return null
        }

        // Validate character groups
        if (!cleanGrid[0].isLetter() || !cleanGrid[1].isLetter()) return null
        if (!cleanGrid[2].isDigit() || !cleanGrid[3].isDigit()) return null
        if (cleanGrid.length == 6 && (!cleanGrid[4].isLetter() || !cleanGrid[5].isLetter())) return null

        val lonField = cleanGrid[0] - 'A'
        val latField = cleanGrid[1] - 'A'
        val lonSquare = cleanGrid[2] - '0'
        val latSquare = cleanGrid[3] - '0'

        if (lonField < 0 || lonField > 17 || latField < 0 || latField > 17) return null
        if (lonSquare < 0 || lonSquare > 9 || latSquare < 0 || latSquare > 9) return null

        var lon = (lonField * 20.0) + (lonSquare * 2.0) - 180.0
        var lat = (latField * 10.0) + latSquare - 90.0

        if (cleanGrid.length == 6) {
            val lonSub = cleanGrid[4] - 'A'
            val latSub = cleanGrid[5] - 'A'
            if (lonSub < 0 || lonSub > 23 || latSub < 0 || latSub > 23) return null
            
            lon += (lonSub * (2.0 / 24.0)) + (1.0 / 24.0)
            lat += (latSub * (1.0 / 24.0)) + (0.5 / 24.0)
        } else {
            // center of 4-digit grid
            lon += 1.0
            lat += 0.5
        }

        return Pair(lat, lon)
    }

    /**
     * Calculates the Great-Circle distance (in km) and the initial bearing (in degrees) between two grids.
     * Returns null if either grid is invalid.
     */
    fun calculateDistanceAndBearing(grid1: String, grid2: String): Pair<Double, Double>? {
        val loc1 = gridToLatLon(grid1) ?: return null
        val loc2 = gridToLatLon(grid2) ?: return null
        return calculateDistanceAndBearing(loc1.first, loc1.second, loc2.first, loc2.second)
    }

    /**
     * Calculates the Great-Circle distance (in km) and the initial bearing (in degrees) between coordinates.
     */
    fun calculateDistanceAndBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Pair<Double, Double> {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        val distance = earthRadius * c

        val y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon)
        var bearing = Math.toDegrees(Math.atan2(y, x))
        bearing = (bearing + 360.0) % 360.0

        return Pair(distance, bearing)
    }

    /**
     * Returns a human-readable cardinal direction (e.g. "NE", "WSW") for a given azimuth bearing.
     */
    fun getCardinalDirection(bearing: Double): String {
        val directions = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = (((bearing + 11.25) % 360.0) / 22.5).toInt()
        return directions[index]
    }
}
