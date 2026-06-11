# API 接口说明

更新时间：2026-06-11

## 文档定位

本文用于说明“半句”当前服务端已经暴露的主要接口，方便前端、后续原生客户端、后台复核页和答辩说明接入。

更精确的字段校验以运行时自动文档为准：

```text
http://127.0.0.1:8000/docs
```

## 基础约定

- 本地 Base URL：`http://127.0.0.1:8000`
- 公网演示 URL：以临时隧道实际输出为准。
- 数据格式：除文件上传接口外，默认使用 JSON。
- 鉴权方式：登录后拿到 `token`，后续需要身份的接口在请求头传入 `Authorization: Bearer <token>`。
- 游客可用：短剧列表、剧集详情、视频播放、高光互动上报、弹幕读取、部分片尾 AI 二创。
- 登录后可用：个人资料、好友、聊天、同看房间、动态、声音资产、观看历史、成长系统。
- 管理员或复核员可用：后台统计、剧集复核、弹幕治理、体验配置、二创精选管理。

## 通用响应

成功响应通常返回对象或数组，例如：

```json
{
  "ok": true
}
```

常见错误：

| 状态码 | 含义 |
| --- | --- |
| `400` | 请求参数不合法，或内容被治理规则拦截 |
| `401` | 未登录或登录已失效 |
| `403` | 当前账号角色无权限 |
| `404` | 资源不存在 |
| `409` | 状态冲突，例如同看房间已满 |

## 系统接口

| 方法 | 路径 | 鉴权 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/health` | 否 | 服务健康检查，返回 `ok` 和 `request_id` |

## 登录与用户

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth/register` | 否 | `username`, `password`, `display_name` | 注册并直接返回登录 token |
| `POST` | `/api/auth/login` | 否 | `username`, `password` | 登录 |
| `GET` | `/api/auth/me` | 是 | 无 | 获取当前用户 |
| `POST` | `/api/auth/logout` | 是 | 无 | 退出当前 session |
| `PATCH` | `/api/users/me/profile` | 是 | `display_name?`, `avatar_url?` | 修改昵称或头像地址 |
| `POST` | `/api/users/me/avatar` | 是 | multipart：`avatar` | 上传自定义头像 |
| `GET` | `/api/avatar-pool` | 否 | 无 | 获取系统头像池 |

注册/登录响应核心字段：

```json
{
  "token": "只在响应中返回一次的登录令牌",
  "user": {
    "id": 1,
    "username": "user_demo",
    "display_name": "普通用户",
    "avatar_url": "/media/avatar-pool/example.png",
    "role": "user"
  }
}
```

## 短剧与播放

| 方法 | 路径 | 鉴权 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/taxonomy/highlights` | 否 | 获取高光分类体系 |
| `GET` | `/api/dramas` | 否 | 获取短剧列表 |
| `GET` | `/api/dramas/{drama_id}/episodes` | 否 | 获取某部短剧的剧集列表 |
| `GET` | `/api/episodes/{episode_id}` | 否 | 获取剧集详情、高光时间轴和视频地址 |
| `GET` | `/media/episodes/{episode_id}` | 否 | 播放剧集视频文件 |

剧集详情核心响应：

```json
{
  "id": 3,
  "episode_no": 1,
  "title": "北往・第1集",
  "duration_sec": 298.0,
  "video_url": "/media/episodes/3",
  "drama": {
    "id": 2,
    "title": "北往",
    "genre": "公路喜剧"
  },
  "highlights": []
}
```

高光对象核心字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 高光点 ID |
| `start_time_sec` / `end_time_sec` | 触发时间窗 |
| `title` | 高光名称 |
| `highlight_type` | 高光分类，例如爽点、反转、虐心、悬念 |
| `emotion` | 预期情绪 |
| `options` | 互动按钮或竞猜选项 |
| `source` | 标注来源，例如 `manual_seed`、`human_review` |
| `confidence` | 标注置信度 |
| `model_version` | 模型或人工复核版本 |

## 高光互动

| 方法 | 路径 | 鉴权 | 请求体 | 用途 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/interactions` | 可选 | `highlight_id`, `option_key`, `session_id` | 上报高光互动，返回当前选项占比和可能获得的奖励 |

请求示例：

```json
{
  "highlight_id": 101,
  "option_key": "shock",
  "session_id": "browser-session-id"
}
```

响应示例：

```json
{
  "ok": true,
  "highlight_id": 101,
  "stats": [],
  "reward": null
}
```

