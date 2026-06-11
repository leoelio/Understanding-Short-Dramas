# 部署与运行说明

更新时间：2026-06-11

## 文档定位

本文说明“半句”当前版本如何在本地运行、如何临时公网演示，以及哪些能力属于可选增强。当前主线优先保证电脑端 Web 展示质量，Android/iOS 原生客户端属于后续迁移方向。

## 部署形态

| 形态 | 用途 | 当前建议 |
| --- | --- | --- |
| 本地 Web | 日常开发、复核、录屏 | 优先使用 |
| 临时公网隧道 | 给不在同一局域网的人演示 | 可用，但 URL 不稳定 |
| Android WebView 壳 | 安装包演示 | 可选，不作为当前主线 |
| 云服务器正式部署 | 长期可访问 | 后续上线前再做 |

## 目录约定

```text
backend/        FastAPI 服务端
frontend/       Web 客户端和后台页面
scripts/        运行、标注、构建和隧道脚本
data/           SQLite 数据库、声音资产、头像资产
docs/           项目文档
视频库/         原始短剧素材，不进入 Git
avatars/        系统头像池
```

## 环境要求

基础必需：

- Windows 电脑
- Python 3.11 或兼容版本
- PowerShell
- 可访问本地视频素材目录

推荐安装：

- `ffmpeg`：用于浏览器录音转码和声音样本规范化。
- `cloudflared`：用于临时公网演示。
- 本地 CosyVoice 服务：用于声音复刻和 mp3 生成。

可选：

- Android SDK / JDK：仅用于构建 Android WebView 壳。

## 本地首次运行

### 1. 进入项目目录

```powershell
cd C:\Users\Administrator\Desktop\短剧理解
```

### 2. 创建虚拟环境

如果 `.venv` 已存在，可以跳过。

```powershell
python -m venv .venv
```

### 3. 安装后端依赖

```powershell
.\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
```

### 4. 配置环境变量

复制示例文件：

```powershell
Copy-Item .env.example .env
```

`.env` 只保存在本地，不提交 Git。

主要配置：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `APP_NAME` | FastAPI 应用名称 | 短剧即时互动激发系统 |
| `DATABASE_URL` | 数据库连接 | `sqlite:///./data/app.db` |
| `VIDEO_LIBRARY_PATH` | 短剧视频库目录 | `./视频库` |
| `SEED_EPISODES_PER_DRAMA` | 每部短剧导入前几集 | `2` |
| `ARK_API_KEY` | 大模型 API Key | 空 |
| `ARK_ENDPOINT_ID` / `ARK_MODEL` | 大模型接入点或模型名 | 空 |
| `ARK_BASE_URL` | 大模型兼容接口地址 | 方舟兼容地址 |
| `COSYVOICE_BASE_URL` | 本地声音服务地址 | `http://127.0.0.1:50001` |
| `COSYVOICE_TIMEOUT_SECONDS` | 声音生成超时 | `120` |
| `VOICE_ASSET_DIR` | 声音资产目录 | `./data/voice_assets` |

安全要求：

- 不要把真实密钥写入 README、文档、提交信息或截图。
- `.env` 必须保持本地私有。

