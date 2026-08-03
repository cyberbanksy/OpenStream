import type { MediaItem } from "../domain/media";
import { MediaCard } from "./MediaCard";

export function MediaRail({ title, items, autoFocus = false }: { title: string; items: MediaItem[]; autoFocus?: boolean }) {
  return (
    <section className="media-rail" aria-labelledby={`rail-${title.replace(/\s/g, "-").toLowerCase()}`}>
      <h2 id={`rail-${title.replace(/\s/g, "-").toLowerCase()}`}>{title}</h2>
      <div className="rail-track">
        {items.map((item, index) => <MediaCard key={item.id} item={item} autoFocus={autoFocus && index === 0} />)}
      </div>
    </section>
  );
}
