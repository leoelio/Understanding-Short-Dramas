const DEFAULT_DANMAKU_SETTINGS = {
  mode: "light",
  fontSize: "medium",
  speed: "normal",
  area: "top",
  opacity: 82,
};

const MIN_INTERACTION_GAP_SEC = 18;
const REVIEW_MIN_HIGHLIGHT_GAP_SEC = 20;
const REVIEW_MAX_HIGHLIGHTS = 5;

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
    name: "云渺·灵雨压场",
    match: ["修仙", "云渺", "仙"],
    className: "theme-xianxia",
    playIcon: "◆",
    pauseIcon: "Ⅱ",
    muteIcon: "◌",
    mutedIcon: "×",
    accent: "#8bd3ff",
    soft: "#e7c6ff",
    badge: "灵雨入门 · 身份悬念",
    signal: "雨伞 / 周家老宅 / 病榻 / 神秘访客",
  },
  treasure: {
    name: "北派寻宝·机关线索",
    match: ["寻宝", "北派"],
    className: "theme-treasure",
    playIcon: "▶",
    pauseIcon: "Ⅱ",
    muteIcon: "罗",
    mutedIcon: "封",
    accent: "#d6b45f",
    soft: "#55d68f",
    badge: "寻宝线索 · 机关风险",
    signal: "地图 / 罗盘 / 机关 / 悬念线索",
  },
  winter: {
    name: "那年冬至·雪夜心事",
    match: ["冬至"],
    className: "theme-winter",
    playIcon: "▶",
    pauseIcon: "Ⅱ",
    muteIcon: "雪",
    mutedIcon: "静",
    accent: "#9fd8ff",
    soft: "#ff9bbd",
    badge: "冬日情绪 · 爱情拉扯",
    signal: "雪夜 / 回忆 / 心动 / 遗憾",
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
  currentExperience: null,
  endingRemixShown: false,
  remixOptions: null,
  remixResult: null,
  remixLoading: false,
  sessionId: localStorage.getItem("session_id") || crypto.randomUUID(),
  firedHighlights: new Set(),
  firedDanmaku: new Set(),
  activeHighlight: null,
  hideTimer: null,
  lastInteractionTime: -Infinity,
  danmaku: [],
  danmakuSettings: loadDanmakuSettings(),
  reviewEpisodes: [],
  reviewExperience: null,
  reviewRemixes: [],
  reviewFilter: "all",
  currentUser: null,
  adminUsers: [],
  authToken: localStorage.getItem("auth_token") || "",
  authMode: "login",
  watchHistory: [],
  watchHistoryTimer: null,
  pendingResume: null,
  lastHistoryRecordAt: 0,
  watchRoom: null,
  roomPollTimer: null,
  roomLastSyncAt: 0,
  roomLastEventId: 0,
  roomFeed: [],
  roomApplyingRemote: false,
  rewardProfile: null,
  danmakuFeedbackTimer: null,
  interactionMode: "choice",
  tapHideDelayMs: 2200,
  stickerSerial: 0,
  ambientStickerTimer: null,
  stickerHideTimers: new Map(),
  stickerCombo: 0,
  danmakuActionTimer: null,
  stickerSuggestionDraft: "",
};

localStorage.setItem("session_id", state.sessionId);

const $ = (selector) => document.querySelector(selector);

const views = {
  auth: $("#authView"),
  home: $("#homeView"),
  profile: $("#profileView"),
  watch: $("#watchView"),
  admin: $("#adminView"),
  review: $("#reviewView"),
};

const homeTab = $("#homeTab");
const profileTab = $("#profileTab");
const adminTab = $("#adminTab");
const reviewTab = $("#reviewTab");
const player = $("#player");
const interactionLayer = $("#interactionLayer");
const endingRemixLayer = $("#endingRemixLayer");
const danmakuLayer = $("#danmakuLayer");
const stickerLayer = $("#stickerLayer");
const playToggle = $("#playToggle");
const muteToggle = $("#muteToggle");
const progressSlider = $("#progressSlider");
const playerTime = $("#playerTime");
const episodePanel = $("#episodePanel");
const playerStatus = $("#playerStatus");
const roomStatus = $("#roomStatus");
const roomCodeInput = $("#roomCodeInput");
const roomMemberList = $("#roomMemberList");
const roomFeed = $("#roomFeed");
const rewardPanel = $("#rewardPanel");
const publicProfileModal = $("#publicProfileModal");
const publicProfileContent = $("#publicProfileContent");

const HIGHLIGHT_ALIAS_TO_KEY = Object.fromEntries(
  Object.entries(HIGHLIGHT_UI).flatMap(([key, config]) => config.aliases.map((alias) => [alias, key]))
);
const REVIEW_TYPE_LABELS = Object.values(HIGHLIGHT_UI).map((config) => config.label);
const REVIEW_EMOTIONS = [
  "爽",
  "震惊",
  "心疼",
  "愤怒",
  "好笑",
  "心动",
  "紧张",
  "期待",
  "站队",
  "意外",
  "恍然大悟",
  "解气",
  "燃",
  "磕到了",
  "甜",
  "难过",
  "破防",
  "好奇",
  "离谱",
  "欢乐",
  "担心",
  "屏息",
];

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
  const key = state.danmakuSettings.mode;
  const fallback = DANMAKU_MODES[key] || DANMAKU_MODES.light;
  const configured = currentExperienceConfig().danmaku_modes?.[key] || {};
  const includeModes = configured.includeModes || configured.include_modes || fallback.includeModes;
  return {
    ...fallback,
    ...configured,
    includeModes,
    enabled: Boolean((configured.enabled ?? fallback.enabled) && includeModes.length),
  };
}

function getHighlightKey(type) {
  return HIGHLIGHT_ALIAS_TO_KEY[type] || "power";
}

function saveDanmakuSettings() {
  localStorage.setItem("danmaku_settings", JSON.stringify(state.danmakuSettings));
}

async function fetchJSON(url, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.authToken) headers.Authorization = `Bearer ${state.authToken}`;
  const response = await fetch(url, {
    ...options,
    headers,
  });
  if (!response.ok) {
    const message = await response.text();
    if (response.status === 401 && !url.startsWith("/api/auth/")) {
      clearAuth();
      setView("auth");
    }
    throw new Error(message || `请求失败：${response.status}`);
  }
  return response.json();
}

function roleLabel(role) {
  return { admin: "管理员", reviewer: "复核员", user: "普通用户" }[role] || role || "用户";
}

function canManage() {
  return ["admin", "reviewer"].includes(state.currentUser?.role);
}

function updateAuthUI() {
  const signedIn = Boolean(state.currentUser);
  $("#userBar").hidden = !signedIn;
  if (signedIn) {
    $("#currentUserLabel").textContent = `${state.currentUser.display_name} · ${roleLabel(state.currentUser.role)}`;
  }
  homeTab.hidden = !signedIn;
  profileTab.hidden = !signedIn;
  adminTab.hidden = !signedIn || !canManage();
  reviewTab.hidden = !signedIn || !canManage();
}

function clearAuth() {
  state.authToken = "";
  state.currentUser = null;
  state.watchHistory = [];
  state.rewardProfile = null;
  leaveWatchRoom();
  localStorage.removeItem("auth_token");
  updateAuthUI();
  renderRewardProfile();
  renderProfileGallery();
}

function setAuthStatus(message, isError = false) {
  const status = $("#authStatus");
  status.textContent = message;
  status.classList.toggle("error", isError);
}

function syncAuthMode() {
  const isRegister = state.authMode === "register";
  $("#displayNameField").hidden = !isRegister;
  $("#authSubmit").textContent = isRegister ? "注册并登录" : "登录";
  $("#toggleAuthMode").textContent = isRegister ? "返回登录" : "注册新用户";
  $("#authPassword").autocomplete = isRegister ? "new-password" : "current-password";
}

async function restoreAuth() {
  if (!state.authToken) {
    updateAuthUI();
    return false;
  }
  try {
    const payload = await fetchJSON("/api/auth/me");
    state.currentUser = payload.user;
    updateAuthUI();
    return true;
  } catch {
    clearAuth();
    return false;
  }
}

async function afterAuth() {
  updateAuthUI();
  await loadDramas();
  await loadWatchHistory();
  await loadRewardProfile();
  await routeAfterAuth();
}

async function routeAfterAuth() {
  const episodeId = Number(new URLSearchParams(location.search).get("episode"));
  if (location.hash === "#admin") {
    setView("admin");
  } else if (location.hash === "#review") {
    setView("review");
  } else if (location.hash === "#profile") {
    setView("profile");
  } else if (Number.isFinite(episodeId) && episodeId > 0) {
    await openEpisodeFromUrl(episodeId);
  } else {
    setView("home");
  }
}

async function submitAuth(event) {
  event.preventDefault();
  const username = $("#authUsername").value.trim();
  const password = $("#authPassword").value;
  const displayName = $("#authDisplayName").value.trim() || username;
  if (!username || !password) {
    setAuthStatus("请输入用户名和密码", true);
    return;
  }
  try {
    const url = state.authMode === "register" ? "/api/auth/register" : "/api/auth/login";
    const payload = await fetchJSON(url, {
      method: "POST",
      body: JSON.stringify(
        state.authMode === "register" ? { username, password, display_name: displayName } : { username, password }
      ),
    });
    state.authToken = payload.token;
    state.currentUser = payload.user;
    localStorage.setItem("auth_token", payload.token);
    setAuthStatus(`已登录：${payload.user.display_name}`);
    await afterAuth();
  } catch (error) {
    setAuthStatus(errorMessage(error), true);
  }
}

