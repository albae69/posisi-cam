package com.bae.posisicam

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bae.posisicam.geo.Geocoder
import com.bae.posisicam.geo.LocationTracker
import com.bae.posisicam.image.Snapshot
import com.bae.posisicam.image.StampText
import com.bae.posisicam.image.Stamper
import java.io.File
import java.time.ZoneId
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: TextView
    private lateinit var shutter: Button
    private lateinit var flip: Button
    private lateinit var status: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val geocoder by lazy { Geocoder(this) }
    private var locationTracker: LocationTracker? = null

    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var capturing = false

    private var fix: Location? = null
    private var address: String? = null
    private var geocodedKey: String? = null

    private val clock = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderOverlay()
            clock.postDelayed(this, 1000)
        }
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCamera() else showDenied()
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            startLocation()
        } else {
            renderOverlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (!hasLocationPermission()) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
            startLocation()
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) startLocation()
        clock.post(tick)
    }

    override fun onPause() {
        super.onPause()
        clock.removeCallbacks(tick)
        locationTracker?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        clock.removeCallbacksAndMessages(null)
        cameraExecutor.shutdown()
    }

    private fun buildUi() {
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x73000000)
            setPadding(24, 16, 24, 16)
            textSize = 13f
        }

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x73000000)
            setPadding(24, 12, 24, 12)
            gravity = Gravity.CENTER
            textSize = 14f
            visibility = TextView.GONE
        }

        shutter = Button(this).apply {
            text = "Ambil"
            setOnClickListener { takePhoto() }
        }

        flip = Button(this).apply {
            text = "Balik"
            setOnClickListener { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; bindCamera() }
        }

        val root = FrameLayout(this)
        root.addView(previewView, matchParent())
        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        ).apply { setMargins(24, 24, 24, 110) })
        root.addView(shutter, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 32 })
        root.addView(flip, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(24, 32, 24, 24) })
        root.addView(status, matchParent())
        setContentView(root)

        renderOverlay()
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun showDenied() {
        status.text = "Izin kamera dibutuhkan untuk memotret.\nAktifkan lewat Pengaturan aplikasi."
        status.visibility = TextView.VISIBLE
        shutter.isEnabled = false
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            bindCamera()
            if (!future.get().hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                flip.isEnabled = false
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    bindCamera()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        if (!future.isDone) return
        val provider = future.get()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, capture)
        imageCapture = capture
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!hasLocationPermission()) {
            renderOverlay()
            return
        }
        if (locationTracker == null) {
            locationTracker = LocationTracker(this) { location ->
                fix = location
                maybeGeocode(location)
                renderOverlay()
            }
        }
        locationTracker?.start()
        renderOverlay()
    }

    private fun maybeGeocode(location: Location) {
        val key = StampText.cacheKey(location.latitude, location.longitude)
        if (key == geocodedKey) return
        geocodedKey = key
        address = null
        geocoder.lookup(location.latitude, location.longitude) { result ->
            address = result
            renderOverlay()
        }
    }

    private fun renderOverlay() {
        if (!::overlay.isInitialized) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val lines = buildList {
            add(StampText.coordinate(fix?.latitude, fix?.longitude))
            if (fix != null && address == null) add("Memuat alamat…")
            address?.let { add(it) }
            add(StampText.time(now, zone))
        }
        overlay.text = lines.joinToString("\n")
    }

    private fun takePhoto() {
        if (capturing) return
        val capture = imageCapture ?: return
        capturing = true
        shutter.isEnabled = false
        val snapshot = Snapshot(
            lat = fix?.latitude,
            lon = fix?.longitude,
            address = address,
            takenAt = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        )
        val file = File.createTempFile("shot_", ".jpg", cacheDir)
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runCatching { Stamper.stampAndSave(this@MainActivity, file, snapshot) }
                        .onSuccess { toast("Tersimpan ke galeri") }
                        .onFailure { toast("Gagal menyimpan foto") }
                    file.delete()
                    done()
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    toast("Gagal memotret")
                    done()
                }
            }
        )
    }

    private fun done() {
        runOnUiThread {
            capturing = false
            shutter.isEnabled = true
        }
    }

    private fun toast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}
