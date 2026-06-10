# Project Status

更新时间：2026-06-11 02:23:18

## 当前目标

Android 原生播放页接入 Web 体验配置贴图时间轴：从 /api/episodes/{id}/experience 读取 sticker_timeline，按播放时间投放原生图形贴纸。

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`ed16522`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java`

## 数据状态

- Android 原生工作树不维护业务数据库。
- 当前客户端通过 `http://127.0.0.1:8000` 消费 Web 主线服务端稳定接口；真机调试使用 `adb reverse tcp:8000 tcp:8000`。
- 本次接口验证：北往第1集 `/api/episodes/3/experience` 返回 `sticker_timeline` 5 个时间窗，主题为 `road`。

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

- mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java 新增体验配置拉取、贴图调度器、按 asset_id 映射的原生贴纸视觉、随机位置与进入/消失动画；北往第1集接口验证返回 5 个贴图时间窗，Android 构建和安装通过。

## 下一步建议

- 手机解锁后补做真机视觉验证；随后继续迁移片尾 AI 二创分镜页的过渡动画和更完整的分享/声音体验。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
