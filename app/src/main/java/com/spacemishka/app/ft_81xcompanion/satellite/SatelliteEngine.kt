package com.spacemishka.app.ft_81xcompanion.satellite

import android.location.Location
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

data class Tle(
    val name: String,
    val line1: String,
    val line2: String
)

data class SatellitePassState(
    val satelliteName: String,
    val azimuth: Double,   // degrees (0-360)
    val elevation: Double, // degrees (-90 to 90)
    val rangeKm: Double,
    val rangeRateMS: Double, // positive = moving away, negative = approaching
    val downlinkDopplerHz: Long,
    val uplinkDopplerHz: Long,
    val isVisible: Boolean
)

class SatelliteEngine {

    companion object {
        private const val EARTH_RADIUS_M = 6378137.0
        private const val MU = 3.986004418e14 // Earth gravitational parameter (m^3/s^2)
        private const val EARTH_ROTATION_SPEED = 7.29211510e-5 // rad/s
        private const val C = 299792458.0 // Speed of light (m/s)
        private const val MINUTES_PER_DAY = 1440.0
        private const val SECONDS_PER_DAY = 86400.0
    }

    data class Vector3D(val x: Double, val y: Double, val z: Double) {
        operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
        operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
        operator fun times(scalar: Double) = Vector3D(x * scalar, y * scalar, z * scalar)
        fun dot(other: Vector3D) = x * other.x + y * other.y + z * other.z
        fun length() = sqrt(x * x + y * y + z * z)
        fun normalize(): Vector3D {
            val len = length()
            return if (len > 0) Vector3D(x / len, y / len, z / len) else this
        }
    }

    /**
     * Parses a standard TLE and extracts orbital elements.
     */
    class Orbit(val tle: Tle) {
        val name = tle.name
        
        // Parsed elements
        val epochYear: Int
        val epochDay: Double
        val inclinationRad: Double
        val raanRad: Double
        val eccentricity: Double
        val argPerigeeRad: Double
        val meanAnomalyRad: Double
        val meanMotionRadPerSec: Double
        val semiMajorAxisM: Double
        
        init {
            val l1 = tle.line1
            val l2 = tle.line2
            
            // Parse Epoch Year (columns 19-20) and Epoch Day (columns 21-32)
            val yr = l1.substring(18, 20).trim().toInt()
            epochYear = if (yr < 57) 2000 + yr else 1900 + yr
            epochDay = l1.substring(20, 32).toDouble()
            
            // Parse Line 2
            inclinationRad = l2.substring(8, 16).trim().toDouble() * PI / 180.0
            raanRad = l2.substring(17, 25).trim().toDouble() * PI / 180.0
            eccentricity = ("0." + l2.substring(26, 33).trim()).toDouble()
            argPerigeeRad = l2.substring(34, 42).trim().toDouble() * PI / 180.0
            meanAnomalyRad = l2.substring(43, 51).trim().toDouble() * PI / 180.0
            
            // Mean Motion (revolutions per day, columns 53-63)
            val revsPerDay = l2.substring(52, 63).trim().toDouble()
            meanMotionRadPerSec = (revsPerDay * 2.0 * PI) / SECONDS_PER_DAY
            
            // semi-major axis a = (mu / n^2)^(1/3)
            semiMajorAxisM = Math.cbrt(MU / (meanMotionRadPerSec * meanMotionRadPerSec))
        }

