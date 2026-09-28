# Smartys CamViewer

Eine saubere, deutsche Android-App zur Überwachung und Steuerung mehrerer
IP-Kameras an einem Ort — alle Kameras in einer App, ohne das Wirrwarr
mehrerer Viewer.

## Was die App kann

- **MultiView** — alle Kameras gleichzeitig in einem Grid (1 Spalte am Handy,
  2 Spalten am Tablet)
- **SingleView** — Tipp auf eine Kachel öffnet die Kamera im Vollbild
- **PTZ-Steuerung** — Schwenken/Neigen/Zoom direkt per ONVIF (Steuerkreuz:
  halten = bewegen, loslassen = stoppen). Festpositionen werden automatisch
  von der Kamera geladen.
- **Kamera-Verwaltung** — Kameras hinzufügen, bearbeiten (Long-Press →
  Bearbeiten), löschen. Lokal auf dem Gerät gespeichert.
- **Zwei Quellen pro Kamera**
  - direkte RTSP-URL (Hikvision, Reolink, Xiongmai, ...)
  - CamViewer-Backend (für UniFi-Protect-Kameras über go2rtc — die App muss
    nie direkt mit Protect sprechen, das Backend verwaltet die Stream-Tokens)

## Technik

- Sprache: Kotlin
- Wiedergabe: Media3 / ExoPlayer (RTSP + HLS)
- PTZ: eigener ONVIF-Client (WS-Security Digest-Auth,
  ContinuousMove / GotoPreset / Stop / GetPresets)
- Speicherung: lokal (SharedPreferences / JSON via kotlinx.serialization)
- minSdk 24 (Android 7.0+), targetSdk 35

## Build

Voraussetzungen: JDK 17 (Java 25 macht Gradle kaputt), Android SDK 35.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
# APK liegt unter: app/build/outputs/apk/debug/app-debug.apk
```

## Warum diese App?

Die meisten Kamera-Viewer decken entweder nur einen Hersteller ab oder sind
mit Funktionen und unübersichtlichen Menüs überladen. Smartys CamViewer
bündelt alle relevanten Optionen — MultiView, SingleView, PTZ, gemischte
Hersteller — hinter einer ruhigen, verständlichen deutschen Oberfläche.

## Lizenz / Status

Privatprojekt. Aktuell als Debug-Build (v2-signiert) — Release-Signatur mit
eigenem Keystore folgt.
