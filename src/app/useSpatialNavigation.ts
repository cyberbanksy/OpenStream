import { useEffect } from "react";

type Direction = "left" | "right" | "up" | "down";

function scoreCandidate(origin: DOMRect, candidate: DOMRect, direction: Direction) {
  const originX = origin.left + origin.width / 2;
  const originY = origin.top + origin.height / 2;
  const candidateX = candidate.left + candidate.width / 2;
  const candidateY = candidate.top + candidate.height / 2;
  const dx = candidateX - originX;
  const dy = candidateY - originY;

  const isForward =
    (direction === "left" && dx < -1) ||
    (direction === "right" && dx > 1) ||
    (direction === "up" && dy < -1) ||
    (direction === "down" && dy > 1);
  if (!isForward) return Number.POSITIVE_INFINITY;

  const primary = direction === "left" || direction === "right" ? Math.abs(dx) : Math.abs(dy);
  const cross = direction === "left" || direction === "right" ? Math.abs(dy) : Math.abs(dx);
  return primary + cross * 2.4;
}

function revealInScrollableAncestor(element: HTMLElement) {
  let parent = element.parentElement;
  while (parent && parent !== document.body) {
    const style = getComputedStyle(parent);
    const scrollsX = /(auto|scroll)/.test(style.overflowX) && parent.scrollWidth > parent.clientWidth;
    const scrollsY = /(auto|scroll)/.test(style.overflowY) && parent.scrollHeight > parent.clientHeight;
    if (scrollsX || scrollsY) {
      const itemRect = element.getBoundingClientRect();
      const parentRect = parent.getBoundingClientRect();
      if (scrollsX && itemRect.left < parentRect.left) parent.scrollLeft -= parentRect.left - itemRect.left + 16;
      else if (scrollsX && itemRect.right > parentRect.right) parent.scrollLeft += itemRect.right - parentRect.right + 16;
      if (scrollsY && itemRect.top < parentRect.top) parent.scrollTop -= parentRect.top - itemRect.top + 16;
      else if (scrollsY && itemRect.bottom > parentRect.bottom) parent.scrollTop += itemRect.bottom - parentRect.bottom + 16;
      return;
    }
    parent = parent.parentElement;
  }
}

export function useSpatialNavigation() {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      const directionByKey: Record<string, Direction | undefined> = {
        ArrowLeft: "left",
        ArrowRight: "right",
        ArrowUp: "up",
        ArrowDown: "down",
      };
      const direction = directionByKey[event.key];
      if (!direction) return;

      // Text fields keep left/right for the caret; up/down still move focus so
      // a remote or keyboard can always leave the field.
      const editsText = target.matches("input:not([type=checkbox]):not([type=radio]):not([type=range]), textarea, select") || target.isContentEditable;
      if (editsText && (direction === "left" || direction === "right")) return;

      const focusScope = document.querySelector<HTMLElement>("[role='dialog'][aria-modal='true']") ?? document;
      const focusables = [...focusScope.querySelectorAll<HTMLElement>("[data-focusable]:not([disabled])")]
        .filter((item) => item.offsetParent !== null);
      if (focusables.length === 0) return;

      const active = document.activeElement instanceof HTMLElement && focusables.includes(document.activeElement)
        ? document.activeElement
        : focusables[0];
      const origin = active.getBoundingClientRect();
      let best: HTMLElement | undefined;
      let bestScore = Number.POSITIVE_INFINITY;

      for (const item of focusables) {
        if (item === active) continue;
        const score = scoreCandidate(origin, item.getBoundingClientRect(), direction);
        if (score < bestScore) {
          best = item;
          bestScore = score;
        }
      }

      if (best) {
        event.preventDefault();
        best.focus({ preventScroll: true });
        revealInScrollableAncestor(best);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);
}
