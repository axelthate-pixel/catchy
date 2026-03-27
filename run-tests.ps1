# ============================================================================
# AngelApp - Automatisiertes Test-Skript fuer Android Emulator
# ============================================================================

param(
    [string]$AVDName = "Pixel_8_Pro",
    [int]$TimeoutSeconds = 300
)

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "AngelApp - Automatisierte Tests fuer Android Emulator" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================================
# SCHRITT 1: Android SDK pruefen
# ============================================================================
Write-Host "[1/5] Pruefe Android SDK Installation..." -ForegroundColor Yellow

$AndroidHome = $env:ANDROID_HOME

# Versuche Android SDK automatisch zu finden
if (-not $AndroidHome) {
    $PossiblePaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:CommonProgramFiles\android\android-sdk",
        "$env:ProgramFiles\Android\Sdk"
    )
    
    foreach ($Path in $PossiblePaths) {
        if (Test-Path "$Path\platform-tools\adb.exe") {
            $AndroidHome = $Path
            Write-Host "   Android SDK found: $AndroidHome" -ForegroundColor Green
            $env:ANDROID_HOME = $AndroidHome
            break
        }
    }
}

if (-not $AndroidHome) {
    Write-Host "[ERROR] ANDROID_HOME nicht gesetzt!" -ForegroundColor Red
    Write-Host "   Fuehre zuerst aus: .\setup-environment.ps1" -ForegroundColor Yellow
    exit 1
}

$AdbPath = "$AndroidHome\platform-tools\adb.exe"
if (-not (Test-Path $AdbPath)) {
    Write-Host "[ERROR] ADB nicht gefunden: $AdbPath" -ForegroundColor Red
    exit 1
}

# Pruefen JAVA_HOME
if (-not $env:JAVA_HOME) {
    $PossibleJavaPaths = @(
        "$env:PROGRAMFILES\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jbr"
    )
    
    foreach ($Path in $PossibleJavaPaths) {
        if (Test-Path "$Path\bin\java.exe") {
            $env:JAVA_HOME = $Path
            Write-Host "   Java (JBR) found: $Path" -ForegroundColor Green
            break
        }
    }
}

Write-Host "[OK] Android SDK found: $AndroidHome" -ForegroundColor Green
Write-Host ""

# ============================================================================
# SCHRITT 2: Laufende Emulator-Instanzen pruefen
# ============================================================================
Write-Host "[2/5] Pruefe laufende Emulatoren..." -ForegroundColor Yellow

& $AdbPath kill-server 2>&1 | Out-Null
Start-Sleep -Seconds 2
& $AdbPath start-server 2>&1 | Out-Null
Start-Sleep -Seconds 2

$Devices = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "device" }
$OnlineCount = ($Devices | Where-Object { $_ -match "device" -and $_ -notmatch "emulator" } | Measure-Object).Count

if ($OnlineCount -gt 0) {
    Write-Host "[OK] $OnlineCount Emulator(e) online" -ForegroundColor Green
} else {
    Write-Host "[INFO] Kein Emulator online, starte $AVDName..." -ForegroundColor Cyan
    
    # ============================================================================
    # SCHRITT 3: Emulator starten
    # ============================================================================
    Write-Host "[3/5] Starte Emulator..." -ForegroundColor Yellow
    
    $EmulatorBin = "$AndroidHome\emulator\emulator.exe"
    if (-not (Test-Path $EmulatorBin)) {
        Write-Host "[ERROR] Emulator nicht gefunden: $EmulatorBin" -ForegroundColor Red
        exit 1
    }
    
    # Emulator im Hintergrund starten
    Start-Process -FilePath $EmulatorBin -ArgumentList "-avd", $AVDName, "-no-snapshot-load" -WindowStyle Hidden
    Write-Host "   Emulator wird gestartet, bitte warten..." -ForegroundColor Gray
    
    # Auf Online-Status warten
    $Elapsed = 0
    $CheckInterval = 5
    
    while ($Elapsed -lt $TimeoutSeconds) {
        Start-Sleep -Seconds $CheckInterval
        $Devices = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "device" -and $_ -notmatch "emulator" }
        
        if ($Devices) {
            Write-Host "[OK] Emulator ist online!" -ForegroundColor Green
            break
        }
        
        $Elapsed += $CheckInterval
        $Remaining = $TimeoutSeconds - $Elapsed
        Write-Host "   Warte auf Emulator... ($Remaining Sekunden)" -ForegroundColor Gray
    }
    
    if ($Elapsed -ge $TimeoutSeconds) {
        Write-Host "[ERROR] Emulator Timeout nach ${TimeoutSeconds}s" -ForegroundColor Red
        exit 1
    }
    
    # Warte auf Boot-Animation
    Write-Host "   Warte auf vollstaendigen Boot..." -ForegroundColor Gray
    Start-Sleep -Seconds 10
}

Write-Host ""

# ============================================================================
# SCHRITT 4: Geraete-Info
# ============================================================================
Write-Host "[4/5] Geraete-Information:" -ForegroundColor Yellow

$DeviceList = & $AdbPath devices -l
Write-Host $DeviceList -ForegroundColor Gray
Write-Host ""

# ============================================================================
# SCHRITT 5: Tests ausfuehren
# ============================================================================
Write-Host "[5/5] Fuehre Tests aus..." -ForegroundColor Yellow
Write-Host ""

$ProjectPath = Get-Location
Push-Location $ProjectPath

Write-Host "Verzeichnis: $(Get-Location)" -ForegroundColor Gray
Write-Host "Befehl: .\gradlew.bat connectedDebugAndroidTest" -ForegroundColor Cyan
Write-Host ""

& .\gradlew.bat connectedDebugAndroidTest

$ExitCode = $LASTEXITCODE

Write-Host ""

if ($ExitCode -eq 0) {
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "SUCCESS: ALLE TESTS BESTANDEN!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Test-Ergebnisse unter:" -ForegroundColor Cyan
    Write-Host "  $ProjectPath\app\build\outputs\androidTest-results\" -ForegroundColor Gray
    exit 0
} else {
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "ERROR: TESTS FEHLGESCHLAGEN (Exit Code: $ExitCode)" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    exit $ExitCode
}
