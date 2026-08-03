import type { ConnectionState } from "../integrations/connection";

export function StatusDot({ state }: { state: ConnectionState }) {
  return <span className={`status-dot ${state}`} role="img" aria-label={`${state} status`} />;
}
