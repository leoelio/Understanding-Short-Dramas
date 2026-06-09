# Project Status

更新时间：2026-06-10 03:02:20

## 当前目标

Android 原生迁移继续推进：聊聊页补齐好友申请闭环，支持发起申请、接受/拒绝收到的申请、撤回已发申请。

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`3e7db6c`
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
- 聊聊页在好友申请卡片中展示收到的申请，并接入 /api/users/me/friend-requests/{id}/accept 与 /decline。
- 聊聊页展示已发出的申请，并接入 /api/users/me/friend-requests/{id}/withdraw。
- 聊聊页展示可认识的人，并通过 /api/users/me/friends 发起好友申请。
- 所有好友申请操作完成后重新拉取 /api/users/me/friends 与 /api/chat/conversations，不在 Android 本地伪造状态。
- Android debug APK 编译成功；本轮未做真机验证。

## 下一步建议

- 手机可用后验证候选人申请、接受/拒绝、撤回申请，以及操作后概览刷新是否正确。
- 下一步建议迁移同看邀请：在聊天详情中发送 watch_link，并接入房间邀请接受/拒绝。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