async function logout() {
  try {
    await fetchJSON("/api/auth/logout", { method: "POST" });
  } catch {
    // Local logout should still complete even if the server session has expired.
  }
  clearAuth();
  setView("auth");
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

function setPlayerStatus(message, isError = false) {
  if (!playerStatus) return;
  playerStatus.textContent = message || "";
  playerStatus.hidden = !message;
  playerStatus.classList.toggle("error", Boolean(isError));
}

function clearPlayerStatus() {
  setPlayerStatus("");
}

function setView(name) {
  if (name !== "auth" && !state.currentUser) {
    name = "auth";
  }
  if ((name === "admin" || name === "review") && !canManage()) {
    name = "home";
  }
  Object.entries(views).forEach(([key, element]) => element.classList.toggle("active", key === name));
  homeTab.classList.toggle("active", name === "home");
  profileTab.classList.toggle("active", name === "profile");
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
  if (name === "profile") {
    loadRewardProfile();
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

function currentExperienceConfig() {
  return state.currentExperience?.config || {};
}

function themeWithExperience(baseTheme) {
  const config = currentExperienceConfig().player_theme || {};
  return {
    ...baseTheme,
    name: config.name || baseTheme.name,
    className: config.class_name || config.className || baseTheme.className,
    accent: config.accent || baseTheme.accent,
    soft: config.soft || baseTheme.soft,
    badge: config.badge || baseTheme.badge,
    signal: config.signal || baseTheme.signal,
    playIcon: config.play_icon || config.playIcon || baseTheme.playIcon,
    pauseIcon: config.pause_icon || config.pauseIcon || baseTheme.pauseIcon,
    muteIcon: config.mute_icon || config.muteIcon || baseTheme.muteIcon,
    mutedIcon: config.muted_icon || config.mutedIcon || baseTheme.mutedIcon,
  };
}

function getCurrentPlayerTheme(episode = state.currentEpisode) {
  return themeWithExperience(getPlayerTheme(episode?.drama?.title || ""));
}

function applyPlayerTheme(episode) {
  const theme = getCurrentPlayerTheme(episode);
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
  const theme = getCurrentPlayerTheme();
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
  if (String(comment.className || "").includes("room-danmaku")) return true;
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
    postRoomEvent("danmaku_like", {
      comment_id: comment.id,
      text: comment.text,
      likes: stats.likes,
    });
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
    postRoomEvent("danmaku_reply", {
      comment_id: comment.id,
      text: comment.text,
      reply: value,
    });
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
  xianxiaRain: { src: "/assets/stickers/xianxia_rain.svg", label: "灵雨伞" },
  xianxiaSeal: { src: "/assets/stickers/xianxia_seal.svg", label: "法阵亮起" },
  xianxiaSpirit: { src: "/assets/stickers/xianxia_spirit.svg", label: "灵气现形" },
  treasureMap: { src: "/assets/stickers/treasure_map.svg", label: "藏宝图" },
  treasureCompass: { src: "/assets/stickers/treasure_compass.svg", label: "罗盘指针" },
  treasureTrap: { src: "/assets/stickers/treasure_trap.svg", label: "机关警报" },
  winterSnow: { src: "/assets/stickers/winter_snow.svg", label: "冬至雪" },
  winterHeart: { src: "/assets/stickers/winter_heart.svg", label: "心事" },
  winterMemory: { src: "/assets/stickers/winter_memory.svg", label: "旧照片" },
  winterCrow: { src: "/assets/stickers/winter_crow.svg", label: "乌鸦无语" },
  winterWow: { src: "/assets/stickers/winter_wow.svg", label: "哇塞" },
  winterKiss: { src: "/assets/stickers/winter_kiss.svg", label: "突然亲吻" },
  winterChoice: { src: "/assets/stickers/winter_choice.svg", label: "选哪边" },
  winterBrokenHeart: { src: "/assets/stickers/winter_broken_heart.svg", label: "心碎" },
  winterBlush: { src: "/assets/stickers/winter_blush.svg", label: "脸红" },
  winterHeartbeat: { src: "/assets/stickers/winter_heartbeat.svg", label: "心跳" },
  winterHoldBack: { src: "/assets/stickers/winter_hold_back.svg", label: "别冲动" },
  winterQuestionLove: { src: "/assets/stickers/winter_question_love.svg", label: "爱还是现实" },
  winterWarmHug: { src: "/assets/stickers/winter_warm_hug.svg", label: "抱抱" },
  vehicleTrain: { src: "/assets/stickers/vehicle_train.svg", label: "火车" },
  vehicleCar: { src: "/assets/stickers/vehicle_car.svg", label: "小车" },
  vehicleMotorcycle: { src: "/assets/stickers/vehicle_motorcycle.svg", label: "摩托车" },
  charge: { src: "/assets/stickers/effect_charge.svg", label: "冲" },
  question: { src: "/assets/stickers/effect_question.svg", label: "问号" },
  laugh: { src: "/assets/stickers/effect_laugh.svg", label: "好笑" },
  rock: { src: "/assets/stickers/effect_rock.svg", label: "摇滚" },
  tear: { src: "/assets/stickers/effect_tear.svg", label: "心疼" },
};

const STICKER_ASSET_GROUPS = [
  {
    key: "road",
    label: "北往返乡",
    assetIds: [
      "mealSteam",
      "noPayBill",
      "goSign",
      "debtCash",
      "homePhone",
      "homeLantern",
      "smokeQuestion",
      "wageStamp",
      "homeTicket",
      "roadQuestion",
      "rockWord",
      "rockMoto",
      "northTitle",
    ],
  },
  { key: "xianxia", label: "仙侠悬念", assetIds: ["xianxiaRain", "xianxiaSeal", "xianxiaSpirit"] },
  { key: "treasure", label: "寻宝机关", assetIds: ["treasureMap", "treasureCompass", "treasureTrap"] },
  {
    key: "winter",
    label: "冬至爱情",
    assetIds: [
      "winterSnow",
      "winterHeart",
      "winterMemory",
      "winterCrow",
      "winterWow",
      "winterKiss",
      "winterChoice",
      "winterBrokenHeart",
      "winterBlush",
      "winterHeartbeat",
      "winterHoldBack",
      "winterQuestionLove",
      "winterWarmHug",
    ],
  },
  { key: "vehicle", label: "交通选择", assetIds: ["vehicleTrain", "vehicleCar", "vehicleMotorcycle"] },
  { key: "common", label: "通用情绪", assetIds: ["charge", "question", "laugh", "rock", "tear"] },
];

const STICKER_ASSET_GROUP_BY_ID = Object.fromEntries(
  STICKER_ASSET_GROUPS.flatMap((group) => group.assetIds.map((assetId) => [assetId, group]))
);

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
  {
    asset: "xianxiaRain",
    className: "sticker-xianxia-rain",
    keywords: ["雨", "撑伞", "老宅", "门房", "白衣女子"],
    tapWords: ["灵雨", "她来了", "身份不明"],
    positions: [
      { left: "9%", top: "25%" },
      { left: "56%", top: "38%" },
    ],
    durationMs: 3800,
    clickHoldMs: 2200,
  },
  {
    asset: "xianxiaSeal",
    className: "sticker-xianxia-seal",
    keywords: ["修仙", "神秘", "身份", "看不到", "法阵"],
    tapWords: ["法阵亮了", "不简单", "有灵气"],
    positions: [
      { left: "58%", top: "22%" },
      { left: "12%", top: "43%" },
    ],
    durationMs: 4100,
    clickHoldMs: 2400,
  },
  {
    asset: "xianxiaSpirit",
    className: "sticker-xianxia-spirit",
    keywords: ["病危", "老爷", "吊了一口气", "看不到她", "奇迹"],
    tapWords: ["悬着", "等她", "灵气+1"],
    positions: [
      { left: "57%", top: "45%" },
      { left: "10%", top: "28%" },
    ],
    durationMs: 4100,
    clickHoldMs: 2400,
  },
  {
    asset: "treasureMap",
    className: "sticker-treasure-map",
    keywords: ["寻宝", "地图", "线索", "北派"],
    tapWords: ["有线索", "标记", "跟上"],
    positions: [
      { left: "10%", top: "30%" },
      { left: "58%", top: "42%" },
    ],
    durationMs: 3900,
    clickHoldMs: 2300,
  },
  {
    asset: "treasureCompass",
    className: "sticker-treasure-compass",
    keywords: ["罗盘", "方向", "机关", "墓", "宝"],
    tapWords: ["指针动了", "方向对了", "开路"],
    positions: [
      { left: "58%", top: "24%" },
      { left: "12%", top: "44%" },
    ],
    durationMs: 4000,
    clickHoldMs: 2400,
  },
  {
    asset: "treasureTrap",
    className: "sticker-treasure-trap",
    keywords: ["危险", "机关", "陷阱", "冲突", "紧张"],
    tapWords: ["小心", "有机关", "别乱动"],
    positions: [
      { left: "12%", top: "24%" },
      { left: "58%", top: "46%" },
    ],
    durationMs: 3800,
    clickHoldMs: 2300,
  },
  {
    asset: "winterSnow",
    className: "sticker-winter-snow",
    keywords: ["冬至", "雪", "冷", "那年"],
    tapWords: ["下雪了", "冬至", "氛围到了"],
    positions: [
      { left: "10%", top: "24%" },
      { left: "58%", top: "42%" },
    ],
    durationMs: 3900,
    clickHoldMs: 2300,
  },
  {
    asset: "winterHeart",
    className: "sticker-winter-heart",
    keywords: ["爱情", "心动", "相遇", "喜欢", "遗憾", "亲吻", "小心心"],
    tapWords: ["心动", "小心心", "磕到了"],
    positions: [
      { left: "64%", top: "12%" },
      { left: "12%", top: "42%" },
    ],
    durationMs: 2200,
    clickHoldMs: 1000,
    tapColor: "rgba(255, 126, 172, 0.82)",
    heartEffect: true,
    maxScale: 2.22,
  },
  {
    asset: "winterMemory",
    className: "sticker-winter-memory",
    keywords: ["回忆", "那年", "旧事", "误会", "反转", "安乐死", "难过"],
    tapWords: ["想起了", "有故事", "破防"],
    positions: [
      { left: "10%", top: "44%" },
      { left: "56%", top: "28%" },
    ],
    durationMs: 4000,
    clickHoldMs: 2400,
  },
  {
    asset: "winterCrow",
    className: "sticker-winter-crow",
    keywords: ["乌鸦", "无语", "尴尬", "沉默"],
    tapWords: ["无语", "沉默了", "这也行"],
    positions: [
      { left: "56%", top: "20%" },
      { left: "12%", top: "36%" },
    ],
    durationMs: 2400,
    clickHoldMs: 1000,
    tapColor: "rgba(159, 216, 255, 0.72)",
  },
  {
    asset: "winterWow",
    className: "sticker-winter-wow",
    keywords: ["震惊", "突然", "脱衣服", "哇塞"],
    tapWords: ["哇塞", "我看傻", "太突然"],
    positions: [
      { left: "12%", top: "20%" },
      { left: "62%", top: "44%" },
    ],
    durationMs: 2400,
    clickHoldMs: 1000,
    tapColor: "rgba(255, 203, 111, 0.82)",
  },
  {
    asset: "winterKiss",
    className: "sticker-winter-kiss",
    keywords: ["亲吻", "亲嘴", "撒糖", "磕到了", "甜"],
    tapWords: ["哇塞", "亲了", "甜疯了"],
    positions: [
      { left: "56%", top: "26%" },
      { left: "11%", top: "44%" },
      { left: "43%", top: "18%" },
    ],
    durationMs: 2200,
    clickHoldMs: 1000,
    tapColor: "rgba(255, 126, 172, 0.9)",
    heartEffect: true,
    maxScale: 2.38,
  },
  {
    asset: "winterChoice",
    className: "sticker-winter-choice",
    keywords: ["选择", "选哪一个", "男主会选择", "期待"],
    tapWords: ["选谁", "站哪边", "等答案"],
    positions: [
      { left: "10%", top: "25%" },
      { left: "60%", top: "44%" },
    ],
    durationMs: 2600,
    clickHoldMs: 1200,
  },
  {
    asset: "winterBrokenHeart",
    className: "sticker-winter-broken-heart",
    keywords: ["安乐死", "伤心", "难过", "心疼", "破防", "沉重"],
    tapWords: ["破防", "心碎", "抱抱"],
    positions: [
      { left: "58%", top: "21%" },
      { left: "10%", top: "40%" },
    ],
    durationMs: 2700,
    clickHoldMs: 1200,
  },
  {
    asset: "winterBlush",
    className: "sticker-winter-blush",
    keywords: ["脸红", "心动", "暧昧", "亲吻", "撒糖"],
    tapWords: ["脸红", "好甜", "磕到"],
    positions: [
      { left: "12%", top: "22%" },
      { left: "61%", top: "39%" },
    ],
    durationMs: 2400,
    clickHoldMs: 1000,
    heartEffect: true,
    maxScale: 2.12,
  },
  {
    asset: "winterHeartbeat",
    className: "sticker-winter-heartbeat",
    keywords: ["心跳", "心动", "紧张", "亲吻", "选择"],
    tapWords: ["心跳", "紧张", "加速"],
    positions: [
      { left: "58%", top: "18%" },
      { left: "10%", top: "46%" },
    ],
    durationMs: 2400,
    clickHoldMs: 1000,
    heartEffect: true,
    maxScale: 2.18,
  },
  {
    asset: "winterHoldBack",
    className: "sticker-winter-hold-back",
    keywords: ["脱衣服", "别冲动", "突然", "震惊"],
    tapWords: ["别冲动", "等一下", "我看傻"],
    positions: [
      { left: "61%", top: "22%" },
      { left: "10%", top: "42%" },
    ],
    durationMs: 2500,
    clickHoldMs: 1100,
  },
  {
    asset: "winterQuestionLove",
    className: "sticker-winter-question-love",
    keywords: ["选择", "选哪一个", "现实", "心动", "期待"],
    tapWords: ["选谁", "好难", "站心动"],
    positions: [
      { left: "13%", top: "22%" },
      { left: "60%", top: "43%" },
    ],
    durationMs: 2600,
    clickHoldMs: 1200,
  },
  {
    asset: "winterWarmHug",
    className: "sticker-winter-warm-hug",
    keywords: ["抱抱", "难过", "心疼", "安慰", "共情"],
    tapWords: ["抱抱", "别怕", "心疼"],
    positions: [
      { left: "58%", top: "40%" },
      { left: "9%", top: "26%" },
    ],
    durationMs: 2600,
    clickHoldMs: 1200,
    heartEffect: true,
    maxScale: 2.08,
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
  const configuredTimeline = currentExperienceConfig().sticker_timeline;
  const timeline = Array.isArray(configuredTimeline) && configuredTimeline.length ? configuredTimeline : null;
  if (!timeline && !isBeiwangEpisode()) return null;
  const slots = timeline || BEIWANG_STICKER_TIMELINE;
  return (
    slots.find((slot, index) => {
      const start = Number(slot.start_time_sec ?? slot.start ?? 0);
      const end = Number(slot.end_time_sec ?? slot.end ?? start);
      const isLast = index === slots.length - 1;
      return timeSec >= start && (timeSec < end || (isLast && timeSec <= end));
    }) || null
  );
}

function stickerRulesFromSlot(slot) {
  return (slot?.assets || slot?.asset_ids || []).map(stickerRuleByAsset).filter(Boolean);
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
  const isHeart = Boolean(rule?.heartEffect);
  const count = isHeart ? (clicks >= 24 ? 18 : clicks >= 8 ? 14 : 9) : clicks >= 10 ? 14 : clicks >= 5 ? 10 : 7;
  for (let index = 0; index < count; index += 1) {
    const particle = document.createElement("i");
    particle.className = `sticker-burst-dot ${isHeart ? "sticker-heart-dot" : ""} burst-${rule?.asset || "default"}`;
    if (isHeart) particle.textContent = "♥";
    const angle = (Math.PI * 2 * index) / count;
    const distance =
      22 + stableRatio(`${rule?.asset}-${clicks}-${index}`) * (isHeart ? Math.min(76, 34 + clicks * 1.2) : clicks >= 10 ? 46 : 30);
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
  const isHeart = Boolean(rule.heartEffect);
  const displayClicks = clicks > 99 ? "MAX" : String(clicks);
  state.stickerCombo += 1;
  sticker.dataset.clicks = String(clicks);
  sticker.style.setProperty("--tap-scale", `${Math.min(rule.maxScale || (isHeart ? 2.2 : 1.75), 1 + clicks * (isHeart ? 0.045 : 0.065))}`);
  sticker.classList.toggle("hot", !isHeart && clicks >= 5);
  sticker.classList.toggle("mega", !isHeart && clicks >= 10);
  sticker.classList.toggle("heart-hot", isHeart && clicks >= 5);
  sticker.classList.toggle("heart-mega", isHeart && clicks >= 16);
  sticker.querySelector(".sticker-count").textContent = displayClicks;
  sticker.classList.add("tapped");
  sticker.classList.add("click-hold");
  window.clearTimeout(sticker.clickHoldTimer);
  sticker.clickHoldTimer = window.setTimeout(() => sticker.classList.remove("click-hold"), 1100);
  window.setTimeout(() => sticker.classList.remove("tapped"), 180);
  const words = rule.tapWords || [STICKER_ASSETS[rule.asset]?.label || "+1"];
  spawnTapText(sticker, `${words[clicks % words.length]} +1`, `fx-${rule.asset || "default"} ${isHeart ? "fx-heart" : ""}`);
  spawnTapText(sticker, `总${displayClicks}`, `fx-count ${isHeart ? "fx-heart-count" : ""}`);
  spawnStickerBurst(sticker, rule, clicks);
  if (isHeart && (clicks === 5 || clicks === 16 || clicks % 25 === 0)) {
    spawnTapText(sticker, clicks >= 16 ? "心动MAX" : "爱心变大", "fx-heart");
  } else if (!isHeart && (clicks === 5 || clicks === 10 || clicks % 15 === 0)) {
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
  if (rule.heartEffect) sticker.dataset.mood = "heart";
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
  const count = slot?.burstCount || slot?.burst_count || 4;
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
    const start = Number(slot.start_time_sec ?? slot.start ?? 0);
    const cadence = Number(slot.cadence_sec ?? slot.cadenceSec ?? 6);
    const localTime = Math.max(0, currentTime - start);
    return rules[Math.floor(localTime / cadence) % rules.length];
  }
  if (isBeiwangEpisode()) return null;
  return STICKER_RULES[Math.floor(currentTime / 4) % STICKER_RULES.length];
}

function scheduleAmbientStickers() {
  window.clearTimeout(state.ambientStickerTimer);
  if (!state.currentEpisode || state.activeHighlight || player.paused || views.watch.classList.contains("active") === false) return;
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
      (drama) => {
        const history = state.watchHistory.find((item) => item.drama.id === drama.id);
        return `
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
            <p>${drama.episode_count} 集已导入${history ? ` · 上次看到 ${formatTime(history.progress_sec)}` : ""}</p>
            ${
              history
                ? `<div class="drama-progress"><i style="width:${Math.min(100, Math.max(0, Number(history.progress_percent || 0)))}%"></i></div>`
                : ""
            }
            <div class="drama-actions">
              <button class="ghost-button drama-action-button" type="button" data-action="start" data-drama-id="${drama.id}">开始看</button>
              ${
                history
                  ? `<button class="primary-button drama-action-button" type="button" data-action="resume" data-episode-id="${history.episode_id}" data-progress="${history.progress_sec}">继续看</button>`
                  : ""
              }
            </div>
          </div>
        </article>
      `;
      }
    )
    .join("");

  document.querySelectorAll(".drama-card").forEach((card) => {
    card.addEventListener("click", () => openDrama(Number(card.dataset.id)));
  });
  document.querySelectorAll(".drama-action-button").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      if (button.dataset.action === "resume") {
        openEpisodeFromUrl(Number(button.dataset.episodeId), Number(button.dataset.progress || 0));
      } else {
        openDrama(Number(button.dataset.dramaId));
      }
    });
  });
}

function renderWatchHistory() {
  const host = $("#watchHistory");
  if (!host) return;
  if (!state.watchHistory.length) {
    host.innerHTML = "";
    return;
  }
  host.innerHTML = `
    <div class="history-title">最近观看</div>
    ${state.watchHistory
      .slice(0, 4)
      .map(
        (item) => `
          <button class="history-row" type="button" data-episode-id="${item.episode_id}">
            <div>
              <strong>${escapeHTML(item.drama.title)} · ${escapeHTML(item.episode_title)}</strong>
              <span>${escapeHTML(item.drama.genre)} · 进度 ${formatTime(item.progress_sec)}</span>
              <span class="history-progress"><i style="width:${Math.min(100, Math.max(0, Number(item.progress_percent || 0)))}%"></i></span>
            </div>
            <span>继续</span>
          </button>
        `
      )
      .join("")}
  `;
  host.querySelectorAll(".history-row").forEach((button) => {
    const item = state.watchHistory.find((row) => row.episode_id === Number(button.dataset.episodeId));
    button.addEventListener("click", () => openEpisodeFromUrl(Number(button.dataset.episodeId), Number(item?.progress_sec || 0)));
  });
}

async function loadWatchHistory() {
  if (!state.currentUser) return;
  try {
    state.watchHistory = await fetchJSON("/api/users/me/watch-history");
  } catch {
    state.watchHistory = [];
  }
  renderWatchHistory();
  if (state.dramas.length) renderDramas();
  if (state.episodes.length) renderEpisodePanel();
}

function recordWatchHistory(progressSec = 0, immediate = false) {
  if (!state.currentUser || !state.currentEpisode) return;
  window.clearTimeout(state.watchHistoryTimer);
  const write = () => {
    fetchJSON("/api/users/me/watch-history", {
      method: "POST",
      body: JSON.stringify({ episode_id: state.currentEpisode.id, progress_sec: progressSec }),
    }).catch(() => {});
  };
  if (immediate) {
    write();
    return;
  }
  state.watchHistoryTimer = window.setTimeout(write, 400);
}

async function loadDramas() {
  state.dramas = await fetchJSON("/api/dramas");
  renderDramas();
}

function findEpisodeHistory(episodeId) {
  return state.watchHistory.find((item) => Number(item.episode_id) === Number(episodeId));
}

function watchPercent(history, episode) {
  if (!history) return 0;
  const direct = Number(history.progress_percent || 0);
  if (direct > 0) return Math.min(100, Math.max(0, direct));
  const duration = Number(history.duration_sec || episode?.duration_sec || 0);
  return duration > 0 ? Math.min(100, Math.max(0, (Number(history.progress_sec || 0) / duration) * 100)) : 0;
}

function renderEpisodePanel() {
  if (!episodePanel) return;
  if (!state.episodes.length) {
    episodePanel.innerHTML = "";
    return;
  }
  episodePanel.innerHTML = state.episodes
    .map((episode, index) => {
      const history = findEpisodeHistory(episode.id);
      const active = state.currentEpisode?.id === episode.id;
      const percent = watchPercent(history, episode);
      return `
        <button class="episode-pill ${active ? "active" : ""}" type="button" data-episode-id="${episode.id}" data-progress="${history?.progress_sec || 0}">
          <span>${index + 1}</span>
          <strong>${escapeHTML(episode.title)}</strong>
          ${history ? `<em>续 ${formatTime(history.progress_sec)}</em>` : ""}
          ${history ? `<i class="episode-progress" style="width:${percent}%"></i>` : ""}
        </button>
      `;
    })
    .join("");

  episodePanel.querySelectorAll(".episode-pill").forEach((button) => {
    button.addEventListener("click", () => openEpisode(Number(button.dataset.episodeId), { resumeTime: Number(button.dataset.progress || 0) }));
  });
}

function renderWatchRoom() {
  const room = state.watchRoom;
  if (!roomStatus) return;
  $("#leaveRoomButton").hidden = !room;
  $("#createRoomButton").textContent = room ? "新开房" : "开房";
  roomCodeInput.value = room?.code || roomCodeInput.value;
  if (!room) {
    roomStatus.textContent = "未开房";
    roomStatus.classList.remove("live");
    if (roomMemberList) roomMemberList.innerHTML = "";
    return;
  }
  const guest = room.guest?.display_name || "等待好友";
  const stateLabel = room.playback_state === "playing" ? "播放中" : "已暂停";
  roomStatus.textContent = `房间 ${room.code} · ${room.member_count}/2 · ${guest} · ${stateLabel}`;
  roomStatus.classList.add("live");
  renderRoomMembers(room);
}

