import { ArrowLeft, Baby, Ban, Lock, Plus, ShieldAlert, Sparkles, Users, X } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useApp } from "../../app/AppContext";
import { Brand } from "../../components/Brand";
import { createKidsPolicy } from "../../domain/contentPolicy";
import { kidsRatingCaps, type KidsRatingCapId } from "../../domain/ratings";

type Step = "mode" | "rating" | "blocklist" | "confirm";

export function OnboardingScreen() {
  const { completeOnboarding } = useApp();
  const [step, setStep] = useState<Step>("mode");
  const [capId, setCapId] = useState<KidsRatingCapId>("teens");
  const [blockedTerms, setBlockedTerms] = useState<string[]>([]);
  const [termDraft, setTermDraft] = useState("");
  const [acknowledged, setAcknowledged] = useState(false);
  const selectedCap = kidsRatingCaps.find((cap) => cap.id === capId) ?? kidsRatingCaps[2];

  const addTerm = (event: FormEvent) => {
    event.preventDefault();
    const term = termDraft.trim().toLowerCase();
    if (term && !blockedTerms.includes(term)) setBlockedTerms((current) => [...current, term].slice(0, 100));
    setTermDraft("");
  };

  const back = () => {
    setAcknowledged(false);
    setStep((current) => current === "confirm" ? "blocklist" : current === "blocklist" ? "rating" : "mode");
  };

  return (
    <main className="screen onboarding-screen" aria-labelledby="onboarding-title">
      <div className="onboarding-backdrop" />
      <header className="onboarding-brand"><Brand /></header>
      {step !== "mode" ? (
        <button className="back-button onboarding-back" data-focusable onClick={back}><ArrowLeft size={21} />Back</button>
      ) : null}

      {step === "mode" ? (
        <section className="onboarding-panel">
          <h1 id="onboarding-title">Who watches on this device?</h1>
          <p>This choice sets up the whole library. Kids Mode is permanent for this installation.</p>
          <div className="onboarding-choice-grid">
            <button className="onboarding-choice" data-focusable data-autofocus autoFocus onClick={() => completeOnboarding("standard")}>
              <span className="choice-icon"><Users size={34} /></span>
              <strong>Everyone</strong>
              <span>The full library with every connected source, request handoff, and add-on.</span>
            </button>
            <button className="onboarding-choice kids" data-focusable onClick={() => setStep("rating")}>
              <span className="choice-icon"><Baby size={34} /></span>
              <strong>Kids Mode</strong>
              <span>Only age-appropriate titles. Restricted and unrated content never appears or plays.</span>
              <small className="choice-warning"><Lock size={14} />Cannot be reverted once enabled</small>
            </button>
          </div>
        </section>
      ) : null}

      {step === "rating" ? (
        <section className="onboarding-panel">
          <h1 id="onboarding-title">What is the highest rating allowed?</h1>
          <p>Titles above this certification are hidden and refuse to play. Unrated content is always blocked in Kids Mode.</p>
          <div className="onboarding-choice-grid caps">
            {kidsRatingCaps.map((cap, index) => (
              <button
                key={cap.id}
                className={cap.id === capId ? "onboarding-choice selected" : "onboarding-choice"}
                data-focusable
                data-autofocus={index === 0 ? true : undefined}
                autoFocus={index === 0}
                aria-pressed={cap.id === capId}
                onClick={() => { setCapId(cap.id); setStep("blocklist"); }}
              >
                <span className="choice-icon"><Sparkles size={30} /></span>
                <strong>{cap.label}</strong>
                <span>{cap.description}</span>
                <small className="choice-examples">{cap.examples}</small>
              </button>
            ))}
          </div>
        </section>
      ) : null}

      {step === "blocklist" ? (
        <section className="onboarding-panel">
          <h1 id="onboarding-title">Anything else to block?</h1>
          <p>Add titles, franchises, or words. Any match in a title or description is hidden even when its rating passes.</p>
          <form className="blocklist-form" onSubmit={addTerm}>
            <input
              data-focusable
              value={termDraft}
              onChange={(event) => setTermDraft(event.target.value)}
              placeholder="Type a title or word, then press Enter"
              aria-label="Content to block"
              maxLength={80}
            />
            <button type="submit" data-focusable className="action-button secondary" disabled={!termDraft.trim()}><Plus size={19} />Add</button>
          </form>
          {blockedTerms.length > 0 ? (
            <div className="blocklist-chips" aria-label="Blocked content list">
              {blockedTerms.map((term) => (
                <button key={term} data-focusable className="blocklist-chip" onClick={() => setBlockedTerms((current) => current.filter((entry) => entry !== term))} aria-label={`Remove ${term} from the block list`}>
                  <Ban size={14} />{term}<X size={14} />
                </button>
              ))}
            </div>
          ) : (
            <p className="blocklist-empty">No extra blocks yet. The {selectedCap.label} rating cap still applies.</p>
          )}
          <footer className="onboarding-actions">
            <button className="action-button primary" data-focusable data-autofocus autoFocus onClick={() => setStep("confirm")}>Continue</button>
          </footer>
        </section>
      ) : null}

      {step === "confirm" ? (
        <section className="onboarding-panel confirm">
          <span className="confirm-icon"><ShieldAlert size={44} /></span>
          <h1 id="onboarding-title">Turn on Kids Mode?</h1>
          <div className="confirm-summary">
            <span><strong>{selectedCap.label}</strong>{selectedCap.examples}</span>
            <span><strong>{blockedTerms.length}</strong>{blockedTerms.length === 1 ? "blocked term" : "blocked terms"}</span>
          </div>
          <div className="confirm-warning" role="alert">
            <Lock size={20} />
            <p>
              <strong>Kids Mode cannot be reverted.</strong> There is no PIN, setting, or hidden exit.
              To turn it off, OpenStream must be uninstalled — removing its app data — and set up again from the beginning.
            </p>
          </div>
          <label className="check-row confirm-check">
            <input data-focusable type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />
            <span>I understand Kids Mode is permanent for this installation.</span>
          </label>
          <footer className="onboarding-actions">
            <button
              className="action-button primary"
              data-focusable
              disabled={!acknowledged}
              onClick={() => completeOnboarding("kids", createKidsPolicy(capId, blockedTerms))}
            >
              <Lock size={19} />Enable Kids Mode permanently
            </button>
          </footer>
        </section>
      ) : null}
    </main>
  );
}
