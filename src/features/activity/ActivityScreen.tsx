import { Clock3 } from "lucide-react";

export function ActivityScreen() {
  return (
    <main className="screen workspace-screen activity-screen">
      <section className="workspace-content full-width">
        <header className="section-header">
          <div><h1>Activity</h1><p>Remote requests, imports, and source health</p></div>
          <span className="live-state"><Clock3 size={17} />Ready</span>
        </header>
        <div className="cinema-empty">
          <h2>No activity yet</h2>
          <p>Requests and source operations appear here after you configure them.</p>
        </div>
      </section>
    </main>
  );
}
