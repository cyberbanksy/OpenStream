export function rankRecommendations(items, history = {}) {
  return [...items].sort((left, right) => score(right, history) - score(left, history));
}

function score(item, history) {
  const signal = history[item.id] || {};
  const progress = Number(signal.progress || 0);
  const recency = Number(signal.lastPlayed || 0) / 1_000_000_000_000;
  const completedPenalty = signal.completed ? -25 : 0;
  const resumeBonus = progress > 0.03 && progress < 0.95 ? 45 : 0;
  const qualityBonus = item.hdr ? 4 : 0;
  return Number(item.popularity || 0) + recency + completedPenalty + resumeBonus + qualityBonus;
}

export function playbackLabels(signal = {}) {
  const labels = [];
  const range = String(signal.dynamicRange || "").toLowerCase();
  const audio = String(signal.audio || "").toLowerCase();
  if (range.includes("dolby vision")) labels.push("DOLBY VISION");
  else if (range.includes("hdr10+")) labels.push("HDR10+");
  else if (range.includes("hdr10") || range === "pq") labels.push("HDR10");
  else if (range.includes("hlg")) labels.push("HLG HDR");
  if (audio.includes("atmos") || audio.includes("joc")) labels.push("DOLBY ATMOS");
  else if (audio.includes("truehd")) labels.push("DOLBY TRUEHD");
  return labels;
}

export function selectVisibleCatalog(online, catalog, offlineItems) {
  const completed = offlineItems.filter((item) => item.offlineAvailable);
  if (!online) return completed;
  const seen = new Set(completed.map((item) => item.id));
  return [...completed, ...catalog.filter((item) => !seen.has(item.id))];
}

export function resumePosition(progress) {
  if (!progress || progress.duration <= 0) return 0;
  if (progress.position < 15 || progress.position >= progress.duration * 0.95) return 0;
  return progress.position;
}
