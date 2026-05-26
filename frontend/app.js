const DEFAULT_DANMAKU_SETTINGS = {
  mode: "light",
  fontSize: "medium",
  speed: "normal",
  area: "top",
  opacity: 82,
};

const MIN_INTERACTION_GAP_SEC = 18;

const DANMAKU_MODES = {
  light: { label: "轻聊", enabled: true, density: 0.72, includeModes: ["light"] },
  carnival: { label: "狂欢", enabled: true, density: 1, includeModes: ["light", "curated", "seed", "carnival"] },
  immerse: { label: "沉浸", enabled: false, density: 0, includeModes: [] },
};

const PLAYER_THEMES = {
  road: {
    name: "北往·返乡烟火",
    match: ["北往", "回家", "返乡"],
    className: "theme-road",
    playIcon: "▶",
    pauseIcon: "Ⅱ",
    muteIcon: "风",
    mutedIcon: "障",
    accent: "#ff7a30",
    soft: "#f2e2c4",
    badge: "返乡烟火 · 年三十",
    signal: "讨薪现金 / 家用电话 / 路边烟雾 / 行李摩托",
  },
  xianxia: {
    name: "仙侠灵光",
    match: ["修仙", "云渺", "仙"],
    className: "theme-xianxia",
    playIcon: "◆",
    pauseIcon: "Ⅱ",
    muteIcon: "◌",
    mutedIcon: "×",
    accent: "#8bd3ff",
    soft: "#e7c6ff",
    badge: "灵光剧情场",
    signal: "法阵 / 灵气 / 身份悬念",
  },
  city: {
    name: "都市霓虹",
    match: [],
    className: "theme-city",
    playIcon: "▶",
    pauseIcon: "Ⅱ",
    muteIcon: "♪",
    mutedIcon: "×",
    accent: "#ff4f64",
    soft: "#12d6b0",
    badge: "都市情绪场",
    signal: "霓虹 / 消息气泡 / 反转光带",
  },
};

const HIGHLIGHT_UI = {
  conflict: {
    label: "冲突对抗",
    aliases: ["冲突", "冲突对抗", "对抗", "争执", "打斗"],
    badge: "站",
    action: "实时站队",
    className: "type-conflict",
    effect: "split",
    padText: "站队开麦",
    burstWords: ["站左边", "站右边", "别怂", "正面刚"],
  },
  reveal: {
    label: "反转揭秘",
    aliases: ["反转", "反转揭秘", "揭秘", "真相"],
    badge: "惊",
    action: "震惊反应",
    className: "type-reveal",
    effect: "tapstorm",
    padText: "牛逼！狂点",
    burstWords: ["牛逼", "我天", "反转了", "还能这样"],
  },
  power: {
    label: "爽点逆袭",
    aliases: ["爽点", "爽点逆袭", "逆袭", "打脸", "名场面", "高能名场面"],
    badge: "燃",
    action: "爽值连击",
    className: "type-power",
    effect: "tapstorm",
    padText: "爽！连击",
    burstWords: ["爽", "解气", "继续", "打脸"],
  },
  sweet: {
    label: "甜蜜心动",
    aliases: ["甜蜜", "甜蜜心动", "撒糖", "心动"],
    badge: "甜",
    action: "心动反应",
    className: "type-sweet",
    effect: "hearts",
    padText: "心动冒泡",
    burstWords: ["磕到了", "心动", "好甜", "在一起"],
  },
  tear: {
    label: "虐心共情",
    aliases: ["虐点", "虐心", "虐心共情", "悲伤感动", "共情"],
    badge: "泪",
    action: "情绪共情",
    className: "type-tear",
    effect: "drops",
    padText: "抱抱一下",
    burstWords: ["心疼", "破防", "抱抱", "别哭"],
  },
  suspense: {
    label: "悬念钩子",
    aliases: ["悬念", "悬念钩子", "悬疑反转", "线索", "钩子"],
    badge: "猜",
    action: "剧情预测",
    className: "type-suspense",
    effect: "clue",
    padText: "展开线索",
    burstWords: ["有线索", "要反转", "接下来呢", "别断"],
  },
  comedy: {
    label: "搞笑解压",
    aliases: ["搞笑", "搞笑解压", "笑点", "喜剧"],
    badge: "哈",
    action: "一起笑",
    className: "type-comedy",
    effect: "laugh",
    padText: "鹅鹅鹅",
    burstWords: ["鹅鹅鹅", "笑死", "绷不住", "太离谱"],
  },
  danger: {
    label: "危机紧张",
    aliases: ["危机", "危机紧张", "危险", "紧张"],
    badge: "急",
    action: "紧张值",
    className: "type-danger",
    effect: "heartbeat",
    padText: "心跳加速",
    burstWords: ["快跑", "小心", "别过去", "屏住"],
  },
};

function loadDanmakuSettings() {
  try {
    return { ...DEFAULT_DANMAKU_SETTINGS, ...JSON.parse(localStorage.getItem("danmaku_settings") || "{}") };
  } catch {
    return { ...DEFAULT_DANMAKU_SETTINGS };
  }
}

const state = {
  dramas: [],
  episodes: [],
  currentEpisode: null,
  sessionId: localStorage.getItem("session_id") || crypto.randomUUID(),
  firedHighlights: new Set(),
  firedDanmaku: new Set(),
  activeHighlight: null,
  hideTimer: null,
  lastInteractionTime: -Infinity,
  danmaku: [],
  danmakuSettings: loadDanmakuSettings(),
  reviewEpisodes: [],
  reviewFilter: "all",
  danmakuFeedbackTimer: null,
  interactionMode: "choice",
  tapHideDelayMs: 2200,
  stickerSerial: 0,
  ambientStickerTimer: null,
  stickerHideTimers: new Map(),
  stickerCombo: 0,
  danmakuActionTimer: null,
};

localStorage.setItem("session_id", state.sessionId);

const $ = (selector) => document.querySelector(selector);

const views = {
  home: $("#homeView"),
  watch: $("#watchView"),
  admin: $("#adminView"),
  review: $("#reviewView"),
};

const homeTab = $("#homeTab");
const adminTab = $("#adminTab");
const reviewTab = $("#reviewTab");
const player = $("#player");
const interactionLayer = $("#interactionLayer");
const danmakuLayer = $("#danmakuLayer");
const stickerLayer = $("#stickerLayer");
const playToggle = $("#playToggle");
const muteToggle = $("#muteToggle");
const progressSlider = $("#progressSlider");
const playerTime = $("#playerTime");

const HIGHLIGHT_ALIAS_TO_KEY = Object.fromEntries(
  Object.entries(HIGHLIGHT_UI).flatMap(([key, config]) => config.aliases.map((alias) => [alias, key]))
);

function escapeHTML(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function getHighlightUI(type) {
  return HIGHLIGHT_UI[getHighlightKey(type)];
}

function getDanmakuMode() {
  return DANMAKU_MODES[state.danmakuSettings.mode] || DANMAKU_MODES.light;
}

function getHighlightKey(type) {
  return HIGHLIGHT_ALIAS_TO_KEY[type] || "power";
}

function saveDanmakuSettings() {
  localStorage.setItem("danmaku_settings", JSON.stringify(state.danmakuSettings));
}

async function fetchJSON(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `请求失败：${response.status}`);
  }
  return response.json();
}

function errorMessage(error) {
  try {
    const payload = JSON.parse(error.message);
    return payload?.detail?.message || payload?.detail || error.message;
  } catch {
    return error.message;
  }
}

function setDanmakuFeedback(message, isError = false) {
  const feedback = $("#danmakuFeedback");
  if (!feedback) return;
  window.clearTimeout(state.danmakuFeedbackTimer);
  feedback.textContent = message;
  feedback.classList.toggle("error", isError);
  if (message) {
    state.danmakuFeedbackTimer = window.setTimeout(() => {
      feedback.textContent = "";
      feedback.classList.remove("error");
    }, 3600);
  }
}

function setView(name) {
  Object.entries(views).forEach(([key, element]) => element.classList.toggle("active", key === name));
  homeTab.classList.toggle("active", name === "home");
  adminTab.classList.toggle("active", name === "admin");
  reviewTab.classList.toggle("active", name === "review");
  if (name !== "watch") {
    window.clearTimeout(state.ambientStickerTimer);
  } else {
    scheduleAmbientStickers();
  }
  if (name === "admin") {
    loadStats();
  }
  if (name === "review") {
    loadReviewEpisodes();
  }
}

