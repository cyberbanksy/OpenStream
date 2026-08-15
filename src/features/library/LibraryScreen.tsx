import { ArrowDownAZ, Film, FolderOpen, Grid2X2, ListFilter, Tv } from "lucide-react";
import { useMemo, useState } from "react";
import { useApp } from "../../app/AppContext";
import { MediaCard } from "../../components/MediaCard";

type LibraryFilter = "all" | "movie" | "series" | "immersive";

export function LibraryScreen() {
  const { catalog } = useApp();
  const [filter, setFilter] = useState<LibraryFilter>("all");
  const [sort, setSort] = useState<"recent" | "title">("recent");
  const items = useMemo(() => {
    const filtered = catalog.filter((item) => {
      if (filter === "all") return item.id !== "immersive-frontier";
      if (filter === "immersive") return item.candidates.some((candidate) => candidate.format.projection !== "flat");
      return item.kind === filter;
    });
    return sort === "title" ? [...filtered].sort((a, b) => a.title.localeCompare(b.title)) : filtered;
  }, [catalog, filter, sort]);

  return (
    <main className="screen workspace-screen library-screen">
      <aside className="workspace-sidebar" aria-label="Library filters">
        <div className="sidebar-heading"><FolderOpen size={18} /><span>Your Library</span><small>{catalog.length} items</small></div>
        {([
          ["all", Grid2X2, "All media"],
          ["movie", Film, "Movies"],
          ["series", Tv, "Series"],
          ["immersive", ListFilter, "Immersive"],
        ] as const).map(([id, Icon, label], index) => (
          <button
            key={id}
            className={filter === id ? "sidebar-button selected" : "sidebar-button"}
            data-focusable
            data-autofocus={index === 0 ? true : undefined}
            autoFocus={index === 0}
            onClick={() => setFilter(id)}
          >
            <Icon size={20} /><span>{label}</span>
          </button>
        ))}
      </aside>
      <section className="workspace-content">
        <header className="section-header">
          <div><h1>Library</h1><p>{items.length} titles from connected sources</p></div>
          <button className="utility-button" data-focusable onClick={() => setSort((value) => value === "recent" ? "title" : "recent")}>
            <ArrowDownAZ size={18} />{sort === "recent" ? "Recently added" : "Title"}
          </button>
        </header>
        <div className="poster-grid">
          {items.map((item) => <MediaCard key={item.id} item={item} portrait />)}
        </div>
      </section>
    </main>
  );
}
