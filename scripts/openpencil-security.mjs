import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  chmodSync,
  constants,
  copyFileSync,
  lstatSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  realpathSync,
  rmSync,
} from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import process from "node:process";

export const OPENPENCIL_APP_PATH = "/Applications/OpenPencil.app";
const OPENPENCIL_EXECUTABLE_PATH = `${OPENPENCIL_APP_PATH}/Contents/MacOS/OpenPencil`;
const OPENPENCIL_EXECUTABLE_SHA256 = "5ba7228bc0712e89eb85e51ea5fe0657791c5d8c99af0bf45826c6d7ceac77df";
const OPENPENCIL_IDENTIFIER = "net.dannote.open-pencil";
const OPENPENCIL_TEAM_IDENTIFIER = "N7D7M3Q3CD";
const OPENPENCIL_MCP_VERSION = "0.13.2";
const OPENPENCIL_MCP_ENTRY_SHA256 = "25d6c4a07d38ff7a91f204192de1eb8af538df03dde40a43a6ac469426c957c4";
const OPENPENCIL_MCP_CLOSURE_SHA256 = "4137599827838760b810d87891e91c5d22ef26e3644484023619edf3d94e4db4";
const privateWorkspaces = new Set();

export const OPENPENCIL_CODEX_TOOLS = Object.freeze([
  "get_selection", "get_node", "find_nodes", "get_jsx", "render", "update_node",
  "set_layout", "set_layout_child", "set_radius", "set_fill", "set_stroke", "set_text",
  "set_text_properties", "delete_node", "reparent_node", "node_resize", "batch_update",
  "describe", "calc", "viewport_zoom_to_fit", "get_page_tree", "get_current_page",
  "query_nodes", "get_components", "page_bounds", "list_fonts", "list_available_fonts",
  "diff_jsx", "create_shape", "search_icons", "insert_icon", "create_component",
  "create_instance", "create_vector", "create_slice", "import_svg", "set_effects",
  "set_opacity", "set_font", "set_visible", "set_constraints", "set_rotation", "set_minmax",
  "set_font_range", "set_text_resize", "set_blend", "set_locked", "set_stroke_align",
  "set_image_fill", "clone_node", "node_move", "rename_node", "group_nodes", "ungroup_node",
  "flatten_nodes", "node_to_component", "node_bounds", "node_ancestors", "node_children",
  "node_tree", "node_bindings", "node_replace_with", "arrange", "list_variables",
  "list_collections", "get_variable", "find_variables", "create_variable", "set_variable",
  "delete_variable", "bind_variable", "get_collection", "create_collection", "delete_collection",
  "boolean_union", "boolean_subtract", "boolean_intersect", "boolean_exclude", "path_get",
  "path_set", "path_scale", "path_flip", "path_move", "viewport_get", "viewport_set",
  "analyze_colors", "analyze_typography", "analyze_spacing", "analyze_clusters",
  "analyze_overlaps", "diff_create", "diff_show", "design_to_tokens", "design_to_component_map",
]);

const CODEX_ENVIRONMENT_ALLOWLIST = Object.freeze([
  "HOME", "PATH", "TMPDIR", "USER", "LOGNAME", "LANG", "LC_ALL", "SHELL", "TERM",
  "COLORTERM", "NO_COLOR", "FORCE_COLOR", "CODEX_HOME", "XDG_CONFIG_HOME",
  "OPENAI_API_KEY", "OPENAI_BASE_URL", "SSL_CERT_FILE", "SSL_CERT_DIR", "HTTP_PROXY",
  "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY",
]);

export function buildCodexChildEnvironment(token, source = process.env) {
  if (typeof token !== "string" || token.length < 16) throw new Error("OpenPencil MCP token is missing or invalid.");
  const environment = {};
  for (const name of CODEX_ENVIRONMENT_ALLOWLIST) {
    if (typeof source[name] === "string") environment[name] = source[name];
  }
  environment.OPENPENCIL_MCP_TOKEN = token;
  return environment;
}