## 弹幕

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/episodes/{episode_id}/danmaku` | 否 | 无 | 获取已审核通过的弹幕时间轴 |
| `GET` | `/api/danmaku/moderation-rules` | 否 | 无 | 获取弹幕治理规则说明 |
| `POST` | `/api/danmaku` | 可选 | `episode_id`, `time_sec`, `text`, `session_id`, `mode` | 发送弹幕，并经过治理规则判断 |

弹幕模式：

| 模式 | 用途 |
| --- | --- |
| `light` | 轻聊，低密度、不打扰 |
| `carnival` | 狂欢，高密度、适合高光点 |
| `curated` | 沉浸/精选，偏稳定展示 |
| `seed` | 系统种子弹幕 |

弹幕治理当前采用规则和本地策略组合：明显违规、剧透、低相关、低质量、重复刷屏等会被隐藏或进入复核。

## 观看历史与成长

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/users/me/watch-history` | 是 | 无 | 获取最近观看 |
| `POST` | `/api/users/me/watch-history` | 是 | `episode_id`, `progress_sec` | 保存观看进度 |
| `GET` | `/api/users/me/rewards` | 是 | 无 | 获取当前用户积分、称号、徽章 |
| `GET` | `/api/users/{user_id}/growth` | 是 | path：`user_id` | 获取指定用户对外展示的成长信息 |

## 好友与聊天

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/users/me/friends` | 是 | 无 | 获取好友、候选用户、好友申请和历史 |
| `POST` | `/api/users/me/friends` | 是 | `user_id` | 发起好友申请或处理互相申请 |
| `POST` | `/api/users/me/friend-requests/{request_id}/accept` | 是 | path：`request_id` | 接受好友申请 |
| `POST` | `/api/users/me/friend-requests/{request_id}/decline` | 是 | path：`request_id` | 拒绝好友申请 |
| `POST` | `/api/users/me/friend-requests/{request_id}/withdraw` | 是 | path：`request_id` | 撤回已发出的好友申请 |
| `GET` | `/api/chat/conversations` | 是 | 无 | 获取会话列表和未读数 |
| `GET` | `/api/chat/messages/{friend_user_id}` | 是 | path：好友 ID | 获取与好友的消息 |
| `POST` | `/api/chat/messages` | 是 | `to_user_id`, `message_type`, `text`, `payload` | 发送文本、表情或同看链接 |

聊天消息类型当前支持：

| 类型 | 说明 |
| --- | --- |
| `text` | 普通文字 |
| `emoji` | 表情 |
| `watch_link` | 同看房间邀请链接 |

## 同看房间

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/watch-rooms` | 是 | `episode_id?`, `progress_sec`, `playback_state` | 创建同看房间 |
| `POST` | `/api/watch-rooms/join` | 是 | `code` | 输入房间码加入 |
| `GET` | `/api/watch-rooms/invitations` | 是 | 无 | 获取收到和发出的同看邀请 |
| `POST` | `/api/watch-rooms/{code}/invite` | 是 | `user_id` | 邀请好友进入房间 |
| `POST` | `/api/watch-rooms/invitations/{invitation_id}/accept` | 是 | path：邀请 ID | 接受同看邀请 |
| `POST` | `/api/watch-rooms/invitations/{invitation_id}/decline` | 是 | path：邀请 ID | 拒绝同看邀请 |
| `GET` | `/api/watch-rooms/{code}` | 是 | path：房间码 | 获取房间状态 |
| `POST` | `/api/watch-rooms/{code}/sync` | 是 | `episode_id`, `progress_sec`, `playback_state` | 同步播放状态 |
| `GET` | `/api/watch-rooms/{code}/events` | 是 | query：`after_id` | 拉取房间事件 |
| `POST` | `/api/watch-rooms/{code}/events` | 是 | `event_type`, `payload` | 上报答题、弹幕、点赞、高光选择等房间事件 |

同看房间当前定位是双人同看 MVP：一个房主，一个访客。多人房间、实时 WebSocket 和强一致播放同步属于后续增强。

## 逛逛动态

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/social/inbox` | 是 | 无 | 获取好友申请、同看邀请、动态通知和红点 |
| `POST` | `/api/social/inbox/read` | 是 | 无 | 标记通知已读 |
| `GET` | `/api/social/feed` | 是 | query：`scope=all/friends/mine` | 获取动态流 |
| `POST` | `/api/social/posts` | 是 | 动态内容 | 发布动态 |
| `POST` | `/api/social/posts/{post_id}/like` | 是 | path：动态 ID | 点赞或取消点赞 |
| `POST` | `/api/social/posts/{post_id}/comments` | 是 | `text` | 评论动态 |
| `DELETE` | `/api/social/comments/{comment_id}` | 是 | path：评论 ID | 删除自己评论，或动态作者删除他人评论 |

动态资产类型预留：

| 字段 | 说明 |
| --- | --- |
| `visibility` | `public`、`friends`、`private` |
| `source_type` | 文字感受、AI 生成、活动专题等来源 |
| `asset_kind` | `text`、AI 图片、AI 声音、AI 剧情卡等 |
| `asset_url` | 资产地址 |
| `asset_payload` | 结构化资产信息 |
| `topic` | 专题，例如北往 AI 声音、男主模仿赛 |

## 片尾 AI 二创

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/episodes/{episode_id}/remix-options` | 否 | 无 | 获取片尾二创主分支、触发时间和精选内容 |
| `POST` | `/api/episodes/{episode_id}/ai-remix` | 可选 | `choice_key`, `variant_key?`, `session_id` | 生成或读取片尾二创分镜 |
| `POST` | `/api/episodes/{episode_id}/remix-voice-clips` | 可选 | `choice_key`, `variant_key`, `shot_index`, `voice_mode`, `session_id` | 为某个分镜生成或读取语音 |
| `GET` | `/api/episodes/{episode_id}/featured-remixes` | 否 | 无 | 获取人工精选后的二创内容 |

