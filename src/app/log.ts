import { type ClientLogEntry, postClientLogs } from "../integrations/gateway";

/** Entries shipped per gateway request. */
const LOG_BATCH_SIZE = 50;
/** Oldest entries are dropped beyond this bound so the queue cannot grow unchecked. */
const LOG_QUEUE_LIMIT = 200;
/** Quiet period before a queued batch is shipped. */
const LOG_FLUSH_DELAY_MS = 1500;

const queue: ClientLogEntry[] = [];
let flushTimer: number | undefined;

function flush() {
  flushTimer = undefined;
  if (queue.length === 0) return;
  const entries = queue.splice(0, LOG_BATCH_SIZE);
  void postClientLogs(entries);
  if (queue.length > 0) flushTimer = window.setTimeout(flush, LOG_FLUSH_DELAY_MS);
}

/** Best-effort synchronous flush for page teardown; keepalive lets the request outlive the page. */
function flushRemaining() {
  if (flushTimer !== undefined) {
    window.clearTimeout(flushTimer);
    flushTimer = undefined;
  }
  if (queue.length === 0) return;
  void postClientLogs(queue.splice(0, queue.length), { keepalive: true });
}

export function logClient(level: ClientLogEntry["level"], message: string, context?: Record<string, string>) {
  queue.push({ level, message: message.slice(0, 2048), context });
  if (queue.length > LOG_QUEUE_LIMIT) queue.splice(0, queue.length - LOG_QUEUE_LIMIT);
  flushTimer ??= window.setTimeout(flush, LOG_FLUSH_DELAY_MS);
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
  window.addEventListener("pagehide", flushRemaining);
  logClient("info", `session started on ${navigator.userAgent}`);
}
