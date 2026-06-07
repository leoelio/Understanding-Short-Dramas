# 半句 Android 原生迁移计划

分支：`native-android-migration`

基线版本：`v0.9.0-webview-baseline`

更新时间：2026-06-07

## 目标

把当前 Web/Capacitor 版本逐步迁移成 Android 原生 App。第一版不追求完整复刻所有 Web 功能，优先完成“北往第一集原生观看闭环”：

1. 登录。
2. 选剧首页。
3. 原生播放页。
4. 高光时间轴触发。
5. 弹幕基础展示。
6. 高光互动上报。
7. 片尾 AI 二创入口和图片分镜展示。

## 已确认决策

- 客户端：Android 原生。
- 服务端：继续使用电脑上的 FastAPI 服务端。
- 第一版重点：北往第一集完整观看体验。
- UI 方向：高级感、动态性、原生流畅，不直接照搬当前 Web 长页面。
- 同看、聊聊、逛逛：第二批迁移，不阻塞第一版播放器。
- 回滚点：`v0.9.0-webview-baseline`。

## 核心假设

- 手机端不运行本地服务端，不内置 AI 模型。
- 手机端通过 HTTP/HTTPS 调用电脑上的 FastAPI。
- 开发期真机联调可以使用 USB `adb reverse` 或局域网 IP。
- 演示给不在同一网络的人使用时，继续使用 Cloudflare Tunnel HTTPS 地址。
- 旧的 `mobile/banju-android` Capacitor 壳保留为回退方案。
- 新原生工程放在 `mobile/banju-native-android`，避免污染 WebView 基线。

## 需要警惕的点

手机访问电脑服务端不能写死 `127.0.0.1`。在 Android 手机上：

- `127.0.0.1` 指手机自己，不是电脑。
- USB 调试时可用 `adb reverse tcp:8000 tcp:8000`，再让 App 访问 `http://127.0.0.1:8000`。
- 局域网联调时使用电脑局域网 IP，例如 `http://192.168.x.x:8000`。
- 公网演示时使用 Cloudflare Tunnel HTTPS 地址。

第一版建议在 App 里提供一个开发期 `baseUrl` 配置入口，默认使用当前 HTTPS 演示地址。这个入口只用于测试，不做复杂环境系统。

## 技术选型

- 语言：第一阶段使用 Java + Android SDK，先验证原生构建和服务端联通；后续播放器和主界面进入时再引入 Kotlin。
- UI：第一阶段使用原生 View，避免为了健康检查引入 Compose 依赖；后续复杂页面可迁移到 Jetpack Compose。
- 播放器：Media3 ExoPlayer。
- 网络：Retrofit + OkHttp。
- 图片：Coil。
- 本地状态：DataStore。
- 架构：轻量 MVVM。第一版只分 `data`、`ui`、`player` 三块，避免过度设计。

## 为什么不直接改现有 Capacitor 工程

现有 `mobile/banju-android` 是 WebView 壳，保留它可以随时回退到当前可演示版本。如果直接在这个目录里混入原生页面，会同时承担 Capacitor 和原生两套生命周期，问题定位更困难。

更稳的方式是新建：

```text
mobile/
  banju-android/          # 现有 Capacitor 壳，保留
  banju-native-android/   # 新原生 App，逐步实现
```

## 第一版接口清单

### 1. 健康检查

`GET /api/health`

用途：确认手机能连上电脑服务端。

成功条件：返回 `ok = true`。

### 2. 登录

`POST /api/auth/login`

请求：

```json
{
  "username": "demo",
  "password": "password"
}
```

返回：

```json
{
  "token": "...",
  "user": {
    "id": 1,
    "username": "demo",
    "display_name": "用户昵称",
    "avatar_url": "/media/avatar-pool/..."
  }
}
```

Android 处理：

- token 存入 DataStore。
- 后续请求加 `Authorization: Bearer <token>`。
- 图片和媒体 URL 如果是相对路径，需要拼接 `baseUrl`。

### 3. 当前用户

`GET /api/auth/me`

用途：App 启动后恢复登录态。

### 4. 剧集列表

`GET /api/dramas`

用途：首页展示短剧卡片。

第一版只需要：

- `id`
- `title`
- `genre`
- `description`
- `episode_count`
- `first_episode_id`
- `preview_video_url`

### 5. 剧集详情

`GET /api/episodes/{episode_id}`

用途：播放页一次性拿到视频地址、高光点和剧信息。

第一版关键字段：

- `id`
- `title`
- `duration_sec`
- `video_url`
- `drama.title`
- `highlights`

高光字段第一版使用：

- `id`
- `start_time_sec`
- `end_time_sec`
- `title`
- `description`
- `highlight_type`
- `emotion`
- `options`

### 6. 视频文件

`GET /media/episodes/{episode_id}`

用途：ExoPlayer 播放。

注意：

