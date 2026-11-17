<#
.SYNOPSIS
    Запуск эмулятора Android с приложением «ФотоОтчёт».

.DESCRIPTION
    Поднимает AVD, ставит APK и открывает приложение.

    По умолчанию эмулятор открывается отдельным окном (без headless),
    чтобы можно было пользоваться камерой эмулятора и видеть интерфейс.
    Для автоматических проверок есть -Headless.

    Координаты в эмуляторе задаются через "adb emu geo fix" — приложение
    получает их из системы, поэтому в водяном знаке они будут корректными.

.PARAMETER Avd
    Имя AVD. По умолчанию — PhotoReport.

.PARAMETER Apk
    Путь к APK. По умолчанию — release-сборка, иначе debug.

.PARAMETER NoInstall
    Не устанавливать APK (просто запустить эмулятор).

.PARAMETER Headless
    Запустить без окна (для CI и автоматических проверок).

.EXAMPLE
    .\run-emulator.ps1
    .\run-emulator.ps1 -Apk app\build\outputs\apk\debug\app-debug.apk
#>
param(
    [string]$Avd = "PhotoReport",
    [string]$Apk = "",
    [switch]$NoInstall,
    [switch]$Headless
)

$ErrorActionPreference = "Stop"

$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$adb = Join-Path $sdk "platform-tools\adb.exe"

# Вариант для эмулятора: x86_64 и отдельный applicationId, чтобы его можно
# было поставить рядом с обычным приложением.
$package = "ru.delivery.photo.x86.debug"

if (-not $Apk) {
    $Apk = Join-Path $PSScriptRoot "app\build\outputs\apk\x86\debug\app-x86-debug.apk"
}

# --- 1. Запуск эмулятора -----------------------------------------------------
$running = & $adb devices | Select-String "emulator-\d+"
if ($running) {
    Write-Host "Эмулятор уже запущен." -ForegroundColor Cyan
} else {
    if (-not (Test-Path $emulator)) { throw "Не найден emulator: $emulator" }

    $args = @("-avd", $Avd, "-no-snapshot-save")
    if ($Headless) { $args += "-no-window" }
    else { $args += @("-no-boot-anim") }

    Write-Host "Запуск эмулятора $Avd ..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList $args | Out-Null
}

# --- 2. Ожидание загрузки ----------------------------------------------------
Write-Host "Ждём загрузки системы..." -ForegroundColor Cyan
& $adb wait-for-device
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    $boot = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($boot -eq "1") { $ready = $true; break }
    Start-Sleep -Seconds 5
}
if (-not $ready) { Write-Host "Эмулятор не загрузился за 10 минут." -ForegroundColor Red; exit 1 }
Write-Host "Система загружена." -ForegroundColor Green

# --- 3. Координаты ------------------------------------------------------------
# Приложение берёт координаты из системы, поэтому задаём их эмулятору.
# Внимание: adb emu geo fix принимает ДОЛГОТУ первой, затем широту.
# Москва: долгота 37.62363, широта 55.75387.
& $adb emu geo fix 37.62363 55.75387 | Out-Null
Write-Host "Координаты: 55.75387, 37.62363 (Москва)" -ForegroundColor Green

# --- 4. Установка и запуск приложения ----------------------------------------
if (-not $NoInstall) {
    if (-not (Test-Path $Apk)) {
        throw "Не найден APK: $Apk. Соберите его: .\build-apk.ps1 -Task ':app:assembleX86Debug'"
    }
    Write-Host "Установка $Apk ..." -ForegroundColor Cyan
    & $adb install -r -d $Apk
    if ($LASTEXITCODE -ne 0) { throw "Не удалось установить APK" }

    # Разрешения заранее — иначе первый запуск упирается в диалоги.
    foreach ($perm in @("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS", "CAMERA")) {
        & $adb shell pm grant $package "android.permission.$perm" 2>&1 | Out-Null
    }

    Write-Host "Запуск приложения..." -ForegroundColor Cyan
    & $adb shell am start -n "$package/ru.delivery.photo.MainActivity" | Out-Null
}

Write-Host "Готово. Приложение запущено." -ForegroundColor Green
Write-Host "Полезные команды:" -ForegroundColor Cyan
Write-Host "  & '$adb' emu geo fix 37.62363 55.75387   # задать координаты (долгота, затем широта)"
Write-Host "  & '$adb' shell pm grant $package android.permission.ACCESS_FINE_LOCATION"
Write-Host "  & '$adb' logcat | Select-String 'delivery.photo'"
