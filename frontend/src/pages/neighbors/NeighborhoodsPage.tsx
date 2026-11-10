import { PageHeader } from "@/components/layout/header";
import { CommunityFinder } from "@/components/custom/community/CommunityFinder";
import styles from "@/pages/neighbors/NeighborhoodsPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 동네 추가 — 이름 검색 · 현재 위치 기준 근처 동네 → 위치 인증 */
export function NeighborhoodsPage() {
  return (
    <div className={c("nbhd page-window")}>
      <PageHeader title="동네 추가" />
      <p className={c("t-body nbhd-muted nbhd-desc")}>
        추가할 동네를 골라 주세요. 해당 동네에서 위치 인증을 마치면 거래할 수 있어요.
      </p>

      <CommunityFinder linkTo={(community) => `/verify?communityId=${community.id}&flow=add`} />
    </div>
  );
}