function syncEpisodeUrl(episodeId) {
  const url = new URL(window.location.href);
  url.searchParams.set("episode", String(episodeId));
  url.hash = "";
  history.replaceState(null, "", url);
}

function formatTime(seconds) {
  const safe = Math.max(0, Math.round(seconds || 0));
  const min = Math.floor(safe / 60).toString().padStart(2, "0");
  const sec = (safe % 60).toString().padStart(2, "0");
  return `${min}:${sec}`;
}

function getPlayerTheme(dramaTitle = "") {
  return (
    Object.values(PLAYER_THEMES).find((theme) => theme.match.some((keyword) => dramaTitle.includes(keyword))) ||
    PLAYER_THEMES.city
  );
}

function applyPlayerTheme(episode) {
  const theme = getPlayerTheme(episode?.drama?.title || "");
  const shell = document.querySelector(".app-shell");
  shell.classList.remove(...Object.values(PLAYER_THEMES).map((item) => item.className));
  shell.classList.add(theme.className);
  shell.style.setProperty("--player-accent", theme.accent);
  shell.style.setProperty("--player-soft", theme.soft);
  $("#watchGenre").textContent = `${episode.drama.genre || "短剧播放"} · ${theme.name}`;
  const badge = $("#themeBadge");
  if (badge) {
    badge.textContent = theme.badge || theme.name;
    badge.title = theme.signal || "";
  }
  updatePlayerControls();
}

function updatePlayerControls() {
  const theme = getPlayerTheme(state.currentEpisode?.drama?.title || "");
  playToggle.textContent = player.paused ? theme.playIcon : theme.pauseIcon;
  muteToggle.textContent = player.muted ? theme.mutedIcon : theme.muteIcon;
  const duration = Number.isFinite(player.duration) ? player.duration : state.currentEpisode?.duration_sec || 0;
  const ratio = duration ? Math.min(1000, Math.max(0, (player.currentTime / duration) * 1000)) : 0;
  progressSlider.value = String(ratio);
  progressSlider.style.setProperty("--progress", `${ratio / 10}%`);
  $("#themeControls")?.style.setProperty("--progress", `${ratio / 10}%`);
  playerTime.textContent = `${formatTime(player.currentTime)} / ${formatTime(duration)}`;
}

function stableRatio(value) {
  const text = String(value);
  let hash = 0;
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) % 997;
  }
  return hash / 997;
}

function applyDanmakuSettings() {
  const settings = state.danmakuSettings;
  const mode = getDanmakuMode();
  document.querySelectorAll(".mode-button").forEach((button) => {
    button.classList.toggle("active", button.dataset.mode === settings.mode);
  });
  $("#danmakuFontSize").value = settings.fontSize;
  $("#danmakuSpeed").value = settings.speed;
  $("#danmakuArea").value = settings.area;
  $("#danmakuOpacity").value = settings.opacity;
  danmakuLayer.className = `danmaku-layer area-${settings.area} size-${settings.fontSize}`;
  danmakuLayer.style.setProperty("--danmaku-opacity", String(settings.opacity / 100));
  danmakuLayer.classList.toggle("disabled", !mode.enabled);
  if (!mode.enabled) danmakuLayer.innerHTML = "";
  $("#danmakuInput").disabled = !mode.enabled;
  $("#danmakuForm").classList.toggle("disabled", !mode.enabled);
  saveDanmakuSettings();
}

function clearDanmakuLayer() {
  state.firedDanmaku.clear();
  danmakuLayer.innerHTML = "";
}

function shouldShowDanmaku(comment) {
  const mode = getDanmakuMode();
  const commentMode = comment.mode || "light";
  return (
    mode.enabled &&
    mode.includeModes.includes(commentMode) &&
    stableRatio(comment.id || `${comment.time_sec}-${comment.text}`) <= mode.density
  );
}

function danmakuDuration() {
  if (state.danmakuSettings.speed === "slow") return 9800;
  if (state.danmakuSettings.speed === "fast") return 5200;
  return 7400;
}

function danmakuKey(comment) {
  return `danmaku_${comment.id || `${comment.time_sec}_${comment.text}`}`;
}

function danmakuStats(comment) {
  try {
    return JSON.parse(localStorage.getItem(danmakuKey(comment)) || "{}");
  } catch {
    return {};
  }
}

function saveDanmakuStats(comment, stats) {
  localStorage.setItem(danmakuKey(comment), JSON.stringify(stats));
}

function danmakuUser(comment) {
  const names = ["路过观众", "追剧搭子", "年味观众", "情绪观察员", "返乡同路人", "剧情雷达"];
  return (
    comment.user?.nickname ||
    comment.user_name ||
    names[Math.floor(stableRatio(comment.session_id || comment.id || comment.text) * names.length)]
  );
}

function showDanmakuActions(comment, anchor) {
  document.querySelector(".danmaku-action-popover")?.remove();
  window.clearTimeout(state.danmakuActionTimer);
  const stats = { likes: 0, replies: [], ...danmakuStats(comment) };
  const popover = document.createElement("div");
  popover.className = "danmaku-action-popover";
  popover.innerHTML = `
    <div>
      <strong>${escapeHTML(danmakuUser(comment))}</strong>
      <p>${escapeHTML(comment.text)}</p>
    </div>
    <div class="danmaku-actions">
      <button type="button" data-action="like">赞 ${stats.likes || 0}</button>
      <button type="button" data-action="reply">回复</button>
      <button type="button" data-action="follow">关注预留</button>
    </div>
    <form class="danmaku-reply-form hidden">
      <input maxlength="32" placeholder="回复这条弹幕" />
      <button type="submit">发送</button>
    </form>
  `;
  const wrap = document.querySelector(".video-wrap");
  const rect = anchor.getBoundingClientRect();
  const hostRect = wrap.getBoundingClientRect();
  popover.style.left = `${Math.min(Math.max(10, rect.left - hostRect.left), hostRect.width - 220)}px`;
  popover.style.top = `${Math.min(Math.max(10, rect.top - hostRect.top + 28), hostRect.height - 112)}px`;
  wrap.appendChild(popover);

  popover.querySelector('[data-action="like"]').addEventListener("click", () => {
    stats.likes = (stats.likes || 0) + 1;
    saveDanmakuStats(comment, stats);
    anchor.querySelector("em").textContent = `♡${stats.likes}`;
    popover.querySelector('[data-action="like"]').textContent = `赞 ${stats.likes}`;
  });
  popover.querySelector('[data-action="reply"]').addEventListener("click", () => {
    popover.querySelector(".danmaku-reply-form").classList.remove("hidden");
    popover.querySelector("input").focus();
  });
  popover.querySelector('[data-action="follow"]').addEventListener("click", () => {
    setDanmakuFeedback("已预留关注/加好友入口，登录后可启用");
  });
  popover.querySelector(".danmaku-reply-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const input = popover.querySelector("input");
    const value = input.value.trim();
    if (!value) return;
    stats.replies = [...(stats.replies || []), { text: value, session_id: state.sessionId, created_at: Date.now() }];
    saveDanmakuStats(comment, stats);
    setDanmakuFeedback(`已回复：${value}`);
    popover.remove();
  });
  state.danmakuActionTimer = window.setTimeout(() => popover.remove(), 5200);
}

function emitDanmaku(comment) {
  if (!shouldShowDanmaku(comment)) return;
  const stats = { likes: 0, ...danmakuStats(comment) };
  const bubble = document.createElement("button");
  bubble.className = `danmaku-item ${comment.className || ""}`;
  bubble.type = "button";
  bubble.innerHTML = `<b>${escapeHTML(danmakuUser(comment))}</b><span>${escapeHTML(comment.text)}</span><em>♡${stats.likes || 0}</em>`;
  const lanes = state.danmakuSettings.area === "full" ? 8 : state.danmakuSettings.area === "middle" ? 4 : 3;
  const lane = Math.floor(stableRatio(`${comment.id}-${comment.text}`) * lanes);
  bubble.style.setProperty("--lane", lane);
  bubble.style.setProperty("--duration", `${danmakuDuration()}ms`);
  bubble.addEventListener("click", (event) => {
    event.stopPropagation();
    showDanmakuActions(comment, bubble);
  });
  danmakuLayer.appendChild(bubble);
  window.setTimeout(() => bubble.remove(), danmakuDuration() + 400);
}

