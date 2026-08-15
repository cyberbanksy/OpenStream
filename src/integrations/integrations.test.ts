import { describe, expect, it } from "vitest";
import { parseArrOptions } from "./gateway";
import { isPrivateHost, normalizeServiceUrl, redactSensitiveValue } from "./http";
import { buildStremioResourceUrl, stremioManifestSchema } from "./stremio";

describe("integration security boundaries", () => {
  it("rejects a private-network target unless the user explicitly enables it", () => {
    expect(() => normalizeServiceUrl("http://192.168.1.10:8989")).toThrow(/Private-network/);
    expect(() => normalizeServiceUrl("https://100.64.0.2:8989")).toThrow(/Private-network/);
    expect(() => normalizeServiceUrl("https://[fd00::1]:8989")).toThrow(/Private-network/);
    expect(
      normalizeServiceUrl("http://192.168.1.10:8989/", {
        allowPrivateNetwork: true,
        allowHttpLoopback: true,
      }),
    ).toBe("http://192.168.1.10:8989");
  });

  it("classifies canonical IPv4-mapped IPv6 hosts before URL authorization", () => {
    for (const host of ["::ffff:127.0.0.1", "::ffff:7f00:1", "0:0:0:0:0:ffff:c0a8:102"]) {
      expect(isPrivateHost(host), host).toBe(true);
    }
    expect(isPrivateHost("::ffff:808:808")).toBe(false);
    expect(() => normalizeServiceUrl("https://[::ffff:7f00:1]")).toThrow(/Private-network/);
    expect(normalizeServiceUrl("https://[::ffff:808:808]")).toBe("https://[::ffff:808:808]");
  });

  it("enforces ARR option cardinality and field budgets before rendering", () => {
    const profiles = Array.from({ length: 128 }, (_, id) => ({ id, name: `Profile ${id}` }));
    const roots = Array.from({ length: 128 }, (_, id) => ({ id, path: `/media/${id}`, freeSpace: id }));
    expect(parseArrOptions({ profiles, roots }).profiles).toHaveLength(128);
    expect(() => parseArrOptions({ profiles: [...profiles, { id: 129, name: "Too many" }], roots })).toThrow();
    expect(() => parseArrOptions({ profiles: [{ id: 1, name: "" }], roots: [] })).toThrow();
    expect(() => parseArrOptions({ profiles: [], roots: [{ path: "" }] })).toThrow();
    expect(() => parseArrOptions({ profiles: [], roots: [{ path: "/media", freeSpace: Infinity }] })).toThrow();
  });

  it("redacts API keys before a URL is surfaced in an error", () => {
    expect(redactSensitiveValue("https://server.test/api?apikey=secret-value&mode=queue")).not.toContain("secret-value");
  });

  it("builds encoded Stremio protocol routes", () => {
    expect(buildStremioResourceUrl("https://addon.test", "stream", "movie", "tt1:1:2")).toBe(
      "https://addon.test/stream/movie/tt1%3A1%3A2.json",
    );
  });

  it("validates the minimum Stremio manifest contract", () => {
    expect(
      stremioManifestSchema.parse({
        id: "org.example.viewer-addon",
        version: "1.0.0",
        name: "Viewer Add-on",
        types: ["movie"],
        resources: ["stream"],
      }).name,
    ).toBe("Viewer Add-on");
  });
});