当前重点示例是《北往》第一集：

- 三条主分支：车坏在半路、借钱买票回家、帮人后一起回家。
- 每条分支有三个个性化选项，形成 27 种图像分镜组合。
- 真实视频生成暂不作为 MVP 依赖，当前采用“图片分镜 + 点击翻页 + 原版/用户声音播放”的稳定展示方案。

## 声音资产

| 方法 | 路径 | 鉴权 | 请求体/参数 | 用途 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/users/me/voice-profile` | 是 | 无 | 获取当前声音授权资料和最近缓存音频 |
| `POST` | `/api/users/me/voice-profile` | 是 | multipart：`consent_text`, `voice_sample` | 上传声音样本并创建 voice profile |
| `POST` | `/api/users/me/voice-clips` | 是 | `text`, `scene_key` | 使用当前声音样本生成或读取缓存音频 |
| `GET` | `/media/voice-clips/{filename}` | 否 | path：文件名 | 播放缓存 mp3 |

声音授权固定文本：

```text
同意利用录入声音生成音频
```

设计原则：

- 上传样本只建立声音资产，不代表立即生成所有音频。
- 片尾二创和陪看小助手按固定文本生成 mp3，并缓存结果。
- 同一用户、同一文本、同一场景优先命中缓存，避免重复生成。

## 后台统计与复核

后台接口需要 `admin` 或 `reviewer` 角色，用户管理只允许 `admin`。

| 方法 | 路径 | 角色 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/stats/summary` | admin/reviewer | 数据总览 |
| `GET` | `/api/stats/highlights` | admin/reviewer | 高光互动排行和选项占比 |
| `GET` | `/api/admin/users` | admin | 用户列表 |
| `PATCH` | `/api/admin/users/{user_id}` | admin | 修改用户昵称、角色、启停状态 |
| `GET` | `/api/admin/episodes` | admin/reviewer | 剧集复核列表 |
| `GET` | `/api/admin/review-status` | admin/reviewer | 高光复核进度汇总 |
| `GET` | `/api/admin/episodes/{episode_id}/highlights` | admin/reviewer | 获取单集高光 JSON |
| `PUT` | `/api/admin/episodes/{episode_id}/highlights` | admin/reviewer | 保存人工复核后的高光 |
| `GET` | `/api/admin/episodes/{episode_id}/experience` | admin/reviewer | 获取单集体验配置 |
| `PUT` | `/api/admin/episodes/{episode_id}/experience` | admin/reviewer | 保存播放器主题、贴图窗、竞猜规则等配置 |
| `GET` | `/api/admin/episodes/{episode_id}/danmaku-governance` | admin/reviewer | 获取弹幕治理列表 |
| `POST` | `/api/admin/episodes/{episode_id}/danmaku-governance/run` | admin/reviewer | 对某集重新执行弹幕治理 |
| `POST` | `/api/admin/episodes/{episode_id}/danmaku-governance/train-small-model` | admin/reviewer | 基于当前弹幕复核结果训练轻量小模型，并对该集重新执行治理 |
| `PATCH` | `/api/admin/danmaku/{comment_id}` | admin/reviewer | 人工修改弹幕状态、时间、模式和原因 |
| `GET` | `/api/admin/episodes/{episode_id}/remixes` | admin/reviewer | 获取某集二创记录 |
| `PATCH` | `/api/admin/remixes/{remix_id}` | admin/reviewer | 修改二创精选状态和文案 |

## 前端静态资源

| 路径 | 用途 |
| --- | --- |
| `/` | Web 客户端入口，由 `frontend/` 静态目录提供 |
| `/downloads/banju-debug.apk` | 可选 Android WebView 壳 debug 包下载 |
| `/media/avatar-pool/{filename}` | 系统头像池 |
| `/media/avatars/{user_folder}/{filename}` | 用户上传头像 |
| `/media/voice-clips/{filename}` | 缓存语音片段 |

## 接口演进建议

当前接口已经能支撑比赛展示和 Web 客户端迭代。后续产品化建议：

1. 为所有接口补统一响应 envelope 和错误码枚举。
2. 为后台写操作增加审计日志。
3. 同看房间从轮询升级为 WebSocket。
4. 使用 Alembic 管理数据库迁移。
5. 对声音、图片、视频等资产增加对象存储适配层。
6. 对 AI 生成任务增加异步任务表和任务状态接口。