function emitHighlightDanmaku(highlight, ui) {
  const words = ui.burstWords || [ui.label];
  words.slice(0, getDanmakuMode().density >= 1 ? 4 : 2).forEach((text, index) => {
    emitDanmaku({
      id: `highlight-${highlight.id}-${index}`,
      text,
      time_sec: highlight.start_time_sec,
      className: `danmaku-impact ${ui.className}`,
    });
  });
}

async function loadDanmaku(episodeId) {
  state.danmaku = await fetchJSON(`/api/episodes/${episodeId}/danmaku`);
  clearDanmakuLayer();
}

function renderDanmakuHint() {
  const mode = getDanmakuMode();
  $("#danmakuInput").placeholder = mode.enabled ? `${mode.label}：发一条弹幕` : "沉浸模式已关闭弹幕";
}

function extractSceneMotifs(highlight) {
  const source = `${highlight.title || ""} ${highlight.description || ""} ${highlight.evidence_text || ""}`;
  const candidates = [
    "门房",
    "病床",
    "老爷",
    "白衣女子",
    "家族",
    "线索",
    "身份",
    "电话",
    "戒指",
    "合同",
    "钥匙",
    "车",
    "雨",
    "酒",
    "灯",
    "房间",
    "医院",
    "婚礼",
    "剑",
    "系统",
    "讨薪",
    "工友",
    "欠薪",
    "回家",
    "年三十",
    "父母",
    "摩托",
    "行李",
    "摇滚",
  ];
  return candidates.filter((word) => source.includes(word)).slice(0, 3);
}

const STICKER_ASSETS = {
  mealSteam: { src: "/assets/stickers/beiwang_meal_steam.svg", label: "打工人日常" },
  noPayBill: { src: "/assets/stickers/beiwang_no_pay_bill.svg", label: "一分没结" },
  goSign: { src: "/assets/stickers/beiwang_go_sign.svg", label: "走" },
  debtCash: { src: "/assets/stickers/beiwang_debt_cash.svg", label: "欠薪结清" },
  homePhone: { src: "/assets/stickers/beiwang_home_phone.svg", label: "想家了" },
  homeLantern: { src: "/assets/stickers/beiwang_home_lantern.svg", label: "安安全全" },
  smokeQuestion: { src: "/assets/stickers/beiwang_smoke_question.svg", label: "悬着心" },
  wageStamp: { src: "/assets/stickers/beiwang_wage_stamp.svg", label: "欠薪得还" },
  homeTicket: { src: "/assets/stickers/beiwang_home_ticket.svg", label: "年三十到家" },
  roadQuestion: { src: "/assets/stickers/beiwang_road_question.svg", label: "回得去吗" },
  rockWord: { src: "/assets/stickers/beiwang_rock_word.svg", label: "你这叫摇滚啊" },
  rockMoto: { src: "/assets/stickers/beiwang_rock_moto.svg", label: "贼摇滚摩托" },
  northTitle: { src: "/assets/stickers/beiwang_title_north.svg", label: "北往" },
  vehicleTrain: { src: "/assets/stickers/vehicle_train.svg", label: "火车" },
  vehicleCar: { src: "/assets/stickers/vehicle_car.svg", label: "小车" },
  vehicleMotorcycle: { src: "/assets/stickers/vehicle_motorcycle.svg", label: "摩托车" },
  charge: { src: "/assets/stickers/effect_charge.svg", label: "冲" },
  question: { src: "/assets/stickers/effect_question.svg", label: "问号" },
  laugh: { src: "/assets/stickers/effect_laugh.svg", label: "好笑" },
  rock: { src: "/assets/stickers/effect_rock.svg", label: "摇滚" },
  tear: { src: "/assets/stickers/effect_tear.svg", label: "心疼" },
};

const STICKER_RULES = [
  {
    asset: "mealSteam",
    className: "sticker-meal",
    keywords: ["饭桌", "吃饭", "生活味", "日常"],
    tapWords: ["太真实", "开饭", "生活味"],
    positions: [
      { left: "60%", top: "48%" },
      { left: "8%", top: "44%" },
    ],
    durationMs: 2600,
    clickHoldMs: 1600,
  },
  {
    asset: "noPayBill",
    className: "sticker-bill",
    keywords: ["一年到头一分没结", "一分没结", "欠薪", "工资账单"],
    tapWords: ["一分没结", "欠薪不行", "讨回来"],
    positions: [
      { left: "56%", top: "28%" },
      { left: "9%", top: "42%" },
    ],
    durationMs: 3900,
    clickHoldMs: 2300,
  },
  {
    asset: "goSign",
    className: "sticker-go",
    keywords: ["走", "要钱去", "冲"],
    tapWords: ["走", "冲", "要回来"],
    positions: [
      { left: "10%", top: "30%" },
      { left: "58%", top: "46%" },
    ],
    durationMs: 3200,
    clickHoldMs: 1900,
  },
  {
    asset: "debtCash",
    className: "sticker-cash",
    keywords: ["一分没结", "要钱去", "钱凑够", "结清", "工钱"],
    tapWords: ["结清", "工钱+1", "站工友"],
    positions: [
      { left: "55%", top: "22%" },
      { left: "11%", top: "40%" },
      { left: "60%", top: "48%" },
    ],
    durationMs: 3900,
    clickHoldMs: 2300,
  },
  {
    asset: "wageStamp",
    className: "sticker-stamp",
    keywords: ["开头", "一分没结", "刚把钱凑够", "要债", "讨薪", "欠薪", "工友", "门没锁"],
    tapWords: ["盖章", "站工友", "讨薪+1"],
    positions: [
      { left: "7%", top: "24%" },
      { left: "57%", top: "22%" },
      { left: "10%", top: "47%" },
    ],
    durationMs: 3600,
    clickHoldMs: 2200,
  },
  {
    asset: "homePhone",
    className: "sticker-phone",
    keywords: ["回家过年", "指定到家", "安安全全", "电话", "视频", "家人"],
    tapWords: ["想家了", "安安全全", "到家"],
    positions: [
      { left: "12%", top: "26%" },
      { left: "58%", top: "38%" },
      { left: "45%", top: "20%" },
    ],
    durationMs: 4200,
    clickHoldMs: 2500,
  },
  {
    asset: "homeLantern",
    className: "sticker-lantern",
    keywords: ["安安全全", "年三十", "指定到家", "回来过年"],
    tapWords: ["安安全全", "一定到家", "别担心"],
    positions: [
      { left: "58%", top: "22%" },
      { left: "10%", top: "44%" },
    ],
    durationMs: 4200,
    clickHoldMs: 2500,
  },
  {
    asset: "homeTicket",
    className: "sticker-ticket",
    keywords: ["没钱", "回家", "过年", "年三十", "父母", "妈妈", "心疼", "想家"],
    tapWords: ["想家", "抱抱", "到家+1"],
    positions: [
      { left: "58%", top: "43%" },
      { left: "9%", top: "26%" },
      { left: "47%", top: "21%" },
    ],
    durationMs: 4200,
    clickHoldMs: 2500,
  },
  {
    asset: "smokeQuestion",
    className: "sticker-smoke",
    keywords: ["咋不想啊", "低声交流", "抽烟", "蹲路边", "悬着心"],
    tapWords: ["悬着心", "咋回去", "别断"],
    positions: [
      { left: "62%", top: "28%" },
      { left: "8%", top: "42%" },
      { left: "52%", top: "48%" },
    ],
    durationMs: 3800,
    clickHoldMs: 2300,
  },
  {
    asset: "roadQuestion",
    className: "sticker-road-sign",
    keywords: ["到底", "能不能", "回不去", "悬念", "疑问", "猜", "路", "车"],
    tapWords: ["有线索", "回得去吗", "别断"],
    positions: [
      { left: "64%", top: "24%" },
      { left: "9%", top: "38%" },
      { left: "50%", top: "46%" },
    ],
    durationMs: 3800,
    clickHoldMs: 2300,
  },
  {
    asset: "rockWord",
    className: "sticker-rock-word",
    keywords: ["你这玩意叫摇滚啊", "摇滚", "啦啦掉"],
    tapWords: ["摇滚", "太损了", "有画面"],
    positions: [
      { left: "10%", top: "26%" },
      { left: "55%", top: "42%" },
    ],
    durationMs: 4000,
    clickHoldMs: 2300,
  },
  {
    asset: "rockMoto",
    className: "sticker-moto",
    keywords: ["摇滚", "摩托", "交通工具", "回家方式", "行李", "玩意", "揭晓"],
    tapWords: ["贼摇滚", "尾灯亮", "出发!"],
    positions: [
      { left: "47%", top: "22%" },
      { left: "13%", top: "42%" },
      { left: "57%", top: "46%" },
    ],
    durationMs: 4300,
    clickHoldMs: 2500,
  },
  {
    asset: "northTitle",
    className: "sticker-north-title",
    keywords: ["北往", "旅途", "出发"],
    tapWords: ["北往", "出发", "追下一集"],
    positions: [
      { left: "55%", top: "24%" },
      { left: "8%", top: "40%" },
    ],
    durationMs: 4300,
    clickHoldMs: 2500,
  },
  { asset: "charge", className: "sticker-charge", keywords: ["冲", "干", "走"], tapWords: ["冲"] },
  { asset: "question", className: "sticker-question", keywords: ["悬念", "疑问"], tapWords: ["?"] },
  { asset: "laugh", className: "sticker-laugh", keywords: ["搞笑", "笑", "好笑", "哈哈"], tapWords: ["鹅鹅鹅"] },
  { asset: "rock", className: "sticker-rock", keywords: ["摇滚"], tapWords: ["摇滚"] },
  { asset: "tear", className: "sticker-tear", keywords: ["心疼", "破防"], tapWords: ["破防"] },
];

