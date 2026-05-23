# V1 技术方案

## 当前版本目标

V1 优先证明完整闭环：短剧播放到高光点时自动触发互动组件，用户点击后服务端记录数据，后台可以看到高光点互动效果。

## 核心流程

```mermaid
flowchart TD
    A["本地视频库"] --> B["素材导入脚本"]
    B --> C["SQLite: 短剧 / 剧集 / 高光点"]
    C --> D["FastAPI 接口"]
    D --> E["移动端 Web 播放页"]
    D --> K["标注复核工作台"]
    E --> F["监听播放进度"]
    F --> G["触发互动组件"]
    G --> H["用户点击"]
    H --> I["互动上报"]
    I --> J["后台统计"]
    K --> C
```

## 数据来源策略

- V1：演示高光点由规则生成，字段标记为 `manual_seed`。
- V1.1：接入大模型离线标注，人工复核后写入数据库，字段标记为 `human_review`。
- V2：当人工复核数据足够后，再训练小模型。

## 标注复核工作台

- 用 `/api/admin/episodes` 查看每集的复核状态和高光来源分布。
- 用 `/api/admin/review-status` 展示已复核、待复核、人工高光数量。
- 用 `/api/admin/episodes/{episode_id}/highlights` 读取和保存单集高光 JSON。
- 第一版把 `human_review` 作为“已复核”的判断标准；`manual_seed` 和模型初稿都进入待复核队列。

## 关键字段

- `source`：标注来源，例如 `manual_seed`、`llm`、`human_review`。
- `confidence`：高光点置信度。
- `model_version`：模型或标注策略版本。
- `highlight_type`：爽点、反转、冲突、甜蜜、虐点等。
- `emotion`：爽、震惊、愤怒、心动、心疼等。
