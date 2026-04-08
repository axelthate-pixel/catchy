# CLAUDE.md — Projektanweisungen für Claude Code

## Projekt-Überblick

**Catchy** ist eine Android-Angelbegleiter-App (auch bekannt als AngelApp) in Kotlin/Jetpack Compose.
- Package: `de.taxel.catchy` | App-ID: `de.taxel.angelapp`
- Sprache: Kotlin, UI: Jetpack Compose / Material3
- Datenhaltung: SharedPreferences + JSON-Serialisierung
- Min Android: API 24 (7.0), Target: API 35 (Android 15)

## Wichtige Build-Befehle

```bash
./gradlew build                          # Vollständiger Build
./gradlew assembleDebug                  # Debug-APK erstellen
./gradlew detekt                         # Code-Qualität prüfen (vor jedem Commit!)
./gradlew test                           # Unit-Tests (JVM, kein Gerät nötig)
./gradlew connectedDebugAndroidTest      # Instrumentierte Tests (Emulator/Gerät nötig)
./gradlew clean                          # Build-Artefakte löschen
```

**Detekt muss fehlerfrei durchlaufen** (max-issues: 0). Immer vor einem Commit prüfen.

## Architektur

- **Monolithisch:** Die gesamte App-Logik und alle UI-Screens befinden sich in `app/src/main/java/de/taxel/catchy/MainActivity.kt` (~1300 Zeilen)
- **Kein MVVM:** Kein ViewModel, kein Repository-Pattern — bewusste Entscheidung für Einfachheit
- **State:** Compose `mutableStateOf()` + `remember {}` direkt in Composables
- **Navigation:** String-basiertes Routing im Haupt-Composable `AngelApp()`

**Screens:** `liste` → `erfassung` → `bearbeiten` → `karte`

## Code-Konventionen

- **Domänenbegriffe auf Deutsch:** `Fang`, `fischart`, `wetter`, `gezeiten`, `mondphase` etc.
- **Funktionen auf Modulebene** für Business-Logik (nicht in Klassen)
- **Composables** folgen dem Muster: `FooScreen()` / `FooDialog()`
- **Logging:** `android.util.Log.e(TAG, ...)` mit der `TAG`-Konstante (`private const val TAG = "Catchy"`)
- **Exception-Handling:** Nur spezifische Exception-Typen fangen; `@Suppress("TooGenericExceptionCaught")` wenn unvermeidbar
- **Imports:** Keine Wildcard-Imports außer `androidx.compose.*` und `java.util.*`

## Detekt-Schlüsselregeln (Grenzwerte)

| Regel | Grenzwert |
|---|---|
| Zyklomatische Komplexität | 50 |
| Methodenlänge | 150 Zeilen |
| Parameter pro Funktion | 8 |
| Funktionen pro Datei | 25 |
| Verschachtelungstiefe | 6 |
| Zeilenlänge | 140 Zeichen |
| Return-Statements pro Funktion | 3 |

Verboten: `FIXME`-Kommentare, `STOPSHIP`-Kommentare.

## Teststruktur

- **Unit-Tests** (`CatchyUnitTest.kt`): Reine JVM-Tests, kein Gerät nötig
  - Datumssortierung, JSON-Serialisierung, GPX-Export, Gezeitenberechnung, Mondphase, EXIF
- **Instrumentierte Tests** (`CatchyInstrumentedTest.kt`): Benötigen Emulator/Gerät
  - EXIF-Lesen, Galerie-Import, Fang-CRUD, vollständige Workflows

## Schlüsseldateien

| Datei | Inhalt |
|---|---|
| `app/src/main/java/de/taxel/catchy/MainActivity.kt` | **Gesamte App-Logik und UI** |
| `app/build.gradle.kts` | Dependencies, SDK-Versionen, Versionsnummer |
| `config/detekt/detekt.yml` | Code-Qualitätsregeln |
| `app/src/main/assets/model.tflite` | ML-Modell für Fischerkennung |
| `.github/workflows/code-quality.yml` | CI/CD: Detekt + PR-Kommentare |
| `memory.json` | Detekt-Trendverfolgung (automatisch generiert) |

## Externe APIs & Abhängigkeiten

- **Open-Meteo** — Wetter (aktuell + historisch), native HTTP-Requests
- **osmdroid 6.1.18** — OpenStreetMap-Karten
- **Google ML Kit** — Fischartenerkennung (TFLite)
- **Coil** — Bildladebibliothek in Compose
- **Google Play Services Location** — GPS

## Was ich vermeiden soll

- Keine neuen Dateien/Klassen anlegen, solange der monolithische Ansatz funktioniert
- Kein MVVM/Repository-Refactoring ohne explizite Aufforderung
- Keine englischen Domänenbegriffe einführen (Konsequenz: deutsche Bezeichner)
- Nie `catch (e: Exception)` ohne `@Suppress`-Annotation und Log-Ausgabe
- Nicht `import org.junit.Assert.*` verwenden — explizite Imports bevorzugen
