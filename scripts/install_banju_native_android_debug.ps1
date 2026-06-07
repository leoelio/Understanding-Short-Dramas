$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk"

$env:ANDROID_SDK_ROOT = "D:\Android\sdk"
$env:Path = "$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"

if (!(Test-Path $apk)) {
    throw "APK not found. Run scripts\build_banju_native_android_debug.ps1 first."
}

adb install -r $apk
