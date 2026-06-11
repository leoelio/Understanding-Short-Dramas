# Project Status

更新时间：2026-06-11 10:29:14

## 当前目标

Android 原生播放器体验优化：播放时控制层自动隐藏，点击视频唤醒自定义进度条和操作面板，减少内容遮挡。

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`b0a2293`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java`

## 数据状态

- Android 原生工作树不维护业务数据库。
- 当前客户端通过 `http://127.0.0.1:8000` 消费 Web 主线服务端稳定接口；真机调试使用 `adb reverse tcp:8000 tcp:8000`。
- 本次真机验证通过：隐藏态无遮挡，点击视频可唤醒自定义控制层，控制层会再次自动隐藏，crash buffer 无崩溃输出。

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

- mobile/banju-native-android/app/src/main/java/com/banju/nativeapp/MainActivity.java 为播放页新增 activePlayerTopScrim/activePlayerTopBar/playerChromeHideRunnable/playerProgressUiRunnable 等状态；移除系统 MediaController，改为自定义进度条、当前/总时长、播放暂停按钮；顶部栏、底部控制栏和同看状态进入播放后短暂显示并自动淡出，点击视频可再次唤醒；真机已验证隐藏态无遮挡、唤醒态无系统控制条叠加、再次自动隐藏，crash buffer 无崩溃输出。

## 下一步建议

- 继续根据真机观感做第二轮视觉精修：如需要，可进一步优化弹幕密度、底部控制面板高度、高光弹层出现位置和片尾 AI 面板质感。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