const BEIWANG_STICKER_TIMELINE = [
  {
    start: 0,
    end: 16,
    assets: ["mealSteam"],
    cadenceSec: 8,
    burstCount: 1,
  },
  {
    start: 16,
    end: 62,
    assets: ["noPayBill", "goSign", "wageStamp", "debtCash"],
    cadenceSec: 5,
    burstCount: 4,
  },
  {
    start: 112,
    end: 180,
    assets: ["homePhone", "homeTicket", "homeLantern"],
    cadenceSec: 7,
    burstCount: 4,
  },
  {
    start: 198,
    end: 244,
    assets: ["smokeQuestion", "roadQuestion"],
    cadenceSec: 7,
    burstCount: 3,
  },
  {
    start: 244,
    end: 300,
    assets: ["rockWord", "rockMoto", "northTitle"],
    cadenceSec: 6,
    burstCount: 4,
  },
];

function clearStickerLayer(clearTimer = true) {
  if (clearTimer) window.clearTimeout(state.ambientStickerTimer);
  state.stickerHideTimers.forEach((timer) => window.clearTimeout(timer));
  state.stickerHideTimers.clear();
  state.stickerCombo = 0;
  if (stickerLayer) stickerLayer.innerHTML = "";
}

function highlightText(highlight) {
  return `${highlight.title || ""} ${highlight.description || ""} ${highlight.highlight_type || ""} ${
    highlight.emotion || ""
  } ${highlight.evidence_text || ""}`;
}

function stickerRuleByAsset(asset) {
  return STICKER_RULES.find((rule) => rule.asset === asset);
}

function isBeiwangEpisode() {
  return (state.currentEpisode?.drama?.title || "").includes("北往");
}

function stickerSlotForTime(timeSec) {
  if (!isBeiwangEpisode()) return null;
  return (
    BEIWANG_STICKER_TIMELINE.find((slot, index) => {
      const isLast = index === BEIWANG_STICKER_TIMELINE.length - 1;
      return timeSec >= slot.start && (timeSec < slot.end || (isLast && timeSec <= slot.end));
    }) || null
  );
}

function stickerRulesFromSlot(slot) {
  return (slot?.assets || []).map(stickerRuleByAsset).filter(Boolean);
}

function getSceneStickerRules(highlight) {
  const timeSlot = stickerSlotForTime(Number(highlight.start_time_sec || 0));
  if (timeSlot) return stickerRulesFromSlot(timeSlot);
  const source = highlightText(highlight);
  const matched = STICKER_RULES.filter((rule) => rule.keywords.some((keyword) => source.includes(keyword)));
  if (matched.length) return matched.slice(0, 3);
  const key = getHighlightKey(highlight.highlight_type);
  if (key === "suspense") return [STICKER_RULES.find((rule) => rule.asset === "question")];
  if (key === "tear") return [STICKER_RULES.find((rule) => rule.asset === "tear")];
  if (key === "comedy") return [STICKER_RULES.find((rule) => rule.asset === "laugh")];
  if (key === "conflict") return [STICKER_RULES.find((rule) => rule.asset === "charge")];
  return [STICKER_RULES.find((rule) => rule.asset === "rock")];
}

function setStickerLifetime(sticker, delayMs = 2000) {
  const id = sticker.dataset.stickerId;
  window.clearTimeout(state.stickerHideTimers.get(id));
  state.stickerHideTimers.set(
    id,
    window.setTimeout(() => {
      sticker.classList.add("leaving");
      window.setTimeout(() => {
        sticker.remove();
        state.stickerHideTimers.delete(id);
      }, 260);
    }, delayMs)
  );
}

function spawnTapText(target, text, className = "") {
  if (!stickerLayer || !target) return;
  const fx = document.createElement("span");
  fx.className = `sticker-tap-fx ${className}`;
  fx.textContent = text;
  const rect = target.getBoundingClientRect();
  const hostRect = stickerLayer.getBoundingClientRect();
  const drift = stableRatio(`${text}-${Date.now()}`);
  fx.style.left = `${rect.left - hostRect.left + rect.width * (0.35 + drift * 0.45)}px`;
  fx.style.top = `${rect.top - hostRect.top + rect.height * (0.08 + (1 - drift) * 0.22)}px`;
  stickerLayer.appendChild(fx);
  window.setTimeout(() => fx.remove(), 720);
}

function spawnStickerBurst(sticker, rule, clicks) {
  if (!stickerLayer || !sticker) return;
  const rect = sticker.getBoundingClientRect();
  const hostRect = stickerLayer.getBoundingClientRect();
  const centerX = rect.left - hostRect.left + rect.width / 2;
  const centerY = rect.top - hostRect.top + rect.height / 2;
  const count = clicks >= 10 ? 14 : clicks >= 5 ? 10 : 7;
  for (let index = 0; index < count; index += 1) {
    const particle = document.createElement("i");
    particle.className = `sticker-burst-dot burst-${rule?.asset || "default"}`;
    const angle = (Math.PI * 2 * index) / count;
    const distance = 22 + stableRatio(`${rule?.asset}-${clicks}-${index}`) * (clicks >= 10 ? 46 : 30);
    particle.style.left = `${centerX}px`;
    particle.style.top = `${centerY}px`;
    particle.style.setProperty("--x", `${Math.cos(angle) * distance}px`);
    particle.style.setProperty("--y", `${Math.sin(angle) * distance}px`);
    stickerLayer.appendChild(particle);
    window.setTimeout(() => particle.remove(), 760);
  }
}

function tapSticker(sticker, rule = {}) {
  const clicks = Number(sticker.dataset.clicks || "0") + 1;
  state.stickerCombo += 1;
  sticker.dataset.clicks = String(clicks);
  sticker.style.setProperty("--tap-scale", `${Math.min(1.75, 1 + clicks * 0.065)}`);
  sticker.classList.toggle("hot", clicks >= 5);
  sticker.classList.toggle("mega", clicks >= 10);
  sticker.querySelector(".sticker-count").textContent = String(clicks);
  sticker.classList.add("tapped");
  sticker.classList.add("click-hold");
  window.clearTimeout(sticker.clickHoldTimer);
  sticker.clickHoldTimer = window.setTimeout(() => sticker.classList.remove("click-hold"), 1100);
  window.setTimeout(() => sticker.classList.remove("tapped"), 180);
  const words = rule.tapWords || [STICKER_ASSETS[rule.asset]?.label || "+1"];
  spawnTapText(sticker, `${words[clicks % words.length]} +1`, `fx-${rule.asset || "default"}`);
  spawnTapText(sticker, `总${clicks}`, "fx-count");
  spawnStickerBurst(sticker, rule, clicks);
  if (clicks === 5 || clicks === 10 || clicks % 15 === 0) {
    spawnTapText(sticker, clicks >= 10 ? "爆了!" : "冒火!", "fx-hot");
    spawnVideoSticker(rule.asset ? rule : STICKER_RULES.find((item) => item.asset === "wageStamp"), clicks, {
      interactive: false,
      durationMs: 1200,
      className: "sticker-bonus",
    });
  }
  setStickerLifetime(sticker, rule.clickHoldMs || 1800);
}

