const DEFAULT_DANMAKU_SETTINGS = {
  mode: "light",
  fontSize: "medium",
  speed: "normal",
  area: "top",
  opacity: 82,
};

const MIN_INTERACTION_GAP_SEC = 18;

const DANMAKU_MODES = {
  light: { label: "轻聊", enabled: true, density: 0.62 },
  carnival: { label: "狂欢", enabled: true, density: 1 },
  immerse: { label: "沉浸", enabled: false, density: 0 },
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
  return mode.enabled && stableRatio(comment.id || `${comment.time_sec}-${comment.text}`) <= mode.density;
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

async function openDrama(dramaId) {
  state.episodes = await fetchJSON(`/api/dramas/${dramaId}/episodes`);
  if (!state.episodes.length) return;
  const select = $("#episodeSelect");
  select.innerHTML = state.episodes
    .map((episode) => `<option value="${episode.id}">${episode.title}</option>`)
    .join("");
  select.onchange = () => openEpisode(Number(select.value));
  await openEpisode(state.episodes[0].id);
  setView("watch");
}

async function openEpisode(episodeId) {
  state.firedHighlights.clear();
  state.lastInteractionTime = -Infinity;
  clearDanmakuLayer();
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

function showInteraction(highlight) {
  const ui = getHighlightUI(highlight.highlight_type);
  const key = getHighlightKey(highlight.highlight_type);
  const motifs = extractSceneMotifs(highlight);
  state.activeHighlight = highlight;
  state.firedHighlights.add(highlight.id);
  state.lastInteractionTime = highlight.start_time_sec;
  window.clearTimeout(state.hideTimer);
  interactionLayer.className = `interaction-layer ${ui.className} effect-${ui.effect}`;
  interactionLayer.innerHTML = `
    <div class="interaction-panel" data-effect="${escapeHTML(ui.effect)}">
      <div class="interaction-aura"></div>
      <div class="effect-stage" aria-hidden="true">${renderEffectStage(ui)}</div>
      <div class="interaction-copy">
        <div class="interaction-meta">
          <b>${escapeHTML(ui.badge)}</b>
          <span>${escapeHTML(ui.label)} · ${escapeHTML(ui.action)} · ${escapeHTML(highlight.emotion)}</span>
        </div>
        <h3>${escapeHTML(highlight.title)}</h3>
        <p>${escapeHTML(highlight.description || "")}</p>
      </div>
      ${renderSceneCaptions(ui, motifs)}
      <button class="impact-pad" type="button">
        <span>${escapeHTML(ui.padText)}</span>
        <strong>0</strong>
      </button>
      <div class="interaction-options">
        ${highlight.options
          .map(
            (option) =>
              `<button class="reaction-button" type="button" data-key="${escapeHTML(option.key)}">${escapeHTML(
                option.label
              )}</button>`
          )
          .join("")}
      </div>
      <div class="interaction-meter"><i></i></div>
      <button class="close-button" type="button" aria-label="关闭">×</button>
    </div>
  `;
  interactionLayer.querySelector(".close-button").addEventListener("click", hideInteraction);
  const impactPad = interactionLayer.querySelector(".impact-pad");
  impactPad.addEventListener("click", () => tapImpactPad(impactPad, ui));
  interactionLayer.querySelectorAll(".interaction-options button").forEach((button) => {
    button.addEventListener("click", () => submitInteraction(button.dataset.key, button));
  });
  emitHighlightDanmaku(highlight, ui);
  if (key === "comedy") {
    window.setTimeout(() => tapImpactPad(impactPad, ui), 120);
  }
  state.hideTimer = window.setTimeout(hideInteraction, 9000);
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
  state.hideTimer = window.setTimeout(hideInteraction, 5000);
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
  await renderReviewEpisodeOptions(Number($("#reviewEpisodeSelect").value));
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

function renderReviewPreview(payload) {
  const highlights = payload.highlights || [];
  $("#reviewPreview").innerHTML = highlights
    .map(
      (item) => `
        <article class="review-card">
          <p>${formatTime(item.start_time_sec)}-${formatTime(item.end_time_sec)} · ${item.highlight_type} · ${item.emotion}</p>
          <h4>${item.title}</h4>
          <span>${(item.options || []).map((option) => option.label).join(" / ")}</span>
          ${item.evidence_text ? `<small>${item.evidence_text}</small>` : ""}
        </article>
      `
    )
    .join("");
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
}

player.addEventListener("timeupdate", () => {
  const highlight = findDueHighlight(player.currentTime);
  if (highlight) showInteraction(highlight);
  checkDanmaku(player.currentTime);
});

player.addEventListener("seeked", () => {
  clearDanmakuLayer();
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

loadDramas().catch((error) => {
  $("#dramaGrid").innerHTML = `<div class="empty-state">加载失败：${error.message}</div>`;
});