        /**
         * Propagates orbit to [minutesSinceEpoch] and returns position and velocity vectors in ECI (TEME).
         */
        fun propagate(minutesSinceEpoch: Double): Pair<Vector3D, Vector3D> {
            val tSec = minutesSinceEpoch * 60.0
            
            // Mean anomaly at t
            val M = (meanAnomalyRad + meanMotionRadPerSec * tSec) % (2 * PI)
            
            // Solve Kepler's Equation M = E - e sin E for E (Eccentric Anomaly)
            var E = M
            for (i in 0..10) {
                val diff = E - eccentricity * sin(E) - M
                E -= diff / (1.0 - eccentricity * cos(E))
            }
            
            // Coordinates in orbital plane
            val cosE = cos(E)
            val sinE = sin(E)
            val xOrb = semiMajorAxisM * (cosE - eccentricity)
            val yOrb = semiMajorAxisM * sqrt(1.0 - eccentricity * eccentricity) * sinE
            
            // Velocities in orbital plane
            val r = semiMajorAxisM * (1.0 - eccentricity * cosE)
            val vFactor = sqrt(MU * semiMajorAxisM) / r
            val vxOrb = -vFactor * sinE
            val vyOrb = vFactor * sqrt(1.0 - eccentricity * eccentricity) * cosE
            
            // Rotate from orbital plane to ECI
            val cosW = cos(argPerigeeRad)
            val sinW = sin(argPerigeeRad)
            val cosI = cos(inclinationRad)
            val sinI = sin(inclinationRad)
            val cosO = cos(raanRad)
            val sinO = sin(raanRad)
            
            // Rotation matrix elements (Orbital -> ECI)
            val Px = cosW * cosO - sinW * sinO * cosI
            val Py = cosW * sinO + sinW * cosO * cosI
            val Pz = sinW * sinI
            
            val Qx = -sinW * cosO - cosW * sinO * cosI
            val Qy = -sinW * sinO + cosW * cosO * cosI
            val Qz = cosW * sinI
            
            val posECI = Vector3D(
                xOrb * Px + yOrb * Qx,
                xOrb * Py + yOrb * Qy,
                xOrb * Pz + yOrb * Qz
            )
            
            val velECI = Vector3D(
                vxOrb * Px + vyOrb * Qx,
                vxOrb * Py + vyOrb * Qy,
                vxOrb * Pz + vyOrb * Qz
            )
            
            return Pair(posECI, velECI)
        }
    }

    /**
     * Calculates the greenwich sidereal time in radians for the given calendar.
     */
    private fun calculateGst(cal: Calendar): Double {
        val utcTime = cal.timeInMillis
        // JD at J2000 epoch
        val julianDate = 2440587.5 + (utcTime / 86400000.0)
        val tut1 = (julianDate - 2451545.0) / 36525.0
        
        // Greenwich Mean Sidereal Time in seconds
        var gmst = 24110.54841 + 8640184.812866 * tut1 + 0.093104 * tut1 * tut1 - 6.2e-6 * tut1 * tut1 * tut1
        gmst = (gmst % 86400.0)
        if (gmst < 0) gmst += 86400.0
        
        // Convert to radians
        return (gmst / 86400.0) * 2.0 * PI
    }

    /**
     * Converts a calendar time to Julian Days.
     */
    private fun calendarToJulianDays(cal: Calendar): Double {
        val utcTime = cal.timeInMillis
        return 2440587.5 + (utcTime / 86400000.0)
    }

