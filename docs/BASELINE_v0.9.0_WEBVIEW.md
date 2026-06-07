# v0.9.0-webview-baseline

更新时间：2026-06-07

## 版本目的

本版本是进入原生 Android App 尝试前的稳定回滚点。当前产品已经具备 Web 移动端、FastAPI 服务端、Capacitor Android 壳、临时公网访问和 APK 分发能力。后续如果原生 App 尝试成本过高或效果不理想，应回滚到本 tag，继续强化 Web/Capacitor 体验。

版本 tag：`v0.9.0-webview-baseline`

## 当前产品状态

- 产品名：半句
- 定位：短剧陪伴，围绕短剧播放时的高光互动、弹幕、同看社交、片尾 AI 二创和声音带入体验。
- 当前客户端形态：移动端 Web + Capacitor Android WebView 壳。
- 当前服务端形态：FastAPI 本地服务，临时公网通过 Cloudflare Tunnel 暴露。
- 当前演示重点：北往第一集的观看互动、弹幕治理、片尾 AI 二创、声音资产、同看社交和用户成长体系。

## 已完成能力

- 短剧列表、最近观看、播放页、我的页、聊聊、逛逛等基础客户端框架。
- 登录态、昵称、头像池、头像管理、个人主页和成长展馆。
- 高光点时间轴下发、播放进度触发互动弹层、用户点击上报。
- 弹幕导入、三种弹幕模式、弹幕点击、点赞、回复和治理流程。
- 弹幕分层治理：规则层、时间感知层、语义审核层、聚类去重层、小模型预留、人工复核层。
- 北往、那年冬至等重点剧集的高光复核、贴图时间窗、贴图素材预览和体验配置。
- 同看房间、演示事件注入、成员卡、称号、徽章、答题结果和房间动态。
- 好友系统、好友申请、撤回、审核、历史申请、聊天消息和同看邀请入口。
- 逛逛动态流、发布入口、点赞评论、可见权限和基础内容审核。
- 片尾 AI 二创保底版：三条剧情分支、个性化选项、分镜图片、文字卡、语音播放入口。
- 声音资产服务：上传或录入声音样本，保存 voice profile，预留原版和用户声音带入两种播放方式。
- Android Capacitor 壳 App：包名 `com.banju.shortdrama`，应用名 `半句`。
- 临时公网下载页：`/download.html`。
- APK 下载接口：`/downloads/banju-debug.apk`。

## 当前目录要点

- `backend/`：FastAPI 服务端、接口、模型、迁移、种子数据和业务逻辑。
- `frontend/`：移动端 Web 客户端、样式、贴图、海报、AI 二创图片和前端资源。
- `mobile/banju-android/`：Capacitor Android 壳工程。
- `scripts/`：标注、弹幕导入治理、AI 资源生成、Android 打包、公网隧道脚本。
- `docs/`：项目状态、运行记录、部署说明、AI 二创提示词和本版本基线说明。
- `data/`：本地运行数据和模型缓存。数据库、日志、语音资产等敏感或运行时文件不进入 Git。

## 本地运行方式

启动后端：

```powershell
.\.venv\Scripts\python.exe -m uvicorn backend.app.main:app --host 127.0.0.1 --port 8000
```

打开本地 Web：

```text
http://127.0.0.1:8000/
```

启动临时公网隧道：

```powershell
.\scripts\start_public_tunnel.ps1
```

构建 Android debug APK：

```powershell
.\scripts\build_banju_android_debug.ps1
```

安装到已连接的 Android 手机：

```powershell
.\scripts\install_banju_android_debug.ps1
```

## 公网和 APK 注意事项

当前公网地址是临时隧道地址，隧道重启后 URL 可能变化。Capacitor App 当前把 WebView 首页指向构建时的公网 HTTPS 地址，如果公网 URL 变化，需要：

1. 修改 `mobile/banju-android/capacitor.config.json` 的 `server.url`。
2. 运行 `npx cap sync android`。
3. 重新构建 APK。

APK 文件和下载目录下的 APK 是构建产物，不作为源码基线长期存储。需要时用脚本重新生成。

## 安全边界

- 不提交 `.env`。
- 不提交 API Key、模型密钥、Bearer Token。
- 不提交本地数据库、日志、语音样本、视频素材、PDF/DOCX 原始材料。
- 不提交 Android keystore、jks、local.properties、Gradle build 缓存和 node_modules。

## 已知限制

- 当前 Android App 本质是 WebView 壳，不是原生 UI。
- 移动端播放页仍有较多 Web 长页面结构，真机观看体验还需要专项适配。
- 同看和社交是本地演示级实现，尚未接入生产级账号体系、权限和消息推送。
- AI 二创当前以图片分镜和语音为主，未接入稳定的视频生成链路。
- 公网部署使用临时隧道，不适合长期线上运行。

## 原生 App 试验边界

原生尝试建议先做最小闭环，不一次性重写全部功能：

1. 原生首页和选剧页。
2. 原生播放页壳，调用现有后端接口。
3. 视频播放、暂停、进度监听。
4. 高光点触发一个原生互动弹层。
5. 保留 Web/服务端作为数据和功能基线。

如果原生试验超过预期成本，应停止扩张，回到本基线继续做 Web/Capacitor 移动端体验优化。

## 回滚方式

查看 tag：

```powershell
git fetch origin --tags
git tag --list "v0.9.0-webview-baseline"
```

回到本版本查看：

```powershell
git checkout v0.9.0-webview-baseline
```

如需从本版本重新开分支：

```powershell
git checkout -b codex/native-android-experiment v0.9.0-webview-baseline
```
