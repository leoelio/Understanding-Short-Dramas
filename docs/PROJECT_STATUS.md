# Project Status

更新时间：2026-05-26 02:05:08

## 当前目标

升级北往第1集专属贴图和返乡公路播放器主题

## Git 状态

- 分支：`main`
- 最新提交：`834d080`
- 远端：`https://github.com/leoelio/Understanding-Short-Dramas.git`
- 工作区：
- `M frontend/app.js`
- `M frontend/index.html`
- `M frontend/styles.css`
- `?? frontend/assets/stickers/beiwang_home_ticket.svg`
- `?? frontend/assets/stickers/beiwang_road_question.svg`
- `?? frontend/assets/stickers/beiwang_rock_moto.svg`
- `?? frontend/assets/stickers/beiwang_wage_stamp.svg`
- `?? frontend/assets/themes/`
- `?? scripts/generate_style_strategy_with_llm.py`

## 数据状态

- 数据库存在：是
- 短剧：10
- 剧集：20
- 高光点：64
- 已复核剧集：3
- 待复核剧集：17
- 互动记录：26
- 弹幕记录：170

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

- 新增大模型主题策略生成脚本，按 episode_3 文本与复核高光生成主题/贴图策略 JSON；当前接口返回 HTTPError，已自动使用本地剧情兜底策略且未泄露密钥。
- 新增北往专属 SVG 贴图：讨薪印章、年三十车票、回家悬念路牌、行李摩托；替换通用贴图触发规则，贴图点击支持分类型 +1、总次数、粒子爆发和 5/10 次升级效果。
- 升级北往播放器为返乡公路票根主题：票根纹理、公路虚线、路牌式按钮、风声/路障静音按钮、摩托进度标记和主题标签。
- 时间轴跳转高光时自动把播放器滚回可视区域，降低贴图被裁切的概率。
- 浏览器验证通过：episode=3 加载 theme-road，新贴图可见可点击，04:30 交通工具选择会投放贼摇滚摩托贴图，控制台无错误。

## 下一步建议

- 下一步建议先修通赛方大模型接口配置，确保主题策略 JSON 能真实由模型生成；同时把贴图策略从前端常量抽到后端策略接口，便于每部剧独立生成和复核。
- 再往后补用户/弹幕持久化表，把当前本地点赞回复升级成数据库记录，为登录体系做准备。

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
