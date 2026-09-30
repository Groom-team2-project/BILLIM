import { Link, useNavigate, useParams } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Avatar } from "@/components/ui/avatar";
import { Photo } from "@/components/custom/items/ItemCard";
import { MOCK_ITEMS } from "@/data/items";
import { TrustLines } from "@/components/custom/profile/OwnerCard";
import styles from "@/pages/neighbors/NeighborPage.module.css";
import { classes } from "@/utils/classes";
import { PageHeader } from "@/components/layout/header";

const c = classes(styles);

/** 목데이터에 물건이 없는 이웃의 기본 신뢰 지표 — 프로토타입 정우 값 */
const FALLBACK_TRUST = { kept: 12, total: 12, done: 18, lent: 15, since: "2026년 3월" };

export function NeighborPage() {
  const { name: raw } = useParams();
  const name = raw ?? "이웃";
  const { confirm, report, toast } = useUi();
  const navigate = useNavigate();
  const items = MOCK_ITEMS.filter((i) => i.owner === name);
  const trust = items[0]?.trust ?? FALLBACK_TRUST;

  return (
    <div className={c("neighbor page-window")}>
      <PageHeader title="이웃 프로필" />

      <div className={c("neighbor-me")}>
        <Avatar name={name} size={56} />
        <div>
          <div className={c("t-title-lg")}>{name}</div>
          <div className={c("t-caption neighbor-muted")}>
            새솔마을 5단지 · {trust.since ?? "2026년"} 가입 · 동네 인증됨
          </div>
        </div>
      </div>

      <section className={c("neighbor-section")}>
        <p className={c("t-label neighbor-muted neighbor-section-label")}>신뢰 지표</p>
        <div className={c("neighbor-card")}>
          <TrustLines trust={trust} />
        </div>
        <p className={c("t-caption neighbor-muted neighbor-note")}>
          초대 코드는 소속을 인증하지 않아요. 거래 기록으로만 판단해요.
        </p>
      </section>

      <section className={c("neighbor-section")}>
        <p className={c("t-label neighbor-muted neighbor-section-label")}>등록한 물건</p>
        <div className={c("neighbor-card neighbor-card--list")}>
          {items.length === 0 ? (
            <p className={c("t-caption neighbor-muted neighbor-empty")}>등록한 물건이 없어요.</p>
          ) : (
            items.map((i) => (
              <Link key={i.id} to={`/items/${i.id}`} className={c("neighbor-row")}>
                <Photo cat={i.cat} iconSize={18} className={c("neighbor-row-thumb")} />
                <span className={c("neighbor-row-body")}>
                  <span className={c("t-body-lg neighbor-row-title")}>{i.title}</span>
                  <span className={c("t-caption neighbor-muted")}>{i.place}</span>
                </span>
                <Icon name="chevronR" size={16} />
              </Link>
            ))
          )}
        </div>
      </section>

      <div className={c("neighbor-actions")}>
        <button
          type="button"
          className={c("neighbor-report t-label")}
          onClick={() => report({ name, onConfirm: () => toast("신고를 접수했어요") })}
        >
          <Icon name="flag" size={18} />
          신고
        </button>
        <Button
          variant="secondary"
          icon="shield"
          onClick={() =>
            confirm({
              title: `${name} 님을 차단할까요?`,
              body: "차단하면 서로 대화할 수 없고 물건도 볼 수 없어요.",
              confirm: "차단",
              danger: true,
              onConfirm: () => {
                toast("차단했어요");
                navigate("/");
              },
            })
          }
        >
          차단
        </Button>
      </div>
      <p className={c("t-caption neighbor-muted")}>
        신고·차단은 되돌리기 어려운 행동이에요. 차단하면 이 이웃과 더는 대화할 수 없어요.
      </p>
    </div>
  );
}