function spawnVideoSticker(rule, index = 0, options = {}) {
  if (!stickerLayer || !rule) return;
  const asset = STICKER_ASSETS[rule.asset];
  if (!asset) return;
  const sticker = document.createElement(options.interactive === false ? "span" : "button");
  const id = `sticker-${Date.now()}-${state.stickerSerial++}`;
  sticker.className = `scene-sticker ${rule.className || ""} ${options.className || ""}`;
  sticker.dataset.stickerId = id;
  sticker.dataset.clicks = "0";
  sticker.dataset.asset = rule.asset;
  if (options.interactive !== false) sticker.type = "button";
  const position = rule.positions?.[index % rule.positions.length];
  sticker.style.setProperty("--left", options.left || position?.left || `${8 + ((index * 23 + state.stickerSerial * 7) % 72)}%`);
  sticker.style.setProperty("--top", options.top || position?.top || `${8 + ((index * 17 + state.stickerSerial * 11) % 48)}%`);
  sticker.style.setProperty("--delay", `${options.delayMs ?? index * 90}ms`);
  sticker.style.setProperty("--tap-color", rule.tapColor || "rgba(255, 122, 48, 0.72)");
  sticker.innerHTML = `<img src="${asset.src}" alt="${escapeHTML(asset.label)}" /><span class="sticker-count">0</span>`;
  if (options.interactive !== false) {
    sticker.addEventListener("click", (event) => {
      event.stopPropagation();
      tapSticker(sticker, rule);
    });
  }
  stickerLayer.appendChild(sticker);
  setStickerLifetime(sticker, options.durationMs || rule.durationMs || 3600);
  return sticker;
}

function spawnHighlightStickers(highlight) {
  const rules = getSceneStickerRules(highlight);
  const slot = stickerSlotForTime(Number(highlight.start_time_sec || 0));
  const count = slot?.burstCount || 4;
  [...rules, ...rules].slice(0, count).forEach((rule, index) => {
    window.setTimeout(() => spawnVideoSticker(rule, index, { durationMs: rule.durationMs || 3800 }), index * 220);
  });
}

function ambientStickerRule() {
  const currentTime = player.currentTime || 0;
  const slot = stickerSlotForTime(currentTime);
  if (slot) {
    const rules = stickerRulesFromSlot(slot);
    if (!rules.length) return null;
    const localTime = Math.max(0, currentTime - slot.start);
    return rules[Math.floor(localTime / slot.cadenceSec) % rules.length];
  }
  if (isBeiwangEpisode()) return null;
  return STICKER_RULES[Math.floor(currentTime / 4) % STICKER_RULES.length];
}

function scheduleAmbientStickers() {
  window.clearTimeout(state.ambientStickerTimer);
  if (!state.currentEpisode || player.paused || views.watch.classList.contains("active") === false) return;
  const mode = getDanmakuMode();
  const baseDelay = state.danmakuSettings.mode === "carnival" ? 1250 : state.danmakuSettings.mode === "immerse" ? 3600 : 2300;
  state.ambientStickerTimer = window.setTimeout(() => {
    const bursts = state.danmakuSettings.mode === "carnival" ? 2 : 1;
    for (let index = 0; index < bursts; index += 1) {
      const rule = ambientStickerRule();
      if (rule) spawnVideoSticker(rule, Math.floor(player.currentTime || 0) + index, { durationMs: rule.durationMs || (mode.enabled ? 3300 : 2200) });
    }
    scheduleAmbientStickers();
  }, baseDelay + stableRatio(`${player.currentTime}-${state.sessionId}`) * 620);
}

function checkDanmaku(currentTime) {
  for (const comment of state.danmaku) {
    const due = currentTime >= comment.time_sec && currentTime <= comment.time_sec + 2.4;
    if (due && !state.firedDanmaku.has(comment.id)) {
      state.firedDanmaku.add(comment.id);
      emitDanmaku(comment);
    }
  }
}

function renderDramas() {
  $("#dramaCount").textContent = `${state.dramas.length} 部短剧`;
  $("#dramaGrid").innerHTML = state.dramas
    .map(
      (drama) => `
        <article class="drama-card" data-id="${drama.id}">
          <div class="thumb">
            ${
              drama.preview_video_url
                ? `<video src="${drama.preview_video_url}" muted playsinline preload="metadata"></video>`
                : `<div class="thumb-empty">暂无视频</div>`
            }
            <span>${escapeHTML(drama.genre)}</span>
          </div>
          <div class="drama-info">
            <h3>${escapeHTML(drama.title)}</h3>
            <p>${drama.episode_count} 集已导入</p>
          </div>
        </article>
      `
    )
    .join("");

  document.querySelectorAll(".drama-card").forEach((card) => {
    card.addEventListener("click", () => openDrama(Number(card.dataset.id)));
  });
}

async function loadDramas() {
  state.dramas = await fetchJSON("/api/dramas");
  renderDramas();
}

async function openDrama(dramaId, preferredEpisodeId = null) {
  state.episodes = await fetchJSON(`/api/dramas/${dramaId}/episodes`);
  if (!state.episodes.length) return;
  const select = $("#episodeSelect");
  select.innerHTML = state.episodes
    .map((episode) => `<option value="${episode.id}">${episode.title}</option>`)
    .join("");
  select.onchange = () => openEpisode(Number(select.value));
  const targetEpisode = state.episodes.find((episode) => episode.id === preferredEpisodeId) || state.episodes[0];
  await openEpisode(targetEpisode.id);
  setView("watch");
}

async function openEpisode(episodeId) {
  state.firedHighlights.clear();
  state.lastInteractionTime = -Infinity;
  clearDanmakuLayer();
  clearStickerLayer();
  hideInteraction();
  const [episode, danmaku] = await Promise.all([
    fetchJSON(`/api/episodes/${episodeId}`),
    fetchJSON(`/api/episodes/${episodeId}/danmaku`),
  ]);
  state.currentEpisode = episode;
  state.danmaku = danmaku;
  $("#watchTitle").textContent = `${state.currentEpisode.drama.title} · ${state.currentEpisode.title}`;
  $("#episodeSelect").value = String(episodeId);
  player.src = state.currentEpisode.video_url;
  applyPlayerTheme(state.currentEpisode);
  renderTimeline();
  renderDanmakuHint();
  syncEpisodeUrl(episodeId);
}

async function openEpisodeFromUrl(episodeId) {
  const episode = await fetchJSON(`/api/episodes/${episodeId}`);
  await openDrama(episode.drama.id, episodeId);
}

function renderTimeline() {
  const highlights = state.currentEpisode?.highlights || [];
  $("#timeline").innerHTML = highlights
    .map(
      (item) => {
        const ui = getHighlightUI(item.highlight_type);
        return `
        <button class="timeline-item ${ui.className}" type="button" data-time="${item.start_time_sec}">
          <span>${formatTime(item.start_time_sec)}</span>
          <strong>${escapeHTML(ui.label)}</strong>
          <em>${escapeHTML(item.emotion)}</em>
        </button>
      `;
      }
    )
    .join("");

  document.querySelectorAll(".timeline-item").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelector(".video-wrap")?.scrollIntoView({ behavior: "smooth", block: "center" });
      const targetTime = Number(button.dataset.time);
      const targetHighlight = state.currentEpisode?.highlights?.find((item) => Number(item.start_time_sec) === targetTime);
      if (targetHighlight) state.firedHighlights.delete(targetHighlight.id);
      hideInteraction();
      state.lastInteractionTime = -Infinity;
      player.currentTime = targetTime;
      player.play();
    });
  });
}

function findDueHighlight(currentTime) {
  if (state.activeHighlight || currentTime - state.lastInteractionTime < MIN_INTERACTION_GAP_SEC) {
    return null;
  }
  const highlights = state.currentEpisode?.highlights || [];
  return highlights.find((item) => {
    const due = currentTime >= item.start_time_sec && currentTime <= item.end_time_sec + 2;
    return due && !state.firedHighlights.has(item.id);
  });
}

