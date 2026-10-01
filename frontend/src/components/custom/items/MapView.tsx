import { MAP_PINS } from "@/data/items";
import styles from "@/components/custom/items/MapView.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type MapViewProps = {
  selected?: string;
  height?: number;
  onSelect?: (id: string) => void;
};

/** 단지 도식 지도 자리 — 실제 구현은 카카오맵 SDK (DESIGN_SYSTEM.md §2) */
export function MapView({ selected, height = 360, onSelect }: MapViewProps) {
  return (
    <div className={c("map-view")} style={{ height }}>
      <svg viewBox="0 0 400 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <rect className={c("map-block")} x="20" y="20" width="110" height="90" rx="10" />
        <rect className={c("map-block")} x="160" y="20" width="90" height="60" rx="10" />
        <rect className={c("map-block")} x="280" y="20" width="100" height="110" rx="10" />
        <rect className={c("map-park")} x="160" y="100" width="90" height="50" rx="25" />
        <rect className={c("map-block")} x="20" y="140" width="110" height="60" rx="10" />
        <rect className={c("map-block")} x="280" y="160" width="100" height="60" rx="10" />
        <rect className={c("map-block")} x="20" y="230" width="150" height="60" rx="10" />
        <rect className={c("map-block")} x="200" y="240" width="180" height="50" rx="10" />
        <path className={c("map-road")} d="M0 125H400M145 0V300M265 0V300M0 218H400" />
      </svg>
      <span className={c("map-note t-micro")}>지도 자리 · 실제는 카카오맵</span>
      {MAP_PINS.map((pin) => (
        <button
          key={pin.id}
          type="button"
          className={c("map-pin t-caption")}
          aria-pressed={selected === pin.id ? "true" : "false"}
          style={{ left: `${pin.x}%`, top: `${pin.y}%` }}
          aria-label={`${pin.label} 물건 ${pin.n}개`}
          onClick={() => onSelect?.(pin.id)}
        >
          <span className={c("map-pin-count t-micro")}>{pin.n}</span>
          {pin.label}
        </button>
      ))}
    </div>
  );
}
