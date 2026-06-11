# 文档开发路线

更新时间：2026-06-11

## 目标

当前分支用于把“半句”从可运行项目整理成可交付、可答辩、可接手迭代的工程材料。

文档按三类读者设计：

- 产品负责人和评委：快速理解产品价值、演示路径和创新点。
- 开发者：知道接口、数据表、运行方式和后续扩展点。
- 后续 AI 或团队成员：能根据状态文档继续推进，不需要重新猜项目背景。

## 已完成

| 文档 | 状态 | 说明 |
| --- | --- | --- |
| `DELIVERY_INDEX.md` | 已完成第一版 | 文档总入口和阅读顺序 |
| `PROJECT_DELIVERY_MANUAL.md` | 已完成第一版 | 产品、系统、功能、演示和限制说明 |
| `PRESENTATION_SCRIPT.md` | 已完成第一版 | 录屏脚本、答辩稿和常见问答 |
| `API_REFERENCE.md` | 已完成第一版 | 主要接口按业务域归档 |
| `DATABASE_SCHEMA.md` | 已完成第一版 | 核心表、关系、数据流和生产化建议 |
| `DEPLOYMENT_GUIDE.md` | 已完成第一版 | 本地运行、临时公网、声音服务、模型和 Android 壳 |
| `MODEL_USAGE.md` | 已完成第一版 | 大模型、小模型预留、弹幕治理、二创和声音资产 |
| `AI_ASSISTED_DEVELOPMENT.md` | 已完成第一版 | AI 辅助产品、代码、UI、资产、模型和文档开发过程 |
| `DEMO_CHECKLIST.md` | 已完成第一版 | 答辩前服务、素材、播放、二创、同看和复核页检查 |
| `architecture.md` | 已更新 | 当前 Web 主线、20 集 LLM 升级、七层弹幕治理、小半陪看、二创和声音缓存链路 |
| `PRIVACY_AND_CONTENT_SAFETY.md` | 已完成第一版 | 声音授权、头像照片、弹幕社交治理、AI 二创声明和上线前安全要求 |
| `PRODUCTION_PLAN.md` | 已完成第一版 | 云服务器、PostgreSQL、对象存储、HTTPS、备份、监控和正式上线迁移方案 |
| `PROJECT_STATUS.md` | 持续更新 | 项目快照 |
| `RUN_LOG.md` | 持续更新 | 每轮运行记录 |

## P0：交付必需

当前已完成第一轮 P0 骨架：

1. 项目交付总说明。
2. 录屏脚本和答辩讲稿。
3. API 接口说明。
4. 数据库说明。
5. 部署与运行说明。
6. 模型使用说明。
7. AI 辅助开发说明。
8. 演示检查清单。
9. README 入口同步。

后续 P0 只做维护：每次功能变化后同步接口、表结构和运行方式。

## P1：技术亮点补强

下一步建议：

1. 继续维护 `architecture.md`、`MODEL_USAGE.md`、`PRIVACY_AND_CONTENT_SAFETY.md` 和 `PRODUCTION_PLAN.md`
   - 每次新增模型链路、用户资产、社交能力或审核策略后同步。

2. 如果开始实际部署，补充：
   - 具体云服务器配置。
   - Nginx 配置样例。
   - PostgreSQL 迁移脚本说明。
   - 对象存储上传脚本说明。
   - 备份恢复演练记录。

## P2：上线和长期迭代

后续如果准备真实上线，需要补：

1. `NATIVE_APP_MIGRATION_PLAN.md`
   - Android/iOS 原生迁移边界。
   - Web 与原生接口复用策略。
   - 播放器、全屏、语音、同看同步的原生实现计划。

## 更新规则

- 每次新增或修改接口：同步 `API_REFERENCE.md`。
- 每次新增或修改表字段：同步 `DATABASE_SCHEMA.md`。
- 每次运行方式、隧道、服务依赖变化：同步 `DEPLOYMENT_GUIDE.md`。
- 每次新增模型链路：同步后续 `MODEL_USAGE.md`。
- 每次结束一个阶段：运行 `scripts/update_project_status.py`。
- 不在任何文档里写真实密钥、账号密码、私密素材路径或不可公开原始内容。
