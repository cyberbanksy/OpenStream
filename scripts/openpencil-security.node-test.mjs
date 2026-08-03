import assert from "node:assert/strict";
import { mkdirSync, readFileSync, symlinkSync, writeFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import {
  OPENPENCIL_CODEX_TOOLS,
  buildCodexChildEnvironment,
  computePackageClosureSha256,
  copyFileExclusive,
  createPrivateTempWorkspace,
  removePrivateTempWorkspace,
  validateOpenPencilListener,
  verifyOpenPencilCodeSignature,
} from "./openpencil-security.mjs";

test("OpenPencil listener validation binds the port to the expected app and user", () => {
  const expected = {
    currentUid: 501,
    appPath: "/Applications/OpenPencil.app",
    nodePath: "/opt/homebrew/bin/node",
  };

  assert.doesNotThrow(() => validateOpenPencilListener({
    uid: 501,
    executablePath: "/opt/homebrew/bin/node",
    parentUid: 501,
    parentExecutablePath: "/Applications/OpenPencil.app/Contents/MacOS/OpenPencil",
    mcpPackageName: "@open-pencil/mcp",
    mcpPackageVersion: "0.13.2",
    mcpEntrySha256: "25d6c4a07d38ff7a91f204192de1eb8af538df03dde40a43a6ac469426c957c4",
    mcpPackageClosureSha256: "4137599827838760b810d87891e91c5d22ef26e3644484023619edf3d94e4db4",
  }, expected));
  assert.throws(() => validateOpenPencilListener({
    uid: 502,
    executablePath: "/opt/homebrew/bin/node",
  }, expected), /user/i);
  assert.throws(() => validateOpenPencilListener({
    uid: 501,
    executablePath: "/opt/homebrew/bin/node",
    parentUid: 501,
    parentExecutablePath: "/tmp/OpenPencil",
    mcpPackageName: "@open-pencil/mcp",
    mcpPackageVersion: "0.13.2",
    mcpEntrySha256: "25d6c4a07d38ff7a91f204192de1eb8af538df03dde40a43a6ac469426c957c4",
  }, expected), /application bundle/i);
});

test("OpenPencil installation verification requires a strict deep signature and designated publisher", () => {
  const calls = [];
  verifyOpenPencilCodeSignature("/Applications/OpenPencil.app", (command, args) => {
    calls.push({ command, args });
    return { status: 0, stdout: "", stderr: "" };
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].command, "/usr/bin/codesign");
  assert.equal(calls[0].args.includes("--deep"), true);
  assert.equal(calls[0].args.includes("--strict"), true);
  assert.equal(calls[0].args.some((value) => value.startsWith("-R=identifier \"net.dannote.open-pencil\"")), true);
  assert.equal(calls[0].args.some((value) => value.includes("N7D7M3Q3CD")), true);
  assert.throws(() => verifyOpenPencilCodeSignature("/Applications/OpenPencil.app", () => ({ status: 1 })), /strict code-signature/i);
});

test("OpenPencil MCP closure measurement changes with imported and unexpected package files", () => {
  const workspace = createPrivateTempWorkspace("openstream-package-closure-test-");
  try {
    const packageRoot = path.join(workspace, "mcp");
    const dependencyRoot = path.join(workspace, "fixture-dependency");
    mkdirSync(path.join(packageRoot, "dist"), { recursive: true });
    mkdirSync(path.join(packageRoot, "node_modules"), { recursive: true });
    mkdirSync(dependencyRoot, { recursive: true });
    writeFileSync(path.join(packageRoot, "package.json"), JSON.stringify({
      name: "@open-pencil/mcp",
      version: "0.13.2",
      dependencies: { "fixture-dependency": "1.0.0" },
    }));
    writeFileSync(path.join(packageRoot, "dist", "index.mjs"), "import 'fixture-dependency';\n");
    writeFileSync(path.join(dependencyRoot, "package.json"), JSON.stringify({
      name: "fixture-dependency",
      version: "1.0.0",
      exports: "./index.mjs",
    }));
    writeFileSync(path.join(dependencyRoot, "index.mjs"), "export const trusted = true;\n");
    symlinkSync(dependencyRoot, path.join(packageRoot, "node_modules", "fixture-dependency"), "dir");

    const approved = computePackageClosureSha256(packageRoot);
    writeFileSync(path.join(dependencyRoot, "index.mjs"), "export const trusted = false;\n");
    const modifiedDependency = computePackageClosureSha256(packageRoot);
    assert.notEqual(modifiedDependency, approved);

    writeFileSync(path.join(dependencyRoot, "index.mjs"), "export const trusted = true;\n");
    writeFileSync(path.join(packageRoot, "unexpected.mjs"), "export default 'injected';\n");
    assert.notEqual(computePackageClosureSha256(packageRoot), approved);
  } finally {
    removePrivateTempWorkspace(workspace);
  }
});

test("optional peers are missing only when their runtime specifier cannot resolve", () => {
  const workspace = createPrivateTempWorkspace("openstream-optional-peer-test-");
  try {
    const packageRoot = path.join(workspace, "mcp");
    const peerRoot = path.join(packageRoot, "node_modules", "bufferutil");
    mkdirSync(packageRoot, { recursive: true });
    writeFileSync(path.join(packageRoot, "package.json"), JSON.stringify({
      name: "@open-pencil/mcp",
      version: "0.13.2",
      peerDependencies: { bufferutil: "*" },
      peerDependenciesMeta: { bufferutil: { optional: true } },
    }));
    writeFileSync(path.join(packageRoot, "index.mjs"), "export const trusted = true;\n");

    const absentDigest = computePackageClosureSha256(packageRoot);
    assert.equal(computePackageClosureSha256(packageRoot), absentDigest);

    mkdirSync(peerRoot, { recursive: true });
    writeFileSync(path.join(peerRoot, "package.json"), JSON.stringify({
      name: "attacker-controlled-peer",
      version: "1.0.0",
      main: "index.cjs",
    }));
    writeFileSync(path.join(peerRoot, "index.cjs"), "module.exports = { injected: true };\n");
    assert.throws(() => computePackageClosureSha256(packageRoot), /bufferutil|package root/i);

    writeFileSync(path.join(peerRoot, "package.json"), JSON.stringify({
      name: "bufferutil",
      version: "1.0.0",
      main: "index.cjs",
    }));
    assert.notEqual(computePackageClosureSha256(packageRoot), absentDigest);

    const outsideFile = path.join(workspace, "outside.cjs");
    writeFileSync(outsideFile, "module.exports = 'outside';\n");
    symlinkSync(outsideFile, path.join(peerRoot, "linked.cjs"));
    assert.throws(() => computePackageClosureSha256(packageRoot), /unexpected symlink/i);
  } finally {
    removePrivateTempWorkspace(workspace);
  }
});

test("Codex receives an explicit OpenPencil tool allowlist and a reduced environment", () => {
  for (const required of ["render", "get_current_page", "get_page_tree", "page_bounds", "batch_update"]) {
    assert.equal(OPENPENCIL_CODEX_TOOLS.includes(required), true, required);
  }
  for (const forbidden of ["eval", "open_file", "save_file", "export_image", "export_pdf", "export_svg"]) {
    assert.equal(OPENPENCIL_CODEX_TOOLS.includes(forbidden), false, forbidden);
  }

  const environment = buildCodexChildEnvironment("synthetic-mcp-token", {
    HOME: "/Users/tester",
    PATH: "/usr/bin:/bin",
    LANG: "en_US.UTF-8",
    OPENAI_API_KEY: "required-provider-key",
    AWS_SECRET_ACCESS_KEY: "must-not-propagate",
    GITHUB_TOKEN: "must-not-propagate",
  });
  assert.equal(environment.OPENPENCIL_MCP_TOKEN, "synthetic-mcp-token");
  assert.equal(environment.OPENAI_API_KEY, "required-provider-key");
  assert.equal(environment.AWS_SECRET_ACCESS_KEY, undefined);
  assert.equal(environment.GITHUB_TOKEN, undefined);
});

test("exclusive copies cannot follow an attacker-planted destination symlink", () => {
  const workspace = createPrivateTempWorkspace("openstream-security-test-");
  try {
    const source = path.join(workspace, "source.fig");
    const destination = path.join(workspace, "roundtrip.fig");
    const victim = path.join(workspace, "victim.txt");
    writeFileSync(source, "generated-fig");
    writeFileSync(victim, "keep-me");
    symlinkSync(victim, destination);

    assert.throws(() => copyFileExclusive(source, destination), /exist|destination/i);
    assert.equal(readFileSync(victim, "utf8"), "keep-me");
  } finally {
    removePrivateTempWorkspace(workspace);
  }
});
