# Project Status

更新时间：2026-05-27 09:53:00

## 当前目标

完善复核页可操作性，并重做那年冬至第1集高光、贴图和爱情点击体验。

## Git 状态

- 分支：`main`
- 最新提交：`c2fa0e0`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/danmaku_comments.json`
- `M backend/app/fixtures/experience_configs.json`
- `M backend/app/fixtures/reviewed_highlights.json`
- `M backend/app/taxonomy.py`
- `M frontend/app.js`
- `M frontend/assets/themes/episode_19_experience_config.json`
- `M frontend/index.html`
- `M frontend/styles.css`
- `?? frontend/assets/stickers/winter_choice.svg`
- `?? frontend/assets/stickers/winter_crow.svg`
- `?? frontend/assets/stickers/winter_kiss.svg`
- `?? frontend/assets/stickers/winter_wow.svg`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：65
- 已复核剧集：4
- 待复核剧集：16
- 互动记录：40
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

- 复核页新增表单化编辑：高光名称、类型、情绪、时间、剧情说明、按钮文案、证据文本均可直接改，体验配置可直接编辑主题和贴图时间窗。
- 那年冬至第1集改为4个人工复核高光：27s安乐死难过、50s震惊、1:50选择悬念、2:50突然亲吻心动。
- 新增冬至爱情贴图资产：乌鸦无语、哇塞、突然亲吻、双选卡，并将2:42乌鸦无语和2:50亲吻爱心特效写入体验配置。
- 爱情点击贴图支持99/MAX计数、小心心粒子、5次后心动增强，并修复贴图被面板遮挡和重叠导致点击不准的问题。
- 补充那年冬至弹幕fixture，轻聊/狂欢/沉浸模式差异更明显。

## 下一步建议

- 继续按单部剧复核方式处理下一部爱情或强冲突短剧，先保证2-3部体验足够稳定。
- 把复核页进一步升级为后台管理雏形：新增一键新增/删除高光点、贴图素材选择器、保存前校验提示。
- 讨论是否接入图像生成服务，把当前SVG贴图替换或补充为模型生成的透明PNG/动图资产。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
