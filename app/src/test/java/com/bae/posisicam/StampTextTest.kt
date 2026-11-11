package com.bae.posisicam

import com.bae.posisicam.image.StampText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StampTextTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val takenAt = Instant.parse("2026-11-11T07:32:00Z").toEpochMilli()

    @Test
    fun `no fix shows fallback text`() {
        assertEquals("Lokasi tidak tersedia", StampText.coordinate(null, null))
    }

    @Test
    fun `coordinate keeps six decimals`() {
        assertEquals("-6.200100, 106.816600", StampText.coordinate(-6.2001, 106.8166))
    }

    @Test
    fun `time uses local zone in indonesian`() {
        assertEquals("11 Nov 2026 14:32 WIB", StampText.time(takenAt, zone))
    }

    @Test
    fun `exif time format`() {
        assertEquals("2026:11:11 14:32:00", StampText.exifTime(takenAt, zone))
    }

    @Test
    fun `cache key rounds to four decimals`() {
        assertEquals("-6.2001,106.8166", StampText.cacheKey(-6.200056, 106.816623))
    }

    @Test
    fun `long text wraps to two lines with ellipsis`() {
        val measure = { s: String -> s.length * 10f }
        val lines = StampText.wrap(
            "Jalan Contoh Raya Nomor Satu Jakarta Pusat Provinsi DKI Jakarta",
            maxWidth = 100f,
            measure = measure,
            maxLines = 2,
        )
        assertEquals(2, lines.size)
        assertTrue(lines.last().endsWith("…"))
        lines.forEach { assertTrue(measure(it) <= 100f) }
    }

    @Test
    fun `short text stays on one line`() {
        val lines = StampText.wrap("Jakarta Pusat", 500f, { it.length * 10f })
        assertEquals(listOf("Jakarta Pusat"), lines)
    }
}
