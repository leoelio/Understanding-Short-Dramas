# Project Status

更新时间：2026-06-11 04:43:28

## 当前目标

Android 原生片尾 AI 分镜页补齐台词卡：每张分镜明确展示本镜头要生成/播放的声音文本。

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`c7d12f2`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java`

## 数据状态

- Android 原生工作树不维护业务数据库。
- 当前客户端通过 `http://127.0.0.1:8000` 消费 Web 主线服务端稳定接口；真机调试使用 `adb reverse tcp:8000 tcp:8000`。
- 本次构建验证通过；本轮真机在安装后从 adb 列表掉线，片尾 AI 分镜交互复测待设备恢复后补。

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

- mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java 在片尾 AI 分镜页新增本镜头台词卡，复用 shot.audio_text/subtitle/caption 作为原声和用户声音生成依据，并补齐 remixScriptBackground 样式。构建通过；本轮真机在安装后 adb 设备掉线，片尾 AI 交互复测待设备恢复后补。

## 下一步建议

- 设备恢复后优先复测片尾 AI 分镜页：点击片尾 AI、进入方向分支、查看台词卡、点击图片翻页、请求原声/我的声音并确认无崩溃。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