function renderRoomMembers(room) {
  if (!roomMemberList) return;
  const members = [room.host, room.guest].filter(Boolean);
  roomMemberList.innerHTML = members
    .map((member) => {
      const isSelf = member.id === state.currentUser?.id;
      return `
        <button class="room-member-card ${isSelf ? "self" : ""}" type="button" data-user-id="${member.id}">
          ${badgeArtHTML(member.growth_title || "剧情新人", false, "mini")}
          <div>
            <strong>${escapeHTML(member.display_name || "同看用户")}${isSelf ? " · 我" : ""}</strong>
            <span>${escapeHTML(member.growth_title || "剧情新人")} · ${Number(member.points || 0)} 分 · ${Number(
              member.badge_count || 0
            )} 枚徽章</span>
          </div>
        </button>
      `;
    })
    .join("");
}

function roomEventSummary(event) {
  const user = event.user?.display_name || "同看好友";
  const payload = event.payload || {};
  if (event.event_type === "danmaku") return `${user} 发弹幕：${payload.text || payload.comment?.text || ""}`;
  if (event.event_type === "danmaku_like") return `${user} 赞了弹幕：${payload.text || ""}`;
  if (event.event_type === "danmaku_reply") return `${user} 回复：${payload.reply || ""}`;
  if (event.event_type === "interaction") {
    if (payload.reward_correct) {
      const badgeTitle = payload.reward_badge?.title || payload.reward_title || "新徽章";
      return `${user} 答对「${payload.option_label || payload.option_key || "未知"}」，解锁「${badgeTitle}」`;
    }
    const suffix = payload.reward_message ? `，${payload.reward_message}` : "";
    return `${user} 选择了「${payload.option_label || payload.option_key || "未知"}」${suffix}`;
  }
  return `${user} 有新动态`;
}

function roomEventBadge(event) {
  const payload = event.payload || {};
  if (event.event_type === "interaction" && payload.reward_correct) {
    return badgeArtHTML(payload.reward_badge?.title || payload.reward_title || "预判成功", false, "tiny");
  }
  if (event.user?.latest_badges?.length) {
    return badgeArtHTML(event.user.latest_badges[0].title, false, "tiny");
  }
  return badgeArtHTML(event.user?.growth_title || "剧情新人", false, "tiny");
}

function renderRoomFeed() {
  if (!roomFeed) return;
  if (!state.watchRoom || !state.roomFeed.length) {
    roomFeed.innerHTML = "";
    return;
  }
  roomFeed.innerHTML = state.roomFeed
    .slice(0, 5)
    .map(
      (event) => `
        <div class="room-feed-item ${event.event_type}">
          ${roomEventBadge(event)}
          <div>
            <b>${escapeHTML(event.user?.display_name || "同看好友")}</b>
            <span>${escapeHTML(roomEventSummary(event).replace(`${event.user?.display_name || "同看好友"} `, ""))}</span>
          </div>
        </div>
      `
    )
    .join("");
}

function pushRoomFeed(event) {
  state.roomFeed = [event, ...state.roomFeed.filter((item) => item.id !== event.id)].slice(0, 12);
  renderRoomFeed();
}

function showRoomEventToast(event) {
  const wrap = document.querySelector(".video-wrap");
  if (!wrap) return;
  const toast = document.createElement("div");
  toast.className = `room-event-toast ${event.event_type}`;
  toast.innerHTML = `
    ${roomEventBadge(event)}
    <div>
      <b>${escapeHTML(event.user?.display_name || "同看好友")}</b>
      <span>${escapeHTML(roomEventSummary(event))}</span>
    </div>
  `;
  wrap.appendChild(toast);
  window.setTimeout(() => toast.remove(), 5200);
}

async function postRoomEvent(eventType, payload) {
  if (!state.watchRoom) return null;
  try {
    const event = await fetchJSON(`/api/watch-rooms/${state.watchRoom.code}/events`, {
      method: "POST",
      body: JSON.stringify({ event_type: eventType, payload }),
    });
    state.roomLastEventId = Math.max(state.roomLastEventId, event.id || 0);
    pushRoomFeed(event);
    return event;
  } catch {
    return null;
  }
}

async function fetchRoomEvents() {
  if (!state.watchRoom) return;
  const events = await fetchJSON(`/api/watch-rooms/${state.watchRoom.code}/events?after_id=${state.roomLastEventId}`);
  for (const event of events) {
    state.roomLastEventId = Math.max(state.roomLastEventId, event.id || 0);
    if (event.user?.id === state.currentUser?.id) continue;
    handleRoomEvent(event);
  }
}

function handleRoomEvent(event) {
  const payload = event.payload || {};
  pushRoomFeed(event);
  showRoomEventToast(event);
  if (event.event_type === "danmaku") {
    const comment = payload.comment || {
      id: `room-${event.id}`,
      episode_id: payload.episode_id,
      time_sec: payload.time_sec || player.currentTime || 0,
      text: payload.text,
      mode: "carnival",
    };
    if (!state.currentEpisode || Number(comment.episode_id) === state.currentEpisode.id) {
      const remoteComment = {
        ...comment,
        id: comment.id || `room-${event.id}`,
        mode: "light",
        user_name: event.user?.display_name,
        className: "room-danmaku",
      };
      state.danmaku.push(remoteComment);
      state.firedDanmaku.add(remoteComment.id);
      emitDanmaku(remoteComment);
    }
  }
  if (event.event_type === "interaction") {
    emitDanmaku({
      id: `room-choice-${event.id}`,
      text: payload.reward_correct
        ? `${event.user?.display_name || "好友"}答对了，解锁${payload.reward_badge?.title || "徽章"}`
        : `${event.user?.display_name || "好友"}选了${payload.option_label || ""}`,
      time_sec: player.currentTime || 0,
      className: "danmaku-impact room-danmaku",
    });
  }
  if (event.event_type === "danmaku_like" || event.event_type === "danmaku_reply") {
    setDanmakuFeedback(roomEventSummary(event));
  }
}

function renderRewardProfile() {
  if (!rewardPanel) return;
  const profile = state.rewardProfile;
  if (!profile) {
    rewardPanel.innerHTML = "";
    return;
  }
  const badges = profile.badges || [];
  const collectionTotal = Number(profile.collection_total || 0);
  const collectionUnlocked = Number(profile.collection_unlocked || badges.length || 0);
  rewardPanel.innerHTML = `
    <div>
      <strong>${escapeHTML(profile.title || "剧情新人")}</strong>
      <span>${Number(profile.points || 0)} 分 · ${collectionUnlocked}/${collectionTotal || badges.length} 枚勋章</span>
    </div>
    <div class="reward-badges">
      ${
        badges.length
          ? badges
              .slice(0, 3)
              .map((badge) => `<em title="${escapeHTML(badge.description || "")}">${escapeHTML(badge.title)}</em>`)
              .join("")
          : "<em>等待首枚勋章</em>"
      }
      <button class="reward-gallery-link" type="button">展馆</button>
    </div>
  `;
}

