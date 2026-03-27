# 🧪 AngelApp - Automatisierte Tests für Android Emulator

Dieses Projekt enthält Skripte zur Automatisierung der Unit-Test-Ausführung auf dem Android Emulator.

## 📋 Voraussetzungen

1. **Android SDK installiert** mit:
   - Android Emulator
   - Platform Tools (ADB)
   - Android API 31+ SDK

2. **ANDROID_HOME konfiguriert**:
   ```powershell
   # Prüfe, ob ANDROID_HOME gesetzt ist
   echo $env:ANDROID_HOME
   ```
   
   Falls nicht gesetzt, setze es in Windows Umgebungsvariablen:
   - Android Studio → File → Settings → Languages & Frameworks → Android SDK
   - Kopiere den SDK-Pfad
   - Systemsteuerung → Umgebungsvariablen → Neu: `ANDROID_HOME` = `C:\Users\[User]\AppData\Local\Android\Sdk`

3. **Emulator erstellt**:
   ```powershell
   # Liste verfügbare AVDs
   emulator -list-avds
   ```

## 🚀 Tests ausführen

### Option 1: Batch-Skript (einfachste Methode)
```batch
cd C:\Users\Axel\AndroidStudioProjects\AngelApp
run-tests.bat
```

### Option 2: PowerShell-Skript (mehr Kontrolle)
```powershell
cd C:\Users\Axel\AndroidStudioProjects\AngelApp
.\run-tests.ps1 -AVDName "Pixel_8_Pro" -TimeoutSeconds 300
```

### Option 3: Gradle direkt (nach manuellem Emulator-Start)
```powershell
# Emulator manuell starten:
emulator -avd Pixel_8_Pro

# Tests ausführen:
cd C:\Users\Axel\AndroidStudioProjects\AngelApp
.\gradlew.bat connectedDebugAndroidTest
```

## 📊 Was die Skripte tun

Das automat isierte Test-Skript führt folgende Schritte aus:

1. **Android SDK prüfen** → Stellt sicher ANDROID_HOME richtig ist
2. **Laufende Emulatoren prüfen** → Sucht nach online Geräten
3. **Emulator starten** (falls nötig) → Bootet den AVD im Hintergrund
4. **Auf Boot warten** → Wartet max. 5 Minuten auf vollständigen Start
5. **Tests ausführen** → Führt `./gradlew connectedDebugAndroidTest` aus

## 📁 Test-Ergebnisse

Nach erfolgreichen Tests finden Sie die Ergebnisse unter:

```
app/build/outputs/androidTest-results/
```

Öffnen Sie die `index.html` Dateien für detaillierte Berichte.

## 🧪 Verfügbare Tests

CatchyInstrumentedUnitTest.kt enthält 20 Tests:

- **Sortierung**: Tests für korrekte Sortierung nach Datum
- **JSON Serialisierung**: Tests für Fang-Serialisierung zu/von JSON
- **EXIF Datum-Parsing**: Tests für Kamera-Zeitstempel Konvertierung
- **Gezeiten-Berechnung**: Tests für Gezeitenzeiten an verschiedenen Standorten
- **Duplikat-Erkennung**: Tests für Import-Duplikat-Detection
- **Datenmodell**: Tests für Fang Copy und Standardwerte

## ⚙️ Erweiterte Optionen

### Nur einen spezifischen Test ausführen
```powershell
.\gradlew.bat connectedDebugAndroidTest --tests *faengeWerdenAbsteigendNachDatumSortiert*
```

### Tests mit Logging ausführen
```powershell
.\gradlew.bat connectedDebugAndroidTest --stacktrace --info
```

### Emulator mit zusätzlichen Optionen starten
```powershell
emulator -avd Pixel_8_Pro `
  -no-snapshot `
  -no-window `  # Ohne GUI (schneller)
  -verbose `
  -show-kernel
```

## 🔧 Problembehebung

### Problem: "ANDROID_HOME nicht gesetzt"
```powershell
# Temporärer Fix:
$env:ANDROID_HOME = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"

# Dauerhafter Fix: In Windows Umgebungsvariablen setzen
```

### Problem: "Emulator bootet nicht"
```powershell
# ADB-Probleme zurücksetzen
adb kill-server
adb start-server

# Emulator mit Snapshot löschen
emulator -avd Pixel_8_Pro -wipe-data

# Mit längeren Timeout erneut versuchen
.\run-tests.ps1 -TimeoutSeconds 600
```

### Problem: "Tests haben Fehler"
```powershell
# Aufräumen und Neuaufbau
.\gradlew.bat clean build connectedDebugAndroidTest

# Oder einzelne Tests debuggen
.\gradlew.bat connectedDebugAndroidTest --stacktrace
```

## 📈 Automatisierung in GitHub Actions

Für CI/CD Pipeline siehe: `.github/workflows/android-tests.yml`

Jeder Push führt automatisch Tests auf Android Device Cloud aus.

## 📝 Notizen

- **Erste Ausführung**: Dauert 5-10 Minuten (Emulator bootet)
- **Nachfolgende Ausführungen**: ~ 1-2 Minuten pro Test
- **Emulator-Prozess**: Bleibt nach Tests laufen (schneller Neustart)
- **Um Emulator zu stoppen**: `adb emu kill` oder Android Studio nutzen

## 🆘 Support

Für Fehler bei Tests:
1. Überprüfen Sie `app/build/outputs/androidTest-results/`
2. Führen Sie `adb logcat` parallel aus um Debug-Output zu sehen:
   ```powershell
   adb logcat -c
   adb logcat | Select-String "AngelApp|Error|Exception"
   ```

---

**Letzte Aktualisierung**: März 2026
