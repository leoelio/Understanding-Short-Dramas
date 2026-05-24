# Project Status

更新时间：2026-05-25 07:11:48

## 当前目标

按产品反馈重做《北往》第1集回家主线互动：四段高光、交通工具选择贴图、弹幕种子与审核规则已完成。

## Git 状态

- 分支：`main`
- 最新提交：`2b568a1`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/reviewed_highlights.json`
- `M backend/app/main.py`
- `M backend/app/seed.py`
- `M frontend/app.js`
- `M frontend/index.html`
- `M frontend/styles.css`
- `?? backend/app/danmaku_moderation.py`
- `?? backend/app/fixtures/danmaku_comments.json`
- `?? docs/future_ai_extensions.md`
- `?? frontend/assets/`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：5
- 弹幕记录：127

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

- 《北往》第1集高光改为：开头要债、没钱回家虐心、能否回去疑问、交通工具揭晓摩托返乡。
- 新增弹幕审核模块：辱骂黑名单、通用剧透拦截、按播放时间判断交通工具剧透。
- 新增精选弹幕 fixture，针对第1集生成 13 条不剧透、贴剧情节奏的弹幕。
- 新增火车/小车/摩托车贴图素材，并在 04:18 高光暂停视频展示交通工具选择。
- 新增未来 AI 延展文档，预留片尾生成、用户上传漫画化和登录体系。

## 下一步建议

- 继续观察《北往》第1集播放体验，确认四个高光的触发时机是否要微调到更贴字幕。
- 下一步处理《北往》第2集，保持同题材连续体验一致。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
