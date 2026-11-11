package com.bae.posisicam.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.ZoneId

/** Data yang dibekukan tepat saat shutter ditekan. */
data class Snapshot(
    val lat: Double?,
    val lon: Double?,
    val address: String?,
    val takenAt: Long,
    val zone: ZoneId,
)

/**
 * Bakar stempel (koordinat/alamat/waktu) ke pojok kiri bawah, tulis EXIF GPS +
 * DateTimeOriginal, lalu simpan ke galeri (Pictures/posisi-cam).
 */
object Stamper {

    private const val MAX_LONG_SIDE = 3000

    fun stampAndSave(context: Context, src: File, snap: Snapshot): Uri {
        val bitmap = decodeScaled(src) ?: error("Gagal membaca gambar")
        val rotated = applyOrientation(bitmap, ExifInterface(src).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        ))
        drawStamp(rotated, snap)

        val tmp = File.createTempFile("stamped_", ".jpg", context.cacheDir)
        try {
            tmp.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()

            ExifInterface(tmp.absolutePath).apply {
                if (snap.lat != null && snap.lon != null) setLatLong(snap.lat, snap.lon)
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, StampText.exifTime(snap.takenAt, snap.zone))
                saveAttributes()
            }
            return saveToGallery(context, tmp, snap.takenAt)
        } finally {
            tmp.delete()
        }
    }

    private fun decodeScaled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_LONG_SIDE) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun drawStamp(bitmap: Bitmap, snap: Snapshot) {
        val w = bitmap.width
        val h = bitmap.height
        val textSize = maxOf(w * 0.026f, 12f)
        val pad = textSize * 0.6f
        val margin = w * 0.02f
        val maxTextWidth = w - margin * 2 - pad * 2

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val measure = { s: String -> paint.measureText(s) }
        val addressLines = StampText.wrap(snap.address ?: "", maxTextWidth, measure, maxLines = 2)
        val lines = buildList {
            add(StampText.coordinate(snap.lat, snap.lon))
            addAll(addressLines)
            add(StampText.time(snap.takenAt, snap.zone))
        }

        val lineHeight = textSize * 1.3f
        val blockHeight = lines.size * lineHeight + pad * 2
        val blockWidth = lines.maxOf { paint.measureText(it) } + pad * 2
        val left = margin
        val bottom = h - margin

        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000 }
        canvas.drawRoundRect(
            RectF(left, bottom - blockHeight, left + blockWidth, bottom),
            textSize * 0.5f, textSize * 0.5f, bg
        )

        paint.textAlign = Paint.Align.LEFT
        var baseline = bottom - blockHeight + pad + textSize
        for (line in lines) {
            canvas.drawText(line, left + pad, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun saveToGallery(context: Context, file: File, takenAt: Long): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "posisicam_$takenAt.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/posisi-cam")
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert gagal")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("MediaStore stream gagal")
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
