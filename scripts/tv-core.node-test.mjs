import assert from "node:assert/strict";
import test from "node:test";
import {
  performanceProfile,
  playbackLabels,
  rankRecommendations,
  resumePosition,
  selectSpatialTarget,
  selectVisibleCatalog,
} from "../native/smart-tv/shared/core.mjs";

test("TV playback labels only report detected premium formats", () => {
  assert.deepEqual(playbackLabels({ dynamicRange: "Dolby Vision", audio: "EAC3 JOC Atmos" }), ["DOLBY VISION", "DOLBY ATMOS"]);
  assert.deepEqual(playbackLabels({ dynamicRange: "SDR", audio: "AAC" }), []);
});

test("TV catalog automatically reduces to offline content", () => {
  const online = [{ id: "remote" }];
  const local = [{ id: "local", offlineAvailable: true }, { id: "partial", offlineAvailable: false }];
  assert.deepEqual(selectVisibleCatalog(false, online, local).map((item) => item.id), ["local"]);
});

test("TV recommendations prioritize resumable content and resume thresholds", () => {
  const ranked = rankRecommendations(
    [{ id: "new", popularity: 30 }, { id: "resume", popularity: 10 }],
    { resume: { position: 240, duration: 600 } },
  );
  assert.equal(ranked[0].id, "resume");
  assert.equal(resumePosition({ position: 120, duration: 600 }), 120);
  assert.equal(resumePosition({ position: 590, duration: 600 }), 0);
});

test("TV performance profile stays conservative when device hints are absent", () => {
  assert.equal(performanceProfile({}), "standard");
  assert.equal(performanceProfile({ deviceMemory: 1, hardwareConcurrency: 2, saveData: false }), "reduced");
  assert.equal(performanceProfile({ deviceMemory: 4, hardwareConcurrency: 4, saveData: false }), "full");
  assert.equal(performanceProfile({ deviceMemory: 8, hardwareConcurrency: 8, saveData: true }), "reduced");
});

test("TV spatial navigation favors the nearest aligned target", () => {
  const current = { id: "play", left: 70, top: 350, width: 180, height: 64 };
  const targets = [
    { id: "details", left: 270, top: 350, width: 180, height: 64 },
    { id: "first-card", left: 70, top: 480, width: 280, height: 170 },
    { id: "second-card", left: 370, top: 480, width: 280, height: 170 },
  ];

  assert.equal(selectSpatialTarget(current, targets, "right")?.id, "details");
  assert.equal(selectSpatialTarget(current, targets, "down")?.id, "first-card");
});
