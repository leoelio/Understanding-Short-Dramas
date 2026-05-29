# Project Status

更新时间：2026-05-30 05:14:01

## 当前目标

片尾 AI 二创策略从视频生成调整为三镜头图片分镜：北往第一集已支持按选项返回 3 张缓存图，前端点击进入下一张。

## Git 状态

- 分支：`main`
- 最新提交：`859ebad`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M  backend/app/main.py`
- `M  docs/PROJECT_STATUS.md`
- `M  docs/RUN_LOG.md`
- `M  frontend/app.js`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_convertible_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_convertible_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_convertible_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_green_train_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_green_train_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_green_train_shot_3.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket_shot_1.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket_shot_2.png`
- `A  frontend/assets/remix_images/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket_shot_3.png`
- `M  frontend/styles.css`
- `A  scripts/generate_beiwang_remix_images.py`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：65
- 已复核剧集：4
- 待复核剧集：16
- 互动记录：47
- 弹幕记录：220
- 体验配置：4
- 片尾 AI 二创：20

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

- 后端新增 image_plan，按 choice + variant 下发三张图片分镜、缓存状态、生成模型和 storage_hint。
- 前端片尾二创结果页新增图片分镜播放器，支持上一张/下一张点击翻页，并在有 image_plan 时不再展示视频播放器。
- 新增 scripts/generate_beiwang_remix_images.py，支持本地占位图生成和 OpenAI 图片模型生成覆盖同名资产。
- 已为北往第一集 3 个方向 x 3 个变体生成 27 张图片占位资产，保证演示流程无需等待实时生成。

## 下一步建议

- 如需更高质量视觉效果，使用 OPENAI_API_KEY 运行图片生成脚本的 openai 模式，覆盖 frontend/assets/remix_images/beiwang_ep1 下同名图片。
- 下一步建议把图片分镜资产纳入复核页管理：预览、替换、重新生成、精选排序。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
