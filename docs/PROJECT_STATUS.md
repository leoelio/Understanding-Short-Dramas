# Project Status

更新时间：2026-06-10 03:20:17

## 当前目标

Android 原生迁移继续推进：同看房间进入播放页后保留 room code，并周期性上报当前播放进度和播放状态。

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`e5b987b`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java`

## 数据状态

- 数据库存在：否
- 短剧：0
- 剧集：0
- 高光点：0
- 已复核剧集：0
- 待复核剧集：0
- 互动记录：0
- 弹幕记录：0
- 体验配置：0
- 片尾 AI 二创：0
- 社交动态：0
- 社交评论：0
- 社交通知：0
- 好友申请：0
- 聊天消息：0

## 高光来源

- 暂无

## 已完成能力

- 移动端 Web 短剧列表、播放页、剧集切换。
- 高光时间轴下发、按播放时间触发互动组件。
- 互动点击上报、选项占比和后台统计。
- 大模型离线标注链路、人工复核工作台、复核进度筛选。
- 8 类高光分类体系和稀疏高光规则。
- 弹幕评论、三种弹幕模式和弹幕样式设置。
- 分类型高光动效：冲突站队、反转狂点、爽点连击、甜蜜气泡、虐心共情、悬念线索、搞笑贴纸、危机心跳。
- 体验配置复核台：服务端存储播放器主题、贴图时间轴、弹幕策略、来源和版本。
- 片尾 AI 二创保底版：剧情预测选项、文字卡、三格分镜、生成记录和精选管理。
- 社交 MVP：聊聊好友会话、文字/表情/同看链接消息、消息红点、逛逛动态发布、公开/好友/仅自己权限、点赞评论、好友申请审核和基础内容审核。

## 本次变更摘要

- 仅在 短剧理解-android / native-android-migration 工作，未启动 Web 服务端口，未修改 Web worktree。
- showNativePlayer 新增 roomCode 重载；普通播放不带房间上下文，从同看房间进入播放才保留 room code。
- 房间页的 进入本集播放 按钮会携带 room code 打开原生播放页。
- 原生播放页准备完成后，如果存在 activePlayerRoomCode，每 5 秒调用 /api/watch-rooms/{code}/sync 上报 episode_id、progress_sec 和 playback_state。
- 退出播放或切换页面时停止房间同步定时器，避免后台继续上报。
- 本轮暂不做另一端自动拉取并跟随房间进度，也不把房间 events 叠加回播放页。
- Android debug APK 编译成功；本轮未做真机验证。

## 下一步建议

- 手机可用后验证：从房间页进入播放，观察服务端 room progress_sec 是否随播放推进。
- 下一步建议迁移播放页房间动态：播放页拉取 /api/watch-rooms/{code}/events，显示对方高光选择、弹幕和点赞。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