    /**
     * Converts TLE epoch year and day fraction to Julian Days.
     */
    private fun tleEpochToJulianDays(year: Int, dayFraction: Double): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.DAY_OF_YEAR, 1)
        }
        val baseJd = 2440587.5 + (cal.timeInMillis / 86400000.0)
        return baseJd + (dayFraction - 1.0)
    }

    /**
     * Calculates the observer's ECEF coordinate vector.
     */
    private fun getObserverEcef(latDeg: Double, lonDeg: Double, altMeters: Double): Vector3D {
        val latRad = latDeg * PI / 180.0
        val lonRad = lonDeg * PI / 180.0
        
        // Simplified spherical model (ellipsoid is cleaner, but spherical is highly accurate for Doppler)
        val r = EARTH_RADIUS_M + altMeters
        val x = r * cos(latRad) * cos(lonRad)
        val y = r * cos(latRad) * sin(lonRad)
        val z = r * sin(latRad)
        return Vector3D(x, y, z)
    }

    /**
     * Main calculation function that takes a TLE, observer coordinates, and targets, and outputs the tracking state.
     */
    fun calculatePassState(
        tle: Tle,
        observerLat: Double,
        observerLon: Double,
        observerAltMeters: Double,
        downlinkFreqHz: Long,
        uplinkFreqHz: Long,
        time: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    ): SatellitePassState {
        val orbit = Orbit(tle)
        
        // 1. Calculate time since epoch in days
        val currentJd = calendarToJulianDays(time)
        val epochJd = tleEpochToJulianDays(orbit.epochYear, orbit.epochDay)
        val daysSinceEpoch = currentJd - epochJd
        val minsSinceEpoch = daysSinceEpoch * MINUTES_PER_DAY
        
        // 2. Propagate orbit in ECI coordinates
        val (posECI, velECI) = orbit.propagate(minsSinceEpoch)
        
        // 3. Convert ECI to ECEF
        val gst = calculateGst(time)
        val cosGst = cos(gst)
        val sinGst = sin(gst)
        
        val posECEF = Vector3D(
            posECI.x * cosGst + posECI.y * sinGst,
            -posECI.x * sinGst + posECI.y * cosGst,
            posECI.z
        )
        
        // Velocity ECEF accounts for frame rotation velocity: V_ecef = V_eci - omega x R
        val velECEF = Vector3D(
            velECI.x * cosGst + velECI.y * sinGst + EARTH_ROTATION_SPEED * posECEF.y,
            -velECI.x * sinGst + velECI.y * cosGst - EARTH_ROTATION_SPEED * posECEF.x,
            velECI.z
        )
        
        // 4. Observer position in ECEF
        val obsECEF = getObserverEcef(observerLat, observerLon, observerAltMeters)
        
        // 5. Relative vector from observer to satellite
        val relECEF = posECEF - obsECEF
        val distance = relECEF.length()
        
        // 6. Calculate Topocentric ENU (East, North, Up) coordinates
        val latRad = observerLat * PI / 180.0
        val lonRad = observerLon * PI / 180.0
        
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinLon = sin(lonRad)
        val cosLon = cos(lonRad)
        
        // Rotation ECEF -> ENU
        val e = -sinLon * relECEF.x + cosLon * relECEF.y
        val n = -sinLat * cosLon * relECEF.x - sinLat * sinLon * relECEF.y + cosLat * relECEF.z
        val u = cosLat * cosLon * relECEF.x + cosLat * sinLon * relECEF.y + sinLat * relECEF.z
        
        // 7. Calculate Azimuth and Elevation
        var az = atan2(e, n) * 180.0 / PI
        if (az < 0) az += 360.0
        
        val el = atan2(u, sqrt(e * e + n * n)) * 180.0 / PI
        
        // 8. Calculate Range Rate (relative speed along the line of sight)
        // rangeRate = (relECEF dot velECEF) / distance
        // Note: we assume observer is stationary in ECEF, so relative velocity is velECEF
        val rangeRate = relECEF.dot(velECEF) / distance
        
        // 9. Calculate Doppler Shift
        // Downlink Doppler: fd = f_0 * (-rangeRate / C)
        // Uplink Doppler: fu = f_0 * (rangeRate / C)
        // Approaching (rangeRate < 0) -> Downlink shifted higher (+), Uplink needs to be shifted lower (-)
        val downlinkDoppler = (-downlinkFreqHz * (rangeRate / C)).toLong()
        val uplinkDoppler = (uplinkFreqHz * (rangeRate / C)).toLong()
        
        return SatellitePassState(
            satelliteName = tle.name,
            azimuth = az,
            elevation = el,
            rangeKm = distance / 1000.0,
            rangeRateMS = rangeRate,
            downlinkDopplerHz = downlinkDoppler,
            uplinkDopplerHz = uplinkDoppler,
            isVisible = el > 0.0
        )
    }

    /**
     * Formats location to standard double format.
     */
    fun getLocation(loc: Location?): Pair<Double, Double> {
        return if (loc != null) {
            Pair(loc.latitude, loc.longitude)
        } else {
            Pair(0.0, 0.0)
        }
    }
}
