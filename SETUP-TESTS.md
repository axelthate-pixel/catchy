# 🔧 AngelApp - Automatisierte Tests - SETUP-ANLEITUNG

## ⚠️ Erste Schritte - Umgebungsvariablen konfigurieren

Die automatisierten Tests benötigen zwei Umgebungsvariablen. Folgen Sie diesen Schritten:

### Schritt 1: Android SDK Location finden

1. Öffne **Android Studio**
2. Gehe zu: **File** → **Settings** (oder **Preferences** auf Mac)
3. Navigiere zu: **Languages & Frameworks** → **Android SDK**
4. Kopiere den **SDK Location** Pfad
   - Beispiel: `C:\Users\Axel\AppData\Local\Android\Sdk`

### Schritt 2: Umgebungsvariablen in PowerShell setzen

Öffne PowerShell und führe diese Befehle aus (ersetze die Pfade mit deinen Pfaden):

```powershell
# Ersetze mit deinem SDK Pfad!
$env:ANDROID_HOME = "C:\Users\Axel\AppData\Local\Android\Sdk"

# Teste, ob es funktioniert
echo $env:ANDROID_HOME
```

**ODER** - Windows Umgebungsvariablen dauerhaft setzen (empfohlen):

1. Drücke **Windows + X** und wähle **System**
2. Klicke auf **Erweiterte Systemeinstellungen** (oder suche "Umgebungsvariablen")
3. Klicke auf **Umgebungsvariablen** Button unten rechts
4. Unter "Benutzervariablen für Axel" klicke **Neu**
5. Variable name: `ANDROID_HOME`
6. Variable Wert: `C:\Users\Axel\AppData\Local\Android\Sdk` (dein echter Pfad!)
7. Klicke **OK** → **OK** → **OK**
8. **PowerShell neu starten** (schließen und öffnen)

### Schritt 3: Emulator prüfen

Stelle sicher dass du einen Emulator hast:

```powershell
# In Android Studio: Tools → Device Manager
# oder in PowerShell:
emulator -list-avds
```

Wenn keine AVD existiert:
- Öffne Android Studio
- **Tools** → **Device Manager**
- **Create Device** → Wähle **Pixel 8 Pro** → Wähle **Android 14 (oder neueste API)**
- Name: `Pixel_8_Pro` (default)
- Fertig

### Schritt 4: Tests starten

Wenn alles konfiguriert ist:

```powershell
cd C:\Users\Axel\AndroidStudioProjects\AngelApp
.\run-tests.bat
```

Oder mit PowerShell direkt:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\run-tests.ps1
```

---

## 🧪 Was wird getestet?

Die automatisierten Tests prüfen:

✅ **Sortierung** - Fänge nach Datum sortieren  
✅ **JSON Serialisierung** - Speicherung und Laden  
✅ **Gezeiten-Berechnung** - Korrekte Gezeitentabelle  
✅ **EXIF Datum** - Kamera-Zeitstempel richtig konvertieren  
✅ **Duplikat-Erkennung** - Import deduplizieren  

**Insgesamt 20 automatisierte Tests**

---

## ✅ Status prüfen

### Test-Session automatisch laufen lassen:
```powershell
.\run-tests.bat
```

### Nur Statusabfrage ohne Tests zu starten:
```powershell
adb devices
```

### Ergebnisse anschauen nach Test-Lauf:
```powershell
explorer "app\build\outputs\androidTest-results"
```

---

## 🆘 Häufige Probleme

### ❌ "ANDROID_HOME nicht gesetzt"
- Befolge Schritt 2 oben (Umgebungsvariable setzen)
- **PowerShell NEU STARTEN** nach dem Setzen!

### ❌ "Emulator bootet nicht"
```powershell
# Versuche Emulator mit Clean boots
emulator -avd Pixel_8_Pro -wipe-data
```

### ❌ "ADB nicht gefunden"
- Stelle sicher dass `Platform Tools` in Android Studio installiert sind
- Gehe zu: **SDK Manager** → **SDK Tools** → ☑️ **Android SDK Platform-Tools**

### ❌ "JAVA_HOME nicht gesetzt"
- Das Skript versucht es automatisch
- Falls es nicht funktioniert, setze es manuell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

---

## 📊 Nach erfolgreicher Konfiguration

1️⃣ **Tests ausführen:**
```powershell
.\run-tests.bat
```

2️⃣ **Warten auf Emulator** (erste Ausführung: 5-10 Minuten)

3️⃣ **Ergebnisse anschauen** 
   - Alle 20 Tests sollten **PASSED** sein ✅

4️⃣ **CI/CD optional:**
   - GitHub Actions Workflow kommt später (optional)

---

**Brauchst du Hilfe? Scheib den Fehler vom Terminalfenster ab und zeige ihn!**
