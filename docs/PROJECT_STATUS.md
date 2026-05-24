# Project Status

更新时间：2026-05-25 06:20:43

## 当前目标

完成《北往》第1集大模型标注、复核写库、播放端验证与可复现 fixture 导出；当前已复核 3/20 集。

## Git 状态

- 分支：`main`
- 最新提交：`d390436`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/reviewed_highlights.json`
- `?? scripts/export_reviewed_highlights.py`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：5
- 弹幕记录：120

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

- 增强 scripts/annotate_with_llm.py：模型输出不合规时自动带校验错误重试一次，并加入按钮与情绪标签归一化。
- 新增 scripts/export_reviewed_highlights.py，可将本地 human_review 高光导出到仓库 fixture，避免只存在本地数据库。
- 《北往》第1集写入并导出 4 个 human_review 高光：爽点逆袭、虐心共情、搞笑解压、悬念钩子。
- 播放页验证通过：时间轴可见，互动卡可触发，用户点击可上报统计。

## 下一步建议

- 继续按同一模板处理《北往》第2集，形成同题材连续两集样本。
- 每完成一集后运行 fixture 导出并提交，保证 GitHub 可复现演示数据。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
