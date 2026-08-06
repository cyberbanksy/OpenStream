import assert from "node:assert/strict";
import test from "node:test";
import { playbackLabels, rankRecommendations, resumePosition, selectVisibleCatalog } from "../native/smart-tv/shared/core.mjs";

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
  const ranked = rankRecommendations([{ id: "new", popularity: 30 }, { id: "resume", popularity: 10 }], { resume: { progress: 0.4 } });
  assert.equal(ranked[0].id, "resume");
  assert.equal(resumePosition({ position: 120, duration: 600 }), 120);
  assert.equal(resumePosition({ position: 590, duration: 600 }), 0);
});
