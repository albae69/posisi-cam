package com.bae.posisicam.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Fix lokasi terakhir. Pemanggil wajib sudah memegang izin lokasi. */
class LocationTracker(context: Context, private val onFix: (Location) -> Unit) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var running = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(onFix)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        client.getLastLocation().addOnSuccessListener { location -> if (location != null) onFix(location) }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        running = false
        client.removeLocationUpdates(callback)
    }
}