function renderEffectStage(ui) {
  if (ui.effect === "laugh") {
    return `
      <div class="laugh-sticker">
        <i></i>
        <span>鹅鹅鹅</span>
      </div>
    `;
  }
  if (ui.effect === "tapstorm") {
    return `<div class="tapstorm-ring"><span>牛逼</span></div>`;
  }
  if (ui.effect === "heartbeat") {
    return `<div class="heartbeat-line"><i></i><i></i><i></i></div>`;
  }
  if (ui.effect === "clue") {
    return `<div class="clue-card"><span>线索浮现</span></div>`;
  }
  return `<div class="soft-spark"></div>`;
}

function renderSceneCaptions(ui, motifs, highlight) {
  const source = highlightText(highlight || {});
  let captions = motifs.length ? motifs : [ui.label, ui.action];
  if ((state.currentEpisode?.drama?.title || "").includes("北往")) {
    if (source.includes("讨薪") || source.includes("欠薪") || source.includes("要债")) {
      captions = ["欠薪得还", "工友站队", "开场对峙"];
    } else if (source.includes("年三十") || source.includes("父母") || source.includes("没钱")) {
      captions = ["年三十到家", "有钱没钱", "想家了"];
    } else if (source.includes("到底") || source.includes("回不去") || source.includes("悬念")) {
      captions = ["回得去吗", "路还悬着", "别断在这"];
    } else if (source.includes("摇滚") || source.includes("摩托") || source.includes("交通工具")) {
      captions = ["贼摇滚", "行李摩托", "出发回家"];
    }
  }
  return `
    <div class="scene-captions">
      ${captions.map((caption) => `<span>${escapeHTML(caption)}</span>`).join("")}
    </div>
  `;
}

function floatingWord(text, ui, anchor) {
  const panel = interactionLayer.querySelector(".interaction-panel");
  if (!panel) return;
  const word = document.createElement("span");
  word.className = `floating-word ${ui.className}`;
  word.textContent = text;
  const left = anchor ? anchor.offsetLeft + anchor.offsetWidth / 2 : panel.clientWidth * (0.35 + stableRatio(text) * 0.35);
  const top = anchor ? anchor.offsetTop : panel.clientHeight * 0.45;
  word.style.left = `${left}px`;
  word.style.top = `${top}px`;
  panel.appendChild(word);
  window.setTimeout(() => word.remove(), 820);
}

function scheduleInteractionHide(delayMs) {
  window.clearTimeout(state.hideTimer);
  state.hideTimer = window.setTimeout(hideInteraction, delayMs);
}

function tapImpactPad(button, ui) {
  const counter = button.querySelector("strong");
  const next = Number(counter.textContent || "0") + 1;
  counter.textContent = String(next);
  button.classList.add("hit");
  window.setTimeout(() => button.classList.remove("hit"), 180);
  const words = ui.burstWords || [ui.label];
  floatingWord(words[next % words.length], ui, button);
  burstReaction(button);
  if (ui.effect === "laugh") {
    spawnLaughSticker(button);
  }
  scheduleInteractionHide(state.tapHideDelayMs);
}

function spawnLaughSticker(anchor) {
  const panel = interactionLayer.querySelector(".interaction-panel");
  if (!panel) return;
  const sticker = document.createElement("span");
  sticker.className = "mini-laugh-sticker";
  sticker.textContent = "鹅鹅鹅";
  sticker.style.left = `${anchor.offsetLeft + anchor.offsetWidth * stableRatio(anchor.textContent)}px`;
  sticker.style.top = `${Math.max(8, anchor.offsetTop - 24)}px`;
  panel.appendChild(sticker);
  window.setTimeout(() => sticker.remove(), 920);
}

const VEHICLE_STICKERS = {
  vehicle_train: { src: "/assets/stickers/vehicle_train.svg", asset: "vehicleTrain", label: "火车" },
  vehicle_car: { src: "/assets/stickers/vehicle_car.svg", asset: "vehicleCar", label: "小车" },
  vehicle_motorcycle: { src: "/assets/stickers/beiwang_rock_moto.svg", asset: "rockMoto", label: "摩托车" },
};

function isVehicleChoice(highlight) {
  return Boolean(highlight?.options?.some((option) => option.key in VEHICLE_STICKERS));
}

function renderReactionButton(option, vehicleChoice) {
  const sticker = VEHICLE_STICKERS[option.key];
  return `
    <button class="reaction-button ${vehicleChoice ? "vehicle-option" : ""}" type="button" data-key="${escapeHTML(
      option.key
    )}">
      ${sticker ? `<img src="${sticker.src}" alt="" />` : ""}
      <span>${escapeHTML(option.label)}</span>
    </button>
  `;
}

function spawnVehicleSticker(optionKey, anchor) {
  const sticker = VEHICLE_STICKERS[optionKey];
  const panel = interactionLayer.querySelector(".interaction-panel");
  if (!sticker || !panel || !anchor) return;
  const panelImage = document.createElement("img");
  panelImage.className = "vehicle-float-sticker";
  panelImage.src = sticker.src;
  panelImage.alt = "";
  panelImage.style.left = `${anchor.offsetLeft + anchor.offsetWidth / 2 - 48}px`;
  panelImage.style.top = `${Math.max(8, anchor.offsetTop - 94)}px`;
  panel.appendChild(panelImage);

  const videoRule =
    STICKER_RULES.find((rule) => rule.asset === sticker.asset) || {
      asset: sticker.asset,
      className: "sticker-vehicle",
      keywords: [],
      tapWords: [sticker.label],
    };
  spawnVideoSticker(videoRule, 4, { left: "53%", top: "15%", durationMs: 2800 });

  window.setTimeout(() => panelImage.remove(), 1400);
}

function getInteractionMode(highlight, ui, vehicleChoice) {
  if (vehicleChoice) return "choice";
  const key = getHighlightKey(highlight.highlight_type);
  if (ui.effect === "tapstorm" || key === "comedy") return "tap";
  return "choice";
}

function showInteraction(highlight) {
  const ui = getHighlightUI(highlight.highlight_type);
  const key = getHighlightKey(highlight.highlight_type);
  const motifs = extractSceneMotifs(highlight);
  const vehicleChoice = isVehicleChoice(highlight);
  const interactionMode = getInteractionMode(highlight, ui, vehicleChoice);
  state.activeHighlight = highlight;
  state.interactionMode = interactionMode;
  state.firedHighlights.add(highlight.id);
  state.lastInteractionTime = highlight.start_time_sec;
  if (vehicleChoice) {
    player.pause();
  }
  window.clearTimeout(state.hideTimer);
  interactionLayer.className = `interaction-layer ${ui.className} effect-${ui.effect} ${
    vehicleChoice ? "vehicle-choice-layer" : ""
  } mode-${interactionMode}`;
  const impactMarkup =
    interactionMode === "tap"
      ? `<button class="impact-pad" type="button">
        <span>${escapeHTML(ui.padText)}</span>
        <strong>0</strong>
      </button>`
      : "";
  const optionsMarkup =
    interactionMode === "choice"
      ? `<div class="interaction-options">
        ${highlight.options.map((option) => renderReactionButton(option, vehicleChoice)).join("")}
      </div>`
      : "";
  const helperText = interactionMode === "tap" ? "连续点击表达情绪" : vehicleChoice ? "先猜交通工具，再看揭晓" : "选一个最贴近你的反应";
  interactionLayer.innerHTML = `
    <div class="interaction-panel ${vehicleChoice ? "vehicle-choice-panel" : ""}" data-effect="${escapeHTML(
      ui.effect
    )}">
      <div class="interaction-aura"></div>
      <div class="effect-stage" aria-hidden="true">${renderEffectStage(ui)}</div>
      <div class="interaction-copy">
        <div class="interaction-meta">
          <b>${escapeHTML(ui.badge)}</b>
          <span>${escapeHTML(ui.label)} · ${escapeHTML(ui.action)} · ${escapeHTML(highlight.emotion)}</span>
        </div>
        <h3>${escapeHTML(highlight.title)}</h3>
        <p>${escapeHTML(highlight.description || "")}</p>
        <small class="interaction-helper">${escapeHTML(helperText)}</small>
      </div>
      ${renderSceneCaptions(ui, motifs, highlight)}
      ${impactMarkup}
      ${optionsMarkup}
      <div class="interaction-meter"><i></i></div>
      <button class="close-button" type="button" aria-label="不看这个互动" title="不看">×</button>
    </div>
  `;
  interactionLayer.querySelector(".close-button").addEventListener("click", hideInteraction);
  const impactPad = interactionLayer.querySelector(".impact-pad");
  if (impactPad) {
    impactPad.addEventListener("click", () => tapImpactPad(impactPad, ui));
  }
  interactionLayer.querySelectorAll(".interaction-options button").forEach((button) => {
    button.addEventListener("click", () => submitInteraction(button.dataset.key, button));
  });
  emitHighlightDanmaku(highlight, ui);
  clearStickerLayer();
  spawnHighlightStickers(highlight);
  if (!vehicleChoice) scheduleAmbientStickers();
  if (key === "comedy" && impactPad) {
    window.setTimeout(() => tapImpactPad(impactPad, ui), 120);
  }
  scheduleInteractionHide(interactionMode === "tap" ? state.tapHideDelayMs : vehicleChoice ? 13000 : 9000);
}

