export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className={compact ? "brand brand-compact" : "brand"}>
      <span className="brand-wordmark">OpenStream</span>
      {compact ? null : <span className="brand-signature">by Cyberbanksy</span>}
    </span>
  );
}
