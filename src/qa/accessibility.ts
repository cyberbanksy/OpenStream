import axe from "axe-core";

type AxeQaViolation = {
  id: string;
  impact: string | null;
  help: string;
  targets: string[];
  summaries: Array<string | null | undefined>;
};

declare global {
  interface Window {
    __OPENSTREAM_RUN_AXE__?: () => Promise<AxeQaViolation[]>;
  }
}

export function installAccessibilityQa() {
  const marker = document.createElement("script");
  marker.id = "openstream-axe-results";
  marker.type = "application/json";
  marker.textContent = JSON.stringify({ status: "running", violations: [] });
  document.head.append(marker);

  window.__OPENSTREAM_RUN_AXE__ = async () => {
    const result = await axe.run(document, {
      runOnly: {
        type: "tag",
        values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"],
      },
    });
    return result.violations.map((violation) => ({
      id: violation.id,
      impact: violation.impact ?? null,
      help: violation.help,
      targets: violation.nodes.map((node) => node.target.join(" ")),
      summaries: violation.nodes.map((node) => node.failureSummary),
    }));
  };

  void window.__OPENSTREAM_RUN_AXE__()
    .then((violations) => {
      marker.textContent = JSON.stringify({ status: "complete", violations });
    })
    .catch((error: unknown) => {
      marker.textContent = JSON.stringify({
        status: "error",
        error: error instanceof Error ? error.message : String(error),
        violations: [],
      });
    });
}
