$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$project = Join-Path $root "mobile\banju-native-android"

$env:JAVA_HOME = "D:\tools\jdk\jdk-21"
$env:ANDROID_SDK_ROOT = "D:\Android\sdk"
$env:GRADLE_USER_HOME = Join-Path $root ".gradle-native"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"

Push-Location $project
try {
    .\gradlew.bat --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

$apk = Join-Path $project "app\build\outputs\apk\debug\app-debug.apk"
Write-Host "APK: $apk"
