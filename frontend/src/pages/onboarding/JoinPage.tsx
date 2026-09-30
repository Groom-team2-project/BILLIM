import { Link } from "react-router-dom";
import { Icon } from "@/components/ui/icon";
import { PageHeader } from "@/components/layout/header";
import { SearchField } from "@/components/ui/search-field";
import { Alert } from "@/components/ui/alert";
import styles from "@/pages/onboarding/JoinPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 커뮤니티 후보 목데이터 — 운영 seed 기준 */
const CANDIDATES = [
  { name: "새솔마을 5단지", desc: "대전 유성구 · 이웃 128명" },
  { name: "새솔마을 4단지", desc: "대전 유성구 · 이웃 86명" },
  { name: "도안신도시 7단지", desc: "대전 서구 · 이웃 152명" },
];

export function JoinPage() {
  return (
    <main className={c("join")}>
      <PageHeader title="우리 동네 찾기" backTo="/login" standalone />
      <div className={c("join-body")}>
        <div>
          <h1 className={c("t-title-lg join-title")}>어느 동네에 사세요?</h1>
          <p className={c("t-body join-muted join-desc")}>
            실제 사는 동네(단지)에서만 이웃과 물건을 주고받아요. 먼저 우리 동네를 찾아 주세요.
          </p>
        </div>

        <SearchField placeholder="동네·단지 이름으로 검색" />

        <Link to="/verify" className={c("join-locate t-label")}>
          <Icon name="pin" size={18} />
          현재 위치로 우리 동네 찾기
        </Link>

        <p className={c("t-label join-results-label")}>검색 결과</p>
        <div className={c("join-card")}>
          {CANDIDATES.map((cand) => (
            <Link key={cand.name} to="/verify" className={c("join-candidate")}>
              <span className={c("join-candidate-icon")}>
                <Icon name="pin" size={20} />
              </span>
              <span className={c("join-candidate-body")}>
                <span className={c("t-body join-candidate-name")}>{cand.name}</span>
                <span className={c("t-caption join-muted")}>{cand.desc}</span>
              </span>
              <Icon name="chevronR" size={18} />
            </Link>
          ))}
        </div>

        <Alert tone="info" title="동네는 실제 사는 곳으로">
          다음 단계에서 위치로 한 번 확인해요. 정확한 위치나 집 주소는 저장하지 않아요.
        </Alert>
      </div>
    </main>
  );
}
