import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { Meta } from "@/components/ui/meta";
import { CATEGORY_MAP } from "@/data/items";
import type { CategoryId, Item } from "@/data/items";
import styles from "@/components/custom/items/ItemCard.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 사진 자리 — 사진이 없으면 카테고리 아이콘 (DESIGN_SYSTEM.md §6) */
export function Photo({
  cat,
  count,
  iconSize = 36,
  className,
  alt,
  src,
}: {
  cat: CategoryId;
  count?: string | number;
  iconSize?: number;
  className?: string;
  alt?: string;
  /** 실제 사진 주소 — 없으면 카테고리 아이콘 */
  src?: string;
}) {
  const category = CATEGORY_MAP[cat];
  return (
    <div className={c(`item-photo${className ? ` ${className}` : ""}`)} role="img" aria-label={alt ?? `${category.label} 사진`}>
      {src ? (
        <img src={src} alt="" className={c("item-photo-img")} loading="lazy" draggable={false} />
      ) : (
        <Icon name={category.icon} size={iconSize} strokeWidth={1.4} />
      )}
      {count != null ? <span className={c("item-photo-count")}>{count}</span> : null}
    </div>
  );
}

function Avail({ item, range }: { item: Item; range?: boolean }) {
  if (item.avail === "partial") {
    return (
      <span className={c("item-avail item-avail--partial")}>
        <Icon name="clock" size={13} />
        일부 날짜 가능 · {item.availText}
      </span>
    );
  }
  if (item.avail === "none") {
    return (
      <span className={c("item-avail item-avail--none")}>
        <Icon name="x" size={13} />
        {item.availText ?? "대여 불가"}
      </span>
    );
  }
  return (
    <span className={c("item-avail")}>
      <Icon name="check" size={13} />
      {range ? "선택한 날짜 가능" : "지금 요청 가능"}
    </span>
  );
}

type ItemCardProps = {
  item: Item;
  layout?: "grid" | "row";
  range?: boolean;
};

export function ItemCard({ item, layout = "grid", range }: ItemCardProps) {
  const to = `/items/${item.id}`;
  if (layout === "row") {
    // 신뢰 지표(C_008)가 없는 API 데이터는 소유자·등록 시각만 표시
    const trustLine = !item.trust
      ? null
      : item.trust.total
        ? `반납 약속 ${item.trust.kept}/${item.trust.total}`
        : "첫 대여를 기다려요";
    const count = item.photos && item.photos > 1 ? item.photos : undefined;
    return (
      <Link to={to} className={c("item-card item-card--row")}>
        <Photo cat={item.cat} count={count} iconSize={30} src={item.thumbnailUrl} />
        <div className={c("item-card-body")}>
          <span className={c("item-card-title")}>{item.title}</span>
          <Meta icon="pin">{item.dist ? `${item.place} · ${item.dist}` : item.place}</Meta>
          <Avail item={item} range={range} />
          <span className={c("item-card-trust t-caption")}>
            {[item.owner, trustLine, item.ago].filter(Boolean).join(" · ")}
          </span>
        </div>
      </Link>
    );
  }
  return (
    <Link to={to} className={c("item-card")}>
      <Photo cat={item.cat} count={item.photos && item.photos > 1 ? item.photos : undefined} src={item.thumbnailUrl} />
      <div className={c("item-card-body")}>
        <span className={c("item-card-title")}>{item.title}</span>
        <Meta icon="pin">{item.place}</Meta>
        <Avail item={item} range={range} />
      </div>
    </Link>
  );
}
