# Project Status

更新时间：2026-05-30 06:54:36

## 当前目标

北往第一集片尾二创图片主角纠偏

## Git 状态

- 分支：`main`
- 最新提交：`5feb834`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M .gitignore`
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
- `M scripts/generate_beiwang_remix_images.py`

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

- 将图片编辑脚本的人物参考帧改为皮德胜与李世鑫两位正确男主，增加中景参考帧，提示词要求故事构图、关键物件可见、票据和标牌不可读；按新策略重跑 27 张片尾二创图片资产。

## 下一步建议

- 由产品侧检查 27 张图片是否符合人物与剧情预期；如果通过，继续补充 27 条对应音频并接入播放；如果不通过，优先针对问题分支单独重跑。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
