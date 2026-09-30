import { useNavigate } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Icon } from "@/components/ui/icon";
import { SearchField } from "@/components/ui/search-field";
import { NEARBY_NEIGHBORHOODS, RECENT_NEIGHBORHOODS } from "@/data/community";
import { hasCommunity, useActiveCommunity } from "@/stores/community";
import styles from "@/pages/neighbors/NeighborhoodsPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 동네 추가 — 검색 · 최근 설정 · 현재 위치 기준 근처 동네 */
export function NeighborhoodsPage() {
  const navigate = useNavigate();
  const { toast } = useUi();
  const activeName = useActiveCommunity();

  const pick = (name: string) => {
    if (hasCommunity(name)) {
      toast("이미 설정된 동네예요");
      return;
    }
    navigate(`/verify?name=${encodeURIComponent(name)}&flow=add`);
  };

  const row = (name: string, sub: string) => (
    <button key={name} type="button" className={c("nbhd-row")} onClick={() => pick(name)}>
      <span className={c("nbhd-row-icon")}>
        <Icon name="pin" size={20} />
      </span>
      <span className={c("nbhd-row-body")}>
        <span className={c("t-body nbhd-row-name")}>{name}</span>
        <span className={c("t-caption nbhd-muted")}>{sub}</span>
      </span>
      <Icon name="chevronR" size={18} />
    </button>
  );

  return (
    <div className={c("nbhd page-window")}>
      <PageHeader title="동네 추가" />
      <p className={c("t-body nbhd-muted nbhd-desc")}>
        추가할 동네를 골라 주세요. 해당 동네에서 위치 인증을 마치면 거래할 수 있어요.
      </p>

      <SearchField placeholder="동·읍·면, 단지 이름으로 검색" />

      <button
        type="button"
        className={c("nbhd-locate t-label")}
        onClick={() => toast("현재 위치 기준으로 근처 동네를 보여드리고 있어요")}
      >
        <Icon name="pin" size={18} />
        현재 위치로 동네 찾기
      </button>

      <section className={c("nbhd-section")}>
        <p className={c("t-label nbhd-muted nbhd-section-label")}>최근 설정한 동네</p>
        <div className={c("nbhd-card")}>
          {RECENT_NEIGHBORHOODS.map((n) => row(n.name, n.region))}
        </div>
      </section>

      <section className={c("nbhd-section")}>
        <p className={c("t-label nbhd-muted nbhd-section-label")}>근처 동네 · 현재 위치({activeName}) 기준</p>
        <div className={c("nbhd-card")}>
          {NEARBY_NEIGHBORHOODS.map((n) => row(n.name, `${n.region} · 이웃 ${n.neighbors}명`))}
        </div>
      </section>
    </div>
  );
}
