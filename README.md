# 🎣 Catchy - Dein digitaler Angelbegleiter

Catchy (auch bekannt als AngelApp) ist eine moderne Android-Anwendung, die Anglern dabei hilft, ihre Fänge detailliert zu dokumentieren. Die App automatisiert die Erfassung von Umweltbedingungen und nutzt KI zur Unterstützung bei der Bestimmung von Fischarten.

## 🌟 Hauptfunktionen

*   **Detailliertes Fangbuch:** Erfasse Fischart, Länge, Notizen und Fotos deiner Fänge.
*   **Automatische Wetterdaten:** Ruft bei jedem Fang automatisch aktuelle oder historische Wetterdaten (Temperatur, Windgeschwindigkeit, Luftdruck, Bewölkung) über die Open-Meteo API ab.
*   **GPS-Integration:** Speichert den genauen Fangort und zeigt diesen auf einer interaktiven Karte an.
*   **KI-Fischartenerkennung:** Nutzt Google ML Kit und ein integriertes TFLite-Modell (`model.tflite`), um Fischarten direkt auf Fotos zu erkennen.
*   **Gezeiten-Berechnung:** Berechnet offline den Gezeitenstand (Ebbe/Flut, Spring-/Niptide) basierend auf Mondphase und Standort – ideal für Küstenangler.
*   **Foto-Import & EXIF-Analyse:** Importiere Fotos aus der Galerie; die App extrahiert automatisch das Aufnahmedatum und die GPS-Koordinaten aus den EXIF-Metadaten.
*   **Daten-Backup & Export:** 
    *   **JSON-Export/Import:** Sichere deine gesamte Fangliste oder übertrage sie auf ein anderes Gerät.
    *   **GPX-Export:** Exportiere deine Fangplätze als Wegpunkte für Navigationsgeräte oder andere Karten-Apps.
*   **Interaktive Karte:** Übersicht über alle Fangspots mit OpenStreetMap (osmdroid).

## 🛠 Technischer Stack

*   **Sprache:** Kotlin
*   **UI-Framework:** Jetpack Compose (Modernes, deklaratives UI)
*   **Karten:** osmdroid (OpenStreetMap Integration)
*   **Bildverarbeitung:** Coil (Image Loading), ExifInterface
*   **Künstliche Intelligenz:** Google ML Kit Custom Image Labeling (TFLite)
*   **Location:** Google Play Services Location
*   **Networking:** native HTTP-Requests für Open-Meteo API
*   **Datenhaltung:** SharedPreferences mit JSON-Serialisierung (leichtgewichtig und schnell)

## 🚀 Installation & Setup

### Voraussetzungen
*   Android Studio Ladybug (oder neuer)
*   Android SDK (API Level 35)
*   Mindestversion: Android 7.0 (API Level 24)

### Build
1. Klone das Repository.
2. Öffne das Projekt in Android Studio.
3. Synchronisiere das Projekt mit den Gradle-Dateien.
4. Stelle sicher, dass die Datei `app/src/main/assets/model.tflite` vorhanden ist (für die KI-Erkennung).
5. Erstelle das Projekt mit `./gradlew assembleDebug`.

## 🧪 Tests
Das Projekt enthält automatisierte Tests für Kernfunktionen wie:
*   Gezeitenberechnung
*   JSON-Serialisierung/Deserialisierung
*   Deduplizierung beim Import
*   EXIF-Datenverarbeitung

Detaillierte Anweisungen zum Ausführen der Tests findest du in der [SETUP-TESTS.md](SETUP-TESTS.md).

## 📁 Projektstruktur (Auszug)
*   `app/src/main/java/de/taxel/catchy/MainActivity.kt`: Enthält die Hauptlogik, Navigation und UI-Screens.
*   `app/src/main/assets/model.tflite`: Das ML-Modell zur Fischerkennung.
*   `app/build.gradle.kts`: Projektkonfiguration und Abhängigkeiten.

## 📝 Lizenz
*Privates Projekt - Alle Rechte vorbehalten.*
