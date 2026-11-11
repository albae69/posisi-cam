# Posisi Cam

APK kamera Android yang membakar lokasi dan waktu ke pojok kiri bawah foto:
koordinat, alamat, dan tanggal/jam. Data yang sama juga ditulis ke EXIF.

Contoh stempel:

```
-6.200100, 106.816600
Jakarta Pusat, Jakarta
11 Nov 2026 14:32 WIB
```

## Fitur

- Kamera belakang & depan (tombol **Balik**).
- Overlay live di preview — hasil foto sama dengan yang terlihat.
- Reverse geocoding gratis via [BigDataCloud](https://www.bigdatacloud.net/) (tanpa API key), bahasa Indonesia, cache per ~11 m.
- EXIF `GPSLatitude`/`GPSLongitude` dan `DateTimeOriginal` ditulis ke file.
- Simpan ke galeri: `Pictures/posisi-cam` (MediaStore, tanpa izin storage).
- Tetap bisa memotret saat GPS/lokasi tidak tersedia — stempel jadi `Lokasi tidak tersedia`.

## Syarat

- JDK 17+
- Android SDK 35 (`compileSdk 35`, `minSdk 29`)
- Perangkat/emulator Android 10+

## Build & jalankan

```bash
./gradlew testDebugUnitTest    # unit test format & word-wrap
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # pasang ke device/emulator yang tersambung
```

`local.properties` berisi `sdk.dir` dan tidak ikut di-commit.

## Cara kerja

1. `LocationTracker` (FusedLocationProvider) minta fix akurasi tinggi tiap 5 detik.
2. `Geocoder` reverse-geocode koordinat → alamat, disimpan di SharedPreferences.
3. Saat shutter: `ImageCapture` simpan JPEG sementara + snapshot `lat/lon/alamat/waktu`.
4. `Stamper` decode (sisi panjang ≤ 3000 px), betulkan rotasi EXIF, gambar kotak hitam 45% + teks, tulis EXIF GPS & waktu, simpan ke MediaStore.

## Struktur

```
app/src/main/java/com/bae/posisicam/
  MainActivity.kt            # layout, preview, overlay, shutter, ganti kamera
  geo/LocationTracker.kt     # fix lokasi
  geo/Geocoder.kt            # BigDataCloud + cache
  image/StampText.kt         # format koordinat/waktu + word-wrap (pure, teruji)
  image/Stamper.kt           # rotate → stempel → EXIF → galeri
app/src/test/java/com/bae/posisicam/StampTextTest.kt
```

## Batasan

- Alamat gratis BigDataCloud hanya sampai level kecamatan/kota, bukan nomor rumah.
- Foto hasil stempel di-cap 3000 px sisi panjang (hemat RAM).
- Belum ada zoom, flash, tap-to-focus, atau timer.
- Cache alamat tanpa batas ukuran (entri kecil; tambah pruning kalau perlu).

## Privasi

- Koordinat dikirim ke `api.bigdatacloud.net` untuk memperoleh alamat.
- Foto yang tersimpan mengandung koordinat GPS. Hati-hati saat membagikan foto.
