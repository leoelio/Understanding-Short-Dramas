$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ApkPath = Join-Path $Root "mobile\banju-android\android\app\build\outputs\apk\debug\app-debug.apk"

$env:ANDROID_SDK_ROOT = "D:\Android\sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:Path = "$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"

if (!(Test-Path $ApkPath)) {
  throw "APK not found. Run scripts\build_banju_android_debug.ps1 first."
}

adb devices
adb install -r $ApkPath
