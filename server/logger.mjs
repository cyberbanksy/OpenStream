import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { redact } from "./security.mjs";

const MAX_FIELD_LENGTH = 4096;

function clean(value) {
  return redact(String(value)).slice(0, MAX_FIELD_LENGTH);
}

/**
 * Structured JSONL logger for the gateway. Every line carries a timestamp,
 * source, level, and message so a test session can be replayed end to end.
 * Values pass through the security redactor before they reach disk.
 */
export function createLogger({
  directory = process.env.OPENSTREAM_LOG_DIR ?? path.join(process.cwd(), "logs"),
  mirrorToConsole = true,
} = {}) {
  let directoryReady;
  const ensureDirectory = () => {
    directoryReady ??= mkdir(directory, { recursive: true }).catch(() => undefined);
    return directoryReady;
  };

  const write = async (level, source, message, context) => {
    const entry = {
      at: new Date().toISOString(),
      level,
      source,
      message: clean(message),
      ...(context && typeof context === "object"
        ? { context: Object.fromEntries(Object.entries(context).slice(0, 32).map(([key, value]) => [key.slice(0, 128), clean(value)])) }
        : {}),
    };
    const line = JSON.stringify(entry);
    if (mirrorToConsole) {
      const target = level === "error" ? console.error : level === "warn" ? console.warn : console.log;
      target(`[openstream:${source}] ${entry.at} ${level} ${entry.message}`);
    }
    try {
      await ensureDirectory();
      const day = entry.at.slice(0, 10);
      await appendFile(path.join(directory, `openstream-${day}.log`), `${line}\n`, "utf8");
    } catch {
      // Logging must never break a media request; console mirror already ran.
    }
  };

  return {
    directory,
    info: (source, message, context) => write("info", source, message, context),
    warn: (source, message, context) => write("warn", source, message, context),
    error: (source, message, context) => write("error", source, message, context),
  };
}
