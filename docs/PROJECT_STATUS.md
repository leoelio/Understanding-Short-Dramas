# Project Status

更新时间：2026-05-25 18:15:29

## 当前目标

优化《北往》第1集复核与互动体验：可视化改时间、贴纸特效、弹幕分层和互动消失逻辑。

## Git 状态

- 分支：`main`
- 最新提交：`37dc546`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M backend/app/fixtures/danmaku_comments.json`
- `M backend/app/fixtures/reviewed_highlights.json`
- `M backend/app/seed.py`
- `M frontend/app.js`
- `M frontend/index.html`
- `M frontend/styles.css`
- `?? frontend/assets/stickers/effect_charge.svg`
- `?? frontend/assets/stickers/effect_laugh.svg`
- `?? frontend/assets/stickers/effect_question.svg`
- `?? frontend/assets/stickers/effect_rock.svg`
- `?? frontend/assets/stickers/effect_tear.svg`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：12
- 弹幕记录：148

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

- 复核页新增高光点卡片，显示片名、名称、类型、时间，并支持直接输入秒数调整开始/结束时间。
- 《北往》第1集交通工具互动触发点从 04:18 校准到 04:30。
- 新增视频贴纸层和冲、问号、哈哈、摇滚、心疼等常用 SVG 贴纸，按高光关键词自动弹出。
- 弹幕 fixture 扩充到 34 条，并按轻聊/狂欢模式分层展示，沉浸模式保持关闭。
- 互动组件区分问答选择和疯狂点击：问答回答后短时消失，点击型互动按最后一次点击刷新消失计时。

## 下一步建议

- 继续按这个复核工作台校准《北往》第2集或下一部素材的关键高光时间。
- 下一轮可以把贴纸规则抽成后端策略接口，并接入大模型批量生成 PNG/WebP 资产。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
