import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { ApiError } from "@/api/client";
import { type ApiMembership, getMyProfile } from "@/api/members";
import styles from "@/components/custom/community/CommunitySheet.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 내 동네 최대 개수. 2개 지원(목록·전환·삭제 API) 전까지 서버는 활성 동네 1개 */
const MAX_COMMUNITIES = 2;

type Mine =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; membership: ApiMembership | null };

type CommunitySheetProps = {
  onClose: () => void;
};

function untilLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getMonth() + 1}/${at.getDate()}까지`;
}

/** 내 동네 설정 바텀시트 — 현재 유효 소속 동네(A_005 activeMembership) · 추가 */
export function CommunitySheet({ onClose }: CommunitySheetProps) {
  const navigate = useNavigate();
  const [mine, setMine] = useState<Mine>({ state: "loading" });

  useEffect(() => {
    let alive = true;
    getMyProfile()
      .then((profile) => alive && setMine({ state: "ok", membership: profile.activeMembership ?? null }))
      .catch((e: unknown) => {
        if (!alive) return;
        setMine({ state: "error", message: e instanceof ApiError ? e.message : "내 동네를 불러오지 못했어요." });
      });
    return () => {
      alive = false;
    };
  }, []);

  const go = (to: string) => {
    onClose();
    navigate(to);
  };
  const membership = mine.state === "ok" ? mine.membership : null;

  return (
    <div className={c("comm-overlay")}>
      <div className={c("comm-scrim")} aria-hidden="true" onClick={onClose} />
      <div className={c("comm-sheet")} role="dialog" aria-label="내 동네 설정">
        <div className={c("comm-grab")} aria-hidden="true" />
        <div className={c("comm-head")}>
          <h2 className={c("t-title")}>내 동네 설정</h2>
          <button type="button" className={c("comm-close")} aria-label="닫기" onClick={onClose}>
            <Icon name="x" size={18} />
          </button>
        </div>
        <p className={c("t-caption comm-muted comm-note")}>
          동네는 최대 {MAX_COMMUNITIES}개까지 설정할 수 있어요. 위치 인증을 마친 동네에서만 거래해요.
        </p>

        {mine.state === "loading" ? (
          <p className={c("t-caption comm-muted")}>내 동네를 불러오는 중…</p>
        ) : mine.state === "error" ? (
          <p className={c("t-caption comm-muted")}>{mine.message}</p>
        ) : membership ? (
          <div className={c("comm-list")}>
            <div className={c("comm-row comm-row--on")}>
              <button type="button" className={c("comm-pick")} onClick={onClose}>
                <Icon name="pin" size={18} />
                <span className={c("comm-body")}>
                  {membership.communityName}
                  <span className={c("t-caption comm-muted")}>
                    동네 인증됨 · {untilLabel(membership.verificationExpiresAt)}
                  </span>
                </span>
              </button>
            </div>
          </div>
        ) : (
          // 인증 만료도 여기 — activeMembership은 유효 소속일 때만 온다
          <p className={c("t-caption comm-muted")}>인증된 동네가 없어요. 동네를 찾아 위치 인증을 해 주세요.</p>
        )}

        <Button variant="secondary" block icon="plus" onClick={() => go("/neighborhoods")}>
          동네 추가
        </Button>
      </div>
    </div>
  );
}
