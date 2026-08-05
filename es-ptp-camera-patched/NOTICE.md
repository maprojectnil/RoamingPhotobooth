# NOTICE

Module ini adalah vendor lokal (bukan fork terpisah di GitHub) dari:

- **es-ptp-camera** — https://github.com/ReemMousaES/es-ptp-camera (tag `v1.0.3`)
  - Fork dari **RemoteYourCam USB** oleh Nils Assbeck, Guersel Ayaz, Michael Zoech
    (https://github.com/michaelzoech/remoteyourcam-usb)

Lisensi asli: Apache License 2.0 — lihat [LICENSE](LICENSE).

## Perubahan dari upstream (v1.0.3)

1. `EosGetLiveViewPictureCommand.java` — tambah `options.inMutable = true`
   sebelum `BitmapFactory.decodeByteArray(...)`, supaya reuse bitmap live view
   (parameter `inBitmap`) yang sudah diniatkan library ini benar-benar
   berfungsi, alih-alih selalu gagal diam-diam dan fallback alokasi baru tiap
   frame (lihat warning logcat "Unable to reuse an immutable bitmap as an
   image decoder target").
2. `NikonGetLiveViewImageCommand.java` — perbaikan yang sama untuk jalur live
   view Nikon.

Cari komentar `// PATCH (RoamingPhotobooth):` di kedua file untuk lokasi
persis perubahan.
