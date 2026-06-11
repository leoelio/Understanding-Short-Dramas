# 半句 Android 原生分支

本分支是 `native-android-migration`，目标是把“基于短剧剧情理解的即时互动激发系统”从移动端 Web 体验迁移到 Android 原生 App。

当前 Android App 名称：`半句`

定位：短剧陪伴型 Android 客户端。手机端负责登录、选剧、播放、弹幕、高光互动、同看社交、片尾 AI 二创和个人资产展示；电脑端或公网服务器继续提供 FastAPI 服务、数据库、视频、AI 资产和接口。

## 当前功能

- 登录和 token 保存。
- 登录后短剧首页，展示真实短剧列表、封面和最近观看。
- 原生视频播放页。
- 播放页控制层自动隐藏，点击视频后唤醒自定义进度条、播放暂停、弹幕模式、片尾 AI 和同看入口。
- 高光时间轴触发互动弹层，支持选项点击、疯狂点击、计数反馈和互动上报。
- 弹幕轨道，支持轻聊、狂欢、沉浸三档。
- 弹幕点击、点赞、回复，并同步到同看房间动态。
- 好友、聊天、好友申请、同看邀请和同看房间。
- 房间成员、称号、徽章、答题结果和房间动态展示。
- 逛逛动态流，支持文字、AI 图片、AI 剧情卡、AI 声音等资产类型的展示和发布入口。
- 我的页：头像池、头像裁切上传、昵称管理、积分、称号、徽章、声音资产上传、麦克风直录和试听生成。
- 北往第 1 集片尾 AI 二创：分支选择、个性化选项、三张图片分镜、原声讲述/我的声音入口、发布到逛逛/同步房间。

## 分支结构

```text
backend/                         FastAPI 服务端，Android 端消费这里的接口
frontend/                        Web 主线客户端和后台，Android 分支保留作服务端/后台基线
mobile/banju-native-android/     Android 原生 App 工程
mobile/banju-android/            旧 WebView/Capacitor 壳，保留作回退参考
scripts/                         构建、安装、数据和 AI 辅助脚本
docs/                            状态、迁移计划和验证文档
data/                            SQLite 数据、生成资产和缓存目录
视频库/                          原始短剧素材目录，通常不进入 Git
```

## 一键复刻流程

以下流程适合在另一台 Windows 电脑上复刻当前 Android 分支，并让手机能使用。

### 1. 克隆并切换分支

```powershell
git clone https://github.com/leoelio/Understanding-Short-Dramas.git
cd Understanding-Short-Dramas
git checkout native-android-migration
```

如果已经有仓库：

```powershell
git fetch origin
git checkout native-android-migration
git pull origin native-android-migration
```

### 2. 准备服务端环境

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
```

准备短剧素材：

- 把原始视频素材放到仓库根目录的 `视频库/`。
- 默认每部剧导入前 2 集。
- 如果没有 `视频库/`，服务端可以启动，但短剧播放和完整演示数据会不完整。

可选 `.env`：

```text
VIDEO_LIBRARY_PATH=./视频库
SEED_EPISODES_PER_DRAMA=2
DATABASE_URL=sqlite:///./data/app.db
```

不需要把任何模型 API Key 写进 Git。大模型、OpenAI、CosyVoice 等密钥只放本机 `.env` 或系统环境变量。

### 3. 启动 FastAPI 服务端

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
```

本机检查：

- API 健康检查：http://127.0.0.1:8000/api/health
- Web 后台/复核页：http://127.0.0.1:8000/
- API 文档：http://127.0.0.1:8000/docs

默认测试账号：

```text
普通用户：user_demo / User12345!
管理员：admin_demo / Admin12345!
```

### 4. 准备 Android 构建环境

当前脚本默认使用：

```text
JDK: D:\tools\jdk\jdk-21
Android SDK: D:\Android\sdk
```

如果你的路径不同，修改：

```text
scripts\build_banju_native_android_debug.ps1
scripts\install_banju_native_android_debug.ps1
```

需要确保 `adb` 可用：

```powershell
D:\Android\sdk\platform-tools\adb.exe devices -l
```

手机端需要开启：

- 开发者选项
- USB 调试
- 连接电脑后允许 USB 调试授权

