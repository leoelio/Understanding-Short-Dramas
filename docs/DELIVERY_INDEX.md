# 半句项目交付文档总索引

更新时间：2026-06-11

## 文档定位

这个目录用于沉淀“半句：基于短剧剧情理解的即时互动激发系统”的比赛交付、答辩讲解、部署运行和后续迭代资料。

当前分支定位为文档开发分支。功能代码可以继续演进，但文档需要始终回答三个问题：

- 这个产品解决什么问题。
- 当前版本已经做到什么程度。
- 如何运行、演示、解释和继续迭代。

## 推荐阅读顺序

1. [PROJECT_STATUS.md](PROJECT_STATUS.md)
   当前项目快照。用于让人或 AI 快速接手，包含数据状态、已完成能力、最近变更和下一步建议。

2. [PROJECT_DELIVERY_MANUAL.md](PROJECT_DELIVERY_MANUAL.md)
   交付总说明。用于比赛提交、老师/评委快速理解项目，也可作为 README 的扩展版。

3. [PRESENTATION_SCRIPT.md](PRESENTATION_SCRIPT.md)
   录屏脚本和答辩讲解稿。用于准备展示视频和现场讲解。

4. [DEMO_CHECKLIST.md](DEMO_CHECKLIST.md)
   答辩演示检查清单。用于演示前逐项检查服务、素材、播放、弹幕、二创、同看和复核页。

5. [API_REFERENCE.md](API_REFERENCE.md)
   服务端接口说明。按业务域说明登录、短剧、播放、高光、弹幕、同看、社交、二创、声音和后台接口。

6. [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)
   数据库说明。解释核心表、关系、JSON 字段、初始化、备份和后续生产化方向。

7. [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
   部署与运行说明。覆盖本地运行、临时公网演示、声音服务、模型调用和 Android WebView 壳。

8. [MODEL_USAGE.md](MODEL_USAGE.md)
   模型使用说明。解释大模型、小模型预留、弹幕治理、片尾 AI 二创和声音资产服务。

9. [AI_ASSISTED_DEVELOPMENT.md](AI_ASSISTED_DEVELOPMENT.md)
   AI 辅助开发说明。解释 AI 如何参与产品、代码、UI、资产、模型和文档开发。

10. [architecture.md](architecture.md)
   技术方案和核心闭环说明。

11. [PRIVACY_AND_CONTENT_SAFETY.md](PRIVACY_AND_CONTENT_SAFETY.md)
   隐私与内容安全说明。解释声音授权、头像照片、弹幕治理、社交内容、AI 二创声明和上线前安全要求。

12. [PRODUCTION_PLAN.md](PRODUCTION_PLAN.md)
   正式上线迁移方案。说明云服务器、PostgreSQL、对象存储、HTTPS、备份、监控和回滚方案。

13. [annotation_pipeline.md](annotation_pipeline.md)
   大模型高光标注、人工复核、写回数据库的流程。

14. [BEIWANG_EP1_REMIX_27_PROMPTS.md](BEIWANG_EP1_REMIX_27_PROMPTS.md)
   北往第一集片尾 AI 二创 27 图方案与提示词。

15. [BEIWANG_EP1_REMIX_AUDIO_LINES.md](BEIWANG_EP1_REMIX_AUDIO_LINES.md)
    北往第一集片尾二创语音台词清单。

16. [V2_ROADMAP.md](V2_ROADMAP.md) 和 [future_ai_extensions.md](future_ai_extensions.md)
    后续迭代方向。

## 交付材料状态

| 材料 | 状态 | 文件 |
| --- | --- | --- |
| 项目 README | 已有，已同步当前定位 | [../README.md](../README.md) |
| 当前项目状态 | 已有，持续更新 | [PROJECT_STATUS.md](PROJECT_STATUS.md) |
| 运行记录 | 已有，持续追加 | [RUN_LOG.md](RUN_LOG.md) |
| 交付总说明 | 已完成第一版 | [PROJECT_DELIVERY_MANUAL.md](PROJECT_DELIVERY_MANUAL.md) |
| 录屏脚本 | 已完成第一版 | [PRESENTATION_SCRIPT.md](PRESENTATION_SCRIPT.md) |
| 答辩讲稿 | 已完成第一版 | [PRESENTATION_SCRIPT.md](PRESENTATION_SCRIPT.md) |
| 演示检查清单 | 已完成第一版 | [DEMO_CHECKLIST.md](DEMO_CHECKLIST.md) |
| 接口说明 | 已完成第一版 | [API_REFERENCE.md](API_REFERENCE.md) |
| 数据库说明 | 已完成第一版 | [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) |
| 部署说明 | 已完成第一版 | [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) |
| 技术架构 | 已同步当前真实架构 | [architecture.md](architecture.md) |
| 模型使用说明 | 已完成第一版 | [MODEL_USAGE.md](MODEL_USAGE.md) |
| AI 辅助开发说明 | 已完成第一版 | [AI_ASSISTED_DEVELOPMENT.md](AI_ASSISTED_DEVELOPMENT.md) |
| 隐私与内容安全 | 已完成第一版 | [PRIVACY_AND_CONTENT_SAFETY.md](PRIVACY_AND_CONTENT_SAFETY.md) |
| 正式上线迁移方案 | 已完成第一版 | [PRODUCTION_PLAN.md](PRODUCTION_PLAN.md) |

## 文档开发原则

- 不写任何 API Key、账号密码、私密素材路径或不可公开的原始内容。
- 每次重要开发后运行状态更新脚本，保持 [PROJECT_STATUS.md](PROJECT_STATUS.md) 和 [RUN_LOG.md](RUN_LOG.md) 最新。
- 文档优先讲清楚产品闭环，再讲技术细节。
- 对比赛展示而言，优先保证“能看懂、能运行、能演示、能答辩”。
- 对后续开发而言，接口、数据表、模型输入输出和部署步骤必须可复现。

## 下一批文档任务

1. 根据后续功能变化继续维护 [architecture.md](architecture.md)、[PRIVACY_AND_CONTENT_SAFETY.md](PRIVACY_AND_CONTENT_SAFETY.md)、[MODEL_USAGE.md](MODEL_USAGE.md) 和 [PRODUCTION_PLAN.md](PRODUCTION_PLAN.md)。
2. 若开始真正部署，补充具体云厂商配置截图、Nginx 配置文件和迁移脚本运行记录。
