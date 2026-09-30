import { useRef, useState } from "react";
import { Icon } from "@/components/ui/icon";
import { Photo } from "@/components/custom/items/ItemCard";
import type { CategoryId } from "@/data/items";
import styles from "@/components/custom/items/PhotoSlider.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type PhotoSliderProps = {
  cat: CategoryId;
  /** 사진 장수 (1~5) */
  count: number;
  title: string;
  className?: string;
};

/** 물건 사진 슬라이드 — 스와이프·드래그·화살표. 이미지 API 연동 시 Photo 자리에 실제 사진 */
export function PhotoSlider({ cat, count, title, className }: PhotoSliderProps) {
  const trackRef = useRef<HTMLDivElement>(null);
  const drag = useRef<{ startX: number; startLeft: number } | null>(null);
  const [idx, setIdx] = useState(0);

  const goTo = (i: number) => {
    const el = trackRef.current;
    if (!el) return;
    const next = Math.max(0, Math.min(count - 1, i));
    el.scrollTo({ left: next * el.clientWidth, behavior: "smooth" });
  };

  return (
    <div className={c(`photo-slider${className ? ` ${className}` : ""}`)}>
      <div
        ref={trackRef}
        className={c("photo-slider-track")}
        onScroll={(e) => {
          const el = e.currentTarget;
          setIdx(Math.max(0, Math.min(count - 1, Math.round(el.scrollLeft / el.clientWidth))));
        }}
        onPointerDown={(e) => {
          if (e.pointerType !== "mouse") return;
          drag.current = { startX: e.clientX, startLeft: e.currentTarget.scrollLeft };
          e.currentTarget.setPointerCapture(e.pointerId);
        }}
        onPointerMove={(e) => {
          if (!drag.current) return;
          e.currentTarget.scrollLeft = drag.current.startLeft - (e.clientX - drag.current.startX);
        }}
        onPointerUp={(e) => {
          if (!drag.current) return;
          drag.current = null;
          goTo(Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth));
        }}
      >
        {Array.from({ length: count }, (_, i) => (
          <Photo key={i} cat={cat} iconSize={64} className={c("photo-slide")} alt={`${title} 사진 ${i + 1}/${count}`} />
        ))}
      </div>

      {idx > 0 ? (
        <button type="button" className={c("photo-slider-nav photo-slider-nav--prev")} aria-label="이전 사진" onClick={() => goTo(idx - 1)}>
          <Icon name="chevronL" size={18} />
        </button>
      ) : null}
      {idx < count - 1 ? (
        <button type="button" className={c("photo-slider-nav photo-slider-nav--next")} aria-label="다음 사진" onClick={() => goTo(idx + 1)}>
          <Icon name="chevronR" size={18} />
        </button>
      ) : null}

      <div className={c("photo-slider-dots")} aria-hidden="true">
        {Array.from({ length: count }, (_, i) => (
          <i key={i} className={c(`photo-slider-dot${i === idx ? " photo-slider-dot--on" : ""}`)} />
        ))}
      </div>
      <span className={c("photo-slider-count t-micro")} aria-live="polite">{idx + 1} / {count}</span>
    </div>
  );
}
