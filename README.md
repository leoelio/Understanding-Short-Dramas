# 基于短剧剧情理解的即时互动激发系统

这是一个面向移动端 Web 的短剧互动 MVP：服务端管理短剧、剧集、高光点和互动数据；客户端播放短剧时按时间轴触发低门槛互动组件；后台展示高光点互动效果。

## V1 范围

- 短剧列表和剧集播放
- 每部短剧按集数排序导入前 2 集
- 高光点时间轴下发
- 播放进度触发互动组件
- 用户点击互动并上报
- 弹幕评论：沉浸 / 轻聊 / 狂欢三种观看模式，支持字号、速度、区域、透明度调整
- 分类型互动体验：冲突站队、反转震惊、爽点连击、甜蜜心动、虐心共情、悬念预测等
- 后台统计总点击量、选项比例和高光排行榜
- 标注复核工作台：筛选待复核剧集、编辑高光 JSON、保存人工复核结果
- 预留大模型标注字段：来源、置信度、模型版本
- 大模型离线标注链路：生成输入、模型标注、人工复核、写回数据库

## 本地运行

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
```

打开：

- 客户端：http://127.0.0.1:8000/
- 后台统计：http://127.0.0.1:8000/#admin
- 标注复核：http://127.0.0.1:8000/#review
- API 文档：http://127.0.0.1:8000/docs

首次启动会扫描 `视频库`，每部短剧导入前 2 集，并生成演示高光点。
其中《云渺1：我修仙多年强亿点怎么了》第 1-2 集会使用已复核的真实高光 fixture，便于演示“大模型 + 人工复核”的闭环。

## 大模型标注链路

详见 [docs/annotation_pipeline.md](docs/annotation_pipeline.md)。

最小验证流程：

```powershell
.\.venv\Scripts\python.exe scripts\prepare_annotation_input.py --episode-id 1
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json --dry-run
.\.venv\Scripts\python.exe scripts\apply_annotations.py --file data\annotations\episode_1_llm.json --replace --source human_review
```

真实调用大模型前，需要把 `.env.example` 复制为 `.env`，并填写 `ARK_API_KEY` 与 `ARK_MODEL`。

## 复核进度接口

- `GET /api/admin/review-status`：返回已复核剧集数、待复核剧集数、人工复核高光数等总览数据。
- `GET /api/admin/episodes`：返回每集的 `review_status`、`review_status_label`、`reviewed_highlight_count` 和来源分布。
- `GET /api/admin/episodes/{episode_id}/highlights`：读取单集高光 JSON，供人工复核编辑。
- `PUT /api/admin/episodes/{episode_id}/highlights`：保存人工复核后的高光点，并清理该集旧互动统计。

## 高光分类与节奏

V1.1 使用 8 个一级高光类型：`冲突对抗`、`反转揭秘`、`爽点逆袭`、`甜蜜心动`、`虐心共情`、`悬念钩子`、`搞笑解压`、`危机紧张`。

短剧一集时间较短，标注时遵循“稀疏高光”原则：每集通常保留 3-5 个最强情绪峰值，两个高光点尽量间隔 25 秒以上，普通铺垫和过场不强行触发互动。

## 弹幕接口

- `GET /api/episodes/{episode_id}/danmaku`：读取当前剧集弹幕时间轴。
- `POST /api/danmaku`：发送当前播放时间点弹幕。
- `GET /api/taxonomy/highlights`：读取高光分类、描述和推荐互动形式。

## 项目结构

```text
backend/        FastAPI 服务端
frontend/       移动端 Web 客户端和后台页面
scripts/        数据导入和大模型标注脚本
data/           SQLite 数据库目录
docs/           技术文档
视频库/         原始短剧素材，不进入 Git
```
