# Project Status

更新时间：2026-06-11 05:20:18

## 当前目标

Android 原生声音资产直录体验补强：我的页录音样本显示时长、停止并上传、离开页面自动释放录音资源。

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`5d0c5fa`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java`

## 数据状态

- Android 原生工作树不维护业务数据库。
- 当前客户端通过 `http://127.0.0.1:8000` 消费 Web 主线服务端稳定接口；真机调试使用 `adb reverse tcp:8000 tcp:8000`。
- 本次构建验证通过；当前 `adb devices` 仍未识别到手机，我的页录音授权、录音上传和试听生成待设备恢复后复测。

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

- mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java 为麦克风直录新增 voiceRecordTicker/voiceRecordStartedAtMs，录音中每秒显示已录时长和 3-8 秒建议；按钮文案改为停止并上传；onPause/stopActiveVideo 会取消录音并删除临时文件；迁移状态文案更新为声音上传、直录、试听和头像裁切已接入。Android 构建通过；当前 adb devices 仍未识别到手机，真机录音授权与上传待设备恢复后复测。

## 下一步建议

- 设备恢复后优先复测我的页：麦克风权限弹窗、录音计时、停止并上传、声音资产刷新、生成试听；随后继续迁移 Web 端复核/AI 配置能力。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
