const PRIVATE_HOSTS = new Set(["localhost", "localhost.localdomain"]);

export type UrlPolicy = {
  allowPrivateNetwork?: boolean;
  allowHttpLoopback?: boolean;
};

function parseIpv4(value: string) {
  const parts = value.split(".").map(Number);
  return parts.length === 4 && parts.every((part) => Number.isInteger(part) && part >= 0 && part <= 255)
    ? parts
    : undefined;
}

function parseIpv6Words(value: string) {
  let address = value;
  if (address.includes(".")) {
    const separator = address.lastIndexOf(":");
    if (separator < 0) return undefined;
    const ipv4 = parseIpv4(address.slice(separator + 1));
    if (!ipv4) return undefined;
    const [a, b, c, d] = ipv4;
    address = `${address.slice(0, separator)}:${((a << 8) | b).toString(16)}:${((c << 8) | d).toString(16)}`;
  }

  const halves = address.split("::");
  if (halves.length > 2) return undefined;
  const parseHalf = (half: string) => {
    if (!half) return [];
    const parts = half.split(":");
    if (parts.some((part) => !/^[0-9a-f]{1,4}$/.test(part))) return undefined;
    return parts.map((part) => Number.parseInt(part, 16));
  };
  const leading = parseHalf(halves[0]);
  const trailing = parseHalf(halves[1] ?? "");
  if (!leading || !trailing) return undefined;
  if (halves.length === 1) return leading.length === 8 ? leading : undefined;
  const omitted = 8 - leading.length - trailing.length;
  if (omitted < 1) return undefined;
  return [...leading, ...Array<number>(omitted).fill(0), ...trailing];
}

function isPrivateIpv4([a, b, c]: number[]) {
  return (
    a === 0 ||
    a === 10 ||
    a === 127 ||
    (a === 100 && b >= 64 && b <= 127) ||
    (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 0 && c === 0) ||
    (a === 192 && b === 0 && c === 2) ||
    (a === 192 && b === 168) ||
    (a === 198 && (b === 18 || b === 19)) ||
    (a === 198 && b === 51 && c === 100) ||
    (a === 203 && b === 0 && c === 113) ||
    a >= 224
  );
}

export function isPrivateHost(hostname: string) {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, "");
  if (PRIVATE_HOSTS.has(normalized) || normalized.endsWith(".localhost")) return true;

  const ipv4 = parseIpv4(normalized);
  if (ipv4) return isPrivateIpv4(ipv4);

  const ipv6 = parseIpv6Words(normalized);
  if (!ipv6) return false;
  const mappedIpv4 = ipv6.slice(0, 5).every((word) => word === 0) && ipv6[5] === 0xffff
    ? [ipv6[6] >> 8, ipv6[6] & 0xff, ipv6[7] >> 8, ipv6[7] & 0xff]
    : undefined;
  if (mappedIpv4) return isPrivateIpv4(mappedIpv4);

  const unspecified = ipv6.every((word) => word === 0);
  const loopback = ipv6.slice(0, 7).every((word) => word === 0) && ipv6[7] === 1;
  const first = ipv6[0];
  return unspecified || loopback || (first & 0xfe00) === 0xfc00 || (first & 0xffc0) === 0xfe80
    || (first === 0x2001 && ipv6[1] === 0x0db8);
}

export function normalizeServiceUrl(value: string, policy: UrlPolicy = {}) {
  const raw = value.trim();
  if (!raw) throw new Error("A server URL is required.");

  const url = new URL(raw);
  if (url.username || url.password) throw new Error("Put credentials in the dedicated credential field, not in the URL.");
  if (url.protocol !== "https:" && url.protocol !== "http:") throw new Error("Only HTTP and HTTPS services are supported.");

  const privateHost = isPrivateHost(url.hostname);
  if (privateHost && !policy.allowPrivateNetwork) {
    throw new Error("Private-network access must be enabled explicitly for this connection.");
  }
  if (url.protocol === "http:" && !(privateHost && policy.allowHttpLoopback)) {
    throw new Error("Use HTTPS. Plain HTTP is allowed only for an explicitly enabled local service.");
  }

  url.hash = "";
  url.search = "";
  url.pathname = url.pathname.replace(/\/+$/, "");
  return url.toString().replace(/\/$/, "");
}

export function redactSensitiveValue(value: string) {
  return value
    .replace(/([?&](?:apikey|api_key|token|access_token|key)=)[^&\s]+/gi, "$1[redacted]")
    .replace(/(authorization:\s*(?:bearer|basic)\s+)[^\s]+/gi, "$1[redacted]")
    .replace(/\/[^/:\s]+:[^@/\s]+@/g, "/[redacted]@[host]/");
}

export async function requestJson<T>(
  url: string,
  init: RequestInit = {},
  timeoutMs = 10_000,
): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        ...init.headers,
      },
    });
    if (!response.ok) {
      throw new Error(`Request failed (${response.status}) for ${redactSensitiveValue(url)}`);
    }
    return (await response.json()) as T;
  } finally {
    window.clearTimeout(timeout);
  }
}
