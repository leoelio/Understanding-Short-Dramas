# Project Status

更新时间：2026-05-27 07:33:16

## 当前目标

批量生成三类题材体验配置草稿：修仙、寻宝、冬至爱情

## Git 状态

- 分支：`main`
- 最新提交：`700e06e`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/experience_configs.json`
- `M frontend/app.js`
- `M frontend/index.html`
- `M frontend/styles.css`
- `M scripts/generate_episode_experience_with_llm.py`
- `?? frontend/assets/stickers/treasure_compass.svg`
- `?? frontend/assets/stickers/treasure_map.svg`
- `?? frontend/assets/stickers/treasure_trap.svg`
- `?? frontend/assets/stickers/winter_heart.svg`
- `?? frontend/assets/stickers/winter_memory.svg`
- `?? frontend/assets/stickers/winter_snow.svg`
- `?? frontend/assets/stickers/xianxia_rain.svg`
- `?? frontend/assets/stickers/xianxia_seal.svg`
- `?? frontend/assets/stickers/xianxia_spirit.svg`
- `?? frontend/assets/themes/episode_19_experience_config.json`
- `?? frontend/assets/themes/episode_1_experience_config.json`
- `?? frontend/assets/themes/episode_5_experience_config.json`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：38
- 弹幕记录：187
- 体验配置：4

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

- 为《云渺1》第1集生成并写入修仙主题体验配置，增加灵雨、法阵、灵气贴图策略
- 为《北派寻宝》第63集生成并写入寻宝主题体验配置，增加地图、罗盘、机关贴图策略；因缺少字幕/画面备注，标记为 llm_draft 待复核
- 为《那年冬至》第1集生成并写入冬至爱情主题体验配置，增加雪、心事、回忆贴图策略；因缺少字幕/画面备注，标记为 llm_draft 待复核
- 补充新增题材播放器主题和贴图资产，播放页已能按服务端配置展示不同风格

## 下一步建议

- 在复核台人工查看《北派寻宝》和《那年冬至》正片画面，修正贴图时间窗和语义描述
- 下一步可把体验配置 JSON 的核心字段做成表单，降低人工复核成本
- 再扩大到每部剧第1集前，先确保这4部样例能稳定展示题材差异

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