export function validateOpenPencilListener(listener, {
  currentUid = typeof process.getuid === "function" ? process.getuid() : undefined,
  appPath = OPENPENCIL_APP_PATH,
  nodePath = process.execPath,
} = {}) {
  if (!listener || !Number.isInteger(listener.uid) || typeof listener.executablePath !== "string") {
    throw new Error("OpenPencil listener identity could not be determined.");
  }
  if (Number.isInteger(currentUid) && listener.uid !== currentUid) {
    throw new Error("OpenPencil MCP is owned by a different operating-system user.");
  }
  const expectedExecutable = path.resolve(appPath, "Contents", "MacOS", "OpenPencil");
  if (path.resolve(listener.executablePath) === expectedExecutable) return listener;
  const trustedChild = path.resolve(listener.executablePath) === path.resolve(nodePath)
    && listener.parentExecutablePath === expectedExecutable
    && listener.parentUid === currentUid
    && listener.mcpPackageName === "@open-pencil/mcp"
    && listener.mcpPackageVersion === OPENPENCIL_MCP_VERSION
    && listener.mcpEntrySha256 === OPENPENCIL_MCP_ENTRY_SHA256
    && listener.mcpPackageClosureSha256 === OPENPENCIL_MCP_CLOSURE_SHA256;
  if (!trustedChild) {
    throw new Error("OpenPencil MCP listener is not running from the trusted application bundle.");
  }
  return listener;
}

