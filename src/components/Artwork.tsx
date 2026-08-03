import type { CSSProperties } from "react";
import mediaSheet from "../assets/media-sheet.png";

export function Artwork({ cell, className = "" }: { cell: [number, number]; className?: string }) {
  const [column, row] = cell;
  return (
    <span
      aria-hidden="true"
      className={`artwork ${className}`}
      style={{
        "--artwork-image": `url(${mediaSheet})`,
        "--artwork-x": `${column * 33.333}%`,
        "--artwork-y": `${row * 50}%`,
      } as CSSProperties}
    />
  );
}
