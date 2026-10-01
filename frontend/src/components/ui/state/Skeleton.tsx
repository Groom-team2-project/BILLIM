import type { CSSProperties, ReactNode } from "react";
import styles from "@/components/ui/state/State.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type SkeletonProps = {
  w?: number | string;
  h?: number | string;
  /** border-radius */
  r?: number | string;
  style?: CSSProperties;
};

/** 뼈대 블록 — 로딩 자리 표시 (프로토타입 nb-skel) */
export function Skeleton({ w = "100%", h = 14, r, style }: SkeletonProps) {
  return <div className={c("skel")} style={{ width: w, height: h, borderRadius: r, ...style }} aria-hidden="true" />;
}

/** 로딩 래퍼 — 스크린리더에 상태를 알리고 뼈대를 감쌈 */
export function Loading({ label = "불러오는 중", children }: { label?: string; children: ReactNode }) {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}

/** 물건 카드 뼈대 — grid/row 레이아웃 (프로토타입 SkeletonItem) */
export function SkeletonItem({ layout = "grid" }: { layout?: "grid" | "row" }) {
  if (layout === "row") {
    return (
      <div className={c("skel-row")}>
        <Skeleton w={96} h={96} r={16} />
        <div className={c("skel-row-body")}>
          <Skeleton w="70%" h={16} />
          <Skeleton w="85%" />
          <Skeleton w="40%" />
        </div>
      </div>
    );
  }
  return (
    <div className={c("skel-grid")}>
      <Skeleton h="auto" r={16} style={{ aspectRatio: "1 / 1" }} />
      <Skeleton w="80%" h={16} />
      <Skeleton w="60%" />
    </div>
  );
}
