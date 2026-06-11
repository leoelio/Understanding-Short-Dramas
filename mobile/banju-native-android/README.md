# 半句 Native Android 工程说明

本目录是 `native-android-migration` 分支里的 Android 原生客户端工程。它不是 WebView 壳，而是 Java 原生页面，通过 HTTP 调用仓库里的 FastAPI 服务端。

App 名称：`半句`

包名：`com.banju.nativeapp`

当前版本：`0.1.0-native-health`

最低系统：Android 7.0，`minSdk 24`

## 当前覆盖范围

- 登录、token 保存、服务端地址配置。
- 登录后短剧首页、最近观看、短剧封面和真实剧集列表。
- 原生视频播放页，播放 `/media/episodes/{episode_id}` 视频流。
- 播放页自定义控制层：点击唤醒、自动隐藏、播放/暂停、自定义进度条。
- 高光时间轴触发：按服务端 `/api/episodes/{episode_id}` 返回的高光点依次展示互动弹层。
- 高光互动上报：选择、疯狂点击等行为上报服务端。
- 弹幕轨道：拉取 `/api/episodes/{episode_id}/danmaku`，支持轻聊、狂欢、沉浸三档。
- 同看房间、好友、聊天、申请、动态流、个人主页等移动端主链路入口。
- 北往第一集片尾 AI 二创：分支选择、图片分镜、原版声音/我的声音入口。

## 工程位置

```text
mobile/banju-native-android/
  app/
    build.gradle
    src/main/
      AndroidManifest.xml
      java/com/banju/nativeapp/MainActivity.java
      res/
```

当前 UI 主要在 `MainActivity.java` 中以原生 View 方式实现。这样做的原因是迁移阶段要先保证手机端闭环，不额外引入复杂框架。

## 与服务端的关系

Android 端只消费稳定接口，不负责维护数据库、AI 资产生成或后台配置。

服务端仍在仓库根目录：

```text
backend/
```

开发调试时，需要先在电脑上启动 FastAPI：

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
```

默认演示账号：

```text
user_demo / User12345!
admin_demo / Admin12345!
```

## 构建环境

当前脚本默认使用：

```text
JDK: D:\tools\jdk\jdk-21
Android SDK: D:\Android\sdk
Gradle 缓存: 仓库根目录\.gradle-native
```

如果本机路径不同，修改仓库根目录下这两个脚本：

```text
scripts\build_banju_native_android_debug.ps1
scripts\install_banju_native_android_debug.ps1
```

先确认手机能被 ADB 识别：

```powershell
D:\Android\sdk\platform-tools\adb.exe devices -l
```

手机需要开启：

- 开发者选项
- USB 调试
- 连接电脑后允许 USB 调试授权

## 构建 APK

从仓库根目录执行：

```powershell
.\scripts\build_banju_native_android_debug.ps1
```

输出文件：

```text
mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk
```

如果需要生成可分发的调试包，可以复制到 `artifacts/`：

```powershell
$commit = (git rev-parse --short HEAD).Trim()
Copy-Item mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk artifacts\banju-native-android-$commit.apk -Force
```

已有的最近一次调试包：

```text
artifacts\banju-native-android-d2cdc9c.apk
```

## 安装到手机

```powershell
.\scripts\install_banju_native_android_debug.ps1
```

或者手动执行：

```powershell
D:\Android\sdk\platform-tools\adb.exe install -r mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk
```

启动 App：

```powershell
D:\Android\sdk\platform-tools\adb.exe shell am start -n com.banju.nativeapp/.MainActivity
```

## 让手机访问电脑服务端

推荐开发方式是 USB reverse：

```powershell
D:\Android\sdk\platform-tools\adb.exe reverse tcp:8000 tcp:8000
```

然后 App 内服务端地址填写：

```text
http://127.0.0.1:8000
```

注意：手机上的 `127.0.0.1` 默认指手机自己。执行 `adb reverse` 后，手机访问 `127.0.0.1:8000` 才会转发到电脑的 FastAPI 服务端。

也可以使用局域网或公网隧道：

```text
http://电脑局域网IP:8000
https://公网隧道地址
```

公网演示时，建议使用 HTTPS 隧道，并在 App 内填写完整公网地址。

## 复刻到另一台电脑和手机

1. 克隆仓库并切到安卓分支：

```powershell
git clone https://github.com/leoelio/Understanding-Short-Dramas.git
cd Understanding-Short-Dramas
git checkout native-android-migration
```

2. 安装后端依赖：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
```

3. 准备素材目录：

```text
视频库/
```

没有原始视频时，服务端可以启动，但手机端完整播放和演示数据会不完整。

4. 启动服务端：

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
```

5. 构建并安装 APK：

```powershell
.\scripts\build_banju_native_android_debug.ps1
.\scripts\install_banju_native_android_debug.ps1
```

6. 建立手机到电脑的端口转发：

```powershell
D:\Android\sdk\platform-tools\adb.exe reverse tcp:8000 tcp:8000
```

7. 打开 App，服务端地址填：

```text
http://127.0.0.1:8000
```

8. 使用 `user_demo / User12345!` 登录，进入《北往》第 1 集验证播放、高光、弹幕和片尾 AI 二创。

## 真机验收路径

最小验收路径：

1. 登录成功。
2. 首页能加载短剧列表和封面。
3. 进入《北往》第 1 集。
4. 视频正常播放。
5. 播放控制层默认不遮挡画面，点击视频后再显示。
6. 拖动进度条能跳转。
7. 高光弹层能按时间出现。
8. 高光选择或点击后能上报。
9. 弹幕三档能切换。
10. 片尾 AI 二创入口能进入图片分镜流程。

完整检查清单见：

```text
docs\ANDROID_NATIVE_QA_CHECKLIST.md
```

## 调试命令

查看崩溃日志：

```powershell
D:\Android\sdk\platform-tools\adb.exe logcat -b crash -d
```

查看实时日志：

```powershell
D:\Android\sdk\platform-tools\adb.exe logcat | Select-String -Pattern "banju|AndroidRuntime|FATAL"
```

截图：

```powershell
D:\Android\sdk\platform-tools\adb.exe shell screencap -p /sdcard/banju.png
D:\Android\sdk\platform-tools\adb.exe pull /sdcard/banju.png artifacts\banju.png
```

卸载：

```powershell
D:\Android\sdk\platform-tools\adb.exe uninstall com.banju.nativeapp
```

## 当前限制

- 当前 APK 是 debug 签名，适合真机测试和比赛演示，不是应用商店发布包。
- App 依赖服务端、数据库和视频库，单独安装 APK 不能离线完整使用。
- AI 图片、AI 声音和片尾二创依赖服务端已有缓存或本地 AI 服务。
- 正式发布前还需要 release 签名、隐私政策、权限说明、素材版权确认和稳定公网 HTTPS 服务。

## 开发边界

- Web 体验、服务端、数据、AI 二创、后台配置继续以 Web 主线为准。
- Android 分支只做原生客户端迁移和手机端体验验证。
- Android 不反向拖乱服务端接口，新增能力优先走已有稳定接口。
