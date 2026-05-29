# Project Status

更新时间：2026-05-30 02:55:26

## 当前目标

重做北往第一集片尾 AI 二创视频资产：先交付稳定可播放的原片重剪预测版，同时明确高质量新剧情视频需要外部视频生成渠道。

## Git 状态

- 分支：`main`
- 最新提交：`80c5a6e`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_kindness_ride_audi_sedan.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_kindness_ride_convertible.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_kindness_ride_wuling_van.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_road_breakdown_chain_bridge.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_road_breakdown_flat_tire.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_road_breakdown_frozen_engine.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_ticket_home_coach_ticket.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_ticket_home_green_train.mp4`
- `M frontend/assets/remix_videos/beiwang_ep1/beiwang_ep1_ticket_home_standing_ticket.mp4`
- `M scripts/generate_beiwang_remix_videos.py`
- `?? data/beiwang_remix_frame_14s.png`
- `?? data/beiwang_remix_frame_23s.png`
- `?? data/beiwang_remix_frame_3s.png`
- `?? data/beiwang_remix_v2_frame_14s.png`
- `?? data/beiwang_remix_v2_frame_23s.png`
- `?? data/beiwang_remix_v2_frame_3s.png`
- `?? data/beiwang_remix_v3_frame_14s.png`
- `?? data/beiwang_remix_v3_frame_3s.png`
- `?? data/beiwang_remix_v4_frame_14s.png`
- `?? data/beiwang_remix_v4_frame_3s.png`
- `?? data/beiwang_source_150.png`
- `?? data/beiwang_source_242.png`
- `?? data/beiwang_source_280.png`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：65
- 已复核剧集：4
- 待复核剧集：16
- 互动记录：46
- 弹幕记录：220
- 体验配置：4
- 片尾 AI 二创：16

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

- 重新生成北往第一集 9 条二创缓存视频，使用原片动态片段、三段式 AI 预测标题、分镜字幕和竖屏包装。
- 调整生成脚本的字幕遮罩，避免原片字幕与二创说明互相干扰。
- 验证接口返回 cached_video，静态 MP4 服务为 video/mp4，客户端可通过 storage_hint 直接播放。

## 下一步建议

- 如果比赛需要更高质感的新剧情视频，使用外部文生/图生视频服务按 storage_hint 文件名替换当前缓存资产。
- 继续先围绕北往第一集打磨二创演示流程，再复制到其他重点剧集。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
