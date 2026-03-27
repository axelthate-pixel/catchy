# ============================================================================
# AngelApp - Automatische Umgebungskonfiguration für Tests
# ============================================================================

param(
    [switch]$ShowPaths
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "AngelApp - Automatische Umgebungskonfiguration" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================================
# Android SDK Location finden und setzen
# ============================================================================
Write-Host "[1] Pruefe Android SDK..." -ForegroundColor Yellow

$PossibleAndroidPaths = @(
    "$env:LOCALAPPDATA\Android\Sdk",
    "$env:CommonProgramFiles\android\android-sdk",
    "$env:ProgramFiles\Android\Sdk",
    "C:\Android\sdk"
)

$AndroidHome = $null
foreach ($Path in $PossibleAndroidPaths) {
    if (Test-Path "$Path\platform-tools\adb.exe") {
        $AndroidHome = $Path
        Write-Host "[OK] Android SDK gefunden: $AndroidHome" -ForegroundColor Green
        break
    }
}

if (-not $AndroidHome) {
    Write-Host "[ERROR] Android SDK nicht automatisch gefunden." -ForegroundColor Red
    Write-Host ""
    Write-Host "Manuelle Einrichtung notwendig:" -ForegroundColor Cyan
    Write-Host "  1. Oeffne Android Studio - Settings - Languages - Android SDK" -ForegroundColor Gray
    Write-Host "  2. Kopiere den SDK Location Pfad" -ForegroundColor Gray
    Write-Host "  3. Fuehre aus: `$env:ANDROID_HOME = 'Dein-SDK-Pfad'" -ForegroundColor Gray
    Write-Host ""
    exit 1
} else {
    $env:ANDROID_HOME = $AndroidHome
}

# ============================================================================
# Java (JBR) Location finden und setzen
# ============================================================================
Write-Host "[2] Pruefe Java Installation..." -ForegroundColor Yellow

$PossibleJavaPaths = @(
    "$env:PROGRAMFILES\Android\Android Studio\jbr",
    "$env:ProgramFiles\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Android\Android Studio\jbr",
    "C:\Program Files\Android\Android Studio\jbr"
)

$JavaHome = $null
foreach ($Path in $PossibleJavaPaths) {
    if (Test-Path "$Path\bin\java.exe") {
        $JavaHome = $Path
        Write-Host "[OK] Java (JBR) gefunden: $JavaHome" -ForegroundColor Green
        break
    }
}

if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
} else {
    Write-Host "[WARN] Java nicht gefunden - Gradle versucht Fallback" -ForegroundColor Yellow
}

Write-Host ""

# ============================================================================
# Emulator prufen
# ============================================================================
Write-Host "[3] Pruefe Android Emulator..." -ForegroundColor Yellow

$EmulatorExe = "$AndroidHome\emulator\emulator.exe"
if (Test-Path $EmulatorExe) {
    Write-Host "[OK] Emulator gefunden" -ForegroundColor Green
} else {
    Write-Host "[WARN] Emulator nicht gefunden" -ForegroundColor Yellow
}

Write-Host ""

# ============================================================================
# Zusammenfassung
# ============================================================================
Write-Host "===================================================" -ForegroundColor Green
Write-Host "OK: Umgebung ist konfiguriert!" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Green
Write-Host ""

if ($ShowPaths) {
    Write-Host "Konfigurierte Variablen:" -ForegroundColor Cyan
    Write-Host "  ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Gray
    if ($JavaHome) {
        Write-Host "  JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Gray
    }
    Write-Host ""
}

Write-Host "Nächster Schritt - Tests ausführen:" -ForegroundColor Yellow
Write-Host "  cd C:\Users\Axel\AndroidStudioProjects\AngelApp" -ForegroundColor Cyan
Write-Host "  .\run-tests.bat" -ForegroundColor Cyan
Write-Host ""
