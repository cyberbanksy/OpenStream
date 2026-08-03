import { describe, expect, it } from "vitest";
import { isCurrentArrSelection } from "./arrSelection";

const options = {
  profiles: [{ id: 4, name: "Remux" }],
  roots: [{ id: 2, path: "/media/movies" }],
};

describe("ARR connection-bound selection", () => {
  it("accepts values loaded for the selected connection", () => {
    expect(isCurrentArrSelection("radarr-a", "radarr-a", options, 4, "/media/movies")).toBe(true);
  });

  it("rejects stale values after the selected connection changes", () => {
    expect(isCurrentArrSelection("radarr-b", "radarr-a", options, 4, "/media/movies")).toBe(false);
  });

  it("rejects values that were not returned by the selected connection", () => {
    expect(isCurrentArrSelection("radarr-a", "radarr-a", options, 99, "/media/movies")).toBe(false);
    expect(isCurrentArrSelection("radarr-a", "radarr-a", options, 4, "/private/other")).toBe(false);
  });
});
