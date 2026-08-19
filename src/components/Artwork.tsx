import type { CSSProperties } from "react";

export function Artwork({ cell, className = "" }: { cell: [number, number]; className?: string }) {
  const [column, row] = cell;
  const hue = (column * 61 + row * 97 + 198) % 360;
  return (
    <span
      aria-hidden="true"
      className={`artwork ${className}`}
      style={{
        "--artwork-image": `linear-gradient(145deg, hsl(${hue} 38% 28%), hsl(${(hue + 42) % 360} 34% 10%))`,
        "--artwork-x": "0%",
        "--artwork-y": "0%",
      } as CSSProperties}
    />
  );
}
