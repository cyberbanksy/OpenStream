import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./styles.css";

if (import.meta.env.DEV && new URLSearchParams(window.location.search).get("qa") === "axe") {
  void import("./qa/accessibility").then(({ installAccessibilityQa }) => installAccessibilityQa());
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
