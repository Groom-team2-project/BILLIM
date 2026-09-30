import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import styles from "@/pages/onboarding/JoinDonePage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

export function JoinDonePage() {
  const navigate = useNavigate();
  return (
    <main className={c("joindone")}>
      <div className={c("joindone-body")}>
        <span className={c("joindone-mark")}>
          <Icon name="check" size={32} />
        </span>
        <h1 className={c("t-title-lg joindone-title")}>새솔마을 5단지 이웃이 되었어요</h1>
        <p className={c("t-body joindone-muted joindone-desc")}>
          동네 인증을 마쳤어요. 이제 이웃의 물건을 빌리고, 내 물건을 빌려줄 수 있어요.
        </p>
        <div className={c("joindone-card")}>
          <span className={c("joindone-card-icon")}>
            <Icon name="shield" size={20} />
          </span>
          <span className={c("joindone-card-body")}>
            <span className={c("t-body joindone-card-title")}>동네 인증 완료</span>
            <span className={c("t-caption joindone-muted")}>2026년 9월 · 새솔마을 5단지 반경 내 확인</span>
          </span>
        </div>
      </div>
      <div className={c("joindone-actions")}>
        <Button size="lg" block onClick={() => navigate("/")}>홈으로</Button>
      </div>
    </main>
  );
}
