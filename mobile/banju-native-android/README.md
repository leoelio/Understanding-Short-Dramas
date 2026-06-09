# 半句 Native Android

这是 `native-android-migration` 分支上的原生 Android 试验工程。

当前阶段：Stage 1，原生启动页和服务端健康检查。

## 目标

- 不使用 WebView。
- 验证手机能访问电脑上的 FastAPI 服务端。
- 为后续登录、选剧、播放器、高光互动迁移建立最小工程骨架。

## 构建

```powershell
.\scripts\build_banju_native_android_debug.ps1
```

APK 输出：

```text
mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk
```

## 安装

```powershell
.\scripts\install_banju_native_android_debug.ps1
```

## 真机回归验证

完整验证步骤见：

```text
..\..\docs\ANDROID_NATIVE_QA_CHECKLIST.md
```

## 手机访问服务端

开发期有三种方式：

1. 使用当前 Cloudflare HTTPS 隧道地址。
2. 手机和电脑在同一局域网时，填写电脑局域网 IP，例如 `http://192.168.x.x:8000`。
3. USB 调试时执行：

```powershell
adb reverse tcp:8000 tcp:8000
```

然后 App 中填写：

```text
http://127.0.0.1:8000
```

注意：Android 手机上的 `127.0.0.1` 默认是手机自己，不是电脑。只有执行 `adb reverse` 后才适用于连接电脑服务端。
