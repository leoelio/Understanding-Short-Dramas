$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ProjectDir = Join-Path $Root "mobile\banju-android\android"

$env:JAVA_HOME = "D:\tools\jdk\jdk-21"
$env:ANDROID_SDK_ROOT = "D:\Android\sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"

Push-Location $ProjectDir
try {
  cmd.exe /c gradlew.bat assembleDebug
} finally {
  Pop-Location
}

$ApkPath = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path $ApkPath)) {
  throw "APK not found: $ApkPath"
}

Get-Item -LiteralPath $ApkPath | Select-Object FullName, Length, LastWriteTime