### 5. 构建 APK

```powershell
.\scripts\build_banju_native_android_debug.ps1
```

默认输出：

```text
mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk
```

如果要复制一份带提交号的安装包：

```powershell
$commit = (git rev-parse --short HEAD).Trim()
Copy-Item mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk artifacts\banju-native-android-$commit.apk -Force
```

### 6. 让手机访问电脑服务端

推荐开发方式：USB reverse。

```powershell
D:\Android\sdk\platform-tools\adb.exe reverse tcp:8000 tcp:8000
```

App 里的服务端地址填写：

```text
http://127.0.0.1:8000
```

注意：Android 手机上的 `127.0.0.1` 默认指手机自己。只有执行 `adb reverse tcp:8000 tcp:8000` 后，它才会转发到电脑的 `127.0.0.1:8000`。

其他方式：

- 手机和电脑在同一 Wi-Fi：填写 `http://电脑局域网IP:8000`。
- 公网演示：启动 Cloudflare 或其他 HTTPS 隧道，App 内填写公网 HTTPS 地址。

### 7. 安装 APK 到手机

```powershell
D:\Android\sdk\platform-tools\adb.exe install -r mobile\banju-native-android\app\build\outputs\apk\debug\app-debug.apk
```

或使用脚本：

```powershell
.\scripts\install_banju_native_android_debug.ps1
```

启动：

```powershell
D:\Android\sdk\platform-tools\adb.exe shell am start -n com.banju.nativeapp/.MainActivity
```

### 8. 手机端验证路径

最小验证：

1. 打开 App。
2. 服务端地址填 `http://127.0.0.1:8000`。
3. 使用 `user_demo / User12345!` 登录。
4. 首页出现短剧列表。
5. 进入《北往》第 1 集。
6. 等待视频播放。
7. 确认播放页控制层会自动隐藏，点击视频后能唤醒。
8. 切换弹幕：轻聊、狂欢、沉浸。
9. 等待或拖动到高光点，确认高光互动弹层出现。
10. 点击片尾 AI，确认能进入图片分镜流程。

完整真机回归见：

```text
docs\ANDROID_NATIVE_QA_CHECKLIST.md
```

## 当前 APK

最近一次手工打包文件：

```text
artifacts\banju-native-android-d2cdc9c.apk
```

说明：

- 这是 debug 签名 APK，适合测试和比赛演示安装。
- 正式上架应用商店需要 release 签名和隐私合规补充。
- App 功能依赖服务端，单独安装 APK 不等于离线可用。

## 常见问题

### 手机打开后接口连不上

检查：

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
D:\Android\sdk\platform-tools\adb.exe reverse tcp:8000 tcp:8000
D:\Android\sdk\platform-tools\adb.exe devices -l
```

App 里服务端地址应填：

```text
http://127.0.0.1:8000
```

### 手机没有出现在 adb devices

处理顺序：

1. 换数据线或 USB 口。
2. 手机 USB 模式切到文件传输或数据传输。
3. 重新开启 USB 调试。
4. 手机上确认“允许 USB 调试”弹窗。
5. 重启 ADB：

```powershell
D:\Android\sdk\platform-tools\adb.exe kill-server
D:\Android\sdk\platform-tools\adb.exe start-server
D:\Android\sdk\platform-tools\adb.exe devices -l
```

### 有页面但没有视频

通常是 `视频库/` 缺失或服务端没有导入视频。确认：

- `视频库/` 放在仓库根目录。
- 服务端启动日志没有视频扫描错误。
- `/media/episodes/{episode_id}` 能在浏览器访问。

### 片尾 AI 或我的声音不可用

基础图片分镜和缓存资产可以展示；生成语音依赖本地 CosyVoice 服务或服务端已有缓存。没有启动 CosyVoice 时，App 应显示明确错误，不影响主播放链路。

## 开发边界

- Web 主线、服务端、数据、AI 二创、后台配置仍以 Web/FastAPI 为主。
- Android 原生端只消费稳定接口，不反向改变 Web 主线架构。
- 本分支优先保证手机端演示体验：选剧、观看、高光互动、弹幕、同看、AI 二创和个人资产。
