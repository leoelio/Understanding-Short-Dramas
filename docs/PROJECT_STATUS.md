# Project Status

更新时间：2026-05-27 06:40:51

## 当前目标

完成体验配置复核台 v1：服务端存储、后台编辑、播放页下发

## Git 状态

- 分支：`main`
- 最新提交：`c717e92`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/main.py`
- `M backend/app/models.py`
- `M backend/app/schemas.py`
- `M backend/app/seed.py`
- `M docs/PROJECT_STATUS.md`
- `M docs/RUN_LOG.md`
- `M docs/future_ai_extensions.md`
- `M frontend/app.js`
- `M frontend/index.html`
- `M frontend/styles.css`
- `M scripts/update_project_status.py`
- `?? backend/app/fixtures/experience_configs.json`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：37
- 弹幕记录：187
- 体验配置：1

## 高光来源

- `human_review`: 13
- `manual_seed`: 51

## 已完成能力

- 移动端 Web 短剧列表、播放页、剧集切换。
- 高光时间轴下发、按播放时间触发互动组件。
- 互动点击上报、选项占比和后台统计。
- 大模型离线标注链路、人工复核工作台、复核进度筛选。
- 8 类高光分类体系和稀疏高光规则。
- 弹幕评论、三种弹幕模式和弹幕样式设置。
- 分类型高光动效：冲突站队、反转狂点、爽点连击、甜蜜气泡、虐心共情、悬念线索、搞笑贴纸、危机心跳。
- 体验配置复核台：服务端存储播放器主题、贴图时间轴、弹幕策略、来源和版本。

## 本次变更摘要

- 新增 episode_experience_configs 数据表，保存每集播放器主题、贴图时间轴和弹幕模式配置
- 新增公开体验配置接口和后台读取/保存接口，支持版本、来源、模型版本和人工复核状态
- 复核页增加体验配置 JSON 编辑区和预览区，可保存到数据库
- 播放页改为读取服务端体验配置，北往第1集贴图时间轴和播放器主题不再只依赖前端常量
- 补充未来路线图：登录、同看房间、选片页、AI二创、用户带入、移动客户端、公网访问和语音智能人控制

## 下一步建议

- 用大模型为第2-3部剧生成体验配置草稿，并在复核台人工确认
- 把体验配置 JSON 中最常用字段做成表单控件，减少手改 JSON 的成本
- 后续再进入登录和真实管理后台，不要在当前阶段打散 MVP 闭环

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
