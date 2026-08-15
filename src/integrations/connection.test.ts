import { describe, expect, it } from "vitest";
import { connectionListsEqual, type ConnectionSummary, mergeGatewayConnections } from "./connection";

function connection(overrides: Partial<ConnectionSummary> & Pick<ConnectionSummary, "id">): ConnectionSummary {
  return {
    kind: "sonarr",
    name: overrides.id,
    detail: "detail",
    state: "connected",
    capabilities: ["requests"],
    ...overrides,
  };
}

describe("gateway connection merging", () => {
  it("replaces managed entries with the fresh payload and drops deleted ones", () => {
    const current = [
      connection({ id: "managed-1", managed: true, state: "attention" }),
      connection({ id: "managed-2", managed: true }),
      connection({ id: "local-1", kind: "nas-folder" }),
    ];
    const managed = [connection({ id: "managed-1", managed: true })];
    const merged = mergeGatewayConnections(current, managed);
    expect(merged.map((entry) => entry.id)).toEqual(["managed-1", "local-1"]);
    expect(merged[0].state).toBe("connected");
  });

  it("keeps ids unique when an unmanaged entry collides with a managed one", () => {
    const current = [connection({ id: "shared-id" })];
    const managed = [connection({ id: "shared-id", managed: true })];
    const merged = mergeGatewayConnections(current, managed);
    expect(merged).toHaveLength(1);
    expect(merged[0].managed).toBe(true);
  });

  it("reports unchanged lists as equal and any field drift as different", () => {
    const a = [connection({ id: "one", lastChecked: "now" })];
    const same = [connection({ id: "one", lastChecked: "now" })];
    expect(connectionListsEqual(a, same)).toBe(true);
    expect(connectionListsEqual(a, [connection({ id: "one" })])).toBe(false);
    expect(connectionListsEqual(a, [connection({ id: "one", lastChecked: "now", state: "attention" })])).toBe(false);
    expect(connectionListsEqual(a, [connection({ id: "one", lastChecked: "now", capabilities: ["requests", "extra"] })])).toBe(false);
    expect(connectionListsEqual(a, [])).toBe(false);
  });
});
