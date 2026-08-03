import { createReadStream } from "node:fs";
import { readdir, realpath, stat } from "node:fs/promises";
import path from "node:path";

const VIDEO_EXTENSIONS = new Map([
  [".mp4", "video/mp4"],
  [".m4v", "video/mp4"],
  [".mov", "video/quicktime"],
  [".mkv", "video/x-matroska"],
  [".webm", "video/webm"],
  [".ts", "video/mp2t"],
  [".m2ts", "video/mp2t"],
  [".avi", "video/x-msvideo"],
  [".wmv", "video/x-ms-wmv"],
]);

const MAX_DIRECTORY_ENTRIES = 500;

export function isVideoFile(name) {
  return VIDEO_EXTENSIONS.has(path.extname(name).toLowerCase());
}

export function videoContentType(name) {
  return VIDEO_EXTENSIONS.get(path.extname(name).toLowerCase()) ?? "application/octet-stream";
}

/**
 * Validates a NAS root the user pointed the gateway at: it must be an
 * absolute path to a readable directory (typically an SMB/NFS share the
 * operating system already mounted, e.g. under /Volumes on macOS).
 */
export async function validateNasRoot(value) {
  if (typeof value !== "string" || !path.isAbsolute(value.trim())) {
    throw new Error("A NAS folder must be an absolute path to a mounted share or local folder.");
  }
  const resolved = await realpath(value.trim());
  const info = await stat(resolved);
  if (!info.isDirectory()) throw new Error("The NAS folder path is not a directory.");
  return resolved;
}

/**
 * Resolves a browse/play request path against its connection root and
 * guarantees the result stays inside that root, following symlinks.
 */
export async function resolveWithinRoot(rootPath, relativeValue = "") {
  const relative = String(relativeValue);
  if (/[\u0000-\u001f]/.test(relative) || relative.length > 4096) throw new Error("The NAS path is invalid.");
  const combined = path.resolve(rootPath, `.${path.sep}${relative}`);
  const resolved = await realpath(combined);
  if (resolved !== rootPath && !resolved.startsWith(`${rootPath}${path.sep}`)) {
    throw new Error("The NAS path escapes the connected folder.");
  }
  return resolved;
}

export async function listNasDirectory(rootPath, relativeValue = "") {
  const directoryPath = await resolveWithinRoot(rootPath, relativeValue);
  const info = await stat(directoryPath);
  if (!info.isDirectory()) throw new Error("The NAS path is not a directory.");
  const entries = await readdir(directoryPath, { withFileTypes: true });
  const directories = [];
  const files = [];
  for (const entry of entries) {
    if (entry.name.startsWith(".")) continue;
    const relativePath = path.relative(rootPath, path.join(directoryPath, entry.name));
    if (entry.isDirectory()) {
      directories.push({ name: entry.name, path: relativePath });
    } else if (entry.isFile() && isVideoFile(entry.name)) {
      files.push({ name: entry.name, path: relativePath });
    }
    if (directories.length + files.length >= MAX_DIRECTORY_ENTRIES) break;
  }
  directories.sort((a, b) => a.name.localeCompare(b.name));
  files.sort((a, b) => a.name.localeCompare(b.name));
  const sized = await Promise.all(files.map(async (file) => {
    try {
      const fileInfo = await stat(path.join(rootPath, file.path));
      return { ...file, size: fileInfo.size };
    } catch {
      return file;
    }
  }));
  return { path: path.relative(rootPath, directoryPath), directories, files: sized };
}

function parseRange(rangeHeader, size) {
  if (!rangeHeader) return undefined;
  const match = /^bytes=(\d*)-(\d*)$/.exec(rangeHeader);
  if (!match || (match[1] === "" && match[2] === "")) throw new Error("Media range request is invalid.");
  let start = match[1] === "" ? undefined : Number(match[1]);
  let end = match[2] === "" ? undefined : Number(match[2]);
  if (start === undefined) {
    // suffix range: last N bytes
    start = Math.max(0, size - (end ?? 0));
    end = size - 1;
  } else {
    end = end === undefined ? size - 1 : Math.min(end, size - 1);
  }
  if (start >= size || (end !== undefined && start > end)) {
    const error = new Error("The requested media range is not satisfiable.");
    error.statusCode = 416;
    throw error;
  }
  return { start, end };
}

/**
 * Streams a validated NAS file with HTTP Range support so seeking works in
 * every player. The caller passes response headers it wants merged in.
 */
export async function streamNasFile(request, response, filePath, extraHeaders = {}) {
  const info = await stat(filePath);
  if (!info.isFile()) throw new Error("The NAS media path is not a file.");
  const range = parseRange(request.headers.range, info.size);
  const headers = {
    ...extraHeaders,
    "Accept-Ranges": "bytes",
    "Content-Type": videoContentType(filePath),
  };
  let stream;
  if (range) {
    headers["Content-Range"] = `bytes ${range.start}-${range.end}/${info.size}`;
    headers["Content-Length"] = String(range.end - range.start + 1);
    response.writeHead(206, headers);
    stream = createReadStream(filePath, { start: range.start, end: range.end });
  } else {
    headers["Content-Length"] = String(info.size);
    response.writeHead(200, headers);
    stream = createReadStream(filePath);
  }
  stream.pipe(response);
  request.on("close", () => stream.destroy());
  return new Promise((resolve, reject) => {
    stream.on("error", reject);
    stream.on("end", resolve);
    response.on("close", resolve);
  });
}
