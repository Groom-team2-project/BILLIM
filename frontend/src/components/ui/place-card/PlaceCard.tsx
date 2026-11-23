import { Icon } from "@/components/ui/icon";
import styles from "@/components/ui/place-card/PlaceCard.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type PlaceCardProps = {
  place: string;
  detail?: string;
  distance?: string;
};

/** 공용 거래 장소 카드 — 집 주소 비공유 안내 포함 (DESIGN_SYSTEM.md §6) */
export function PlaceCard({ place, detail, distance }: PlaceCardProps) {
  return (
    <div className={c("place-card")}>
      <span className={c("place-card-pin")}>
        <Icon name="pin" size={18} />
      </span>
      <div className={c("place-card-body")}>
        <span className={c("t-micro place-card-muted")}>거래 장소 · 공용 장소</span>
        <strong className={c("t-body-lg place-card-name")}>{place}</strong>
        {detail ? <span className={c("t-caption place-card-muted")}>{detail}</span> : null}
        <span className={c("t-caption place-card-muted place-card-lock")}>
          <Icon name="lock" size={14} />
          집 주소는 서로 공유되지 않아요
        </span>
      </div>
      {distance ? <span className={c("t-label place-card-distance")}>{distance}</span> : null}
    </div>
  );
}