function formatDateTime(value) {
  if (!value) return "未解锁";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "未解锁";
  return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, "0")}:${date
    .getMinutes()
    .toString()
    .padStart(2, "0")}`;
}

function badgeSeed(title = "") {
  return Array.from(String(title || "剧情新人")).reduce((sum, char) => sum + char.charCodeAt(0), 0);
}

function badgeMark(title = "") {
  const text = String(title || "剧情");
  const chars = Array.from(text).filter((char) => /[\u4e00-\u9fa5A-Za-z0-9]/.test(char));
  return (chars.slice(0, 2).join("") || "剧").slice(0, 2);
}

function badgeTheme(title = "") {
  const text = String(title || "");
  if (/摇滚|返乡|北往|摩托/.test(text)) {
    return { key: "road", mark: "返乡", a: "#ff9852", b: "#28d4c8", c: "#43224c" };
  }
  if (/冬至|心|爱|甜|预言/.test(text)) {
    return { key: "winter", mark: "冬至", a: "#9fd8ff", b: "#ff8ebb", c: "#3c2c76" };
  }
  if (/北派|宝|护|寻/.test(text)) {
    return { key: "treasure", mark: "护宝", a: "#f2bf63", b: "#4ed18c", c: "#24375e" };
  }
  if (/灵雨|云渺|识局|身份/.test(text)) {
    return { key: "xianxia", mark: "灵雨", a: "#8bd3ff", b: "#b990ff", c: "#20356f" };
  }
  if (/读心|高光|收藏/.test(text)) {
    return { key: "growth", mark: "高光", a: "#ffd66e", b: "#ff6d8f", c: "#2d335e" };
  }
  return { key: "default", mark: badgeMark(title), a: "#ffd66e", b: "#66e4c1", c: "#27324f" };
}

function badgeIconSVG(key) {
  if (key === "road") {
    return `
      <g class="badge-icon road">
        <circle cx="48" cy="72" r="9"></circle><circle cx="74" cy="72" r="9"></circle>
        <path d="M46 69 L58 54 L75 58 L83 67"></path>
        <path d="M61 54 L68 45 L79 48"></path>
        <path d="M36 49 C47 42 58 40 70 44"></path>
        <path d="M35 61 H22 M31 70 H17"></path>
        <text x="57" y="39">家</text>
      </g>
    `;
  }
  if (key === "winter") {
    return `
      <g class="badge-icon winter">
        <path d="M60 76 C38 60 45 42 58 46 C64 37 82 43 78 59 C76 68 66 73 60 76Z"></path>
        <path d="M31 42 L39 42 M35 38 L35 46 M32 39 L38 45 M38 39 L32 45"></path>
        <path d="M82 36 L92 36 M87 31 L87 41 M83 32 L91 40 M91 32 L83 40"></path>
        <circle cx="42" cy="73" r="4"></circle><circle cx="82" cy="76" r="3"></circle>
      </g>
    `;
  }
  if (key === "treasure") {
    return `
      <g class="badge-icon treasure">
        <path d="M35 58 H83 V79 H35Z"></path>
        <path d="M39 58 C42 42 77 42 80 58"></path>
        <path d="M58 58 V79 M33 67 H85"></path>
        <rect x="54" y="62" width="8" height="8" rx="2"></rect>
        <path d="M82 42 L91 34 L96 45 L87 53Z"></path>
        <path d="M24 47 L29 37 L35 48"></path>
      </g>
    `;
  }
  if (key === "xianxia") {
    return `
      <g class="badge-icon xianxia">
        <path d="M35 50 C39 39 54 41 57 50 C65 40 80 47 80 59 C80 69 66 72 55 68 C45 73 32 67 32 58 C32 54 33 52 35 50Z"></path>
        <path d="M45 76 L42 88 M57 77 L55 91 M69 76 L66 88"></path>
        <path d="M58 40 L66 54 L58 66 L50 54Z"></path>
        <circle cx="58" cy="54" r="4"></circle>
      </g>
    `;
  }
  if (key === "growth") {
    return `
      <g class="badge-icon growth">
        <path d="M60 35 L67 53 L86 54 L71 66 L76 84 L60 74 L44 84 L49 66 L34 54 L53 53Z"></path>
        <path d="M34 76 C46 88 72 90 88 75"></path>
        <circle cx="35" cy="42" r="4"></circle><circle cx="88" cy="43" r="4"></circle>
      </g>
    `;
  }
  return `
    <g class="badge-icon default">
      <path d="M60 37 L69 55 L89 58 L74 72 L78 92 L60 82 L42 92 L46 72 L31 58 L51 55Z"></path>
      <rect x="38" y="48" width="44" height="33" rx="10"></rect>
      <circle cx="53" cy="64" r="3"></circle><circle cx="67" cy="64" r="3"></circle>
    </g>
  `;
}

function badgeArtHTML(title = "", locked = false, size = "normal") {
  const seed = badgeSeed(title);
  const theme = badgeTheme(title);
  const gradientId = `badgeGradient${seed}${theme.key}${locked ? "Locked" : ""}`;
  const mark = locked ? "待解锁" : theme.mark || badgeMark(title);
  return `
    <div class="badge-art q-badge kind-${theme.key} ${locked ? "locked" : ""} size-${size}" title="${escapeHTML(title)}">
      <svg viewBox="0 0 120 140" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id="${gradientId}" x1="20" y1="14" x2="100" y2="118" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="${theme.a}" />
            <stop offset="1" stop-color="${theme.b}" />
          </linearGradient>
        </defs>
        <path class="badge-shadow" d="M27 112 C42 124 79 124 95 111 L82 86 H40Z"></path>
        <path class="badge-ribbon left" d="M46 88 L35 130 L58 116 L67 134 L72 91Z"></path>
        <path class="badge-ribbon right" d="M74 88 L86 130 L63 116 L54 134 L48 91Z"></path>
        <path class="badge-shell" fill="url(#${gradientId})" d="M60 8 C69 16 80 12 88 21 C96 30 91 41 101 50 C111 59 104 71 109 82 C99 88 103 102 91 108 C80 114 71 108 60 117 C49 108 40 114 29 108 C17 102 21 88 11 82 C16 71 9 59 19 50 C29 41 24 30 32 21 C40 12 51 16 60 8Z"></path>
        <circle class="badge-inner" cx="60" cy="63" r="42"></circle>
        <circle class="badge-highlight" cx="43" cy="41" r="9"></circle>
        <circle class="badge-dot dot-a" cx="27" cy="57" r="4"></circle>
        <circle class="badge-dot dot-b" cx="91" cy="39" r="4"></circle>
        <circle class="badge-dot dot-c" cx="96" cy="78" r="3"></circle>
        ${locked ? `<g class="badge-icon locked-icon"><rect x="43" y="56" width="34" height="28" rx="8"></rect><path d="M50 56 V48 C50 37 70 37 70 48 V56"></path></g>` : badgeIconSVG(theme.key)}
        <text class="badge-label" x="60" y="105">${escapeHTML(mark)}</text>
      </svg>
    </div>
  `;
}

function renderProfileGallery() {
  const host = $("#profileGallery");
  if (!host) return;
  const profile = state.rewardProfile;
  if (!state.currentUser) {
    host.innerHTML = `<div class="empty-state">登录后可以查看个人勋章展馆。</div>`;
    return;
  }
  if (!profile) {
    host.innerHTML = `<div class="empty-state">正在加载成长数据...</div>`;
    return;
  }
  const badges = profile.badges || [];
  const collection = profile.collection || [];
  const unlocked = collection.filter((item) => item.unlocked);
  const locked = collection.filter((item) => !item.unlocked);
  const nextTarget = locked[0];
  const completion = Number(profile.completion_percent || 0);
  host.innerHTML = `
    <section class="profile-hero">
      <div>
        <p class="eyebrow">User Growth</p>
        <h3>${escapeHTML(state.currentUser.display_name)}</h3>
        <strong>${escapeHTML(profile.title || "剧情新人")}</strong>
        <span>${Number(profile.points || 0)} 分 · ${unlocked.length}/${collection.length || badges.length} 枚专属徽章</span>
      </div>
      <div class="profile-ring" style="--progress:${Math.min(100, Math.max(0, completion))}%">
        <b>${Math.round(completion)}%</b>
        <small>收集率</small>
      </div>
    </section>

    <section class="growth-pipeline">
      ${["高光竞猜", "答对入账", "称号升级", "展馆沉淀"]
        .map((label, index) => `<div><span>0${index + 1}</span><strong>${label}</strong></div>`)
        .join("")}
    </section>

    ${
      nextTarget
        ? `<section class="next-badge-card">
            <span>下一枚可解锁</span>
            <strong>${escapeHTML(nextTarget.title)}</strong>
            <p>${escapeHTML(nextTarget.drama_title)} · ${escapeHTML(nextTarget.highlight_title)} · 答对 +${Number(
              nextTarget.points || 0
            )} 分</p>
            <button class="primary-button profile-open-episode" type="button" data-episode-id="${nextTarget.episode_id}">去解锁</button>
          </section>`
        : `<section class="next-badge-card complete">
            <span>当前展示徽章已集齐</span>
            <strong>完整收集者</strong>
            <p>后续每配置一集竞猜奖励，这里会自动扩展新的收集目标。</p>
          </section>`
    }

    <section class="badge-gallery">
      <div class="profile-section-title">
        <h3>剧集专属徽章</h3>
        <span>${unlocked.length}/${collection.length || badges.length}</span>
      </div>
      ${
        collection.length
          ? collection.map(renderCollectionBadge).join("")
          : `<div class="empty-state">暂无可收集徽章。先在复核页为剧集配置竞猜奖励。</div>`
      }
    </section>

    <section class="badge-history">
      <div class="profile-section-title">
        <h3>最近解锁</h3>
        <span>${badges.length} 条记录</span>
      </div>
      ${
        badges.length
          ? badges
              .slice(0, 6)
              .map(
                (badge) => `
                  <article>
                    <strong>${escapeHTML(badge.title)}</strong>
                    <span>+${Number(badge.points || 0)} 分 · ${formatDateTime(badge.created_at)}</span>
                  </article>
                `
              )
              .join("")
          : `<div class="empty-state">还没有解锁记录。答对同看竞猜后会出现在这里。</div>`
      }
    </section>
  `;
}

function renderCollectionBadge(item) {
  return `
    <article class="collection-badge ${item.unlocked ? "unlocked" : "locked"}">
      ${badgeArtHTML(item.title, !item.unlocked)}
      <div>
        <span>${escapeHTML(item.drama_title)} · 第${Number(item.episode_no || 1)}集</span>
        <h4>${escapeHTML(item.title)}</h4>
        <p>${escapeHTML(item.description || "")}</p>
        <small>${escapeHTML(item.highlight_title || "竞猜高光")} · ${item.unlocked ? formatDateTime(item.unlocked_at) : `待解锁 +${Number(item.points || 0)} 分`}</small>
      </div>
      <button class="ghost-button profile-open-episode" type="button" data-episode-id="${item.episode_id}">
        ${item.unlocked ? "回看" : "去解锁"}
      </button>
    </article>
  `;
}

function renderPublicProfileBadge(item) {
  return `
    <article class="public-badge-row ${item.unlocked ? "unlocked" : "locked"}">
      ${badgeArtHTML(item.title, !item.unlocked, "mini")}
      <div>
        <strong>${escapeHTML(item.title)}</strong>
        <span>${escapeHTML(item.drama_title || "短剧")} · ${escapeHTML(item.highlight_title || "竞猜高光")}</span>
      </div>
    </article>
  `;
}

function renderPublicProfile(payload) {
  const user = payload.user || {};
  const collection = payload.collection || [];
  const unlocked = collection.filter((item) => item.unlocked);
  const badges = payload.badges || [];
  const visibleBadges = unlocked.length ? unlocked : badges.map((badge) => ({ ...badge, unlocked: true }));
  publicProfileContent.innerHTML = `
    <section class="public-profile-hero">
      ${badgeArtHTML(payload.title || user.growth_title || "剧情新人", false)}
      <div>
        <p class="eyebrow">Public Gallery</p>
        <h3>${escapeHTML(user.display_name || "同看用户")}</h3>
        <strong>${escapeHTML(payload.title || user.growth_title || "剧情新人")}</strong>
        <span>${Number(payload.points || 0)} 分 · ${Number(payload.collection_unlocked || badges.length || 0)}/${Number(
          payload.collection_total || collection.length || badges.length || 0
        )} 枚徽章</span>
      </div>
    </section>
    <section class="public-profile-stats">
      <div><span>积分</span><strong>${Number(payload.points || 0)}</strong></div>
      <div><span>称号</span><strong>${escapeHTML(payload.title || user.growth_title || "剧情新人")}</strong></div>
      <div><span>收集率</span><strong>${Math.round(Number(payload.completion_percent || 0))}%</strong></div>
    </section>
    <section class="public-badge-list">
      <div class="profile-section-title">
        <h3>已展示徽章</h3>
        <span>${visibleBadges.length}</span>
      </div>
      ${
        visibleBadges.length
          ? visibleBadges.slice(0, 8).map(renderPublicProfileBadge).join("")
          : `<div class="empty-state">这个用户还没有解锁公开徽章。</div>`
      }
    </section>
  `;
}

async function openPublicProfile(userId) {
  if (!userId || !publicProfileModal || !publicProfileContent) return;
  publicProfileModal.classList.remove("hidden");
  publicProfileContent.innerHTML = `<div class="empty-state">正在加载用户成长展馆...</div>`;
  try {
    renderPublicProfile(await fetchJSON(`/api/users/${userId}/growth`));
  } catch (error) {
    publicProfileContent.innerHTML = `<div class="empty-state">无法加载展馆：${escapeHTML(errorMessage(error))}</div>`;
  }
}

function closePublicProfile() {
  publicProfileModal?.classList.add("hidden");
  if (publicProfileContent) publicProfileContent.innerHTML = "";
}

async function loadRewardProfile() {
  if (!state.currentUser) return;
  try {
    state.rewardProfile = await fetchJSON("/api/users/me/rewards");
  } catch {
    state.rewardProfile = null;
  }
  renderRewardProfile();
  renderProfileGallery();
}

function leaveWatchRoom() {
  window.clearInterval(state.roomPollTimer);
  state.roomPollTimer = null;
  state.watchRoom = null;
  state.roomLastSyncAt = 0;
  state.roomLastEventId = 0;
  state.roomFeed = [];
  renderWatchRoom();
  renderRoomFeed();
}

function startRoomPolling() {
  window.clearInterval(state.roomPollTimer);
  if (!state.watchRoom) return;
  state.roomPollTimer = window.setInterval(fetchRoomState, 2400);
}

async function fetchRoomState() {
  if (!state.watchRoom) return;
  try {
    const room = await fetchJSON(`/api/watch-rooms/${state.watchRoom.code}`);
    await applyRoomState(room);
    await fetchRoomEvents();
  } catch (error) {
    setDanmakuFeedback(errorMessage(error), true);
    leaveWatchRoom();
  }
}

async function applyRoomState(room) {
  state.watchRoom = room;
  renderWatchRoom();
  if (!room.episode_id || room.updated_by?.id === state.currentUser?.id) return;
  state.roomApplyingRemote = true;
  try {
    if (state.currentEpisode?.id !== room.episode_id) {
      await openEpisode(room.episode_id, { resumeTime: room.progress_sec });
    } else if (Math.abs((player.currentTime || 0) - Number(room.progress_sec || 0)) > 2.2) {
      player.currentTime = Number(room.progress_sec || 0);
    }
    if (room.playback_state === "playing" && player.paused) {
      player.play().catch(() => {});
    }
    if (room.playback_state !== "playing" && !player.paused) {
      player.pause();
    }
  } finally {
    window.setTimeout(() => {
      state.roomApplyingRemote = false;
    }, 600);
  }
}

async function syncRoomState(immediate = false) {
  if (!state.watchRoom || !state.currentEpisode || state.roomApplyingRemote) return;
  const now = Date.now();
  if (!immediate && now - state.roomLastSyncAt < 2600) return;
  state.roomLastSyncAt = now;
  try {
    state.watchRoom = await fetchJSON(`/api/watch-rooms/${state.watchRoom.code}/sync`, {
      method: "POST",
      body: JSON.stringify({
        episode_id: state.currentEpisode.id,
        progress_sec: player.currentTime || 0,
        playback_state: player.paused ? "paused" : "playing",
      }),
    });
    renderWatchRoom();
  } catch (error) {
    setDanmakuFeedback(errorMessage(error), true);
  }
}

async function createWatchRoom() {
  if (!state.currentEpisode) {
    setDanmakuFeedback("请先打开一集短剧", true);
    return;
  }
  try {
    state.watchRoom = await fetchJSON("/api/watch-rooms", {
      method: "POST",
      body: JSON.stringify({
        episode_id: state.currentEpisode.id,
        progress_sec: player.currentTime || 0,
        playback_state: player.paused ? "paused" : "playing",
      }),
    });
    state.roomLastEventId = 0;
    state.roomFeed = [];
    renderWatchRoom();
    renderRoomFeed();
    startRoomPolling();
    setDanmakuFeedback(`同看房间已创建：${state.watchRoom.code}`);
  } catch (error) {
    setDanmakuFeedback(errorMessage(error), true);
  }
}

async function joinWatchRoom() {
  const code = roomCodeInput.value.trim();
  if (!code) {
    setDanmakuFeedback("请输入房间码", true);
    return;
  }
  try {
    const room = await fetchJSON("/api/watch-rooms/join", {
      method: "POST",
      body: JSON.stringify({ code }),
    });
    state.roomLastEventId = 0;
    state.roomFeed = [];
    await applyRoomState(room);
    startRoomPolling();
    await fetchRoomEvents();
    setDanmakuFeedback(`已加入同看房间：${room.code}`);
  } catch (error) {
    setDanmakuFeedback(errorMessage(error), true);
  }
}

async function openDrama(dramaId, preferredEpisodeId = null, resumeTime = 0) {
  state.episodes = await fetchJSON(`/api/dramas/${dramaId}/episodes`);
  if (!state.episodes.length) return;
  const select = $("#episodeSelect");
  select.innerHTML = state.episodes
    .map((episode) => `<option value="${episode.id}">${episode.title}</option>`)
    .join("");
  select.onchange = () => openEpisode(Number(select.value));
  renderEpisodePanel();
  const targetEpisode = state.episodes.find((episode) => episode.id === preferredEpisodeId) || state.episodes[0];
  await openEpisode(targetEpisode.id, { resumeTime });
  setView("watch");
}

async function openEpisode(episodeId, options = {}) {
  state.firedHighlights.clear();
  state.lastInteractionTime = -Infinity;
  clearDanmakuLayer();
  clearStickerLayer();
  clearEndingRemix();
  hideInteraction();
  setPlayerStatus("正在加载剧集...");
  try {
    const [episode, danmaku, experience] = await Promise.all([
      fetchJSON(`/api/episodes/${episodeId}`),
      fetchJSON(`/api/episodes/${episodeId}/danmaku`),
      fetchJSON(`/api/episodes/${episodeId}/experience`),
    ]);
    state.currentEpisode = episode;
    state.currentExperience = experience;
    state.danmaku = danmaku;
    state.pendingResume = Number(options.resumeTime || 0) > 1 ? { episodeId, time: Number(options.resumeTime) } : null;
    state.lastHistoryRecordAt = 0;
    $("#watchTitle").textContent = `${state.currentEpisode.drama.title} · ${state.currentEpisode.title}`;
    $("#episodeSelect").value = String(episodeId);
    setPlayerStatus("视频加载中...");
    player.src = state.currentEpisode.video_url;
    player.load();
    applyPlayerTheme(state.currentEpisode);
    renderTimeline();
    renderDanmakuHint();
    renderEpisodePanel();
    syncEpisodeUrl(episodeId);
  } catch (error) {
    const message = `剧集加载失败：${errorMessage(error)}`;
    setPlayerStatus(message, true);
    setDanmakuFeedback(message, true);
  }
}

async function openEpisodeFromUrl(episodeId, resumeTime = 0) {
  const episode = await fetchJSON(`/api/episodes/${episodeId}`);
  await openDrama(episode.drama.id, episodeId, resumeTime);
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

function clearEndingRemix() {
  state.endingRemixShown = false;
  state.remixOptions = null;
  state.remixResult = null;
  state.remixLoading = false;
  if (endingRemixLayer) {
    endingRemixLayer.className = "ending-remix-layer hidden";
    endingRemixLayer.innerHTML = "";
  }
}

function renderEndingRemixOptions(payload) {
  if (!endingRemixLayer) return;
  const options = payload.options || [];
  const featured = payload.featured_remixes || [];
  endingRemixLayer.className = "ending-remix-layer";
  endingRemixLayer.innerHTML = `
    <section class="ending-remix-panel">
      <div class="remix-head">
        <span>片尾 AI 二创</span>
        <button class="close-button remix-close-button" type="button" data-remix-action="close" aria-label="关闭">×</button>
      </div>
      <h3>你想看哪种下一集走向？</h3>
      <p>${escapeHTML(payload.disclaimer || "AI 猜测剧情，非正片内容")}</p>
      <div class="remix-choice-grid">
        ${options
          .map(
            (option) => `
              <button class="remix-choice-card" type="button" data-remix-choice="${escapeHTML(option.key)}">
                <b>${escapeHTML(option.icon || "AI")}</b>
                <strong>${escapeHTML(option.label)}</strong>
                <span>${escapeHTML(option.description || "")}</span>
                <em>${escapeHTML(option.tone || "剧情预测")}</em>
              </button>
            `
          )
          .join("")}
      </div>
      ${
        featured.length
          ? `<div class="featured-remix-strip">
              <strong>精选二创</strong>
              ${featured
                .map(
                  (item) => `
                    <article>
                      <b>${escapeHTML(item.choice?.icon || "AI")}</b>
                      <span>${escapeHTML(item.title)}</span>
                      <em>${escapeHTML(item.logline || item.choice_label || "")}</em>
                    </article>
                  `
                )
                .join("")}
            </div>`
          : ""
      }
      <small>保底版先生成文字卡和三格分镜，后续可接入图片/视频生成。</small>
    </section>
  `;
}

function renderEndingRemixLoading(choice) {
  if (!endingRemixLayer) return;
  endingRemixLayer.className = "ending-remix-layer";
  endingRemixLayer.innerHTML = `
    <section class="ending-remix-panel remix-loading">
      <div class="remix-head">
        <span>AI 正在续写</span>
      </div>
      <div class="remix-orbit" aria-hidden="true"><i></i><i></i><i></i></div>
      <h3>${escapeHTML(choice?.label || "剧情预测")}</h3>
      <p>正在根据本集高光和你的选择生成文字卡/分镜文案。</p>
    </section>
  `;
}

function renderEndingRemixResult(result) {
  if (!endingRemixLayer) return;
  const storyboard = result.storyboard || [];
  endingRemixLayer.className = "ending-remix-layer";
  endingRemixLayer.innerHTML = `
    <section class="ending-remix-panel remix-result-panel">
      <div class="remix-head">
        <span>${escapeHTML(result.disclaimer || "AI 猜测剧情，非正片内容")}</span>
        <button class="close-button remix-close-button" type="button" data-remix-action="close" aria-label="关闭">×</button>
      </div>
      <div class="remix-result-title">
        <b>${escapeHTML(result.choice?.icon || "AI")}</b>
        <div>
          <h3>${escapeHTML(result.title || "AI 猜测卡")}</h3>
          <p>${escapeHTML(result.logline || "")}</p>
        </div>
      </div>
      <p class="remix-story-text">${escapeHTML(result.story_text || "")}</p>
      <div class="storyboard-grid">
        ${storyboard
          .map(
            (shot, index) => `
              <article class="storyboard-card">
                <span>0${index + 1}</span>
                <strong>${escapeHTML(shot.shot || `镜头${index + 1}`)}</strong>
                <p>${escapeHTML(shot.visual || "")}</p>
                <em>${escapeHTML(shot.subtitle || "")}</em>
                <small>${escapeHTML(shot.sound || "")}</small>
              </article>
            `
          )
          .join("")}
      </div>
      <div class="remix-footer">
        <span>${escapeHTML(result.share_copy || "AI 已生成一个非正片番外走向。")}</span>
        <button class="ghost-button" type="button" data-remix-action="back">换一个走向</button>
        <button class="primary-button" type="button" data-remix-action="close">收起</button>
      </div>
      <small>生成来源：${result.source === "llm" ? "大模型" : "本地兜底"} · ${escapeHTML(
        result.model_version || "remix-text-v1"
      )}${result.record_id ? ` · 记录 #${Number(result.record_id)}` : ""}</small>
    </section>
  `;
}

function renderEndingRemixError(message) {
  if (!endingRemixLayer) return;
  endingRemixLayer.className = "ending-remix-layer";
  endingRemixLayer.innerHTML = `
    <section class="ending-remix-panel">
      <div class="remix-head">
        <span>片尾 AI 二创</span>
        <button class="close-button remix-close-button" type="button" data-remix-action="close" aria-label="关闭">×</button>
      </div>
      <h3>生成失败</h3>
      <p>${escapeHTML(message)}</p>
      <div class="remix-footer">
        <button class="ghost-button" type="button" data-remix-action="back">返回选项</button>
      </div>
    </section>
  `;
}

async function showEndingRemix() {
  if (!state.currentEpisode || state.endingRemixShown || state.remixLoading) return;
  state.endingRemixShown = true;
  hideInteraction();
  window.clearTimeout(state.ambientStickerTimer);
  if (!player.paused) {
    player.pause();
  }
  try {
    const payload = await fetchJSON(`/api/episodes/${state.currentEpisode.id}/remix-options`);
    state.remixOptions = payload;
    renderEndingRemixOptions(payload);
  } catch (error) {
    renderEndingRemixError(errorMessage(error));
  }
}

function maybeShowEndingRemix(force = false) {
  if (!state.currentEpisode || state.endingRemixShown || state.activeHighlight) return;
  const duration = Number.isFinite(player.duration) ? player.duration : state.currentEpisode.duration_sec || 0;
  if (!duration && !force) return;
  const triggerAt = Math.max(0, duration - 8);
  if (force || (duration > 20 && player.currentTime >= triggerAt)) {
    showEndingRemix();
  }
}

async function generateEndingRemix(choiceKey) {
  if (!state.currentEpisode || state.remixLoading) return;
  const choice = state.remixOptions?.options?.find((item) => item.key === choiceKey);
  state.remixLoading = true;
  renderEndingRemixLoading(choice);
  try {
    const result = await fetchJSON(`/api/episodes/${state.currentEpisode.id}/ai-remix`, {
      method: "POST",
      body: JSON.stringify({ choice_key: choiceKey, session_id: state.sessionId }),
    });
    state.remixResult = result;
    renderEndingRemixResult(result);
  } catch (error) {
    renderEndingRemixError(errorMessage(error));
  } finally {
    state.remixLoading = false;
  }
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
  } ${highlight.reward_hint ? "quiz-layer" : ""} mode-${interactionMode}`;
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
  const helperText = highlight.reward_hint
    ? `同看竞猜：答对 +${highlight.reward_hint.points} 分，解锁「${highlight.reward_hint.title}」`
    : interactionMode === "tap"
      ? "连续点击表达情绪"
      : vehicleChoice
        ? "先猜交通工具，再看揭晓"
        : "选一个最贴近你的反应";
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
      ${
        highlight.reward_hint
          ? `<div class="quiz-banner"><b>竞猜</b><span>${escapeHTML(highlight.reward_hint.description)}</span></div>`
          : ""
      }
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
  scheduleAmbientStickers();
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
  const highlight = state.activeHighlight;
  const option = highlight.options?.find((item) => item.key === optionKey);
  if (anchor) burstReaction(anchor);
  spawnVehicleSticker(optionKey, anchor);
  const result = await fetchJSON("/api/interactions", {
    method: "POST",
    body: JSON.stringify({
      highlight_id: highlight.id,
      option_key: optionKey,
      session_id: state.sessionId,
    }),
  });
  renderInteractionResult(result.stats, result.reward);
  postRoomEvent("interaction", {
    episode_id: state.currentEpisode?.id,
    highlight_id: highlight.id,
    highlight_title: highlight.title,
    option_key: optionKey,
    option_label: option?.label || optionKey,
    reward_correct: Boolean(result.reward?.correct),
    reward_message: result.reward?.message || "",
    reward_title: result.reward?.badge?.title || "",
    reward_points: result.reward?.points || 0,
    reward_badge: result.reward?.badge || null,
  });
  if (result.reward?.correct) {
    await loadRewardProfile();
    if (state.watchRoom) await fetchRoomState();
  }
}

