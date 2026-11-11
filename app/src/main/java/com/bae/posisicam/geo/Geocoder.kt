package com.bae.posisicam.geo

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bae.posisicam.image.StampText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Reverse geocoding via BigDataCloud (tanpa API key, level locality/kota).
 * Cache permanen di SharedPreferences per ~11 m. ponytail: tanpa batas ukuran;
 * tambah pruning kalau entri > beberapa ribu.
 */
class Geocoder(context: Context) {

    private val prefs = context.getSharedPreferences("geocode", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val inFlight = HashSet<String>()

    fun lookup(lat: Double, lon: Double, onResult: (String?) -> Unit) {
        val key = StampText.cacheKey(lat, lon)
        prefs.getString(key, null)?.let {
            onResult(it)
            return
        }
        synchronized(inFlight) {
            if (!inFlight.add(key)) return
        }
        io.execute {
            val address = runCatching { fetch(lat, lon) }.getOrNull()
            if (address != null) prefs.edit().putString(key, address).apply()
            synchronized(inFlight) { inFlight.remove(key) }
            main.post { onResult(address) }
        }
    }

    private fun fetch(lat: Double, lon: Double): String? {
        val url = URL(
            "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                "?latitude=$lat&longitude=$lon&localityLanguage=id"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val country = o.optString("countryName")
            val admin = o.optJSONObject("localityInfo")?.optJSONArray("administrative")
            val names = buildList {
                for (i in 0 until (admin?.length() ?: 0)) {
                    admin?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }.distinct().filter { it != country }
            if (names.size >= 2) return names.takeLast(2).reversed().joinToString(", ")
            if (names.isNotEmpty()) return names.last()
            val locality = o.optString("locality").ifBlank { o.optString("city") }
            val subdivision = o.optString("principalSubdivision")
            val parts = listOf(locality, subdivision).filter { it.isNotBlank() }
            return (if (parts.isNotEmpty()) parts.joinToString(", ") else country).ifBlank { null }
        } finally {
            conn.disconnect()
        }
    }
}
