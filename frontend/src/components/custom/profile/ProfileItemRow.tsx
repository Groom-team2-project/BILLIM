import type { ReactNode } from "react";
import type { ApiItemSummary } from "@/api/items";
import { Icon } from "@/components/ui/icon";
import { Photo } from "@/components/custom/items/ItemCard";
import { toCategoryId } from "@/utils/itemView";
import styles from "@/components/custom/profile/profileBlocks.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 프로필의 물건 한 줄. actions는 행 끝 자리를 차지한다 — 내 물건의 공개 전환·삭제 */
export function ProfileItemRow({
  item,
  subtitle,
  onOpen,
  actions,
}: {
  item: ApiItemSummary;
  subtitle: string;
  onOpen: () => void;
  actions?: ReactNode;
}) {
  return (
    <button type="button" className={c("profile-row profile-row--item")} onClick={onOpen}>
      <Photo
        cat={toCategoryId(item.category.code)}
        src={item.thumbnailUrl}
        alt={item.title}
        iconSize={18}
        className={c("profile-row-thumb")}
      />
      <span className={c("profile-row-body")}>
        <span className={c("profile-row-title")}>{item.title}</span>
        <span className={c("t-caption profile-muted")}>{subtitle}</span>
      </span>
      {actions ?? <Icon name="chevronR" size={18} />}
    </button>
  );
}