- URL 要拼接 `baseUrl`。
- 如果使用 HTTP 局域网地址，Android 需要允许 cleartext。
- 如果使用 HTTPS 隧道，不需要 cleartext。

### 7. 弹幕

`GET /api/episodes/{episode_id}/danmaku`

用途：播放页弹幕基础展示。

第一版只用：

- `id`
- `time_sec`
- `text`
- `mode`
- `user`

### 8. 互动上报

`POST /api/interactions`

请求：

```json
{
  "highlight_id": 1,
  "option_key": "shock",
  "session_id": "android-session-id"
}
```

用途：

- 用户点击高光选项。
- 获取选项占比。
- 获取奖励结果。

### 9. 观看进度

`POST /api/users/me/watch-history`

请求：

```json
{
  "episode_id": 3,
  "progress_sec": 258.4
}
```

用途：离开播放页时保存进度。第一版可以只在暂停、退出、播放结束时上报。

### 10. 片尾 AI 二创选项

`GET /api/episodes/{episode_id}/remix-options`

用途：播放接近片尾时展示 AI 二创入口。

第一版使用：

- `trigger_time_sec`
- `options`
- `featured_remixes`

### 11. 创建 AI 二创

`POST /api/episodes/{episode_id}/ai-remix`

请求：

```json
{
  "choice_key": "road_breakdown",
  "variant_key": "green_soda",
  "session_id": "android-session-id"
}
```

用途：拿到图片分镜和文字内容。

### 12. 二创语音

`POST /api/episodes/{episode_id}/remix-voice-clips`

请求：

```json
{
  "choice_key": "road_breakdown",
  "variant_key": "green_soda",
  "shot_index": 1,
  "voice_mode": "original",
  "session_id": "android-session-id"
}
```

用途：第一版只接原版语音；用户声音带入第二批接入。

## 第一版页面结构

### 启动页

职责：

- 读取 token。
- 检查服务端健康状态。
- 决定进入登录页或首页。

### 登录页

职责：

- 用户名密码登录。
- 保存 token。
- 视觉上保持“半句”的高级感，不复刻 Web 页面。

### 选剧首页

职责：

- 拉取短剧列表。
- 优先展示北往。
- 点击进入第一集播放页。

### 播放页

职责：

- 竖屏沉浸播放。
- ExoPlayer 播放视频。
- 拉取高光和弹幕。
- 播放进度监听。
- 到时间触发高光弹层。
- 离开页面自动暂停。

### 片尾 AI 二创页

职责：

- 全屏展示三条主分支。
- 用户选择个性化选项。
- 展示三张图片分镜。
- 点击翻页。
- 播放原版语音。

## 阶段计划

### 阶段 1：原生工程骨架

目标：

- 创建 `mobile/banju-native-android`。
- App 能安装启动。
- 能配置服务端地址。
- 能调用 `GET /api/health`。
- 不引入播放器、登录、Compose 或复杂依赖。

验证：

- 真机或模拟器打开 App。
- 首页显示服务端连接成功。

### 阶段 2：登录和选剧

目标：

- 登录成功并保存 token。
- 拉取 `/api/dramas`。
- 点击北往进入播放页。

验证：

- 重启 App 后能保持登录态。
- 选剧卡片显示真实数据。

### 阶段 3：原生播放器

目标：

- ExoPlayer 播放北往第一集。
- 显示基础控制条。
- 离开页面暂停。

验证：

- 北往第一集可连续播放。
- 进度条稳定。
- 返回首页后视频停止。

### 阶段 4：高光和弹幕

目标：

- 拉取高光时间轴。
- 到点弹出高光互动。
- 展示基础弹幕。
- 点击互动后上报。

验证：

- 北往第一集四个高光能触发。
- 弹幕不遮挡核心画面。
- 上报接口返回 stats。

### 阶段 5：片尾 AI 二创

目标：

- 片尾出现二创入口。
- 三分支选择。
- 三图分镜翻页。
- 原版语音播放。

验证：

- 北往第一集片尾可完整走通。
- 同一时间只播放一段语音。

## 暂不迁移内容

第一版不做：

- 聊聊完整聊天。
- 逛逛动态流。
- 好友申请。
- 同看房间。
- 用户声音录入。
- 头像池完整管理。
- 后台复核页。
- 弹幕治理后台。

这些功能作为第二批迁移，避免第一版播放器被拖慢。

## 下一步编码入口

如果确认继续，下一步开始阶段 1：

1. 创建 `mobile/banju-native-android` 原生工程。
2. 写最小启动页和服务端连接检查。
3. 在真机上验证能访问电脑服务端。
4. 服务端联通后，再进入登录、选剧和播放器迁移。

成功标准：

- 可以生成 debug APK。
- App 启动不是 WebView。
- `GET /api/health` 成功。
- 页面显示当前服务端地址和连接状态。
