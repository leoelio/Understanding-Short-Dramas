# 基于短剧剧情理解的即时互动激发系统

这是一个面向移动端 Web 的短剧互动 MVP：服务端管理短剧、剧集、高光点和互动数据；客户端播放短剧时按时间轴触发低门槛互动组件；后台展示高光点互动效果。

## V1 范围

- 短剧列表和剧集播放
- 每部短剧按集数排序导入前 2 集
- 高光点时间轴下发
- 播放进度触发互动组件
- 用户点击互动并上报
- 后台统计总点击量、选项比例和高光排行榜
- 预留大模型标注字段：来源、置信度、模型版本
- 大模型离线标注链路：生成输入、模型标注、人工复核、写回数据库

## 本地运行

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --reload --host 127.0.0.1 --port 8000
```

打开：

- 客户端：http://127.0.0.1:8000/
- 后台统计：http://127.0.0.1:8000/#admin
- API 文档：http://127.0.0.1:8000/docs

首次启动会扫描 `视频库`，每部短剧导入前 2 集，并生成演示高光点。

## 大模型标注链路

详见 [docs/annotation_pipeline.md](docs/annotation_pipeline.md)。

最小验证流程：

```powershell
.\.venv\Scripts\python.exe scripts\prepare_annotation_input.py --episode-id 1
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json --dry-run
.\.venv\Scripts\python.exe scripts\apply_annotations.py --file data\annotations\episode_1_llm.json --replace --source human_review
```

真实调用大模型前，需要把 `.env.example` 复制为 `.env`，并填写 `ARK_API_KEY` 与 `ARK_MODEL`。

## 项目结构

```text
backend/        FastAPI 服务端
frontend/       移动端 Web 客户端和后台页面
scripts/        数据导入和大模型标注脚本
data/           SQLite 数据库目录
docs/           技术文档
视频库/         原始短剧素材，不进入 Git
```
