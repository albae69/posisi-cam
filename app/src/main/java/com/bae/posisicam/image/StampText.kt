package com.bae.posisicam.image

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure formatting/text-layout helpers so they can be unit tested without Android. */
object StampText {
    private val timeFormat = DateTimeFormatter
        .ofPattern("dd MMM yyyy HH:mm z", Locale.forLanguageTag("id-ID"))

    fun coordinate(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "Lokasi tidak tersedia"
        else String.format(Locale.US, "%.6f, %.6f", lat, lon)

    fun time(millis: Long, zone: ZoneId): String =
        timeFormat.withZone(zone).format(Instant.ofEpochMilli(millis))

    fun exifTime(millis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
            .withZone(zone)
            .format(Instant.ofEpochMilli(millis))

    /** Key cache alamat: ~11 m per 0.0001 derajat. */
    fun cacheKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.4f,%.4f", lat, lon)

    /**
     * Bungkus teks ke lebar tertentu. [measure] mengukur lebar render (Paint.measureText).
     * Baris terakhir dipotong dengan "…" kalau masih ada sisa kata.
     */
    fun wrap(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
        maxLines: Int = 2,
    ): List<String> {
        if (text.isBlank() || maxLines <= 0) return emptyList()
        val words = text.split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var line = words[i]
            i++
            while (i < words.size && measure("$line ${words[i]}") <= maxWidth) {
                line = "$line ${words[i]}"
                i++
            }
            lines += ellipsize(line, maxWidth, measure)
        }
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines).toMutableList()
        kept[maxLines - 1] = ellipsize(kept[maxLines - 1] + " …", maxWidth, measure)
        return kept
    }

    private fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        var out = text
        while (out.isNotEmpty() && measure("$out…") > maxWidth) out = out.dropLast(1)
        return "$out…"
    }
}
