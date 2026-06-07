# 半句公网演示与 Android 壳计划

## 当前公网演示

- App 名称：半句
- 定位：短剧陪伴
- 临时公网地址：https://achievements-combine-advanced-readily.trycloudflare.com
- 本地目标服务：http://127.0.0.1:8000
- 隧道工具：D:\tools\cloudflared\cloudflared.exe

Cloudflare Quick Tunnel 是临时演示方案，不保证长期可用。电脑关机、本地 8000 服务停止、cloudflared 进程停止后，公网地址都会失效。重新启动隧道通常会生成新的 `trycloudflare.com` 地址。

## 常用命令

启动后端服务：

```powershell
cd C:\Users\Administrator\Desktop\短剧理解
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
```

启动公网隧道：

```powershell
cd C:\Users\Administrator\Desktop\短剧理解
.\scripts\start_public_tunnel.ps1
```

停止公网隧道：

```powershell
cd C:\Users\Administrator\Desktop\短剧理解
.\scripts\stop_public_tunnel.ps1
```

## Android 构建环境

- JDK 21：D:\tools\jdk\jdk-21
- Android SDK：D:\Android\sdk
- Capacitor 工程：mobile\banju-android
- App 展示名：半句
- 包名：com.banju.shortdrama
- 启动地址：当前公网 HTTPS 地址
- 图标素材：frontend\assets\brand\banju-icon.png

Debug APK 构建命令：

```powershell
cd C:\Users\Administrator\Desktop\短剧理解
.\scripts\build_banju_android_debug.ps1
```

APK 输出：

```text
mobile\banju-android\android\app\build\outputs\apk\debug\app-debug.apk
```

安装到已连接安卓设备：

```powershell
cd C:\Users\Administrator\Desktop\短剧理解
.\scripts\install_banju_android_debug.ps1
```

第一版 Android 壳只负责启动页、图标、网络权限、麦克风/文件上传权限和全屏 WebView。播放器、高光、弹幕、AI 二创、声音资产、同看社交都继续由 Web + 服务端承载，这样后续大部分功能修改不需要重新打包 APK。
