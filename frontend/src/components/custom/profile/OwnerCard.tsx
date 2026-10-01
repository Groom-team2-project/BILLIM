import { Link } from "react-router-dom";
import { Avatar } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import type { Trust } from "@/data/items";
import styles from "@/components/custom/profile/OwnerCard.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 점수 대신 실제 거래 기록 문장 (DESIGN_SYSTEM.md P5) */
export function TrustLines({ trust }: { trust: Trust }) {
  return (
    <ul className={c("trust-lines t-caption")}>
      {trust.total > 0 ? (
        <li>
          <Icon name="check" size={16} />
          <span>
            반납 약속 <b>{trust.kept}/{trust.total}</b> 지킴
            {trust.late ? ` · ${trust.late}` : ""}
          </span>
        </li>
      ) : (
        <li>
          <Icon name="info" size={16} />
          <span>아직 빌린 기록이 없어요</span>
        </li>
      )}
      <li>
        <Icon name="swap" size={16} />
        <span>
          거래 완료 <b>{trust.done}회</b>
          {trust.lent != null ? ` (빌려줌 ${trust.lent} · 빌림 ${trust.done - trust.lent})` : ""}
        </span>
      </li>
      {trust.since ? (
        <li>
          <Icon name="calendar" size={16} />
          <span>{trust.since} 가입 · 동네 인증됨</span>
        </li>
      ) : null}
    </ul>
  );
}

type OwnerCardProps = {
  owner: string;
  trust: Trust;
  community?: string;
};

export function OwnerCard({ owner, trust, community = "새솔마을 5단지" }: OwnerCardProps) {
  return (
    <div className={c("owner-card")}>
      <div className={c("owner-card-head")}>
        <Avatar name={owner} size={44} />
        <div className={c("owner-card-name")}>
          <div className={c("t-body-lg owner-card-owner")}>{owner}</div>
          <div className={c("t-caption owner-card-muted")}>{community}</div>
        </div>
        <Link to={`/neighbors/${owner}`} className={c("t-label owner-card-link")}>프로필</Link>
      </div>
      <TrustLines trust={trust} />
      <p className={c("t-caption owner-card-muted owner-card-note")}>
        점수가 아니라 실제 거래 기록으로만 보여줘요.
      </p>
    </div>
  );
}
