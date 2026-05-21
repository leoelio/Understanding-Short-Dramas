# 大模型高光标注链路

## 目标

把“人工看剧找高光”变成可追踪的半自动流程：先生成剧集片段输入，再由大模型输出候选高光点，最后人工复核后写回数据库。

## 为什么第一版不直接全自动

短剧高光依赖剧情上下文。当前素材只有 mp4，没有稳定字幕或 ASR 文本时，大模型很容易凭空猜剧情。因此 V1 采用：

```text
视频素材 -> 分段输入 JSON -> 人工补充字幕/画面备注 -> 大模型标注 -> 人工复核 -> 写回数据库
```

这样既能体现 AI 能力，也能保证演示时高光点准确。

## 环境变量

复制 `.env.example` 为 `.env`，填写：

```text
ARK_API_KEY=你的方舟 API Key
ARK_MODEL=你的模型或接入点 ID
ARK_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
```

火山引擎文档说明 OpenAI 兼容调用可使用 Chat Completions 风格接口；方舟对话接口路径是 `/api/v3/chat/completions`。

## 使用步骤

1. 生成待标注输入：

```powershell
.\.venv\Scripts\python.exe scripts\prepare_annotation_input.py --episode-id 1
```

输出位置类似：

```text
data/annotation_inputs/episode_1.json
```

2. 人工补充片段信息：

打开输入 JSON，给每个 segment 补充：

- `subtitle_text`：该时间段字幕或台词
- `visual_note`：画面里发生了什么
- `audio_note`：音乐、尖叫、沉默、重音等提示
- `manual_note`：你作为产品负责人的补充判断

3. 调用大模型生成候选标注：

```powershell
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json
```

如果只是验证链路，不调用模型：

```powershell
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json --dry-run
```

4. 人工复核输出：

检查 `data/annotations/episode_1_llm.json`，重点确认：

- 时间点是否准确
- 类型和情绪是否合理
- 按钮文案是否短、准、有情绪
- 是否会打断关键剧情

5. 写回数据库：

```powershell
.\.venv\Scripts\python.exe scripts\apply_annotations.py --file data\annotations\episode_1_llm.json --replace --source human_review
```

## 输出 JSON 格式

```json
{
  "episode_id": 1,
  "prompt_version": "highlight-annotation-v1",
  "highlights": [
    {
      "start_time_sec": 38.2,
      "end_time_sec": 46.2,
      "title": "剧情突然反转",
      "description": "主角身份或局势发生反转，适合触发震惊类互动。",
      "highlight_type": "反转",
      "emotion": "震惊",
      "confidence": 0.86,
      "reason": "用户通常会在反转出现时产生强表达欲。",
      "options": [
        { "key": "shock", "label": "震惊" },
        { "key": "unexpected", "label": "还能这样" },
        { "key": "rewatch", "label": "倒回去看" }
      ]
    }
  ]
}
```

## 后续升级

- 接入字幕提取或 ASR，减少人工补充工作。
- 支持关键帧描述，提升画面理解能力。
- 增加后台复核页面，避免直接编辑 JSON。
- 积累复核数据后，再训练小模型。

