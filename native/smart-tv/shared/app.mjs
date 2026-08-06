import { playbackLabels, rankRecommendations, resumePosition, selectVisibleCatalog } from "./core.mjs";

const demoCatalog = [
  { id: "northstar", title: "Northstar", year: 2026, duration: "2h 14m", quality: "4K", popularity: 98, uri: "openstream_demo.mp4", offlineAvailable: true },
  { id: "last-signal", title: "The Last Signal", year: 2025, duration: "1h 48m", quality: "4K HDR10", popularity: 87, dynamicRange: "HDR10" },
  { id: "solar-drift", title: "Solar Drift", year: 2026, duration: "2h 03m", quality: "Dolby Vision", popularity: 92, dynamicRange: "Dolby Vision", audio: "Atmos" },
  { id: "echoes-mars", title: "Echoes of Mars", year: 2024, duration: "Series", quality: "4K", popularity: 79 },
  { id: "silent-tides", title: "Silent Tides", year: 2025, duration: "1h 57m", quality: "HLG HDR", popularity: 83, dynamicRange: "HLG" },
];

const history = JSON.parse(localStorage.getItem("openstream.tv.history") || "{}");
const cachedCatalog = JSON.parse(localStorage.getItem("openstream.tv.catalog") || "null");
let catalog = cachedCatalog || demoCatalog;
let active = rankRecommendations(selectVisibleCatalog(navigator.onLine, catalog, demoCatalog), history)[0] || demoCatalog[0];

const cards = document.querySelector("#cards");
const video = document.querySelector("#player");
const playerShell = document.querySelector("#player-shell");
const formatIndicator = document.querySelector("#format-indicator");
let lastSave = 0;

function render() {
  const visible = rankRecommendations(selectVisibleCatalog(navigator.onLine, catalog, demoCatalog), history);
  cards.replaceChildren(...visible.slice(0, 6).map((item) => {
    const button = document.createElement("button");
    button.className = "card focusable";
    button.dataset.mediaId = item.id;
    button.innerHTML = `<span class="card-copy"><strong>${escapeHTML(item.title)}</strong><small>${escapeHTML(item.quality || item.duration || "")}</small></span>`;
    button.addEventListener("click", () => select(item));
    return button;
  }));
  updateHero(active);
}

function select(item) {
  active = item;
  updateHero(item);
  document.querySelector("#play").focus();
}

function updateHero(item) {
  document.querySelector("#hero-title").textContent = item.title;
  document.querySelector("#hero-meta").textContent = [item.year, item.duration, item.quality].filter(Boolean).join(" · ");
}

function play(item) {
  if (!item.uri) {
    document.querySelector("#details-dialog").showModal();
    return;
  }
  video.src = item.uri;
  playerShell.classList.remove("hidden");
  const saved = history[item.id];
  video.addEventListener("loadedmetadata", () => {
    video.currentTime = resumePosition(saved);
    video.play().catch(() => {});
  }, { once: true });
  const labels = playbackLabels(item);
  if (labels.length) {
    formatIndicator.textContent = labels.join("  •  ");
    formatIndicator.classList.remove("hidden");
    setTimeout(() => formatIndicator.classList.add("hidden"), 3500);
  }
}

function closePlayer() {
  video.pause();
  saveProgress(true);
  playerShell.classList.add("hidden");
  video.removeAttribute("src");
  video.load();
  document.querySelector("#play").focus();
}

function saveProgress(force = false) {
  if (!video.duration || (!force && Date.now() - lastSave < 5000)) return;
  lastSave = Date.now();
  history[active.id] = { position: video.currentTime, duration: video.duration, lastPlayed: Date.now(), completed: video.currentTime >= video.duration * .95 };
  localStorage.setItem("openstream.tv.history", JSON.stringify(history));
}

function updateNetwork() {
  const node = document.querySelector("#network");
  node.textContent = navigator.onLine ? "ONLINE" : "OFFLINE";
  node.classList.toggle("offline", !navigator.onLine);
  render();
}

function focusByDirection(key) {
  const targets = [...document.querySelectorAll(".focusable:not([disabled])")].filter((node) => node.offsetParent !== null);
  const current = document.activeElement;
  const currentRect = current?.getBoundingClientRect();
  if (!currentRect) return targets[0]?.focus();
  const cx = currentRect.left + currentRect.width / 2;
  const cy = currentRect.top + currentRect.height / 2;
  const candidates = targets.filter((node) => {
    if (node === current) return false;
    const rect = node.getBoundingClientRect();
    const x = rect.left + rect.width / 2;
    const y = rect.top + rect.height / 2;
    return key === "ArrowLeft" ? x < cx : key === "ArrowRight" ? x > cx : key === "ArrowUp" ? y < cy : y > cy;
  });
  candidates.sort((a, b) => distance(a) - distance(b));
  candidates[0]?.focus();
  function distance(node) {
    const rect = node.getBoundingClientRect();
    const dx = rect.left + rect.width / 2 - cx;
    const dy = rect.top + rect.height / 2 - cy;
    return Math.hypot(dx, dy) + (key === "ArrowLeft" || key === "ArrowRight" ? Math.abs(dy) * 2 : Math.abs(dx) * 2);
  }
}

function platformBack() {
  if (!playerShell.classList.contains("hidden")) return closePlayer();
  const dialog = document.querySelector("#details-dialog");
  if (dialog.open) return dialog.close();
  if (globalThis.tizen?.application) globalThis.tizen.application.getCurrentApplication().exit();
  else if (globalThis.webOS?.platformBack) globalThis.webOS.platformBack();
  else globalThis.history.back();
}

function escapeHTML(value) {
  const node = document.createElement("span");
  node.textContent = value;
  return node.innerHTML;
}

document.addEventListener("keydown", (event) => {
  if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) {
    event.preventDefault();
    focusByDirection(event.key);
  }
  if (["Escape", "Backspace"].includes(event.key) || [8, 10009, 461].includes(event.keyCode)) {
    event.preventDefault();
    platformBack();
  }
  if (event.key === "MediaPlayPause" || event.keyCode === 179) video.paused ? video.play() : video.pause();
});

document.querySelector("#play").addEventListener("click", () => play(active));
document.querySelector("#details").addEventListener("click", () => document.querySelector("#details-dialog").showModal());
document.querySelector("#close-details").addEventListener("click", () => document.querySelector("#details-dialog").close());
document.querySelector("#close-player").addEventListener("click", closePlayer);
video.addEventListener("timeupdate", () => saveProgress(false));
video.addEventListener("ended", closePlayer);
addEventListener("online", updateNetwork);
addEventListener("offline", updateNetwork);

if (globalThis.tizen?.tvinputdevice) {
  for (const key of ["MediaPlay", "MediaPause", "MediaPlayPause", "MediaStop", "MediaFastForward", "MediaRewind"]) {
    try { globalThis.tizen.tvinputdevice.registerKey(key); } catch {}
  }
}

globalThis.OpenStreamTV = {
  configure(next) {
    if (Array.isArray(next?.catalog) && next.catalog.length) {
      catalog = next.catalog;
      localStorage.setItem("openstream.tv.catalog", JSON.stringify(catalog));
      render();
    }
  },
};

setInterval(() => { document.querySelector("#clock").textContent = new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }); }, 1000);
updateNetwork();
document.querySelector("#play").focus();