function commandResult(command, args) {
  const result = spawnSync(command, args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
  if (result.error) throw result.error;
  return result;
}

function updateDigestField(digest, label, value) {
  const content = Buffer.isBuffer(value) ? value : Buffer.from(String(value));
  digest.update(`${label.length}:${label}:${content.length}:`);
  digest.update(content);
}

function findPackageRoot(resolvedPath, expectedName) {
  let current = path.dirname(realpathSync(resolvedPath));
  while (current !== path.dirname(current)) {
    const manifestPath = path.join(current, "package.json");
    try {
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      if (manifest.name === expectedName) return current;
    } catch {
      // Keep walking until the package that owns the resolved module is found.
    }
    current = path.dirname(current);
  }
  throw new Error(`Could not resolve the installed package root for ${expectedName}.`);
}

class UnresolvedDependencyError extends Error {
  constructor(dependencyName, cause) {
    super(`Could not resolve the installed dependency ${dependencyName}.`, { cause });
    this.name = "UnresolvedDependencyError";
  }
}

function resolveDependencyRoot(packageRoot, dependencyName, expectedPackageName = dependencyName) {
  const requireFromPackage = createRequire(path.join(packageRoot, "package.json"));
  let resolvedPath;
  try {
    resolvedPath = requireFromPackage.resolve(dependencyName);
  } catch (error) {
    if (error?.code === "MODULE_NOT_FOUND") throw new UnresolvedDependencyError(dependencyName, error);
    throw error;
  }
  return findPackageRoot(resolvedPath, expectedPackageName);
}

function declaredPackageName(dependencyName, versionRange) {
  if (typeof versionRange !== "string" || !versionRange.startsWith("npm:")) return dependencyName;
  const alias = versionRange.slice(4);
  const versionSeparator = alias.lastIndexOf("@");
  return versionSeparator > 0 ? alias.slice(0, versionSeparator) : alias;
}

function packageFiles(packageRoot) {
  const files = [];
  const visit = (directory, relativeDirectory = "") => {
    const entries = readdirSync(directory, { withFileTypes: true })
      .sort((left, right) => left.name.localeCompare(right.name));
    for (const entry of entries) {
      if (!relativeDirectory && entry.name === "node_modules") continue;
      const relativePath = relativeDirectory ? `${relativeDirectory}/${entry.name}` : entry.name;
      const absolutePath = path.join(directory, entry.name);
      const metadata = lstatSync(absolutePath);
      if (metadata.isSymbolicLink()) {
        throw new Error(`OpenPencil MCP package content contains an unexpected symlink: ${relativePath}`);
      }
      if (metadata.isDirectory()) {
        visit(absolutePath, relativePath);
      } else if (metadata.isFile()) {
        files.push({ relativePath, absolutePath });
      } else {
        throw new Error(`OpenPencil MCP package content has an unsupported file type: ${relativePath}`);
      }
    }
  };
  visit(packageRoot);
  return files;
}

export function computePackageClosureSha256(entryPackageRoot) {
  const digest = createHash("sha256");
  const visited = new Set();

  const visitPackage = (candidateRoot) => {
    const packageRoot = realpathSync(candidateRoot);
    if (visited.has(packageRoot)) return;
    visited.add(packageRoot);

    const manifest = JSON.parse(readFileSync(path.join(packageRoot, "package.json"), "utf8"));
    if (typeof manifest.name !== "string" || typeof manifest.version !== "string") {
      throw new Error("OpenPencil MCP package metadata is incomplete.");
    }
    updateDigestField(digest, "package", `${manifest.name}@${manifest.version}`);
    for (const file of packageFiles(packageRoot)) {
      updateDigestField(digest, "path", `${manifest.name}@${manifest.version}/${file.relativePath}`);
      updateDigestField(digest, "content", readFileSync(file.absolutePath));
    }

    const optionalPeerDependencies = Object.keys(manifest.peerDependencies ?? {})
      .filter((name) => manifest.peerDependenciesMeta?.[name]?.optional === true);
    const requiredDependencies = new Set([
      ...Object.keys(manifest.dependencies ?? {}),
      ...Object.keys(manifest.peerDependencies ?? {}).filter((name) => !optionalPeerDependencies.includes(name)),
    ]);
    const optionalDependencies = new Set([
      ...Object.keys(manifest.optionalDependencies ?? {}),
      ...optionalPeerDependencies,
    ]);
    const dependencyNames = [...new Set([...requiredDependencies, ...optionalDependencies])].sort();
    for (const dependencyName of dependencyNames) {
      let dependencyRoot;
      try {
        const versionRange = manifest.optionalDependencies?.[dependencyName]
          ?? manifest.dependencies?.[dependencyName]
          ?? manifest.peerDependencies?.[dependencyName];
        dependencyRoot = resolveDependencyRoot(
          packageRoot,
          dependencyName,
          declaredPackageName(dependencyName, versionRange),
        );
      } catch (error) {
        if (!optionalDependencies.has(dependencyName) || !(error instanceof UnresolvedDependencyError)) throw error;
        updateDigestField(digest, "optional-missing", dependencyName);
        continue;
      }
      visitPackage(dependencyRoot);
    }
  };

  updateDigestField(digest, "format", "openstream-package-closure/v1");
  visitPackage(entryPackageRoot);
  return digest.digest("hex");
}

function parseSignatureDetails(output) {
  const details = {};
  for (const line of output.split(/\r?\n/)) {
    const separator = line.indexOf("=");
    if (separator > 0) details[line.slice(0, separator)] = line.slice(separator + 1);
  }
  return details;
}

export function verifyOpenPencilCodeSignature(appPath = OPENPENCIL_APP_PATH, runCommand = commandResult) {
  const designatedRequirement = `identifier "${OPENPENCIL_IDENTIFIER}" and anchor apple generic and certificate leaf[subject.OU] = "${OPENPENCIL_TEAM_IDENTIFIER}"`;
  const verification = runCommand("/usr/bin/codesign", [
    "--verify",
    "--deep",
    "--strict",
    "--verbose=4",
    `-R=${designatedRequirement}`,
    appPath,
  ]);
  if (verification.status !== 0) {
    throw new Error("OpenPencil failed strict code-signature and publisher verification.");
  }
}

export function assertTrustedOpenPencilInstallation() {
  verifyOpenPencilCodeSignature();
  const executable = readFileSync(OPENPENCIL_EXECUTABLE_PATH);
  const digest = createHash("sha256").update(executable).digest("hex");
  if (digest !== OPENPENCIL_EXECUTABLE_SHA256) {
    throw new Error("OpenPencil does not match the pinned executable. Reverify the installed release before updating the pin.");
  }
  const signature = commandResult("/usr/bin/codesign", ["-dv", "--verbose=4", OPENPENCIL_APP_PATH]);
  if (signature.status !== 0) throw new Error("OpenPencil code-signing metadata could not be verified.");
  const details = parseSignatureDetails(`${signature.stdout}\n${signature.stderr}`);
  if (details.Identifier !== OPENPENCIL_IDENTIFIER || details.TeamIdentifier !== OPENPENCIL_TEAM_IDENTIFIER) {
    throw new Error("OpenPencil signing identity does not match the pinned publisher.");
  }
  return { executablePath: OPENPENCIL_EXECUTABLE_PATH, sha256: digest };
}

function parseListenerProcesses(output) {
  const records = [];
  let current;
  for (const line of output.split(/\r?\n/)) {
    if (line.startsWith("p")) {
      current = { pid: Number(line.slice(1)) };
      records.push(current);
    } else if (line.startsWith("u") && current) {
      current.uid = Number(line.slice(1));
    }
  }
  return records.filter((record) => Number.isInteger(record.pid));
}

function executablePathForPid(pid) {
  const result = commandResult("/usr/sbin/lsof", ["-a", "-p", String(pid), "-d", "txt", "-Fn"]);
  if (result.status !== 0) throw new Error("OpenPencil process executable path could not be inspected.");
  const executablePath = result.stdout.split(/\r?\n/)
    .find((line) => line.startsWith("n") && line.length > 1)?.slice(1);
  if (!executablePath) throw new Error("OpenPencil process executable path could not be determined.");
  return realpathSync(executablePath);
}

function processDetails(pid) {
  const result = commandResult("/bin/ps", ["-p", String(pid), "-o", "ppid=,uid=,command="]);
  if (result.status !== 0) throw new Error("OpenPencil process ancestry could not be inspected.");
  const match = result.stdout.match(/^\s*(\d+)\s+(\d+)\s+(.+)$/m);
  if (!match) throw new Error("OpenPencil process ancestry could not be determined.");
  return { parentPid: Number(match[1]), uid: Number(match[2]), command: match[3] };
}

export function inspectTrustedOpenPencilListener() {
  const listenerResult = commandResult("/usr/sbin/lsof", ["-nP", "-iTCP:7600", "-sTCP:LISTEN", "-Fpu"]);
  if (listenerResult.status === 1 && !listenerResult.stdout.trim()) return null;
  if (listenerResult.status !== 0) throw new Error("OpenPencil MCP listener ownership could not be inspected.");
  const listeners = parseListenerProcesses(listenerResult.stdout);
  if (listeners.length !== 1) throw new Error("OpenPencil MCP port must have exactly one trusted listener.");

  const listener = listeners[0];
  listener.executablePath = executablePathForPid(listener.pid);
  if (listener.executablePath !== realpathSync(OPENPENCIL_EXECUTABLE_PATH)) {
    const details = processDetails(listener.pid);
    const parent = processDetails(details.parentPid);
    listener.parentUid = parent.uid;
    listener.parentExecutablePath = executablePathForPid(details.parentPid);
    if (listener.parentExecutablePath === realpathSync(OPENPENCIL_EXECUTABLE_PATH)) {
      const commandParts = details.command.trim().split(/\s+/);
      if (commandParts[1] && !commandParts[1].startsWith("-")) {
        const scriptPath = realpathSync(commandParts[1]);
        const packageRoot = path.resolve(path.dirname(scriptPath), "..");
        const packageManifest = JSON.parse(readFileSync(path.join(packageRoot, "package.json"), "utf8"));
        listener.mcpPackageName = packageManifest.name;
        listener.mcpPackageVersion = packageManifest.version;
        listener.mcpEntrySha256 = createHash("sha256").update(readFileSync(scriptPath)).digest("hex");
        listener.mcpPackageClosureSha256 = computePackageClosureSha256(packageRoot);
      }
    }
  }
  return validateOpenPencilListener(listener, {
    appPath: realpathSync(OPENPENCIL_APP_PATH),
    nodePath: realpathSync(process.execPath),
  });
}

export function assertTrustedOpenPencilRuntime() {
  assertTrustedOpenPencilInstallation();
  const listener = inspectTrustedOpenPencilListener();
  if (!listener) throw new Error("The trusted OpenPencil app is not listening on 127.0.0.1:7600.");
  return listener;
}

export function createPrivateTempWorkspace(prefix) {
  if (!/^[A-Za-z0-9._-]+-$/.test(prefix)) throw new Error("Temporary workspace prefix is invalid.");
  const workspace = mkdtempSync(path.join(tmpdir(), prefix));
  chmodSync(workspace, 0o700);
  privateWorkspaces.add(workspace);
  return workspace;
}

export function copyFileExclusive(source, destination) {
  copyFileSync(source, destination, constants.COPYFILE_EXCL);
}

export function removePrivateTempWorkspace(workspace) {
  if (!privateWorkspaces.delete(workspace)) throw new Error("Refusing to remove an untracked temporary workspace.");
  rmSync(workspace, { recursive: true, force: true });
}
