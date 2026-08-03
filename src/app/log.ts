import { type ClientLogEntry, postClientLogs } from "../integrations/gateway";

const queue: ClientLogEntry[] = [];
let flushTimer: number | undefined;

function flush() {
  flushTimer = undefined;
  if (queue.length === 0) return;
  const entries = queue.splice(0, 50);
  void postClientLogs(entries);
}

export function logClient(level: ClientLogEntry["level"], message: string, context?: Record<string, string>) {
  queue.push({ level, message: message.slice(0, 2048), context });
  if (queue.length > 200) queue.splice(0, queue.length - 200);
  flushTimer ??= window.setTimeout(flush, 1500);
}

/**
 * Captures uncaught errors and rejections so a test session's full story
 * lands in the gateway log alongside server and transcode events.
 */
export function installClientLogCapture() {
  window.addEventListener("error", (event) => {
    logClient("error", `Unhandled error: ${event.message}`, {
      source: `${event.filename ?? "unknown"}:${event.lineno ?? 0}`,
    });
  });
  window.addEventListener("unhandledrejection", (event) => {
    logClient("error", `Unhandled rejection: ${String(event.reason).slice(0, 600)}`);
  });
  logClient("info", `session started on ${navigator.userAgent}`);
}
