# Project Status

更新时间：2026-05-30 06:17:22

## 当前目标

北往第一集片尾二创图片分镜已按剧集参考帧重跑：每张图以两位男主作为主体，并预留语音台词与音频文件路径。

## Git 状态

- 分支：`main`
- 最新提交：`4c60605`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M .gitignore`
- `M backend/app/main.py`
- `M frontend/app.js`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_convertible_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_convertible_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_convertible_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_green_train_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_green_train_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_green_train_shot_3.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket_shot_1.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket_shot_2.png`
- `M frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket_shot_3.png`
- `M frontend/index.html`
- `M frontend/styles.css`
- `M scripts/generate_beiwang_remix_images.py`
- `?? docs/BEIWANG_EP1_REMIX_AUDIO_LINES.md`
- `?? frontend/assets/remix_audio/`

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
- 片尾 AI 二创：25

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

- 图片生成脚本新增 openai-edit 模式，会从原剧抽取两位男主参考帧和场景参考帧，再调用图片编辑接口生成 27 张分镜图。
- 已重新生成北往第一集 3 个二创方向 x 3 个变体 x 3 个镜头，共 27 张双男主图片分镜。
- 后端 image_plan 新增 audio_text、audio_storage_hint、audio_status，音频缺失时标记 pending_upload。
- 前端二创分镜点击时已预留音频播放逻辑；复核页可看到每张图的语音台词和待上传音频路径。
- 新增 docs/BEIWANG_EP1_REMIX_AUDIO_LINES.md，列出 27 条台词和对应 mp3 路径。

## 下一步建议

- 等音频文件准备好后，按清单文件名放入 frontend/assets/remix_audio/beiwang_ep1/，刷新页面即可自动播放。
- 下一步可在复核页增加单张图片重生成按钮，使用同一 openai-edit 脚本按 choice/variant/shot 单独覆盖。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
