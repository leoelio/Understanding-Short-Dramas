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

2. 生成关键帧复核图：

```powershell
.\.venv\Scripts\python.exe scripts\extract_review_frames.py --episode-id 1 --interval 5
```

输出位置类似：

```text
data/context/episode_1/contact_sheet_5s.jpg
```

3. 人工补充片段信息：

打开输入 JSON，给每个 segment 补充：

- `subtitle_text`：该时间段字幕或台词
- `visual_note`：画面里发生了什么
- `audio_note`：音乐、尖叫、沉默、重音等提示
- `manual_note`：你作为产品负责人的补充判断

4. 调用大模型生成候选标注：

```powershell
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json
```

如果输入里没有字幕、画面描述、音频提示或人工备注，脚本会拒绝真实调用，防止模型只根据剧名脑补剧情。

如果只是验证链路，不调用模型：

```powershell
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json --dry-run
```

如果只是测试大模型接口连通性，可以显式允许空上下文：

```powershell
.\.venv\Scripts\python.exe scripts\annotate_with_llm.py --input data\annotation_inputs\episode_1.json --allow-empty-context
```

连通性测试结果不能直接入库。

5. 人工复核输出：

检查 `data/annotations/episode_1_llm.json`，重点确认：

- 时间点是否准确
- 类型和情绪是否合理
- 按钮文案是否短、准、有情绪
- 是否会打断关键剧情
- 是否保持“稀疏高光”：一集通常 3-5 个强峰值，两个高光点尽量间隔 25 秒以上

6. 写回数据库：

```powershell
.\.venv\Scripts\python.exe scripts\apply_annotations.py --file data\annotations\episode_1_llm.json --replace --source human_review
```

7. 生成贴图建议 JSON：

```powershell
.\.venv\Scripts\python.exe scripts\generate_sticker_suggestions_with_llm.py --episode-id 19 --require-llm
```

输出位置类似：

```text
frontend/assets/sticker_suggestions/episode_19_sticker_suggestions.json
```

这个文件不会直接写库。打开复核页后，点击“加载建议文件”，再点击“导入并合并”，逐项检查时间、贴图和遮挡风险，最后点击“保存体验配置”才会写入服务端。

## 输出 JSON 格式

`highlight_type` 使用固定 8 类：`冲突对抗`、`反转揭秘`、`爽点逆袭`、`甜蜜心动`、`虐心共情`、`悬念钩子`、`搞笑解压`、`危机紧张`。

“名场面”只作为强动效或展示标签理解，不作为一级分类，避免和爽点、反转、冲突重复。

```json
{
  "episode_id": 1,
  "prompt_version": "highlight-annotation-v2",
  "highlights": [
    {
      "start_time_sec": 38.2,
      "end_time_sec": 46.2,
      "title": "剧情突然反转",
      "description": "主角身份或局势发生反转，适合触发震惊类互动。",
      "highlight_type": "反转揭秘",
      "emotion": "震惊",
      "confidence": 0.86,
      "reason": "用户通常会在反转出现时产生强表达欲。",
      "evidence_segment_ids": [3],
      "evidence_text": "来自第 3 段字幕或画面描述的证据。",
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