### 5. 启动服务

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
```

启动时会自动：

1. 建表或补表字段。
2. 扫描视频库并导入短剧/剧集。
3. 创建默认演示用户。

### 6. 打开页面

```text
客户端首页：http://127.0.0.1:8000/
API 自动文档：http://127.0.0.1:8000/docs
后台统计：http://127.0.0.1:8000/#admin
复核工作台：http://127.0.0.1:8000/#review
```

重点演示：

```text
北往第一集：http://127.0.0.1:8000/?episode=3
北往第一集片尾二创：http://127.0.0.1:8000/?episode=3&remix=1
```

## 日常开发运行

后端和前端当前由同一个 FastAPI 服务承载：

- API 来自 `backend/app/main.py`
- 静态页面来自 `frontend/`
- 不需要单独启动 Vite 或 Node dev server

修改前端 HTML/CSS/JS 后，通常刷新浏览器即可。

如果浏览器缓存影响验证，可以在 URL 后追加：

```text
?cache_bust=任意字符串
```

## 临时公网演示

临时公网演示使用 Cloudflare Quick Tunnel，把本地 `127.0.0.1:8000` 映射成 HTTPS 地址。

### 1. 确认本地服务已启动

先确认本地可打开：

```text
http://127.0.0.1:8000/
```

### 2. 启动隧道

```powershell
.\scripts\start_public_tunnel.ps1
```

脚本会输出类似：

```text
Public URL: https://xxxx.trycloudflare.com
```

### 3. 停止隧道

```powershell
.\scripts\stop_public_tunnel.ps1
```

注意：

- Quick Tunnel 是临时演示方案，不保证长期可用。
- 电脑关机、后端停止、隧道进程停止后，公网地址都会失效。
- 重启隧道通常会生成新的公网地址。
- 如果要做正式上线，应使用云服务器、固定域名和 HTTPS 证书。

## 声音资产服务

声音模块依赖本地 CosyVoice 服务。项目后端只负责：

- 保存用户授权声音样本。
- 调用本地声音服务生成音频。
- 缓存生成后的 mp3。
- 给前端返回可播放 URL。

### 1. 启动 CosyVoice

具体启动方式取决于本地 CosyVoice 项目。后端默认访问：

```text
http://127.0.0.1:50001
```

### 2. 配置后端

`.env` 中保持：

```text
COSYVOICE_BASE_URL=http://127.0.0.1:50001
COSYVOICE_TIMEOUT_SECONDS=120
VOICE_ASSET_DIR=./data/voice_assets
```

### 3. 浏览器录音要求

如果使用浏览器直接录音，后端可能需要 `ffmpeg` 将录音转成 16k 单声道 wav。没有 `ffmpeg` 时：

- wav 文件可能还能直接使用。
- webm、m4a 等格式可能无法转码。

## 大模型标注和 AI 生成

大模型能力主要用于离线或半离线流程：

- 高光点标注。
- 体验配置生成。
- 贴图建议。
- 弹幕批量治理。
- 片尾二创文案和分镜。

相关脚本在 `scripts/` 下。真实调用前必须在 `.env` 配置模型相关变量。

连通性测试：

```powershell
.\.venv\Scripts\python.exe scripts\test_llm_connection.py
```

安全原则：

- 只检查是否可用，不打印真实密钥。
- 模型输出进入 JSON 或数据库后再人工复核。
- 比赛演示优先使用已缓存和已复核结果，避免现场等待或失败。

## Android WebView 壳

当前已有 Android WebView/Capacitor 壳的尝试，定位是“安装包演示”，不是当前主线。

相关脚本：

```powershell
.\scripts\build_banju_android_debug.ps1
.\scripts\install_banju_android_debug.ps1
```

APK 输出位置：

```text
mobile\banju-android\android\app\build\outputs\apk\debug\app-debug.apk
```

限制：

- WebView 壳主要打开公网 HTTPS 地址。
- 如果公网隧道地址变化，需要同步更新壳工程配置并重新打包。
- 当前电脑端 Web 体验优先，原生 Android 全量迁移另起分支处理。

## 数据备份

开发前建议备份数据库：

```powershell
Copy-Item data\app.db data\app.backup.db
```

如果涉及声音、头像、二创图片和音频，也同步备份：

```text
data\voice_assets
data\avatar_assets
frontend\assets\remix_audio
frontend\assets\remix_images
frontend\assets\themes
```

## 常见问题

### 页面打不开

检查：

1. 后端 uvicorn 是否还在运行。
2. 端口是否为 `8000`。
3. 浏览器地址是否是 `http://127.0.0.1:8000/`。
4. 如果是公网地址，确认 cloudflared 还在运行。

### 视频无法播放

检查：

1. `VIDEO_LIBRARY_PATH` 是否指向正确视频库。
2. `data/app.db` 中剧集的 `video_path` 是否存在。
3. 访问 `/media/episodes/{episode_id}` 是否返回视频。
4. 视频格式是否为浏览器可播放格式，优先使用 mp4。

### 登录后接口 401

检查：

1. 是否已调用 `/api/auth/login`。
2. 请求头是否带 `Authorization: Bearer <token>`。
3. token 是否过期。
4. 用户是否被管理员停用。

### 后台接口 403

说明当前用户角色不足。后台统计和复核接口需要 `admin` 或 `reviewer`，用户管理需要 `admin`。

### 声音生成不可用

检查：

1. CosyVoice 服务是否启动。
2. `COSYVOICE_BASE_URL` 是否正确。
3. 上传音频是否小于 12MB。
4. 是否安装 `ffmpeg`。
5. 是否使用固定授权文本。

### 公网能打开但素材加载慢

Quick Tunnel 只适合演示，不适合大规模分发视频。正式部署应把视频、图片、音频放到对象存储或 CDN。

## 正式上线前待补

1. 使用云服务器或容器部署后端。
2. 使用 PostgreSQL 替代 SQLite。
3. 使用对象存储管理视频、图片、音频和头像。
4. 配置固定域名和 HTTPS。
5. 增加日志、监控、错误告警。
6. 增加定期数据库备份。
7. 增加用户隐私协议、声音授权协议和内容审核策略。
8. 将 AI 生成任务异步化，避免请求阻塞。