function hideInteraction() {
  window.clearTimeout(state.hideTimer);
  state.activeHighlight = null;
  interactionLayer.className = "interaction-layer hidden";
  interactionLayer.innerHTML = "";
}

function burstReaction(anchor) {
  const panel = interactionLayer.querySelector(".interaction-panel");
  if (!panel) return;
  panel.classList.add("pulse");
  window.setTimeout(() => panel.classList.remove("pulse"), 420);
  for (let index = 0; index < 14; index += 1) {
    const particle = document.createElement("i");
    particle.className = "reaction-particle";
    particle.style.setProperty("--x", `${Math.cos(index) * (34 + index * 2)}px`);
    particle.style.setProperty("--y", `${Math.sin(index * 1.7) * (26 + index)}px`);
    particle.style.left = `${anchor.offsetLeft + anchor.offsetWidth / 2}px`;
    particle.style.top = `${anchor.offsetTop + anchor.offsetHeight / 2}px`;
    panel.appendChild(particle);
    window.setTimeout(() => particle.remove(), 680);
  }
}

async function submitInteraction(optionKey, anchor) {
  if (!state.activeHighlight) return;
  if (anchor) burstReaction(anchor);
  spawnVehicleSticker(optionKey, anchor);
  const result = await fetchJSON("/api/interactions", {
    method: "POST",
    body: JSON.stringify({
      highlight_id: state.activeHighlight.id,
      option_key: optionKey,
      session_id: state.sessionId,
    }),
  });
  renderInteractionResult(result.stats);
}

function renderInteractionResult(stats) {
  interactionLayer.querySelector(".interaction-panel").classList.add("answered");
  interactionLayer.querySelector(".interaction-options").innerHTML = stats.options
    .map(
      (option) => `
        <div class="result-row">
          <span>${escapeHTML(option.label)}</span>
          <div class="bar"><i style="width:${option.percent}%"></i></div>
          <strong>${option.percent}%</strong>
        </div>
      `
    )
    .join("");
  interactionLayer.querySelector(".interaction-copy span").textContent = `${stats.total} 次互动`;
  scheduleInteractionHide(3200);
}

function renderEvidence(item) {
  if (!item.evidence_text && !item.annotation_reason) {
    return "";
  }
  const evidenceIds = item.evidence_segment_ids?.length ? `片段 ${item.evidence_segment_ids.join(", ")}` : "证据";
  return `
    <details class="evidence-box">
      <summary>${evidenceIds} · 标注依据</summary>
      ${item.evidence_text ? `<p>${item.evidence_text}</p>` : ""}
      ${item.annotation_reason ? `<p>${item.annotation_reason}</p>` : ""}
    </details>
  `;
}

async function loadStats() {
  const [summary, highlights] = await Promise.all([
    fetchJSON("/api/stats/summary"),
    fetchJSON("/api/stats/highlights"),
  ]);
  $("#summaryCards").innerHTML = [
    ["短剧", summary.drama_count],
    ["剧集", summary.episode_count],
    ["高光点", summary.highlight_count],
    ["互动次数", summary.interaction_count],
    ["弹幕", summary.danmaku_count || 0],
  ]
    .map(([label, value]) => `<div class="summary-card"><span>${label}</span><strong>${value}</strong></div>`)
    .join("");

  $("#statsList").innerHTML = highlights
    .map((item) => {
      const top = item.stats.options[0] || { label: "暂无", percent: 0 };
      return `
        <article class="stats-row">
          <div>
            <p>${item.drama_title} · ${item.episode_title} · ${formatTime(item.start_time_sec)}</p>
            <h4>${item.title}</h4>
            <span>${item.highlight_type} / ${item.source} / 置信度 ${item.confidence}</span>
            ${renderEvidence(item)}
          </div>
          <div class="stats-metric">
            <strong>${item.stats.total}</strong>
            <span>${top.label} ${top.percent}%</span>
          </div>
        </article>
      `;
    })
    .join("");
}

function getFilteredReviewEpisodes() {
  if (state.reviewFilter === "reviewed") {
    return state.reviewEpisodes.filter((episode) => episode.review_status === "reviewed");
  }
  if (state.reviewFilter === "pending") {
    return state.reviewEpisodes.filter((episode) => episode.review_status !== "reviewed");
  }
  return state.reviewEpisodes;
}

function renderReviewSummary() {
  const total = state.reviewEpisodes.length;
  const reviewed = state.reviewEpisodes.filter((episode) => episode.review_status === "reviewed").length;
  const reviewedHighlights = state.reviewEpisodes.reduce(
    (sum, episode) => sum + (episode.reviewed_highlight_count || 0),
    0
  );
  const totalHighlights = state.reviewEpisodes.reduce((sum, episode) => sum + (episode.highlight_count || 0), 0);
  $("#reviewSummaryCards").innerHTML = [
    ["全部剧集", total],
    ["已复核", reviewed],
    ["待复核", total - reviewed],
    ["人工高光", `${reviewedHighlights}/${totalHighlights}`],
  ]
    .map(([label, value]) => `<div class="summary-card"><span>${label}</span><strong>${value}</strong></div>`)
    .join("");
}

async function renderReviewEpisodeOptions(preferredEpisodeId) {
  const episodes = getFilteredReviewEpisodes();
  const select = $("#reviewEpisodeSelect");
  const currentValue = preferredEpisodeId ? String(preferredEpisodeId) : select.value;

  if (!episodes.length) {
    select.innerHTML = `<option value="">当前筛选没有剧集</option>`;
    $("#reviewJson").value = "";
    $("#reviewPreview").innerHTML = "";
    setReviewStatus("当前筛选没有剧集");
    return;
  }

  select.innerHTML = episodes
    .map(
      (episode) => `
        <option value="${episode.id}">
          ${episode.review_status_label} · ${episode.drama_title} · ${episode.episode_title} · ${episode.highlight_count} 个高光
        </option>
      `
    )
    .join("");

  if (currentValue && [...select.options].some((option) => option.value === currentValue)) {
    select.value = currentValue;
  }
  await loadReviewPayload(Number(select.value));
}

async function loadReviewEpisodes() {
  state.reviewEpisodes = await fetchJSON("/api/admin/episodes");
  renderReviewSummary();
  const urlEpisodeId = Number(new URLSearchParams(location.search).get("episode"));
  const preferredEpisodeId = state.currentEpisode?.id || (Number.isFinite(urlEpisodeId) ? urlEpisodeId : 0);
  await renderReviewEpisodeOptions(preferredEpisodeId || Number($("#reviewEpisodeSelect").value));
}

async function loadReviewPayload(episodeId) {
  if (!episodeId) return;
  const payload = await fetchJSON(`/api/admin/episodes/${episodeId}/highlights`);
  const meta = state.reviewEpisodes.find((episode) => episode.id === episodeId);
  $("#reviewJson").value = JSON.stringify(payload, null, 2);
  renderReviewPreview(payload);
  const status = meta
    ? `${meta.review_status_label} · 人工 ${meta.reviewed_highlight_count || 0}/${meta.highlight_count || 0}`
    : "已加载";
  setReviewStatus(`已加载：${payload.drama_title} · ${payload.episode_title} · ${status}`);
}

function parseReviewJson() {
  return JSON.parse($("#reviewJson").value);
}

function setReviewStatus(message, isError = false) {
  const status = $("#reviewStatus");
  status.textContent = message;
  status.classList.toggle("error", isError);
}

function syncReviewJson(payload) {
  $("#reviewJson").value = JSON.stringify(payload, null, 2);
}

