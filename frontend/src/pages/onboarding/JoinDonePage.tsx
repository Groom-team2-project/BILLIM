import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { type ApiCommunity, getCommunity } from "@/api/communities";
import styles from "@/pages/onboarding/JoinDonePage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 프로토타입 문구: "2026년 9월 · 새솔마을 5단지 반경 내 확인" */
function verifiedLabel(name: string): string {
  const now = new Date();
  return `${now.getFullYear()}년 ${now.getMonth() + 1}월 · ${name} 반경 내 확인`;
}

export function JoinDonePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const communityId = params.get("communityId");
  const [community, setCommunity] = useState<ApiCommunity | null>(null);

  useEffect(() => {
    if (!communityId) return;
    let alive = true;
    // 인증은 이미 끝났다. 이름 표시용 조회라 실패해도 화면을 막지 않는다
    getCommunity(communityId)
      .then((found) => alive && setCommunity(found))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [communityId]);

  const name = community?.name;

  return (
    <main className={c("joindone")}>
      <div className={c("joindone-body")}>
        <span className={c("joindone-mark")}>
          <Icon name="check" size={32} />
        </span>
        <h1 className={c("t-title-lg joindone-title")}>
          {name ? `${name} 이웃이 되었어요` : "이웃이 되었어요"}
        </h1>
        <p className={c("t-body joindone-muted joindone-desc")}>
          동네 인증을 마쳤어요. 이제 이웃의 물건을 빌리고, 내 물건을 빌려줄 수 있어요.
        </p>
        <div className={c("joindone-card")}>
          <span className={c("joindone-card-icon")}>
            <Icon name="shield" size={20} />
          </span>
          <span className={c("joindone-card-body")}>
            <span className={c("t-body joindone-card-title")}>동네 인증 완료</span>
            <span className={c("t-caption joindone-muted")}>{name ? verifiedLabel(name) : "반경 내 확인"}</span>
          </span>
        </div>
      </div>
      <div className={c("joindone-actions")}>
        <Button size="lg" block onClick={() => navigate("/")}>홈으로</Button>
      </div>
    </main>
  );
}