function renderRewardOutcome(reward) {
  if (!reward) return "";
  const className = reward.correct ? "correct" : "wrong";
  const title = reward.correct ? "预判成功" : "预判偏差";
  return `
    <div class="reward-outcome ${className}">
      <strong>${title}</strong>
      <span>${escapeHTML(reward.message || "")}</span>
    </div>
  `;
}

function renderInteractionResult(stats, reward = null) {
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
  const panel = interactionLayer.querySelector(".interaction-panel");
  panel.querySelector(".reward-outcome")?.remove();
  panel.insertAdjacentHTML("beforeend", renderRewardOutcome(reward));
  scheduleInteractionHide(reward ? 4300 : 3200);
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
  let summary;
  let highlights;
  try {
    [summary, highlights] = await Promise.all([fetchJSON("/api/stats/summary"), fetchJSON("/api/stats/highlights")]);
  } catch (error) {
    $("#summaryCards").innerHTML = "";
    $("#statsList").innerHTML = `<div class="empty-state">无法加载统计：${escapeHTML(errorMessage(error))}</div>`;
    return;
  }
  $("#adminStatus").textContent = canManage() ? "后台数据已加载" : "";
  $("#summaryCards").innerHTML = [
    ["短剧", summary.drama_count],
    ["剧集", summary.episode_count],
    ["高光点", summary.highlight_count],
    ["互动次数", summary.interaction_count],
    ["弹幕", summary.danmaku_count || 0],
    ["体验配置", summary.experience_config_count || 0],
    ["AI二创", summary.ai_remix_count || 0],
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
  await loadAdminUsers();
}

function roleSelectOptions(role) {
  return [
    ["user", "普通用户"],
    ["reviewer", "复核员"],
    ["admin", "管理员"],
  ]
    .map(([value, label]) => `<option value="${value}" ${role === value ? "selected" : ""}>${label}</option>`)
    .join("");
}

function renderAdminUsers() {
  const card = $("#userManagementCard");
  const list = $("#adminUserList");
  if (!card || !list) return;
  card.hidden = state.currentUser?.role !== "admin";
  if (card.hidden) {
    list.innerHTML = "";
    return;
  }
  list.innerHTML = state.adminUsers
    .map(
      (user) => `
        <article class="admin-user-row" data-user-id="${user.id}">
          <div>
            <strong>${escapeHTML(user.display_name)}</strong>
            <span>${escapeHTML(user.username)} · ${roleLabel(user.role)} · ${
              user.is_active ? "启用" : "停用"
            }</span>
            <small>会话 ${user.active_session_count} · 观看 ${user.watch_history_count} · 互动 ${
              user.interaction_count
            } · 弹幕 ${user.danmaku_count}</small>
          </div>
          <label>
            昵称
            <input class="admin-user-input" data-user-field="display_name" value="${escapeHTML(user.display_name)}" />
          </label>
          <label>
            角色
            <select class="admin-user-input" data-user-field="role">${roleSelectOptions(user.role)}</select>
          </label>
          <label class="admin-user-active">
            <input class="admin-user-input" type="checkbox" data-user-field="is_active" ${
              user.is_active ? "checked" : ""
            } />
            启用
          </label>
          <button class="ghost-button save-admin-user-button" type="button" data-user-id="${user.id}">保存</button>
        </article>
      `
    )
    .join("");
}

async function loadAdminUsers() {
  if (state.currentUser?.role !== "admin") {
    renderAdminUsers();
    return;
  }
  try {
    state.adminUsers = await fetchJSON("/api/admin/users");
    renderAdminUsers();
  } catch (error) {
    $("#adminUserList").innerHTML = `<div class="empty-state">无法加载用户：${escapeHTML(errorMessage(error))}</div>`;
  }
}

async function saveAdminUser(button) {
  const row = button.closest(".admin-user-row");
  const userId = Number(button.dataset.userId);
  if (!row || !userId) return;
  const displayName = row.querySelector('[data-user-field="display_name"]').value.trim();
  const role = row.querySelector('[data-user-field="role"]').value;
  const isActive = row.querySelector('[data-user-field="is_active"]').checked;
  try {
    $("#adminStatus").classList.remove("error");
    const updated = await fetchJSON(`/api/admin/users/${userId}`, {
      method: "PATCH",
      body: JSON.stringify({ display_name: displayName, role, is_active: isActive }),
    });
    state.adminUsers = state.adminUsers.map((user) => (user.id === updated.id ? updated : user));
    renderAdminUsers();
    $("#adminStatus").textContent = `已保存用户：${updated.display_name}`;
    $("#adminStatus").classList.remove("error");
    if (state.currentUser?.id === updated.id) {
      state.currentUser = {
        id: updated.id,
        username: updated.username,
        display_name: updated.display_name,
        role: updated.role,
      };
      updateAuthUI();
    }
  } catch (error) {
    $("#adminStatus").textContent = errorMessage(error);
    $("#adminStatus").classList.add("error");
  }
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
  const configured = state.reviewEpisodes.filter((episode) => episode.experience_config_version > 0).length;
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
    ["体验配置", configured],
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
    $("#experienceJson").value = "";
    $("#reviewPreview").innerHTML = "";
    $("#experiencePreview").innerHTML = "";
    $("#remixReviewPanel").innerHTML = "";
    setReviewStatus("当前筛选没有剧集");
    return;
  }

  select.innerHTML = episodes
    .map(
      (episode) => `
        <option value="${episode.id}">
          ${episode.review_status_label} · 配置v${episode.experience_config_version || 0} · ${episode.drama_title} · ${
            episode.episode_title
          } · ${episode.highlight_count} 个高光
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
  try {
    state.reviewEpisodes = await fetchJSON("/api/admin/episodes");
  } catch (error) {
    $("#reviewSummaryCards").innerHTML = "";
    $("#reviewPreview").innerHTML = `<div class="empty-state">无法加载复核列表：${escapeHTML(errorMessage(error))}</div>`;
    $("#experiencePreview").innerHTML = "";
    $("#remixReviewPanel").innerHTML = "";
    setReviewStatus(errorMessage(error), true);
    return;
  }
  renderReviewSummary();
  const urlEpisodeId = Number(new URLSearchParams(location.search).get("episode"));
  const preferredEpisodeId = state.currentEpisode?.id || (Number.isFinite(urlEpisodeId) ? urlEpisodeId : 0);
  await renderReviewEpisodeOptions(preferredEpisodeId || Number($("#reviewEpisodeSelect").value));
}

async function loadReviewPayload(episodeId) {
  if (!episodeId) return;
  const [payload, experience, remixes] = await Promise.all([
    fetchJSON(`/api/admin/episodes/${episodeId}/highlights`),
    fetchJSON(`/api/admin/episodes/${episodeId}/experience`),
    fetchJSON(`/api/admin/episodes/${episodeId}/remixes`),
  ]);
  const meta = state.reviewEpisodes.find((episode) => episode.id === episodeId);
  $("#reviewJson").value = JSON.stringify(payload, null, 2);
  $("#experienceJson").value = JSON.stringify(experience, null, 2);
  state.reviewExperience = experience;
  state.reviewRemixes = remixes;
  renderReviewPreview(payload);
  renderExperiencePreview(experience);
  renderRemixReviewPanel(remixes);
  const status = meta
    ? `${meta.review_status_label} · 人工 ${meta.reviewed_highlight_count || 0}/${meta.highlight_count || 0}`
    : "已加载";
  setReviewStatus(`已加载：${payload.drama_title} · ${payload.episode_title} · ${status}`);
}

function parseReviewJson() {
  return JSON.parse($("#reviewJson").value);
}

function parseExperienceJson() {
  return JSON.parse($("#experienceJson").value);
}

function setReviewStatus(message, isError = false) {
  const status = $("#reviewStatus");
  status.textContent = message;
  status.classList.toggle("error", isError);
}

function syncReviewJson(payload) {
  $("#reviewJson").value = JSON.stringify(payload, null, 2);
}

function syncExperienceJson(payload) {
  $("#experienceJson").value = JSON.stringify(payload, null, 2);
}

function remixStatusLabel(status, featured) {
  if (featured || status === "featured") return "精选";
  if (status === "hidden") return "隐藏";
  return "待复核";
}

function renderRemixStoryboardEditor(item) {
  const storyboard = item.storyboard?.length === 3 ? item.storyboard : [{}, {}, {}];
  return `
    <div class="remix-storyboard-editor">
      ${storyboard
        .map(
          (shot, index) => `
            <fieldset>
              <legend>分镜 ${index + 1}</legend>
              <label>
                名称
                <input class="remix-edit-field" data-remix-field="storyboard" data-shot-index="${index}" data-shot-field="shot" value="${escapeHTML(
                  shot.shot || `镜头${index + 1}`
                )}" />
              </label>
              <label>
                画面
                <textarea class="remix-edit-field" data-remix-field="storyboard" data-shot-index="${index}" data-shot-field="visual" rows="2">${escapeHTML(
                  shot.visual || ""
                )}</textarea>
              </label>
              <label>
                字幕
                <input class="remix-edit-field" data-remix-field="storyboard" data-shot-index="${index}" data-shot-field="subtitle" value="${escapeHTML(
                  shot.subtitle || ""
                )}" />
              </label>
              <label>
                声音
                <input class="remix-edit-field" data-remix-field="storyboard" data-shot-index="${index}" data-shot-field="sound" value="${escapeHTML(
                  shot.sound || ""
                )}" />
              </label>
            </fieldset>
          `
        )
        .join("")}
    </div>
  `;
}

function renderRemixReviewPanel(remixes = state.reviewRemixes) {
  const panel = $("#remixReviewPanel");
  if (!panel) return;
  const featuredCount = remixes.filter((item) => item.is_featured).length;
  panel.innerHTML = `
    <div class="remix-review-summary">
      <span>共 ${remixes.length} 条生成记录</span>
      <strong>${featuredCount} 条精选</strong>
    </div>
    ${
      remixes.length
        ? `<div class="remix-review-list">
            ${remixes
              .map(
                (item) => `
                  <article class="remix-review-card ${item.is_featured ? "featured" : ""} ${
                    item.review_status === "hidden" ? "hidden-remix" : ""
                  }" data-remix-id="${item.id}">
                    <div class="remix-review-card-head">
                      <div>
                        <span>${escapeHTML(remixStatusLabel(item.review_status, item.is_featured))} · #${Number(
                          item.id
                        )}</span>
                        <h4>${escapeHTML(item.title)}</h4>
                        <p>${escapeHTML(item.logline || "")}</p>
                      </div>
                      <b>${escapeHTML(item.choice?.icon || "AI")}</b>
                    </div>
                    <div class="remix-review-meta">
                      <span>${escapeHTML(item.choice_label || "")}</span>
                      <span>${escapeHTML(item.source === "llm" ? "大模型" : "本地兜底")}</span>
                      <span>${escapeHTML(item.model_version || "")}</span>
                      <span>${formatDateTime(item.created_at)}</span>
                      <span>排序 ${Number(item.featured_order || 0)}</span>
                    </div>
                    <div class="remix-edit-grid">
                      <label>
                        标题
                        <input class="remix-edit-field" data-remix-field="title" value="${escapeHTML(item.title || "")}" />
                      </label>
                      <label>
                        情绪
                        <input class="remix-edit-field" data-remix-field="emotion" value="${escapeHTML(item.emotion || "")}" />
                      </label>
                      <label>
                        精选排序
                        <input class="remix-edit-field" type="number" min="0" max="999" step="1" data-remix-field="featured_order" value="${Number(
                          item.featured_order || 0
                        )}" />
                      </label>
                      <label class="remix-wide-field">
                        钩子句
                        <textarea class="remix-edit-field" data-remix-field="logline" rows="2">${escapeHTML(item.logline || "")}</textarea>
                      </label>
                      <label class="remix-wide-field">
                        正文
                        <textarea class="remix-edit-field" data-remix-field="story_text" rows="4">${escapeHTML(
                          item.story_text || ""
                        )}</textarea>
                      </label>
                      <label class="remix-wide-field">
                        分享文案
                        <input class="remix-edit-field" data-remix-field="share_copy" value="${escapeHTML(item.share_copy || "")}" />
                      </label>
                      <label class="remix-wide-field">
                        审核备注
                        <input class="remix-edit-field" data-remix-field="review_note" value="${escapeHTML(item.review_note || "")}" />
                      </label>
                    </div>
                    ${renderRemixStoryboardEditor(item)}
                    <div class="remix-review-actions">
                      <button class="primary-button remix-review-action" type="button" data-action="save" data-remix-id="${item.id}">保存内容</button>
                      <button class="primary-button remix-review-action" type="button" data-action="feature" data-remix-id="${item.id}" ${
                        item.is_featured ? "disabled" : ""
                      }>设为精选</button>
                      <button class="ghost-button remix-review-action" type="button" data-action="draft" data-remix-id="${item.id}" ${
                        !item.is_featured && item.review_status !== "hidden" ? "disabled" : ""
                      }>取消精选</button>
                      <button class="danger-button remix-review-action" type="button" data-action="hide" data-remix-id="${item.id}" ${
                        item.review_status === "hidden" ? "disabled" : ""
                      }>隐藏</button>
                    </div>
                  </article>
                `
              )
              .join("")}
          </div>`
        : `<div class="empty-state">当前剧集还没有片尾二创记录。先在播放页片尾生成一次，再回到这里精选。</div>`
    }
  `;
}

async function updateRemixReview(button) {
  const remixId = Number(button.dataset.remixId);
  const action = button.dataset.action;
  if (!remixId || !action) return;
  const card = button.closest(".remix-review-card");
  const payload =
    action === "save"
      ? collectRemixEditPayload(card)
      : action === "feature"
        ? { review_status: "featured", is_featured: true }
        : action === "hide"
          ? { review_status: "hidden", is_featured: false }
          : { review_status: "draft", is_featured: false };
  button.disabled = true;
  try {
    const updated = await fetchJSON(`/api/admin/remixes/${remixId}`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    state.reviewRemixes = state.reviewRemixes.map((item) => (item.id === updated.id ? updated : item));
    renderRemixReviewPanel();
    setReviewStatus(`已更新片尾二创：${updated.title}`);
  } catch (error) {
    button.disabled = false;
    setReviewStatus(errorMessage(error), true);
  }
}

function collectRemixEditPayload(card) {
  const payload = {};
  const storyboard = [{}, {}, {}];
  card.querySelectorAll(".remix-edit-field").forEach((field) => {
    const key = field.dataset.remixField;
    if (!key) return;
    if (key === "storyboard") {
      const index = Number(field.dataset.shotIndex);
      const shotField = field.dataset.shotField;
      if (Number.isFinite(index) && storyboard[index] && shotField) {
        storyboard[index][shotField] = field.value.trim();
      }
      return;
    }
    if (key === "featured_order") {
      payload[key] = Math.max(0, Number(field.value || 0));
      return;
    }
    payload[key] = field.value.trim();
  });
  payload.storyboard = storyboard;
  return payload;
}

function renderSelectOptions(values, selected) {
  return values.map((value) => `<option value="${escapeHTML(value)}" ${value === selected ? "selected" : ""}>${escapeHTML(value)}</option>`).join("");
}

function renderValidationSummary(kind, title, validation) {
  const errors = validation.errors || [];
  const warnings = validation.warnings || [];
  const status = errors.length ? "error" : warnings.length ? "warning" : "ok";
  const messages = errors.length || warnings.length ? [...errors, ...warnings] : ["可以保存"];
  return `
    <div class="validation-summary ${status}" data-validation="${escapeHTML(kind)}">
      <strong>${escapeHTML(title)}</strong>
      <ul>
        ${messages.map((message) => `<li>${escapeHTML(message)}</li>`).join("")}
      </ul>
    </div>
  `;
}

function refreshValidationSummary(kind, title, validation) {
  const element = document.querySelector(`[data-validation="${kind}"]`);
  if (element) element.outerHTML = renderValidationSummary(kind, title, validation);
}

function validateReviewPayload(payload) {
  const errors = [];
  const warnings = [];
  const highlights = Array.isArray(payload.highlights) ? payload.highlights : [];
  if (!highlights.length) errors.push("至少保留 1 个高光点");
  if (highlights.length > REVIEW_MAX_HIGHLIGHTS) errors.push(`单集高光点不超过 ${REVIEW_MAX_HIGHLIGHTS} 个，避免互动过密`);

  highlights.forEach((item, index) => {
    const label = `高光点 ${index + 1}`;
    const start = Number(item.start_time_sec);
    const end = Number(item.end_time_sec);
    if (!Number.isFinite(start) || start < 0) errors.push(`${label} 的开始秒必须是非负数字`);
    if (!Number.isFinite(end) || end <= start) errors.push(`${label} 的结束秒必须大于开始秒`);
    if (!item.title?.trim()) errors.push(`${label} 缺少高光名称`);
    if (!REVIEW_TYPE_LABELS.includes(item.highlight_type)) errors.push(`${label} 的高光类型不在允许范围`);
    if (!REVIEW_EMOTIONS.includes(item.emotion)) errors.push(`${label} 的情绪不在允许范围`);
    if (!Array.isArray(item.evidence_segment_ids) || !item.evidence_segment_ids.length) errors.push(`${label} 缺少证据片段ID`);
    if (!String(item.evidence_text || "").trim()) warnings.push(`${label} 缺少证据文本，建议补充台词或画面描述`);

    const options = Array.isArray(item.options) ? item.options : [];
    if (options.length < 2 || options.length > 4) errors.push(`${label} 需要 2-4 个互动按钮`);
    const keys = new Set();
    options.forEach((option, optionIndex) => {
      const optionLabel = `${label} 按钮 ${optionIndex + 1}`;
      if (!option.key) errors.push(`${optionLabel} 缺少 key`);
      if (keys.has(option.key)) errors.push(`${optionLabel} key 重复`);
      keys.add(option.key);
      const text = String(option.label || "").trim();
      if (!text || text.length > 8) errors.push(`${optionLabel} 文案必须是 1-8 个字`);
    });
  });

  const sorted = highlights
    .map((item) => Number(item.start_time_sec))
    .filter(Number.isFinite)
    .sort((left, right) => left - right);
  for (let index = 1; index < sorted.length; index += 1) {
    if (sorted[index] - sorted[index - 1] < REVIEW_MIN_HIGHLIGHT_GAP_SEC) {
      errors.push(`${formatTime(sorted[index - 1])} 与 ${formatTime(sorted[index])} 间隔不足 ${REVIEW_MIN_HIGHLIGHT_GAP_SEC} 秒`);
    }
  }

  return { errors, warnings };
}

function validateExperiencePayload(payload) {
  const errors = [];
  const warnings = [];
  const config = payload.config || {};
  const theme = config.player_theme || {};
  const timeline = Array.isArray(config.sticker_timeline) ? config.sticker_timeline : [];
  const quizRewards = Array.isArray(config.quiz_rewards) ? config.quiz_rewards : [];
  const availableAssets = new Set(Object.keys(STICKER_ASSETS));
  if (!theme.name?.trim()) warnings.push("播放器主题名为空，建议补充");
  if (!timeline.length) warnings.push("暂无贴图时间窗，播放时不会出现剧情贴图");

  timeline.forEach((slot, index) => {
    const label = `贴图窗 ${index + 1}`;
    const start = Number(slot.start_time_sec ?? slot.start);
    const end = Number(slot.end_time_sec ?? slot.end);
    if (!Number.isFinite(start) || start < 0) errors.push(`${label} 的开始秒必须是非负数字`);
    if (!Number.isFinite(end) || end <= start) errors.push(`${label} 的结束秒必须大于开始秒`);
    const assets = slot.asset_ids || slot.assets || [];
    if (!Array.isArray(assets) || !assets.length) warnings.push(`${label} 未选择贴图素材`);
    assets.forEach((asset) => {
      if (!availableAssets.has(asset)) errors.push(`${label} 引用了不存在的贴图：${asset}`);
    });
    if (Number(slot.cadence_sec || 0) <= 0) errors.push(`${label} 的频率秒必须大于 0`);
    if (Number(slot.burst_count || 0) <= 0) errors.push(`${label} 的出现数量必须大于 0`);
  });

  const ordered = timeline
    .map((slot) => ({
      start: Number(slot.start_time_sec ?? slot.start),
      end: Number(slot.end_time_sec ?? slot.end),
    }))
    .filter((slot) => Number.isFinite(slot.start) && Number.isFinite(slot.end))
    .sort((left, right) => left.start - right.start);
  for (let index = 1; index < ordered.length; index += 1) {
    if (ordered[index].start < ordered[index - 1].end) {
      warnings.push(`${formatTime(ordered[index - 1].start)}-${formatTime(ordered[index - 1].end)} 与 ${formatTime(ordered[index].start)}-${formatTime(ordered[index].end)} 有重叠`);
    }
  }

  quizRewards.forEach((rule, index) => {
    const label = `竞猜奖励 ${index + 1}`;
    if (!String(rule.highlight_title || "").trim()) errors.push(`${label} 缺少高光名称`);
    if (!String(rule.correct_option_key || "").trim()) errors.push(`${label} 缺少正确选项 key`);
    if (!String(rule.reward_key || "").trim()) errors.push(`${label} 缺少奖励 key`);
    if (!String(rule.title || "").trim()) errors.push(`${label} 缺少称号/徽章名`);
    if (Number(rule.points || 0) <= 0) errors.push(`${label} 积分必须大于 0`);
  });

  return { errors, warnings };
}

function defaultReviewHighlight(payload) {
  const highlights = Array.isArray(payload.highlights) ? payload.highlights : [];
  const duration = Number(payload.duration_sec || 0);
  const starts = highlights.map((item) => Number(item.start_time_sec)).filter(Number.isFinite);
  let start = starts.length ? Math.max(...starts) + REVIEW_MIN_HIGHLIGHT_GAP_SEC : 0;
  if (duration && start + 8 > duration) {
    start = 0;
    for (let candidate = REVIEW_MIN_HIGHLIGHT_GAP_SEC; candidate <= Math.max(0, duration - 8); candidate += 5) {
      if (starts.every((existing) => Math.abs(existing - candidate) >= REVIEW_MIN_HIGHLIGHT_GAP_SEC)) {
        start = candidate;
        break;
      }
    }
  }
  const end = Number(Math.min(duration || start + 8, start + 8).toFixed(2));
  const index = highlights.length + 1;
  return {
    start_time_sec: Number(start.toFixed(2)),
    end_time_sec: end > start ? end : Number((start + 8).toFixed(2)),
    title: `待复核高光 ${index}`,
    description: "请补充这一段剧情说明。",
    highlight_type: "悬念钩子",
    emotion: "期待",
    confidence: 0.7,
    reason: "复核台人工新增，待确认。",
    evidence_segment_ids: [`manual-new-${Date.now()}`],
    evidence_text: "请补充台词或画面证据。",
    options: [
      { key: `option_${index}_a`, label: "想看" },
      { key: `option_${index}_b`, label: "好奇" },
      { key: `option_${index}_c`, label: "等等" },
    ],
  };
}

function addReviewHighlight() {
  const payload = parseReviewJson();
  payload.highlights ||= [];
  if (payload.highlights.length >= REVIEW_MAX_HIGHLIGHTS) {
    setReviewStatus(`最多保留 ${REVIEW_MAX_HIGHLIGHTS} 个高光点，先删除不需要的高光`, true);
    return;
  }
  payload.highlights.push(defaultReviewHighlight(payload));
  syncReviewJson(payload);
  renderReviewPreview(payload);
  setReviewStatus("已新增 1 个待复核高光点，修改后点击保存生效");
}

function deleteReviewHighlight(button) {
  const index = Number(button.dataset.index);
  const payload = parseReviewJson();
  const item = payload.highlights?.[index];
  if (!item) return;
  payload.highlights.splice(index, 1);
  syncReviewJson(payload);
  renderReviewPreview(payload);
  setReviewStatus(`已删除「${item.title}」，点击保存后生效`);
}

function renderStickerAssetPicker(assets, slotIndex) {
  const selected = new Set(assets);
  const ungrouped = Object.keys(STICKER_ASSETS).filter((assetId) => !STICKER_ASSET_GROUP_BY_ID[assetId]);
  const groups = ungrouped.length
    ? [...STICKER_ASSET_GROUPS, { key: "other", label: "其他", assetIds: ungrouped }]
    : STICKER_ASSET_GROUPS;
  return `
    <div class="asset-picker-shell" data-slot-index="${slotIndex}">
      <div class="asset-filter-row">
        <input class="asset-filter-input" type="search" placeholder="搜索贴图名称、ID、题材" data-slot-index="${slotIndex}" />
        <label>
          <input class="asset-selected-only-filter" type="checkbox" data-slot-index="${slotIndex}" />
          只看已选
        </label>
      </div>
      ${groups
        .map((group) => {
          const availableAssetIds = group.assetIds.filter((assetId) => STICKER_ASSETS[assetId]);
          if (!availableAssetIds.length) return "";
          const selectedCount = availableAssetIds.filter((assetId) => selected.has(assetId)).length;
          const open = selectedCount > 0 || group.key === "common";
          return `
            <details class="asset-group" data-group="${escapeHTML(group.key)}" ${open ? "open" : ""}>
              <summary>${escapeHTML(group.label)} <span>${selectedCount}/${availableAssetIds.length}</span></summary>
              <div class="asset-picker" data-slot-index="${slotIndex}">
                ${availableAssetIds
                  .map((assetId) => {
                    const asset = STICKER_ASSETS[assetId];
                    const active = selected.has(assetId);
                    const searchText = `${assetId} ${asset.label} ${group.label}`.toLowerCase();
                    return `<button class="asset-chip ${active ? "selected" : ""}" type="button" data-slot-index="${slotIndex}" data-asset-id="${escapeHTML(
                      assetId
                    )}" data-search="${escapeHTML(searchText)}" title="${escapeHTML(assetId)}">
                      <img src="${escapeHTML(asset.src)}" alt="" />
                      <span>${escapeHTML(asset.label)}</span>
                    </button>`;
                  })
                  .join("")}
              </div>
            </details>
          `;
        })
        .join("")}
    </div>
  `;
}

function suggestedStickerAssetsForHighlight(highlight) {
  const key = getHighlightKey(highlight.highlight_type);
  const source = highlightText(highlight);
  const picks = [];
  const add = (...assetIds) => {
    assetIds.forEach((assetId) => {
      if (STICKER_ASSETS[assetId] && !picks.includes(assetId)) picks.push(assetId);
    });
  };

  if (source.includes("安乐死") || source.includes("难过") || source.includes("心疼") || source.includes("破防")) {
    add("winterBrokenHeart", "winterWarmHug", "tear");
  }
  if (source.includes("亲") || source.includes("心动") || source.includes("撒糖") || source.includes("磕")) {
    add("winterKiss", "winterHeart", "winterBlush", "winterHeartbeat");
  }
  if (source.includes("脱") || source.includes("震惊") || (source.includes("突然") && !source.includes("亲"))) {
    add("winterWow", "winterHoldBack", "question");
  }
  if (source.includes("选择") || source.includes("选哪") || source.includes("现实")) {
    add("winterChoice", "winterQuestionLove", "question");
  }
  if (source.includes("无语") || source.includes("尴尬") || source.includes("沉默")) add("winterCrow");

  if (!picks.length && key === "tear") add("winterBrokenHeart", "winterWarmHug", "tear");
  if (!picks.length && key === "reveal") add("winterWow", "question");
  if (!picks.length && key === "suspense") add("winterChoice", "winterQuestionLove", "question");
  if (!picks.length && key === "sweet") add("winterKiss", "winterHeart", "winterBlush");
  if (!picks.length && key === "comedy") add("laugh", "winterCrow");
  if (!picks.length) add("question", "winterSnow");
  return picks.slice(0, 4);
}

function highlightSlotMeaning(highlight, assets) {
  return `按高光《${highlight.title || "未命名"}》自动生成：${highlight.highlight_type || "高光"} / ${
    highlight.emotion || "情绪待定"
  }，推荐贴图 ${assets.join(" / ")}，需人工复核是否遮挡关键画面。`;
}

function defaultExperienceSlot(payload) {
  const timeline = payload.config?.sticker_timeline || [];
  const duration = Number(parseReviewJson().duration_sec || 0);
  const ranges = timeline
    .map((slot) => ({
      start: Number(slot.start_time_sec ?? slot.start ?? 0),
      end: Number(slot.end_time_sec ?? slot.end ?? 0),
    }))
    .filter((slot) => Number.isFinite(slot.start) && Number.isFinite(slot.end))
    .sort((left, right) => left.start - right.start);
  let start = ranges.reduce((max, slot) => Math.max(max, slot.end), 0) + 2;
  if (duration && start + 8 > duration) {
    start = 0;
    for (let candidate = 0; candidate <= Math.max(0, duration - 8); candidate += 5) {
      const candidateEnd = candidate + 8;
      if (ranges.every((range) => candidateEnd <= range.start || candidate >= range.end)) {
        start = candidate;
        break;
      }
    }
  }
  const end = duration ? Math.min(duration, start + 8) : start + 8;
  return {
    start_time_sec: Number(start.toFixed(2)),
    end_time_sec: Number(end.toFixed(2)),
    asset_ids: ["question"],
    cadence_sec: 2,
    burst_count: 2,
    meaning: "人工新增贴图时间窗，请选择贴图素材并补充含义。",
  };
}

function addExperienceSlot() {
  const payload = parseExperienceJson();
  payload.config ||= {};
  payload.config.sticker_timeline ||= [];
  payload.config.sticker_timeline.push(defaultExperienceSlot(payload));
  syncExperienceJson(payload);
  renderExperiencePreview(payload);
  setReviewStatus("已新增贴图时间窗，点击保存体验配置后生效");
}

function deleteExperienceSlot(button) {
  const index = Number(button.dataset.index);
  const payload = parseExperienceJson();
  const slot = payload.config?.sticker_timeline?.[index];
  if (!slot) return;
  payload.config.sticker_timeline.splice(index, 1);
  syncExperienceJson(payload);
  renderExperiencePreview(payload);
  setReviewStatus(`已删除贴图时间窗 ${index + 1}，点击保存体验配置后生效`);
}

function defaultQuizReward(payload) {
  const reviewPayload = parseReviewJson();
  const highlights = Array.isArray(reviewPayload.highlights) ? reviewPayload.highlights : [];
  const highlight =
    highlights.find((item) => /选择|选|猜|谁|方式|身份|到底/.test(`${item.title || ""}${item.description || ""}`)) ||
    highlights[0] ||
    {};
  const option = highlight.options?.[0] || {};
  const dramaPrefix = String(reviewPayload.drama_title || payload.drama_title || "剧情").slice(0, 6);
  return {
    kind: "prediction",
    highlight_title: highlight.title || "",
    correct_option_key: option.key || "",
    correct_label: option.label || "",
    points: 20,
    reward_key: `quiz_${Date.now()}`,
    title: `${dramaPrefix}预言家`,
    description: `猜中「${highlight.title || "关键剧情"}」后解锁。`,
    prompt: "同看竞猜题，答对后解锁称号和积分。",
  };
}

function addQuizReward() {
  const payload = parseExperienceJson();
  payload.config ||= {};
  payload.config.quiz_rewards ||= [];
  payload.config.quiz_rewards.push(defaultQuizReward(payload));
  syncExperienceJson(payload);
  renderExperiencePreview(payload);
  setReviewStatus("已新增竞猜奖励规则，选择高光和正确选项后保存生效");
}

function deleteQuizReward(button) {
  const index = Number(button.dataset.index);
  const payload = parseExperienceJson();
  const rule = payload.config?.quiz_rewards?.[index];
  if (!rule) return;
  payload.config.quiz_rewards.splice(index, 1);
  syncExperienceJson(payload);
  renderExperiencePreview(payload);
  setReviewStatus(`已删除竞猜奖励 ${index + 1}，保存后生效`);
}

function generateStickerSlotsFromHighlights() {
  const reviewPayload = parseReviewJson();
  const experiencePayload = parseExperienceJson();
  const highlights = Array.isArray(reviewPayload.highlights) ? reviewPayload.highlights : [];
  experiencePayload.config ||= {};
  const existing = Array.isArray(experiencePayload.config.sticker_timeline)
    ? experiencePayload.config.sticker_timeline
    : [];
  let changed = 0;

  highlights.forEach((highlight) => {
    const start = Number(highlight.start_time_sec || 0);
    const end = Number(highlight.end_time_sec || start + 8);
    const assets = suggestedStickerAssetsForHighlight(highlight);
    const matched = existing.find((slot) => Math.abs(Number(slot.start_time_sec ?? slot.start ?? -999) - start) < 1);
    const nextSlot = {
      start_time_sec: Number(start.toFixed(2)),
      end_time_sec: Number(Math.max(end, start + 4).toFixed(2)),
      asset_ids: assets,
      cadence_sec: getHighlightKey(highlight.highlight_type) === "sweet" ? 1 : 2,
      burst_count: getHighlightKey(highlight.highlight_type) === "sweet" ? 4 : 3,
      meaning: highlightSlotMeaning(highlight, assets),
    };
    if (matched) {
      Object.assign(matched, nextSlot);
    } else {
      existing.push(nextSlot);
    }
    changed += 1;
  });

  experiencePayload.config.sticker_timeline = existing.sort(
    (left, right) => Number(left.start_time_sec ?? left.start ?? 0) - Number(right.start_time_sec ?? right.start ?? 0)
  );
  syncExperienceJson(experiencePayload);
  renderExperiencePreview(experiencePayload);
  setReviewStatus(`已按 ${changed} 个高光生成/更新贴图时间窗，点击保存体验配置后生效`);
}

function stickerSuggestionSlotsFromHighlights() {
  const reviewPayload = parseReviewJson();
  const highlights = Array.isArray(reviewPayload.highlights) ? reviewPayload.highlights : [];
  return highlights.map((highlight) => {
    const start = Number(highlight.start_time_sec || 0);
    const end = Number(highlight.end_time_sec || start + 8);
    const assets = suggestedStickerAssetsForHighlight(highlight);
    return {
      start_time_sec: Number(start.toFixed(2)),
      end_time_sec: Number(Math.max(end, start + 4).toFixed(2)),
      asset_ids: assets,
      cadence_sec: getHighlightKey(highlight.highlight_type) === "sweet" ? 1 : 2,
      burst_count: getHighlightKey(highlight.highlight_type) === "sweet" ? 4 : 3,
      meaning: highlightSlotMeaning(highlight, assets),
      review_note: "本地样例用于验证导入格式；正式版本应由大模型脚本生成后人工复核。",
    };
  });
}

function fillStickerSuggestionSample() {
  const reviewPayload = parseReviewJson();
  const sample = {
    episode_id: reviewPayload.episode_id,
    drama_title: reviewPayload.drama_title,
    episode_title: reviewPayload.episode_title,
    source: "local_review_sample",
    model_version: "sticker-suggestion-sample-v1",
    slots: stickerSuggestionSlotsFromHighlights(),
  };
  state.stickerSuggestionDraft = JSON.stringify(sample, null, 2);
  renderExperiencePreview(parseExperienceJson());
  setReviewStatus("已生成当前高光的贴图建议样例，可导入并合并");
}

function normalizeSuggestionSlot(slot) {
  const start = Number(slot.start_time_sec ?? slot.start ?? slot.highlight_start_sec ?? 0);
  const end = Number(slot.end_time_sec ?? slot.end ?? slot.highlight_end_sec ?? start + 8);
  const rawAssets = slot.asset_ids ?? slot.assets ?? slot.assetIds ?? [];
  const assetIds = (Array.isArray(rawAssets) ? rawAssets : String(rawAssets).split(","))
    .map((assetId) => String(assetId).trim())
    .filter((assetId) => STICKER_ASSETS[assetId]);
  if (!assetIds.length || !Number.isFinite(start)) return null;
  return {
    start_time_sec: Number(start.toFixed(2)),
    end_time_sec: Number(Math.max(Number.isFinite(end) ? end : start + 8, start + 1).toFixed(2)),
    asset_ids: [...new Set(assetIds)].slice(0, 5),
    cadence_sec: Math.max(1, Number(slot.cadence_sec ?? slot.cadence ?? 2)),
    burst_count: Math.max(1, Number(slot.burst_count ?? slot.burst ?? 3)),
    meaning: String(slot.meaning || slot.reason || slot.review_note || "大模型贴图建议，需人工复核。"),
  };
}

function stickerSuggestionSlotsFromPayload(payload) {
  const rawSlots =
    payload.slots ||
    payload.suggestions ||
    payload.sticker_timeline ||
    payload.config?.sticker_timeline ||
    payload.config?.slots ||
    [];
  if (!Array.isArray(rawSlots)) return [];
  return rawSlots.map(normalizeSuggestionSlot).filter(Boolean);
}

function mergeStickerSuggestionSlots(experiencePayload, slots, meta = {}) {
  experiencePayload.config ||= {};
  const existing = Array.isArray(experiencePayload.config.sticker_timeline)
    ? experiencePayload.config.sticker_timeline
    : [];
  slots.forEach((slot) => {
    const matched = existing.find(
      (item) => Math.abs(Number(item.start_time_sec ?? item.start ?? -999) - Number(slot.start_time_sec)) < 1
    );
    if (matched) {
      Object.assign(matched, slot);
    } else {
      existing.push(slot);
    }
  });
  experiencePayload.config.sticker_timeline = existing.sort(
    (left, right) => Number(left.start_time_sec ?? left.start ?? 0) - Number(right.start_time_sec ?? right.start ?? 0)
  );
  experiencePayload.config.sticker_suggestion_meta = {
    source: meta.source || "manual_import",
    model_version: meta.model_version || "unknown",
    imported_slot_count: slots.length,
    imported_at: new Date().toISOString(),
  };
  return experiencePayload;
}

function importStickerSuggestionJson() {
  try {
    const raw = $("#stickerSuggestionJson")?.value || state.stickerSuggestionDraft || "";
    const suggestionPayload = JSON.parse(raw);
    const slots = stickerSuggestionSlotsFromPayload(suggestionPayload);
    if (!slots.length) {
      setReviewStatus("导入失败：贴图建议 JSON 中没有可用 slots/sticker_timeline", true);
      return;
    }
    const experiencePayload = mergeStickerSuggestionSlots(parseExperienceJson(), slots, suggestionPayload);
    syncExperienceJson(experiencePayload);
    renderExperiencePreview(experiencePayload);
    setReviewStatus(`已导入并合并 ${slots.length} 个贴图建议时间窗，点击保存体验配置后生效`);
  } catch (error) {
    setReviewStatus(`导入失败：${error.message}`, true);
  }
}

async function loadStickerSuggestionFile() {
  try {
    const episodeId = Number($("#reviewEpisodeSelect").value || parseReviewJson().episode_id);
    const response = await fetch(`/assets/sticker_suggestions/episode_${episodeId}_sticker_suggestions.json`, {
      cache: "no-store",
    });
    if (!response.ok) throw new Error(`未找到 episode_${episodeId}_sticker_suggestions.json`);
    state.stickerSuggestionDraft = JSON.stringify(await response.json(), null, 2);
    renderExperiencePreview(parseExperienceJson());
    setReviewStatus("已加载当前剧集的大模型贴图建议 JSON，可导入并合并");
  } catch (error) {
    setReviewStatus(`加载建议文件失败：${error.message}`, true);
  }
}

function filterAssetPicker(control) {
  const shell = control.closest(".asset-picker-shell");
  if (!shell) return;
  const query = (shell.querySelector(".asset-filter-input")?.value || "").trim().toLowerCase();
  const selectedOnly = Boolean(shell.querySelector(".asset-selected-only-filter")?.checked);
  shell.querySelectorAll(".asset-group").forEach((group) => {
    let visibleCount = 0;
    group.querySelectorAll(".asset-chip").forEach((chip) => {
      const matchesQuery = !query || chip.dataset.search.includes(query);
      const matchesSelected = !selectedOnly || chip.classList.contains("selected");
      const visible = matchesQuery && matchesSelected;
      chip.hidden = !visible;
      if (visible) visibleCount += 1;
    });
    group.hidden = visibleCount === 0;
    if (query && visibleCount > 0) group.open = true;
  });
}

function toggleStickerAsset(button) {
  const index = Number(button.dataset.slotIndex);
  const assetId = button.dataset.assetId;
  const payload = parseExperienceJson();
  const slot = payload.config?.sticker_timeline?.[index];
  if (!slot || !assetId) return;
  const assets = new Set(slot.asset_ids || slot.assets || []);
  if (assets.has(assetId)) {
    assets.delete(assetId);
  } else {
    assets.add(assetId);
  }
  slot.asset_ids = [...assets];
  delete slot.assets;
  syncExperienceJson(payload);
  renderExperiencePreview(payload);
  setReviewStatus("已调整贴图素材，点击保存体验配置后生效");
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
  refreshValidationSummary("review", "高光保存前检查", validateReviewPayload(payload));
  const card = input.closest(".review-card");
  const timeLabel = card?.querySelector("[data-time-label]");
  if (timeLabel) {
    timeLabel.textContent = `${formatTime(item.start_time_sec)}-${formatTime(item.end_time_sec)}`;
  }
  setReviewStatus(`已调整 ${item.title} 的${field === "start_time_sec" ? "开始" : "结束"}时间，点击保存后生效`);
}

function updateReviewFieldInput(input) {
  const index = Number(input.dataset.index);
  const field = input.dataset.field;
  const payload = parseReviewJson();
  const item = payload.highlights?.[index];
  if (!item || !field) return;
  item[field] = input.value;
  syncReviewJson(payload);
  refreshValidationSummary("review", "高光保存前检查", validateReviewPayload(payload));
  const card = input.closest(".review-card");
  const title = card?.querySelector("[data-review-title]");
  if (title && field === "title") title.textContent = input.value || `高光点 ${index + 1}`;
  setReviewStatus(`已调整高光点 ${index + 1} 的${input.dataset.label || field}，点击保存后生效`);
}

function updateReviewOptionInput(input) {
  const index = Number(input.dataset.index);
  const optionIndex = Number(input.dataset.optionIndex);
  const field = input.dataset.field;
  const payload = parseReviewJson();
  const option = payload.highlights?.[index]?.options?.[optionIndex];
  if (!option || !field) return;
  option[field] = input.value;
  syncReviewJson(payload);
  refreshValidationSummary("review", "高光保存前检查", validateReviewPayload(payload));
  setReviewStatus(`已调整高光点 ${index + 1} 的互动按钮，点击保存后生效`);
}

function updateExperienceFieldInput(input) {
  const payload = parseExperienceJson();
  const section = input.dataset.section;
  const field = input.dataset.field;
  if (!section || !field) return;
  if (section === "theme") {
    payload.config ||= {};
    payload.config.player_theme ||= {};
    payload.config.player_theme[field] = input.value;
  }
  if (section === "slot") {
    const index = Number(input.dataset.index);
    const slot = payload.config?.sticker_timeline?.[index];
    if (!slot) return;
    if (["start_time_sec", "end_time_sec", "cadence_sec", "burst_count"].includes(field)) {
      const value = Number(input.value);
      if (!Number.isFinite(value) || value < 0) return;
      slot[field] = Number(value.toFixed(2));
    } else if (field === "asset_ids") {
      slot.asset_ids = input.value
        .split(/[,/，、\s]+/)
        .map((item) => item.trim())
        .filter(Boolean);
    } else {
      slot[field] = input.value;
    }
  }
  if (section === "quiz") {
    const index = Number(input.dataset.index);
    payload.config ||= {};
    payload.config.quiz_rewards ||= [];
    const rule = payload.config.quiz_rewards[index];
    if (!rule) return;
    if (field === "points") {
      const value = Number(input.value);
      if (!Number.isFinite(value) || value < 0) return;
      rule[field] = Math.round(value);
    } else {
      rule[field] = input.value;
    }
  }
  syncExperienceJson(payload);
  refreshValidationSummary("experience", "体验配置保存前检查", validateExperiencePayload(payload));
  setReviewStatus("已调整体验配置，点击保存后生效");
}

function renderReviewPreview(payload) {
  const highlights = payload.highlights || [];
  $("#reviewPreview").innerHTML = `
    <div class="review-preview-head">
      <div>
        <span>片名</span>
        <strong>${escapeHTML(payload.drama_title)} · ${escapeHTML(payload.episode_title)}</strong>
        <small>${highlights.length} 个高光点 · 总时长 ${formatTime(payload.duration_sec)}</small>
      </div>
      <button class="ghost-button add-highlight-button" type="button">新增高光</button>
    </div>
    ${renderValidationSummary("review", "高光保存前检查", validateReviewPayload(payload))}
    ${highlights
      .map(
        (item, index) => `
          <article class="review-card" data-index="${index}">
            <div class="review-card-title">
              <div>
                <span>高光点 ${index + 1}</span>
                <h4 data-review-title>${escapeHTML(item.title)}</h4>
              </div>
              <button class="danger-button delete-highlight-button" type="button" data-index="${index}">删除</button>
            </div>
            <div class="review-field-grid">
              <label>
                高光名称
                <input class="review-field-input" data-index="${index}" data-field="title" data-label="高光名称" value="${escapeHTML(item.title)}" />
              </label>
              <label>
                高光类型
                <select class="review-field-input" data-index="${index}" data-field="highlight_type" data-label="高光类型">
                  ${renderSelectOptions(REVIEW_TYPE_LABELS, item.highlight_type)}
                </select>
              </label>
              <label>
                情绪
                <select class="review-field-input" data-index="${index}" data-field="emotion" data-label="情绪">
                  ${renderSelectOptions(REVIEW_EMOTIONS, item.emotion)}
                </select>
              </label>
            </div>
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
            <label class="review-wide-field">
              剧情说明
              <textarea class="review-field-input" data-index="${index}" data-field="description" data-label="剧情说明" rows="2">${escapeHTML(
                item.description || ""
              )}</textarea>
            </label>
            <div class="review-options-editor">
              ${(item.options || [])
                .map(
                  (option, optionIndex) => `
                    <label>
                      按钮 ${optionIndex + 1}
                      <input class="review-option-input" data-index="${index}" data-option-index="${optionIndex}" data-field="label" value="${escapeHTML(
                        option.label
                      )}" />
                    </label>
                  `
                )
                .join("")}
            </div>
            <label class="review-wide-field">
              证据文本
              <textarea class="review-field-input" data-index="${index}" data-field="evidence_text" data-label="证据文本" rows="2">${escapeHTML(
                item.evidence_text || ""
              )}</textarea>
            </label>
          </article>
        `
      )
      .join("")}
  `;
}

function renderExperiencePreview(payload) {
  const config = payload.config || {};
  const theme = config.player_theme || {};
  const timeline = Array.isArray(config.sticker_timeline) ? config.sticker_timeline : [];
  const modes = config.danmaku_modes || {};
  const quizRewards = Array.isArray(config.quiz_rewards) ? config.quiz_rewards : [];
  $("#experiencePreview").innerHTML = `
    <article class="experience-card">
      <strong>${escapeHTML(payload.drama_title || "")} · ${escapeHTML(payload.episode_title || "")}</strong>
      <p>版本 ${escapeHTML(payload.version || 1)} · ${escapeHTML(payload.source || "unknown")} · ${escapeHTML(
        payload.review_status || "draft"
      )}</p>
      <small>${payload.persisted ? "已存入数据库" : "系统默认草稿，保存后进入数据库"}</small>
    </article>
    ${renderValidationSummary("experience", "体验配置保存前检查", validateExperiencePayload(payload))}
    <article class="experience-card">
      <strong>播放器主题</strong>
      <div class="experience-field-grid">
        <label>
          主题名
          <input class="experience-field-input" data-section="theme" data-field="name" value="${escapeHTML(theme.name || "")}" />
        </label>
        <label>
          主题标识
          <input class="experience-field-input" data-section="theme" data-field="badge" value="${escapeHTML(theme.badge || "")}" />
        </label>
      </div>
      <label class="experience-wide-field">
        风格信号
        <input class="experience-field-input" data-section="theme" data-field="signal" value="${escapeHTML(theme.signal || "")}" />
      </label>
    </article>
    <article class="experience-card">
      <div class="experience-card-head">
        <strong>同看竞猜奖励</strong>
        <button class="ghost-button add-quiz-reward-button" type="button">新增竞猜</button>
      </div>
      <small>按高光名称匹配。用户选择正确 option key 后发放积分和称号，适合配置每集专属徽章。</small>
      ${
        quizRewards.length
          ? `<div class="quiz-reward-list">${quizRewards
              .map(
                (rule, index) => `
                  <div class="quiz-reward-card">
                    <div class="experience-slot-head">
                      <strong>${escapeHTML(rule.title || `竞猜奖励 ${index + 1}`)}</strong>
                      <button class="danger-button delete-quiz-reward-button" type="button" data-index="${index}">删除</button>
                    </div>
                    <div class="experience-field-grid">
                      <label>
                        高光名称
                        <input class="experience-field-input" data-section="quiz" data-index="${index}" data-field="highlight_title" value="${escapeHTML(
                          rule.highlight_title || ""
                        )}" />
                      </label>
                      <label>
                        正确选项 key
                        <input class="experience-field-input" data-section="quiz" data-index="${index}" data-field="correct_option_key" value="${escapeHTML(
                          rule.correct_option_key || ""
                        )}" />
                      </label>
                      <label>
                        正确选项文案
                        <input class="experience-field-input" data-section="quiz" data-index="${index}" data-field="correct_label" value="${escapeHTML(
                          rule.correct_label || ""
                        )}" />
                      </label>
                    </div>
                    <div class="experience-field-grid">
                      <label>
                        积分
                        <input class="experience-field-input" type="number" min="1" step="1" data-section="quiz" data-index="${index}" data-field="points" value="${Number(
                          rule.points || 20
                        )}" />
                      </label>
                      <label>
                        奖励 key
                        <input class="experience-field-input" data-section="quiz" data-index="${index}" data-field="reward_key" value="${escapeHTML(
                          rule.reward_key || ""
                        )}" />
                      </label>
                      <label>
                        称号/徽章
                        <input class="experience-field-input" data-section="quiz" data-index="${index}" data-field="title" value="${escapeHTML(
                          rule.title || ""
                        )}" />
                      </label>
                    </div>
                    <label class="experience-wide-field">
                      说明
                      <textarea class="experience-field-input" data-section="quiz" data-index="${index}" data-field="description" rows="2">${escapeHTML(
                        rule.description || ""
                      )}</textarea>
                    </label>
                    <label class="experience-wide-field">
                      前端提示
                      <input class="experience-field-input" data-section="quiz" data-index="${index}" data-field="prompt" value="${escapeHTML(
                        rule.prompt || "同看竞猜题，答对后解锁称号和积分。"
                      )}" />
                    </label>
                  </div>
                `
              )
              .join("")}</div>`
          : "<p>暂无竞猜奖励规则</p>"
      }
    </article>
    <article class="experience-card">
      <div class="experience-card-head">
        <strong>大模型贴图建议导入</strong>
        <div>
          <button class="ghost-button load-sticker-suggestion-file-button" type="button">加载建议文件</button>
          <button class="ghost-button fill-sticker-suggestion-sample-button" type="button">生成导入样例</button>
          <button class="primary-button import-sticker-suggestion-button" type="button">导入并合并</button>
        </div>
      </div>
      <small>支持导入 slots、suggestions、sticker_timeline 或完整 config.sticker_timeline。导入只更新当前编辑区，保存后才写入服务端。</small>
      <textarea id="stickerSuggestionJson" class="sticker-suggestion-json" spellcheck="false" rows="7" placeholder='{"slots":[{"start_time_sec":27,"end_time_sec":36,"asset_ids":["winterBrokenHeart"],"meaning":"..."}]}'>${escapeHTML(
        state.stickerSuggestionDraft
      )}</textarea>
    </article>
    <article class="experience-card">
      <div class="experience-card-head">
        <strong>贴图时间轴</strong>
        <div>
          <button class="ghost-button generate-highlight-slots-button" type="button">按高光生成</button>
          <button class="ghost-button add-slot-button" type="button">新增时间窗</button>
        </div>
      </div>
      ${
        timeline.length
          ? `<div class="experience-slot-list">${timeline
              .map((slot, index) => {
                const start = Number(slot.start_time_sec ?? slot.start ?? 0);
                const end = Number(slot.end_time_sec ?? slot.end ?? start);
                const assets = slot.asset_ids || slot.assets || [];
                return `
                  <div class="experience-slot-card">
                    <div class="experience-slot-head">
                      <strong>${formatTime(start)}-${formatTime(end)}</strong>
                      <button class="danger-button delete-slot-button" type="button" data-index="${index}">删除</button>
                    </div>
                    <div class="experience-field-grid">
                      <label>
                        开始秒
                        <input class="experience-field-input" type="number" min="0" step="0.1" data-section="slot" data-index="${index}" data-field="start_time_sec" value="${start}" />
                      </label>
                      <label>
                        结束秒
                        <input class="experience-field-input" type="number" min="0" step="0.1" data-section="slot" data-index="${index}" data-field="end_time_sec" value="${end}" />
                      </label>
                    </div>
                    <label class="experience-wide-field">
                      贴图ID，用逗号分隔
                      <input class="experience-field-input" data-section="slot" data-index="${index}" data-field="asset_ids" value="${assets.map(escapeHTML).join(", ")}" />
                    </label>
                    ${renderStickerAssetPicker(assets, index)}
                    <div class="experience-field-grid">
                      <label>
                        频率秒
                        <input class="experience-field-input" type="number" min="1" step="1" data-section="slot" data-index="${index}" data-field="cadence_sec" value="${Number(
                          slot.cadence_sec || 3
                        )}" />
                      </label>
                      <label>
                        出现数量
                        <input class="experience-field-input" type="number" min="1" step="1" data-section="slot" data-index="${index}" data-field="burst_count" value="${Number(
                          slot.burst_count || 2
                        )}" />
                      </label>
                    </div>
                    <label class="experience-wide-field">
                      贴图含义
                      <textarea class="experience-field-input" data-section="slot" data-index="${index}" data-field="meaning" rows="2">${escapeHTML(
                        slot.meaning || ""
                      )}</textarea>
                    </label>
                  </div>
                `;
              })
              .join("")}</div>`
          : "<p>暂无贴图时间窗</p>"
      }
    </article>
    <article class="experience-card">
      <strong>弹幕模式</strong>
      <ul>
        ${["light", "carnival", "immerse"]
          .map((key) => {
            const item = modes[key] || {};
            const includeModes = item.include_modes || item.includeModes || [];
            return `<li>${escapeHTML(item.label || key)}：密度 ${escapeHTML(item.density ?? "-")} · ${
              item.enabled ? "展示" : "关闭"
            } · ${includeModes.map(escapeHTML).join("/")}</li>`;
          })
          .join("")}
      </ul>
    </article>
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

