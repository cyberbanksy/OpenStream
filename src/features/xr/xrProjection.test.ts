import { describe, expect, it } from "vitest";
import { cropUvForEye, parseXrViewerOptions, projectionArc } from "./xrProjection";

describe("XR viewer options", () => {
  it("uses conservative defaults", () => {
    const options = parseXrViewerOptions({
      href: "https://openstream.example/xr/viewer",
      origin: "https://openstream.example",
    });
    expect(options).toMatchObject({
      projection: "180",
      stereo: "mono",
      dynamicRange: "runtime check",
      rejectedLegacySource: false,
    });
    expect(options.source).toBe("");
  });

  it("accepts only an opaque fragment handoff and ignores legacy source parameters", () => {
    const handoffId = "opaque_handoff_identifier_with_32_chars";
    const options = parseXrViewerOptions({
      href: `https://openstream.example/xr/viewer?src=${encodeURIComponent("https://media.example/movie.mp4?token=secret")}#handoff=${handoffId}`,
      origin: "https://openstream.example",
    });
    expect(options.handoffId).toBe(handoffId);
    expect(options.rejectedLegacySource).toBe(true);
    expect(options.source).toBe("");
    expect(options.source).not.toContain("secret");
  });
});

describe("stereo UV cropping", () => {
  const fullFrame = new Float32Array([0, 0, 1, 1]);

  it("maps side-by-side eyes to separate horizontal halves", () => {
    expect(Array.from(cropUvForEye(fullFrame, "sbs", "left"))).toEqual([0, 0, 0.5, 1]);
    expect(Array.from(cropUvForEye(fullFrame, "sbs", "right"))).toEqual([0.5, 0, 1, 1]);
  });

  it("maps top-bottom eyes to separate vertical halves", () => {
    expect(Array.from(cropUvForEye(fullFrame, "tb", "left"))).toEqual([0, 0.5, 1, 1]);
    expect(Array.from(cropUvForEye(fullFrame, "tb", "right"))).toEqual([0, 0, 1, 0.5]);
  });
});

describe("projection arcs", () => {
  it("centers partial spheres on the forward-facing azimuth", () => {
    expect(projectionArc("180")).toEqual({ start: Math.PI / 2, length: Math.PI });
    const wide = projectionArc("wide");
    expect(wide.start + wide.length / 2).toBeCloseTo(Math.PI);
  });

  it("keeps 360 media on a complete sphere", () => {
    expect(projectionArc("360")).toEqual({ start: 0, length: Math.PI * 2 });
  });
});
