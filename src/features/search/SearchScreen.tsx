import { Search, X } from "lucide-react";
import { useMemo, useState } from "react";
import { useApp } from "../../app/AppContext";
import { MediaCard } from "../../components/MediaCard";

export function SearchScreen() {
  const { catalog, navigate } = useApp();
  const [query, setQuery] = useState("");
  const results = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return normalized ? catalog.filter((item) => `${item.title} ${item.synopsis}`.toLowerCase().includes(normalized)) : catalog.slice(0, 8);
  }, [catalog, query]);

  return (
    <main className="screen search-screen">
      <header className="search-header"><Search size={29} /><input data-focusable aria-label="Search all connected media" autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search titles across every connected source" /><button data-focusable aria-label="Close search" onClick={() => navigate("discover")}><X size={23} /></button></header>
      <section className="search-results"><div className="section-header"><div><h1>{query ? `Results for “${query}”` : "Browse everything"}</h1><p>{results.length} matches across connected libraries and add-ons</p></div></div><div className="poster-grid">{results.map((item) => <MediaCard key={item.id} item={item} portrait />)}</div></section>
    </main>
  );
}
