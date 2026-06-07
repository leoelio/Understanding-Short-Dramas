# 半句 Android 壳 App

这是“半句 - 短剧陪伴”的 Android WebView/Capacitor 壳工程。

- App 名称：半句
- 包名：com.banju.shortdrama
- 当前公网地址：https://achievements-combine-advanced-readily.trycloudflare.com
- 业务主体：继续由 Web + FastAPI 服务承载

## 构建环境

- JDK：D:\tools\jdk\jdk-21
- Android SDK：D:\Android\sdk

## 构建 APK

在仓库根目录运行：

```powershell
.\scripts\build_banju_android_debug.ps1
```

也可以在本目录手动运行：

```powershell
cd C:\Users\Administrator\Desktop\短剧理解\mobile\banju-android
$env:JAVA_HOME='D:\tools\jdk\jdk-21'
$env:ANDROID_SDK_ROOT='D:\Android\sdk'
$env:ANDROID_HOME='D:\Android\sdk'
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"
cmd.exe /c npm run build:debug
```

Debug APK 输出位置：

```text
mobile\banju-android\android\app\build\outputs\apk\debug\app-debug.apk
```

## 安装到安卓设备

手机开启开发者模式和 USB 调试后，在仓库根目录运行：

```powershell
.\scripts\install_banju_android_debug.ps1
```

当前没有连接设备时，`adb devices` 会只显示空列表。
