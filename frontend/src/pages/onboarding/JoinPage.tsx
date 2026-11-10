import { PageHeader } from "@/components/layout/header";
import { Alert } from "@/components/ui/alert";
import { CommunityFinder } from "@/components/custom/community/CommunityFinder";
import styles from "@/pages/onboarding/JoinPage.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

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

        <CommunityFinder linkTo={(community) => `/verify?communityId=${community.id}`} />

        <Alert tone="info" title="동네는 실제 사는 곳으로">
          다음 단계에서 위치로 한 번 확인해요. 정확한 위치나 집 주소는 저장하지 않아요.
        </Alert>
      </div>
    </main>
  );
}
