package com.scouty.app.utils

import java.util.*
import kotlin.math.*

/**
 * A simple offline calculator for Sunrise/Sunset based on NOAA algorithms.
 */
object SolarCalculator {

    fun getSunsetTime(lat: Double, lon: Double, date: Calendar = Calendar.getInstance()): Calendar? {
        val zenit = 90.833 // Official sunset
        val day = date.get(Calendar.DAY_OF_YEAR)
        
        val lnHour = lon / 15.0
        val t = day + ((18.0 - lnHour) / 24.0)
        
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2.0 * m))) + 282.634
        l = normalizeDegrees(l)
        
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = normalizeDegrees(ra)
        
        val lQuad = floor(l / 90.0) * 90.0
        val raQuad = floor(ra / 90.0) * 90.0
        ra += (lQuad - raQuad)
        ra /= 15.0
        
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        
        val cosH = (cos(Math.toRadians(zenit)) - (sinDec * sin(Math.toRadians(lat)))) / (cosDec * cos(Math.toRadians(lat)))
        
        if (cosH > 1 || cosH < -1) return null // Sun never sets/rises at this location/date
        
        val h = Math.toDegrees(acos(cosH)) / 15.0
        val localT = h + ra - (0.06571 * t) - 6.622
        val ut = normalizeHours(localT - lnHour)
        
        val sunset = date.clone() as Calendar
        sunset.set(Calendar.HOUR_OF_DAY, 0)
        sunset.set(Calendar.MINUTE, 0)
        sunset.set(Calendar.SECOND, 0)
        sunset.add(Calendar.MINUTE, (ut * 60).toInt())
        
        // Adjust for timezone
        val tz = TimeZone.getDefault()
        sunset.add(Calendar.MILLISECOND, tz.getOffset(sunset.timeInMillis))
        
        return sunset
    }

    private fun normalizeDegrees(deg: Double): Double {
        var d = deg
        while (d < 0) d += 360.0
        while (d >= 360) d -= 360.0
        return d
    }

    private fun normalizeHours(h: Double): Double {
        var hour = h
        while (h < 0) hour += 24.0
        while (h >= 24) hour -= 24.0
        return hour
    }
}
