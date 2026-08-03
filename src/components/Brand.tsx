import openStreamMark from "../assets/openstream-mark-v2.png";

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className={compact ? "brand brand-compact" : "brand"}>
      <span className="brand-mark-motion" aria-hidden="true">
        <img className="brand-mark" src={openStreamMark} alt="" />
      </span>
      <span className="brand-wordmark">OpenStream</span>
      {compact ? null : <span className="brand-signature">by Orgista</span>}
    </span>
  );
}
