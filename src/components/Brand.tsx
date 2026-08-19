import { BRAND_NAME, BRAND_SIGNATURE } from "./brandConstants";

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className={compact ? "brand brand-compact" : "brand"}>
      <span className="brand-wordmark">{BRAND_NAME}</span>
      {compact ? null : <span className="brand-signature">{BRAND_SIGNATURE}</span>}
    </span>
  );
}