function formatExperienceJson() {
  try {
    const payload = parseExperienceJson();
    syncExperienceJson(payload);
    renderExperiencePreview(payload);
    setReviewStatus("体验配置格式化完成");
  } catch (error) {
    setReviewStatus(`体验配置 JSON 格式错误：${error.message}`, true);
  }
}

async function saveReviewPayload() {
  try {
    const payload = parseReviewJson();
    const validation = validateReviewPayload(payload);
    if (validation.errors.length) {
      renderReviewPreview(payload);
      setReviewStatus(`保存前检查失败：${validation.errors.slice(0, 2).join("；")}`, true);
      return;
    }
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

async function saveExperiencePayload() {
  try {
    const payload = parseExperienceJson();
    const validation = validateExperiencePayload(payload);
    if (validation.errors.length) {
      renderExperiencePreview(payload);
      setReviewStatus(`体验配置检查失败：${validation.errors.slice(0, 2).join("；")}`, true);
      return;
    }
    const episodeId = Number($("#reviewEpisodeSelect").value || payload.episode_id);
    const result = await fetchJSON(`/api/admin/episodes/${episodeId}/experience`, {
      method: "PUT",
      body: JSON.stringify({
        version: payload.version || 1,
        source: payload.source || "human_review",
        model_version: payload.model_version || "experience-config-v1",
        review_status: payload.review_status || "human_reviewed",
        config: payload.config || {},
      }),
    });
    syncExperienceJson(result);
    state.reviewExperience = result;
    renderExperiencePreview(result);
    if (state.currentEpisode?.id === episodeId) {
      state.currentExperience = result;
      applyPlayerTheme(state.currentEpisode);
    }
    setReviewStatus(`体验配置已保存：版本 ${result.version} · ${result.source}`);
  } catch (error) {
    setReviewStatus(`体验配置保存失败：${error.message}`, true);
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
    postRoomEvent("danmaku", {
      episode_id: comment.episode_id,
      time_sec: comment.time_sec,
      text: comment.text,
      comment,
    });
    input.value = "";
    setDanmakuFeedback("已发送");
  } catch (error) {
    setDanmakuFeedback(errorMessage(error), true);
  }
}

function applyPendingResume() {
  const pending = state.pendingResume;
  if (!pending || !state.currentEpisode || pending.episodeId !== state.currentEpisode.id) return;
  const duration = Number.isFinite(player.duration) ? player.duration : state.currentEpisode.duration_sec || 0;
  const target = Math.min(Math.max(0, pending.time), Math.max(0, duration - 2));
  if (target > 1) {
    player.currentTime = target;
    setDanmakuFeedback(`已续播到 ${formatTime(target)}`);
  }
  state.pendingResume = null;
}

player.addEventListener("timeupdate", () => {
  const highlight = findDueHighlight(player.currentTime);
  if (highlight) showInteraction(highlight);
  checkDanmaku(player.currentTime);
  if (player.currentTime > 2 && Math.abs(player.currentTime - state.lastHistoryRecordAt) >= 8) {
    state.lastHistoryRecordAt = player.currentTime;
    recordWatchHistory(player.currentTime || 0);
  }
  syncRoomState();
  updatePlayerControls();
  maybeShowEndingRemix();
});

player.addEventListener("loadedmetadata", () => {
  clearPlayerStatus();
  applyPendingResume();
  updatePlayerControls();
});

player.addEventListener("canplay", clearPlayerStatus);

player.addEventListener("error", () => {
  setPlayerStatus("视频加载失败，请检查素材文件或刷新重试", true);
});

player.addEventListener("play", () => {
  updatePlayerControls();
  scheduleAmbientStickers();
  syncRoomState(true);
});

player.addEventListener("pause", () => {
  updatePlayerControls();
  window.clearTimeout(state.ambientStickerTimer);
  recordWatchHistory(player.currentTime || 0, true);
  syncRoomState(true);
});

player.addEventListener("seeked", () => {
  const duration = Number.isFinite(player.duration) ? player.duration : state.currentEpisode?.duration_sec || 0;
  if (state.endingRemixShown && duration && player.currentTime < duration - 12 && !state.remixLoading) {
    clearEndingRemix();
  }
  clearDanmakuLayer();
  clearStickerLayer();
  scheduleAmbientStickers();
  syncRoomState(true);
});

player.addEventListener("ended", () => {
  maybeShowEndingRemix(true);
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
profileTab.addEventListener("click", () => setView("profile"));
adminTab.addEventListener("click", () => setView("admin"));
reviewTab.addEventListener("click", () => setView("review"));
$("#authForm").addEventListener("submit", submitAuth);
$("#toggleAuthMode").addEventListener("click", () => {
  state.authMode = state.authMode === "login" ? "register" : "login";
  syncAuthMode();
});
$("#logoutButton").addEventListener("click", logout);
document.querySelectorAll(".demo-accounts button").forEach((button) => {
  button.addEventListener("click", () => {
    state.authMode = "login";
    syncAuthMode();
    $("#authUsername").value = button.dataset.demoUser;
    $("#authPassword").value = button.dataset.demoPass;
    setAuthStatus(`已填入${button.textContent}演示账号`);
  });
});
$("#backButton").addEventListener("click", () => setView("home"));
$("#refreshProfile").addEventListener("click", loadRewardProfile);
$("#profileGallery").addEventListener("click", (event) => {
  const button = event.target.closest(".profile-open-episode");
  if (button) {
    openEpisodeFromUrl(Number(button.dataset.episodeId));
  }
});
endingRemixLayer?.addEventListener("click", (event) => {
  const choice = event.target.closest("[data-remix-choice]");
  if (choice) {
    generateEndingRemix(choice.dataset.remixChoice);
    return;
  }
  const actionButton = event.target.closest("[data-remix-action]");
  if (!actionButton) return;
  const action = actionButton.dataset.remixAction;
  if (action === "back" && state.remixOptions) {
    renderEndingRemixOptions(state.remixOptions);
    return;
  }
  if (action === "close") {
    endingRemixLayer.className = "ending-remix-layer hidden";
    endingRemixLayer.innerHTML = "";
    const duration = Number.isFinite(player.duration) ? player.duration : state.currentEpisode?.duration_sec || 0;
    if (duration && player.currentTime < duration - 1 && player.paused) {
      player.play().catch(() => {});
    }
  }
});
roomMemberList?.addEventListener("click", (event) => {
  const card = event.target.closest(".room-member-card");
  if (card) openPublicProfile(Number(card.dataset.userId));
});
$("#closePublicProfile").addEventListener("click", closePublicProfile);
publicProfileModal?.addEventListener("click", (event) => {
  if (event.target === publicProfileModal) closePublicProfile();
});
rewardPanel?.addEventListener("click", (event) => {
  if (event.target.closest(".reward-gallery-link")) {
    setView("profile");
  }
});
$("#refreshStats").addEventListener("click", loadStats);
$("#adminView").addEventListener("click", (event) => {
  if (event.target.classList.contains("save-admin-user-button")) {
    saveAdminUser(event.target);
  }
});
$("#reviewStatusFilter").addEventListener("change", async (event) => {
  state.reviewFilter = event.target.value;
  await renderReviewEpisodeOptions(Number($("#reviewEpisodeSelect").value));
});
$("#reviewEpisodeSelect").addEventListener("change", (event) => loadReviewPayload(Number(event.target.value)));
$("#reloadReview").addEventListener("click", () => loadReviewPayload(Number($("#reviewEpisodeSelect").value)));
$("#formatReview").addEventListener("click", formatReviewJson);
$("#saveReview").addEventListener("click", saveReviewPayload);
$("#formatExperience").addEventListener("click", formatExperienceJson);
$("#saveExperience").addEventListener("click", saveExperiencePayload);
$("#reviewPreview").addEventListener("input", (event) => {
  if (event.target.classList.contains("review-time-input")) {
    updateReviewTimeInput(event.target);
  }
  if (event.target.classList.contains("review-field-input")) {
    updateReviewFieldInput(event.target);
  }
  if (event.target.classList.contains("review-option-input")) {
    updateReviewOptionInput(event.target);
  }
});
$("#reviewPreview").addEventListener("change", (event) => {
  if (event.target.classList.contains("review-field-input")) {
    updateReviewFieldInput(event.target);
  }
});
$("#reviewPreview").addEventListener("click", (event) => {
  if (event.target.classList.contains("add-highlight-button")) {
    addReviewHighlight();
  }
  if (event.target.classList.contains("delete-highlight-button")) {
    deleteReviewHighlight(event.target);
  }
});
$("#experiencePreview").addEventListener("input", (event) => {
  if (event.target.classList.contains("experience-field-input")) {
    updateExperienceFieldInput(event.target);
  }
  if (event.target.id === "stickerSuggestionJson") {
    state.stickerSuggestionDraft = event.target.value;
  }
  if (event.target.classList.contains("asset-filter-input")) {
    filterAssetPicker(event.target);
  }
});
$("#experiencePreview").addEventListener("change", (event) => {
  if (event.target.classList.contains("experience-field-input")) {
    updateExperienceFieldInput(event.target);
  }
  if (event.target.classList.contains("asset-selected-only-filter")) {
    filterAssetPicker(event.target);
  }
});
$("#experiencePreview").addEventListener("click", (event) => {
  const assetChip = event.target.closest(".asset-chip");
  if (assetChip) {
    toggleStickerAsset(assetChip);
    return;
  }
  if (event.target.classList.contains("add-slot-button")) {
    addExperienceSlot();
  }
  if (event.target.classList.contains("delete-slot-button")) {
    deleteExperienceSlot(event.target);
  }
  if (event.target.classList.contains("add-quiz-reward-button")) {
    addQuizReward();
  }
  if (event.target.classList.contains("delete-quiz-reward-button")) {
    deleteQuizReward(event.target);
  }
  if (event.target.classList.contains("generate-highlight-slots-button")) {
    generateStickerSlotsFromHighlights();
  }
  if (event.target.classList.contains("fill-sticker-suggestion-sample-button")) {
    fillStickerSuggestionSample();
  }
  if (event.target.classList.contains("import-sticker-suggestion-button")) {
    importStickerSuggestionJson();
  }
  if (event.target.classList.contains("load-sticker-suggestion-file-button")) {
    loadStickerSuggestionFile();
  }
});
$("#remixReviewPanel").addEventListener("click", (event) => {
  const button = event.target.closest(".remix-review-action");
  if (button) updateRemixReview(button);
});
$("#danmakuForm").addEventListener("submit", sendDanmaku);
$("#danmakuSettingsToggle").addEventListener("click", () => {
  $("#danmakuSettings").classList.toggle("open");
});
$("#createRoomButton").addEventListener("click", createWatchRoom);
$("#joinRoomButton").addEventListener("click", joinWatchRoom);
$("#leaveRoomButton").addEventListener("click", () => {
  leaveWatchRoom();
  setDanmakuFeedback("已离开同看房间");
});
roomCodeInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") joinWatchRoom();
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

syncAuthMode();
updateAuthUI();
applyDanmakuSettings();
renderDanmakuHint();

async function bootstrap() {
  const signedIn = await restoreAuth();
  if (!signedIn) {
    setView("auth");
    return;
  }
  await loadDramas();
  await loadWatchHistory();
  await loadRewardProfile();
  await routeAfterAuth();
}

bootstrap().catch((error) => {
  const target = state.currentUser ? $("#dramaGrid") : $("#authStatus");
  target.innerHTML = `加载失败：${escapeHTML(errorMessage(error))}`;
});
