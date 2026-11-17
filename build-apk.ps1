<#
.SYNOPSIS
    Сборка APK приложения «ФотоОтчёт».

.DESCRIPTION
    Собирает проект на месте, в каталоге android/.

    Тонкость: в local.properties путь к SDK должен быть записан с прямыми
    слэшами (sdk.dir=C:/Users/...). Файл читается как Java Properties, и
    последовательность «\U» в «C:\Users\...» трактуется как escape-последовательность:
    путь становится относительным, и AGP падает с невнятным IOException при
    поиске SDK. Скрипт проверяет и чинит это автоматически.

.PARAMETER Task
    Задачи Gradle через пробел. По умолчанию — debug-APK, release-APK и тесты.

.EXAMPLE
    .\build-apk.ps1
    .\build-apk.ps1 -Task ":app:assembleRelease"
#>
param(
    [string]$Task = ":app:assembleArmDebug :app:assembleArmRelease :app:testArmDebugUnitTest"
)

$ErrorActionPreference = "Stop"

$projectDir = $PSScriptRoot
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"

# --- 1. Окружение -------------------------------------------------------------
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $roots = @(
        "C:\Java",
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "$env:LOCALAPPDATA\Programs"
    )
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        $found = Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^(jdk|jre)' } |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
            Select-Object -First 1
        if ($found) { $javaHome = $found.FullName; break }
    }
}
if (-not $javaHome) {
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $javaHome = Split-Path (Split-Path $onPath.Source -Parent) -Parent }
}
if (-not $javaHome) { throw "Не найден JDK. Укажите JAVA_HOME." }

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$javaHome\bin;$env:PATH"

# --- 2. local.properties ------------------------------------------------------
# Пишем без BOM и с прямыми слэшами — см. описание выше.
$sdkLine = "sdk.dir=$($sdk -replace '\\','/')`r`n"
[System.IO.File]::WriteAllText(
    (Join-Path $projectDir "local.properties"),
    $sdkLine,
    (New-Object System.Text.UTF8Encoding $false)
)

# --- 3. Gradle ----------------------------------------------------------------
$gradleBat = $null
$cached = Get-ChildItem (Join-Path $env:USERPROFILE ".gradle\wrapper\dists") -Recurse `
    -Filter "gradle.bat" -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "gradle-8\.\d+" } |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($cached) { $gradleBat = $cached.FullName }
if (-not $gradleBat) { $gradleBat = Join-Path $projectDir "gradlew.bat" }

# --- 3. Сборка через junction с латинским путём --------------------------------
# Каталог проекта содержит кириллицу. Сама сборка в нём работает, но тестовая
# JVM не может загрузить классы из classpath с не-ASCII путём — падает
# ClassNotFoundException. Поэтому собираем через junction: физически это те же
# файлы, но все инструменты видят латинский путь.
$linkDir = Join-Path $env:TEMP "delivery-photo-link"
$needLink = $true
if (Test-Path $linkDir) {
    $item = Get-Item $linkDir -Force
    $target = $item.Target
    if ($target -and ($target[0] -eq $projectDir)) { $needLink = $false }
}
if ($needLink) {
    if (Test-Path $linkDir) {
        # rmdir по junction удаляет только ссылку, содержимое папки не страдает.
        cmd /c "rmdir `"$linkDir`"" | Out-Null
    }
    cmd /c "mklink /J `"$linkDir`" `"$projectDir`"" | Out-Null
    if (-not (Test-Path $linkDir)) {
        throw "Не удалось создать junction $linkDir"
    }
}

Write-Host "Сборка: $Task" -ForegroundColor Cyan
$gradleArgs = @()
foreach ($t in ($Task -split '\s+' | Where-Object { $_ })) { $gradleArgs += $t }
$gradleArgs += "--console=plain"
Push-Location $linkDir
try {
    & $gradleBat @gradleArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -ne 0) {
    Write-Host "Сборка не удалась (код $code)" -ForegroundColor Red
    exit $code
}

# --- 4. Артефакты -------------------------------------------------------------
$apkRoot = Join-Path $projectDir "app\build\outputs\apk"
if (Test-Path $apkRoot) {
    Get-ChildItem $apkRoot -Recurse -Filter *.apk | ForEach-Object {
        $size = "{0:N1} МБ" -f ($_.Length / 1MB)
        Write-Host ("APK: {0} ({1})" -f $_.FullName, $size) -ForegroundColor Green
    }
}

Write-Host "Готово." -ForegroundColor Green
exit 0
