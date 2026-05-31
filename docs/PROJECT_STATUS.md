# Project Status

更新时间：2026-05-31 08:04:44

## 当前目标

Fix microphone recording voice generation by normalizing browser audio to wav before CosyVoice calls

## Git 状态

- 分支：`main`
- 最新提交：`2bac371`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/main.py`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：65
- 已复核剧集：4
- 待复核剧集：16
- 互动记录：50
- 弹幕记录：220
- 体验配置：4
- 片尾 AI 二创：29

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

## 本次变更摘要

- Added backend ffmpeg normalization for uploaded voice samples, so browser webm/opus recordings are converted to wav before being stored as active voice profiles.
- Added generation-time fallback normalization for old non-wav voice profiles, fixing existing recorded samples without requiring users to re-upload.

## 下一步建议

- Keep voice prompt normalization on the server path for mobile deployment; mobile clients can upload native recordings without knowing CosyVoice audio format requirements.

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
