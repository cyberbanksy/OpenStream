import { type RefObject, useEffect, useRef } from "react";

const focusableSelector = [
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "a[href]",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

export function useDialogFocusTrap(ref: RefObject<HTMLElement>, onClose: () => void) {
  // Keep the latest onClose in a ref so a new handler identity does not tear
  // down and re-run the trap, which would yank focus while the user types.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;

    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
    const initialTarget =
      dialog.querySelector<HTMLElement>("[data-dialog-autofocus]:not([disabled])") ??
      dialog.querySelector<HTMLElement>(focusableSelector);
    window.requestAnimationFrame(() => initialTarget?.focus({ preventScroll: true }));

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== "Tab") return;

      const focusables = [...dialog.querySelectorAll<HTMLElement>(focusableSelector)]
        .filter((element) => element.offsetParent !== null);
      if (focusables.length === 0) return;

      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus({ preventScroll: true });
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus({ preventScroll: true });
      }
    };

    dialog.addEventListener("keydown", handleKeyDown);
    return () => {
      dialog.removeEventListener("keydown", handleKeyDown);
      previouslyFocused?.focus({ preventScroll: true });
    };
  }, [ref]);
}
