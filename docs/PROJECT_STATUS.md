# Project Status

更新时间：2026-05-28 06:31:09

## 当前目标

完善贴图复核管理：时间窗增删、素材缩略图选择、按高光自动生成贴图窗，并扩展那年冬至贴图素材。

## Git 状态

- 分支：`main`
- 最新提交：`d321b15`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/experience_configs.json`
- `M frontend/app.js`
- `M frontend/assets/themes/episode_19_experience_config.json`
- `M frontend/index.html`
- `M frontend/styles.css`
- `?? frontend/assets/stickers/winter_blush.svg`
- `?? frontend/assets/stickers/winter_broken_heart.svg`
- `?? frontend/assets/stickers/winter_heartbeat.svg`
- `?? frontend/assets/stickers/winter_hold_back.svg`
- `?? frontend/assets/stickers/winter_question_love.svg`
- `?? frontend/assets/stickers/winter_warm_hug.svg`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：65
- 已复核剧集：4
- 待复核剧集：16
- 互动记录：41
- 弹幕记录：220
- 体验配置：4

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

## 本次变更摘要

- 体验配置复核台新增贴图时间窗管理：支持新增时间窗、删除时间窗，并在保存前继续做时间和素材校验。
- 贴图素材选择器升级为缩略图模式，所有素材可直接通过预览按钮勾选/取消，降低手输 asset_id 的成本。
- 新增按高光自动生成/更新贴图时间窗能力，会根据高光类型、情绪和文本关键词推荐对应贴图、频率和出现数量。
- 补充6个那年冬至高相关贴图素材：心碎、脸红、心跳、别冲动、爱还是现实、抱抱。
- 那年冬至第1集体验配置升级到 v3：27s 心碎/抱抱，50s 哇塞/别冲动，1:50 爱还是现实，2:50 亲吻/爱心/脸红/心跳。

## 下一步建议

- 继续增加贴图素材缩略图分组和搜索，避免素材量继续扩大后选择区过长。
- 把按高光生成贴图窗接入大模型脚本，输出可审计的贴图建议 JSON，再由复核台导入。
- 开始复核下一部样片，验证这套贴图管理流程是否能跨题材复用。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
