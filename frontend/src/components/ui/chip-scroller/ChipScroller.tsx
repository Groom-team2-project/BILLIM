import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/chip-scroller/ChipScroller.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 드래그로 밀리는 것으로 판단하는 최소 이동 거리 */
const DRAG_THRESHOLD = 4;

type ChipScrollerProps = {
  label: string;
  children: ReactNode;
};

/** 가로 칩 줄 — 스크롤바 숨김 · 드래그 스크롤 · 넘칠 때 좌우 이동 버튼 */
export function ChipScroller({ label, children }: ChipScrollerProps) {
  const trackRef = useRef<HTMLDivElement>(null);
  const drag = useRef<{ startX: number; startLeft: number; moved: boolean } | null>(null);
  const [edges, setEdges] = useState({ left: false, right: false });

  const sync = useCallback(() => {
    const el = trackRef.current;
    if (!el) return;
    const max = el.scrollWidth - el.clientWidth;
    setEdges({ left: el.scrollLeft > 1, right: el.scrollLeft < max - 1 });
  }, []);

  useEffect(() => {
    const el = trackRef.current;
    if (!el) return;
    sync();
    const ro = new ResizeObserver(sync);
    ro.observe(el);
    return () => ro.disconnect();
  }, [sync]);

  const scrollBy = (dir: 1 | -1) => {
    const el = trackRef.current;
    if (!el) return;
    el.scrollBy({ left: dir * el.clientWidth * 0.8, behavior: "smooth" });
  };

  return (
    <div className={c(`chipscroll${edges.left ? " chipscroll--left" : ""}${edges.right ? " chipscroll--right" : ""}`)}>
      {edges.left ? (
        <button type="button" className={c("chipscroll-nav chipscroll-nav--prev")} aria-label="이전 항목" onClick={() => scrollBy(-1)}>
          <Icon name="chevronL" size={16} />
        </button>
      ) : null}

      <div
        ref={trackRef}
        className={c("chipscroll-track")}
        role="group"
        aria-label={label}
        onScroll={sync}
        onPointerDown={(e) => {
          if (e.pointerType !== "mouse") return;
          drag.current = { startX: e.clientX, startLeft: e.currentTarget.scrollLeft, moved: false };
        }}
        onPointerMove={(e) => {
          const d = drag.current;
          if (!d) return;
          const dx = e.clientX - d.startX;
          if (!d.moved && Math.abs(dx) < DRAG_THRESHOLD) return;
          if (!d.moved) {
            d.moved = true;
            e.currentTarget.setPointerCapture(e.pointerId);
          }
          e.currentTarget.scrollLeft = d.startLeft - dx;
        }}
        onPointerUp={() => {
          /* 드래그 직후의 클릭은 아래 onClickCapture에서 무시 */
          setTimeout(() => {
            drag.current = null;
          }, 0);
        }}
        onPointerLeave={() => {
          drag.current = null;
        }}
        onClickCapture={(e) => {
          if (drag.current?.moved) {
            e.preventDefault();
            e.stopPropagation();
          }
        }}
      >
        {children}
      </div>

      {edges.right ? (
        <button type="button" className={c("chipscroll-nav chipscroll-nav--next")} aria-label="다음 항목" onClick={() => scrollBy(1)}>
          <Icon name="chevronR" size={16} />
        </button>
      ) : null}
    </div>
  );
}
