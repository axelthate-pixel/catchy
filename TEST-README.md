# Tests — Technische Übersicht

## Teststruktur

| Datei | Typ | Gerät nötig? |
|---|---|---|
| `CatchyUnitTest.kt` | Unit-Tests (JVM) | Nein |
| `CatchyInstrumentedTest.kt` | Instrumentierte Tests | Ja |

## CI/CD

Unit-Tests und Detekt laufen automatisch bei jedem Push via GitHub Actions (`.github/workflows/code-quality.yml`).

Instrumentierte Tests erfordern ein Gerät und müssen lokal ausgeführt werden.

## Gradle-Befehle

```bash
./gradlew test                        # Unit-Tests
./gradlew detekt                      # Code-Qualität
./gradlew detekt test                 # Beides zusammen
./gradlew connectedDebugAndroidTest   # Instrumentierte Tests (Gerät/Emulator)
./gradlew clean                       # Build-Artefakte löschen
```

## Testberichte

Nach dem Ausführen:

```
app/build/reports/tests/testDebugUnitTest/index.html   # Unit-Tests
app/build/outputs/androidTest-results/                 # Instrumentierte Tests
build/reports/detekt/detekt.html                       # Detekt
```
