import {
  type ContentPolicy,
  type KidsPolicy,
  standardPolicy,
  strictestKidsPolicy,
} from "../domain/contentPolicy";

const PROFILE_KEY = "openstream.profile.v1";
const KIDS_LOCK_KEY = "openstream.kidsLock.v1";
const LOCK_SALT = "openstream-kids-lock";

type StoredProfile = {
  policy: ContentPolicy;
  onboardedAt: string;
};

/**
 * FNV-1a checksum used to pair the kids lock with the stored policy. This is
 * a tamper-evidence seal for casual edits, not a cryptographic boundary; the
 * enforcement rule is that a present lock always resolves to a kids policy.
 */
function checksum(value: string): string {
  let hash = 0x811c9dc5;
  const input = `${LOCK_SALT}:${value}`;
  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function parseStoredProfile(raw: string | null): StoredProfile | undefined {
  if (!raw) return undefined;
  try {
    const parsed = JSON.parse(raw) as StoredProfile;
    if (parsed?.policy?.mode === "standard") return parsed;
    if (
      parsed?.policy?.mode === "kids" &&
      typeof parsed.policy.maxAge === "number" &&
      Array.isArray(parsed.policy.blockedTerms)
    ) {
      return parsed;
    }
    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * Resolves the active content policy.
 * - `undefined`: first launch; onboarding must run.
 * - Once a kids lock exists, every failure mode resolves to a kids policy —
 *   deleting or editing the stored profile can only make the filter stricter.
 *   Removing the lock itself requires clearing the app's storage entirely,
 *   which is the documented reinstall-and-set-up-again path.
 */
export function loadActivePolicy(): ContentPolicy | undefined {
  const lock = window.localStorage.getItem(KIDS_LOCK_KEY);
  const raw = window.localStorage.getItem(PROFILE_KEY);
  const stored = parseStoredProfile(raw);

  if (lock !== null) {
    if (stored?.policy.mode === "kids" && raw !== null && checksum(raw) === lock) return stored.policy;
    return strictestKidsPolicy;
  }
  if (stored?.policy.mode === "kids") {
    // A kids profile must always carry its lock; restore the missing seal.
    window.localStorage.setItem(KIDS_LOCK_KEY, checksum(raw ?? ""));
    return stored.policy;
  }
  return stored?.policy;
}

export function completeStandardOnboarding(): ContentPolicy {
  const stored: StoredProfile = { policy: standardPolicy, onboardedAt: new Date().toISOString() };
  window.localStorage.setItem(PROFILE_KEY, JSON.stringify(stored));
  return stored.policy;
}

export function enableKidsMode(policy: KidsPolicy): ContentPolicy {
  const stored: StoredProfile = { policy, onboardedAt: new Date().toISOString() };
  const raw = JSON.stringify(stored);
  window.localStorage.setItem(PROFILE_KEY, raw);
  window.localStorage.setItem(KIDS_LOCK_KEY, checksum(raw));
  return policy;
}

export function kidsLockPresent(): boolean {
  return window.localStorage.getItem(KIDS_LOCK_KEY) !== null;
}
