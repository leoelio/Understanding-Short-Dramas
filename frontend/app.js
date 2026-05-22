const state = {
  dramas: [],
  episodes: [],
  currentEpisode: null,
  sessionId: localStorage.getItem("session_id") || crypto.randomUUID(),
  firedHighlights: new Set(),
  activeHighlight: null,
  hideTimer: null,
};

localStorage.setItem("session_id", state.sessionId);

const $ = (selector) => document.querySelector(selector);

const views = {
  home: $("#homeView"),
  watch: $("#watchView"),
  admin: $("#adminView"),
};

const homeTab = $("#homeTab");
const adminTab = $("#adminTab");
const player = $("#player");
const interactionLayer = $("#interactionLayer");

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
  if (name === "admin") {
    loadStats();
  }
}

function formatTime(seconds) {
  const safe = Math.max(0, Math.round(seconds || 0));
  const min = Math.floor(safe / 60).toString().padStart(2, "0");
  const sec = (safe % 60).toString().padStart(2, "0");
  return `${min}:${sec}`;
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
            <span>${drama.genre}</span>
          </div>
          <div class="drama-info">
            <h3>${drama.title}</h3>
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
  hideInteraction();
  state.currentEpisode = await fetchJSON(`/api/episodes/${episodeId}`);
  $("#watchGenre").textContent = state.currentEpisode.drama.genre || "短剧播放";
  $("#watchTitle").textContent = `${state.currentEpisode.drama.title} · ${state.currentEpisode.title}`;
  $("#episodeSelect").value = String(episodeId);
  player.src = state.currentEpisode.video_url;
  renderTimeline();
}

function renderTimeline() {
  const highlights = state.currentEpisode?.highlights || [];
  $("#timeline").innerHTML = highlights
    .map(
      (item) => `
        <button class="timeline-item" type="button" data-time="${item.start_time_sec}">
          <span>${formatTime(item.start_time_sec)}</span>
          <strong>${item.highlight_type}</strong>
          <em>${item.emotion}</em>
        </button>
      `
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
  const highlights = state.currentEpisode?.highlights || [];
  return highlights.find((item) => {
    const due = currentTime >= item.start_time_sec && currentTime <= item.end_time_sec + 2;
    return due && !state.firedHighlights.has(item.id);
  });
}

function showInteraction(highlight) {
  state.activeHighlight = highlight;
  state.firedHighlights.add(highlight.id);
  window.clearTimeout(state.hideTimer);
  interactionLayer.className = `interaction-layer type-${highlight.highlight_type}`;
  interactionLayer.innerHTML = `
    <div class="interaction-panel">
      <div class="interaction-copy">
        <span>${highlight.highlight_type} · ${highlight.emotion}</span>
        <h3>${highlight.title}</h3>
      </div>
      <div class="interaction-options">
        ${highlight.options
          .map((option) => `<button type="button" data-key="${option.key}">${option.label}</button>`)
          .join("")}
      </div>
      <button class="close-button" type="button" aria-label="关闭">×</button>
    </div>
  `;
  interactionLayer.querySelector(".close-button").addEventListener("click", hideInteraction);
  interactionLayer.querySelectorAll(".interaction-options button").forEach((button) => {
    button.addEventListener("click", () => submitInteraction(button.dataset.key));
  });
  state.hideTimer = window.setTimeout(hideInteraction, 9000);
}

function hideInteraction() {
  window.clearTimeout(state.hideTimer);
  state.activeHighlight = null;
  interactionLayer.className = "interaction-layer hidden";
  interactionLayer.innerHTML = "";
}

async function submitInteraction(optionKey) {
  if (!state.activeHighlight) return;
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
          <span>${option.label}</span>
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

player.addEventListener("timeupdate", () => {
  const highlight = findDueHighlight(player.currentTime);
  if (highlight) showInteraction(highlight);
});

homeTab.addEventListener("click", () => setView("home"));
adminTab.addEventListener("click", () => setView("admin"));
$("#backButton").addEventListener("click", () => setView("home"));
$("#refreshStats").addEventListener("click", loadStats);

if (location.hash === "#admin") {
  setView("admin");
} else {
  setView("home");
}

loadDramas().catch((error) => {
  $("#dramaGrid").innerHTML = `<div class="empty-state">加载失败：${error.message}</div>`;
});
