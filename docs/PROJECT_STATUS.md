# Project Status

更新时间：2026-06-07 23:32:34

## 当前目标

原生 Android 迁移阶段 1：Native 健康检查 App 已构建

## Git 状态

- 分支：`native-android-migration`
- 最新提交：`9133e1f`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M .gitignore`
- `?? mobile/banju-native-android/`
- `?? scripts/build_banju_native_android_debug.ps1`
- `?? scripts/install_banju_native_android_debug.ps1`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：65
- 已复核剧集：4
- 待复核剧集：16
- 互动记录：67
- 弹幕记录：1281
- 体验配置：4
- 片尾 AI 二创：53
- 社交动态：2
- 社交评论：1
- 社交通知：28
- 好友申请：22
- 聊天消息：13

## 高光来源

- `human_review`: 17
- `manual_seed`: 48

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

- 新增 mobile/banju-native-android 原生 Android 工程，独立于现有 Capacitor WebView 壳
- 实现原生 MainActivity：服务端地址输入、/api/health 检查、连接状态展示
- 新增原生 Android debug 构建和安装脚本
- 构建通过，APK 包名 com.banju.nativeapp，应用名 半句，v2 签名验证通过

## 下一步建议

- 连接安卓手机后安装原生 APK，验证 Cloudflare HTTPS、局域网 IP 或 adb reverse 三种服务端连接方式
- 阶段 2 接入登录和选剧首页，仍优先保持最小闭环

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
