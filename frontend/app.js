const DEFAULT_DANMAKU_SETTINGS = {
  mode: "light",
  fontSize: "medium",
  speed: "normal",
  area: "top",
  opacity: 82,
};

const MIN_INTERACTION_GAP_SEC = 18;

const DANMAKU_MODES = {
  light: { label: "轻聊", enabled: true, density: 0.68, includeModes: ["light", "curated", "seed"] },
  carnival: { label: "狂欢", enabled: true, density: 1, includeModes: ["light", "curated", "seed", "carnival"] },
  immerse: { label: "沉浸", enabled: false, density: 0, includeModes: [] },
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

function emitDanmaku(comment) {
  if (!shouldShowDanmaku(comment)) return;
  const bubble = document.createElement("span");
  bubble.className = `danmaku-item ${comment.className || ""}`;
  bubble.textContent = comment.text;
  const lanes = state.danmakuSettings.area === "full" ? 8 : state.danmakuSettings.area === "middle" ? 4 : 3;
  const lane = Math.floor(stableRatio(`${comment.id}-${comment.text}`) * lanes);
  bubble.style.setProperty("--lane", lane);
  bubble.style.setProperty("--duration", `${danmakuDuration()}ms`);
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
  ];
  return candidates.filter((word) => source.includes(word)).slice(0, 3);
}

const STICKER_ASSETS = {
  charge: { src: "/assets/stickers/effect_charge.svg", label: "冲" },
  question: { src: "/assets/stickers/effect_question.svg", label: "问号" },
  laugh: { src: "/assets/stickers/effect_laugh.svg", label: "好笑" },
  rock: { src: "/assets/stickers/effect_rock.svg", label: "摇滚" },
  tear: { src: "/assets/stickers/effect_tear.svg", label: "心疼" },
};

const STICKER_RULES = [
  { asset: "charge", className: "sticker-charge", keywords: ["冲", "干", "走", "要债", "讨薪", "欠薪", "工友"] },
  { asset: "question", className: "sticker-question", keywords: ["到底", "能不能", "回不去", "悬念", "疑问", "猜"] },
  { asset: "laugh", className: "sticker-laugh", keywords: ["搞笑", "笑", "好笑", "哈哈"] },
  { asset: "rock", className: "sticker-rock", keywords: ["摇滚", "摩托", "交通工具", "车", "回家方式"] },
  { asset: "tear", className: "sticker-tear", keywords: ["没钱", "回家", "过年", "心疼", "破防", "父母"] },
];

function clearStickerLayer() {
  if (stickerLayer) stickerLayer.innerHTML = "";
}

function highlightText(highlight) {
  return `${highlight.title || ""} ${highlight.description || ""} ${highlight.highlight_type || ""} ${
    highlight.emotion || ""
  } ${highlight.evidence_text || ""}`;
}

function getSceneStickerRules(highlight) {
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

function spawnVideoSticker(rule, index = 0) {
  if (!stickerLayer || !rule) return;
  const asset = STICKER_ASSETS[rule.asset];
  if (!asset) return;
  const image = document.createElement("img");
  image.className = `scene-sticker ${rule.className || ""}`;
  image.src = asset.src;
  image.alt = asset.label;
  image.style.setProperty("--left", `${12 + ((index * 29) % 62)}%`);
  image.style.setProperty("--top", `${12 + ((index * 19) % 34)}%`);
  image.style.setProperty("--delay", `${index * 160}ms`);
  stickerLayer.appendChild(image);
  window.setTimeout(() => image.remove(), 2300 + index * 160);
}

function spawnHighlightStickers(highlight) {
  getSceneStickerRules(highlight).forEach((rule, index) => {
    window.setTimeout(() => spawnVideoSticker(rule, index), index * 180);
  });
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
  $("#watchGenre").textContent = state.currentEpisode.drama.genre || "短剧播放";
  $("#watchTitle").textContent = `${state.currentEpisode.drama.title} · ${state.currentEpisode.title}`;
  $("#episodeSelect").value = String(episodeId);
  player.src = state.currentEpisode.video_url;
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
      player.currentTime = Number(button.dataset.time);
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

function renderSceneCaptions(ui, motifs) {
  const captions = motifs.length ? motifs : [ui.label, ui.action];
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
  vehicle_train: "/assets/stickers/vehicle_train.svg",
  vehicle_car: "/assets/stickers/vehicle_car.svg",
  vehicle_motorcycle: "/assets/stickers/vehicle_motorcycle.svg",
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
      ${sticker ? `<img src="${sticker}" alt="" />` : ""}
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
  panelImage.src = sticker;
  panelImage.alt = "";
  panelImage.style.left = `${anchor.offsetLeft + anchor.offsetWidth / 2 - 48}px`;
  panelImage.style.top = `${Math.max(8, anchor.offsetTop - 94)}px`;
  panel.appendChild(panelImage);

  const videoImage = document.createElement("img");
  videoImage.className = "scene-sticker sticker-vehicle";
  videoImage.src = sticker;
  videoImage.alt = "";
  videoImage.style.setProperty("--left", "58%");
  videoImage.style.setProperty("--top", "18%");
  if (stickerLayer) stickerLayer.appendChild(videoImage);

  window.setTimeout(() => panelImage.remove(), 1400);
  window.setTimeout(() => videoImage.remove(), 2300);
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
      ${renderSceneCaptions(ui, motifs)}
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
  spawnHighlightStickers(highlight);
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
});

player.addEventListener("seeked", () => {
  clearDanmakuLayer();
  clearStickerLayer();
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
