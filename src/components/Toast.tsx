import { CheckCircle2, Info, TriangleAlert, X } from "lucide-react";
import { useApp } from "../app/AppContext";

export function Toast() {
  const { toast, dismissToast } = useApp();
  if (!toast) return null;
  const Icon = toast.tone === "success" ? CheckCircle2 : toast.tone === "warning" ? TriangleAlert : Info;
  return (
    <div className={`toast ${toast.tone}`} role="status">
      <Icon size={22} />
      <span><strong>{toast.title}</strong><small>{toast.detail}</small></span>
      <button aria-label="Dismiss notification" onClick={dismissToast}><X size={18} /></button>
    </div>
  );
}