function updateReviewTimeInput(input) {
  const value = Number(input.value);
  if (!Number.isFinite(value) || value < 0) return;
  const index = Number(input.dataset.index);
  const field = input.dataset.field;
  const payload = parseReviewJson();
  const item = payload.highlights?.[index];
  if (!item || !["start_time_sec", "end_time_sec"].includes(field)) return;

  item[field] = Number(value.toFixed(2));
  if (field === "start_time_sec" && Number(item.end_time_sec) <= item.start_time_sec) {
    item.end_time_sec = Number(Math.min(payload.duration_sec || item.start_time_sec + 8, item.start_time_sec + 8).toFixed(2));
  }
  if (field === "end_time_sec" && Number(item.end_time_sec) <= Number(item.start_time_sec)) {
    item.end_time_sec = Number((Number(item.start_time_sec) + 1).toFixed(2));
    input.value = item.end_time_sec;
  }
  syncReviewJson(payload);
  const card = input.closest(".review-card");
  const timeLabel = card?.querySelector("[data-time-label]");
  if (timeLabel) {
    timeLabel.textContent = `${formatTime(item.start_time_sec)}-${formatTime(item.end_time_sec)}`;
  }
  setReviewStatus(`已调整 ${item.title} 的${field === "start_time_sec" ? "开始" : "结束"}时间，点击保存后生效`);
}

function renderReviewPreview(payload) {
  const highlights = payload.highlights || [];
  $("#reviewPreview").innerHTML = `
    <div class="review-preview-head">
      <span>片名</span>
      <strong>${escapeHTML(payload.drama_title)} · ${escapeHTML(payload.episode_title)}</strong>
      <small>${highlights.length} 个高光点 · 总时长 ${formatTime(payload.duration_sec)}</small>
    </div>
    ${highlights
      .map(
        (item, index) => `
          <article class="review-card" data-index="${index}">
            <div class="review-card-title">
              <span>高光点 ${index + 1}</span>
              <h4>${escapeHTML(item.title)}</h4>
            </div>
            <p>${escapeHTML(item.highlight_type)} · ${escapeHTML(item.emotion)}</p>
            <div class="review-time-editor">
              <label>
                开始秒
                <input class="review-time-input" type="number" min="0" step="0.1" data-index="${index}" data-field="start_time_sec" value="${Number(
                  item.start_time_sec
                )}" />
              </label>
              <label>
                结束秒
                <input class="review-time-input" type="number" min="0" step="0.1" data-index="${index}" data-field="end_time_sec" value="${Number(
                  item.end_time_sec
                )}" />
              </label>
              <strong data-time-label>${formatTime(item.start_time_sec)}-${formatTime(item.end_time_sec)}</strong>
            </div>
            <span>${(item.options || []).map((option) => escapeHTML(option.label)).join(" / ")}</span>
            ${item.evidence_text ? `<small>${escapeHTML(item.evidence_text)}</small>` : ""}
          </article>
        `
      )
      .join("")}
  `;
}

function formatReviewJson() {
  try {
    const payload = parseReviewJson();
    $("#reviewJson").value = JSON.stringify(payload, null, 2);
    renderReviewPreview(payload);
    setReviewStatus("格式化完成");
  } catch (error) {
    setReviewStatus(`JSON 格式错误：${error.message}`, true);
  }
}

async function saveReviewPayload() {
  try {
    const payload = parseReviewJson();
    const episodeId = Number($("#reviewEpisodeSelect").value || payload.episode_id);
    const result = await fetchJSON(`/api/admin/episodes/${episodeId}/highlights`, {
      method: "PUT",
      body: JSON.stringify({
        ...payload,
        source: "human_review",
        model_version: payload.prompt_version || "admin-review-v1",
      }),
    });
    renderReviewPreview(payload);
    await loadStats();
    await loadReviewEpisodes();
    setReviewStatus(`已保存 ${result.highlight_count} 个高光点，复核状态已更新`);
  } catch (error) {
    setReviewStatus(`保存失败：${error.message}`, true);
  }
}

async function sendDanmaku(event) {
  event.preventDefault();
  const input = $("#danmakuInput");
  const text = input.value.trim();
  if (!text || !state.currentEpisode || !getDanmakuMode().enabled) return;
  try {
    const comment = await fetchJSON("/api/danmaku", {
      method: "POST",
      body: JSON.stringify({
        episode_id: state.currentEpisode.id,
        time_sec: player.currentTime || 0,
        text,
        session_id: state.sessionId,
        mode: state.danmakuSettings.mode,
      }),
    });
    state.danmaku.push(comment);
    state.firedDanmaku.add(comment.id);
    emitDanmaku(comment);
    input.value = "";
    setDanmakuFeedback("已发送");
  } catch (error) {
    setDanmakuFeedback(errorMessage(error), true);
  }
}

player.addEventListener("timeupdate", () => {
  const highlight = findDueHighlight(player.currentTime);
  if (highlight) showInteraction(highlight);
  checkDanmaku(player.currentTime);
  updatePlayerControls();
});

player.addEventListener("loadedmetadata", updatePlayerControls);

player.addEventListener("play", () => {
  updatePlayerControls();
  scheduleAmbientStickers();
});

player.addEventListener("pause", () => {
  updatePlayerControls();
  window.clearTimeout(state.ambientStickerTimer);
});

player.addEventListener("seeked", () => {
  clearDanmakuLayer();
  clearStickerLayer();
  scheduleAmbientStickers();
});

playToggle.addEventListener("click", () => {
  if (player.paused) {
    player.play();
  } else {
    player.pause();
  }
});

muteToggle.addEventListener("click", () => {
  player.muted = !player.muted;
  updatePlayerControls();
});

progressSlider.addEventListener("input", () => {
  const duration = Number.isFinite(player.duration) ? player.duration : state.currentEpisode?.duration_sec || 0;
  player.currentTime = duration * (Number(progressSlider.value) / 1000);
  updatePlayerControls();
});

homeTab.addEventListener("click", () => setView("home"));
adminTab.addEventListener("click", () => setView("admin"));
reviewTab.addEventListener("click", () => setView("review"));
$("#backButton").addEventListener("click", () => setView("home"));
$("#refreshStats").addEventListener("click", loadStats);
$("#reviewStatusFilter").addEventListener("change", async (event) => {
  state.reviewFilter = event.target.value;
  await renderReviewEpisodeOptions(Number($("#reviewEpisodeSelect").value));
});
$("#reviewEpisodeSelect").addEventListener("change", (event) => loadReviewPayload(Number(event.target.value)));
$("#reloadReview").addEventListener("click", () => loadReviewPayload(Number($("#reviewEpisodeSelect").value)));
$("#formatReview").addEventListener("click", formatReviewJson);
$("#saveReview").addEventListener("click", saveReviewPayload);
$("#reviewPreview").addEventListener("input", (event) => {
  if (event.target.classList.contains("review-time-input")) {
    updateReviewTimeInput(event.target);
  }
});
$("#danmakuForm").addEventListener("submit", sendDanmaku);
$("#danmakuSettingsToggle").addEventListener("click", () => {
  $("#danmakuSettings").classList.toggle("open");
});
document.querySelectorAll(".mode-button").forEach((button) => {
  button.addEventListener("click", () => {
    state.danmakuSettings.mode = button.dataset.mode;
    applyDanmakuSettings();
    renderDanmakuHint();
  });
});
["danmakuFontSize", "danmakuSpeed", "danmakuArea"].forEach((id) => {
  $(`#${id}`).addEventListener("change", (event) => {
    const key = id.replace("danmaku", "");
    const settingKey = key.charAt(0).toLowerCase() + key.slice(1);
    state.danmakuSettings[settingKey] = event.target.value;
    applyDanmakuSettings();
  });
});
$("#danmakuOpacity").addEventListener("input", (event) => {
  state.danmakuSettings.opacity = Number(event.target.value);
  applyDanmakuSettings();
});

if (location.hash === "#admin") {
  setView("admin");
} else if (location.hash === "#review") {
  setView("review");
} else {
  setView("home");
}

applyDanmakuSettings();
renderDanmakuHint();

async function bootstrap() {
  await loadDramas();
  const episodeId = Number(new URLSearchParams(location.search).get("episode"));
  if (Number.isFinite(episodeId) && episodeId > 0 && !location.hash) {
    await openEpisodeFromUrl(episodeId);
  }
}

bootstrap().catch((error) => {
  $("#dramaGrid").innerHTML = `<div class="empty-state">加载失败：${error.message}</div>`;
});
