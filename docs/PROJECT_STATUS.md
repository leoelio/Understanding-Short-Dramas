# Project Status

更新时间：2026-05-27 05:25:32

## 当前目标

优化北往第一集的定时贴图、点击特效、个性化播放器氛围和三档弹幕体验

## Git 状态

- 分支：`main`
- 最新提交：`63fcf71`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/danmaku_comments.json`
- `M docs/PROJECT_STATUS.md`
- `M docs/RUN_LOG.md`
- `M frontend/app.js`
- `M frontend/index.html`
- `M frontend/styles.css`
- `?? frontend/assets/stickers/beiwang_go_sign.svg`
- `?? frontend/assets/stickers/beiwang_home_lantern.svg`
- `?? frontend/assets/stickers/beiwang_meal_steam.svg`
- `?? frontend/assets/stickers/beiwang_no_pay_bill.svg`
- `?? frontend/assets/stickers/beiwang_rock_word.svg`
- `?? frontend/assets/stickers/beiwang_title_north.svg`
- `?? frontend/assets/themes/episode_3_experience_plan.json`
- `?? scripts/generate_episode_experience_with_llm.py`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：30
- 弹幕记录：187

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

## 本次变更摘要

- 新增北往主题贴图资源，并按视频文本时间段建立贴图投放节奏
- 修复时间轴复核时旧互动不关闭、贴图时间窗边界错误、连续点击高亮不刷新的问题
- 扩展北往弹幕种子数据，区分轻聊、狂欢和深度讨论三类体验
- 接入本地大模型经验生成脚本，生成北往第一集体验计划 JSON

## 下一步建议

- 继续用复核页校准每集高光时间，优先保证一集一集体验稳定
- 下一轮可把贴图资源从 SVG 继续升级为更高质量图片/动图素材

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
