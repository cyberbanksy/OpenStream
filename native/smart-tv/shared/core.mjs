export function rankRecommendations(items, history = {}) {
  return [...items].sort((left, right) => score(right, history) - score(left, history));
}

function score(item, history) {
  const signal = history[item.id] || {};
  const progress = progressFraction(signal);
  const recency = Number(signal.lastPlayed || 0) / 1_000_000_000_000;
  const completedPenalty = signal.completed ? -25 : 0;
  const resumeBonus = progress > 0.03 && progress < 0.95 ? 45 : 0;
  const qualityBonus = item.hdr ? 4 : 0;
  return Number(item.popularity || 0) + recency + completedPenalty + resumeBonus + qualityBonus;
}

function progressFraction(signal = {}) {
  const explicit = Number(signal.progress);
  if (Number.isFinite(explicit) && explicit > 0) return Math.min(explicit, 1);
  const position = Number(signal.position);
  const duration = Number(signal.duration);
  return Number.isFinite(position) && Number.isFinite(duration) && duration > 0
    ? Math.min(Math.max(position / duration, 0), 1)
    : 0;
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

/**
 * Uses only hints that TV browsers may expose. Unknown devices deliberately
 * stay on the stable middle profile instead of being treated as premium.
 */
export function performanceProfile({ deviceMemory, hardwareConcurrency, saveData = false } = {}) {
  const memory = Number(deviceMemory);
  const cores = Number(hardwareConcurrency);
  if (saveData || (Number.isFinite(memory) && memory <= 1) || (Number.isFinite(cores) && cores <= 2)) {
    return "reduced";
  }
  if (Number.isFinite(memory) && memory >= 4 && Number.isFinite(cores) && cores >= 4) {
    return "full";
  }
  return "standard";
}

/**
 * Geometric D-pad navigation for layouts whose card counts vary by device.
 * Cross-axis drift is penalized so a nearby aligned control wins over a
 * diagonally closer one.
 */
export function selectSpatialTarget(current, targets, direction) {
  if (!current) return targets[0] || null;
  const origin = center(current);
  const horizontal = direction === "left" || direction === "right";
  const candidates = targets
    .filter((target) => target && target !== current)
    .map((target) => ({ target, point: center(target) }))
    .filter(({ point }) => {
      if (direction === "left") return point.x < origin.x;
      if (direction === "right") return point.x > origin.x;
      if (direction === "up") return point.y < origin.y;
      return direction === "down" && point.y > origin.y;
    })
    .map(({ target, point }) => {
      const dx = Math.abs(point.x - origin.x);
      const dy = Math.abs(point.y - origin.y);
      const primary = horizontal ? dx : dy;
      const cross = horizontal ? dy : dx;
      return { target, score: primary + cross * 2.25 };
    })
    .sort((left, right) => left.score - right.score);
  return candidates[0]?.target || null;
}

function center(rect) {
  return {
    x: Number(rect.left) + Number(rect.width) / 2,
    y: Number(rect.top) + Number(rect.height) / 2,
  };
}
