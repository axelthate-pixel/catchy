# Tests ausführen

## Unit-Tests (kein Gerät nötig)

Unit-Tests laufen direkt auf der JVM und benötigen weder Emulator noch physisches Gerät:

```bash
./gradlew test
```

Ergebnisse: `app/build/reports/tests/testDebugUnitTest/index.html`

## Instrumentierte Tests (Emulator oder Gerät nötig)

### Voraussetzungen

1. Android Studio installiert
2. Emulator eingerichtet: **Android Studio → Tools → Device Manager → Create Device**
   - Empfehlung: Pixel 8 Pro, Android API 35
3. Emulator gestartet (oder physisches Gerät per USB verbunden)

### Tests starten

```bash
./gradlew connectedDebugAndroidTest
```

Ergebnisse: `app/build/outputs/androidTest-results/`

### Einzelnen Test ausführen

```bash
./gradlew connectedDebugAndroidTest --tests "*faengeWerdenAbsteigendNachDatumSortiert*"
```

## Alle Qualitätsprüfungen auf einmal

```bash
./gradlew detekt test
```

## Was wird getestet?

**Unit-Tests** (`CatchyUnitTest.kt`) — laufen ohne Gerät:
- Datumssortierung von Fängen
- JSON-Serialisierung und -Deserialisierung
- GPX-Export
- Gezeitenberechnung
- Mondphase
- EXIF-Datumsformatierung

**Instrumentierte Tests** (`CatchyInstrumentedTest.kt`) — benötigen Gerät/Emulator:
- EXIF-Metadaten lesen (Datum, GPS, Zeitzone)
- Galerie-Import (einzeln und mehrfach)
- Fang speichern, laden, bearbeiten, löschen
- Vollständiger Import-Workflow
