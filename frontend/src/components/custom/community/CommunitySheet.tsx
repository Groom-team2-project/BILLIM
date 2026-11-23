import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { ApiError } from "@/api/client";
import { type ApiMyCommunities, type ApiSavedCommunity, getMyCommunities, removeMyCommunity, selectMyCommunity } from "@/api/communities";
import { useUi } from "@/components/overlay/UiContext";
import { ErrorState } from "@/components/ui/state";
import styles from "@/components/custom/community/CommunitySheet.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

const MAX_COMMUNITIES = 2;

type Mine =
  | { state: "loading" }
  | { state: "error"; message: string }
  | { state: "ok"; data: ApiMyCommunities };

type CommunitySheetProps = {
  onClose: () => void;
  onCurrentChange: () => void;
};

const expiryFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", month: "numeric", day: "numeric",
});

function untilLabel(iso: string): string {
  return `${expiryFormatter.format(new Date(iso))}까지`;
}

export function CommunitySheet({ onClose, onCurrentChange }: CommunitySheetProps) {
  const navigate = useNavigate();
  const { confirm, toast } = useUi();
  const [mine, setMine] = useState<Mine>({ state: "loading" });
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    getMyCommunities()
      .then((data) => alive && setMine({ state: "ok", data }))
      .catch((e: unknown) => {
        if (!alive) return;
        setMine({ state: "error", message: e instanceof ApiError ? e.message : "내 동네를 불러오지 못했어요." });
      });
    return () => {
      alive = false;
    };
  }, [retry]);

  const go = (to: string) => {
    onClose();
    navigate(to);
  };
  const saved = mine.state === "ok" ? mine.data : null;
  const full = saved !== null && saved.items.length >= MAX_COMMUNITIES;

  const reload = () => {
    setMine({ state: "loading" });
    setRetry((n) => n + 1);
  };

  const update = async (community: ApiSavedCommunity, action: "select" | "remove") => {
    if (!saved || busy) return;
    setBusy(true);
    try {
      const data = await (action === "select" ? selectMyCommunity : removeMyCommunity)(community.communityId);
      setMine({ state: "ok", data });
      if (data.currentCommunityId !== saved.currentCommunityId) onCurrentChange();
      toast(action === "select" ? "현재 동네를 변경했어요." : "저장 동네에서 삭제했어요.");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "동네 설정을 변경하지 못했어요.");
      if (e instanceof ApiError && (e.status === 404 || e.status === 409)) reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={c("comm-overlay")}>
      <div className={c("comm-scrim")} aria-hidden="true" onClick={busy ? undefined : onClose} />
      <div className={c("comm-sheet")} role="dialog" aria-label="내 동네 설정" aria-busy={busy || mine.state === "loading"}>
        <div className={c("comm-grab")} aria-hidden="true" />
        <div className={c("comm-head")}>
          <h2 className={c("t-title")}>내 동네 설정</h2>
          <button type="button" className={c("comm-close")} aria-label="닫기" disabled={busy} onClick={onClose}>
            <Icon name="x" size={18} />
          </button>
        </div>
        <p className={c("t-caption comm-muted comm-note")}>
          동네는 최대 {MAX_COMMUNITIES}개까지 설정할 수 있어요. 위치 인증을 마친 동네에서만 거래해요.
        </p>

        {mine.state === "loading" ? (
          <p className={c("t-caption comm-muted")} role="status">내 동네를 불러오는 중…</p>
        ) : mine.state === "error" ? (
          <ErrorState title="내 동네를 불러오지 못했어요" onRetry={reload}>{mine.message}</ErrorState>
        ) : mine.data.items.length ? (
          <div className={c("comm-list")}>
            {mine.data.items.map((community) => (
              <div key={community.communityId} className={c(`comm-row${community.isCurrent ? " comm-row--on" : ""}`)}>
                <button type="button" className={c("comm-pick")} aria-pressed={community.isCurrent}
                  disabled={busy || community.isCurrent} onClick={() => void update(community, "select")}>
                  <Icon name="pin" size={18} />
                  <span className={c("comm-body")}>
                    {community.communityName}
                    <span className={c("t-caption comm-muted")}>
                      {community.isCurrent ? "현재 동네 · " : ""}
                      {community.isValid ? `동네 인증됨 · ${untilLabel(community.verificationExpiresAt)}` : "인증 만료"}
                    </span>
                  </span>
                </button>
                <div className={c("comm-actions")}>
                  {!community.isValid && (
                    <Button variant="secondary" size="sm" disabled={busy}
                      onClick={() => go(`/verify?communityId=${encodeURIComponent(community.communityId)}&flow=add`)}>
                      재인증
                    </Button>
                  )}
                  {mine.data.items.length > 1 && (
                    <Button variant="ghost" size="sm" disabled={busy} onClick={() => confirm({
                      title: `${community.communityName} 동네를 삭제할까요?`,
                      body: "다시 추가하려면 위치 인증이 필요해요.",
                      confirm: "삭제",
                      danger: true,
                      onConfirm: () => void update(community, "remove"),
                    })}>삭제</Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className={c("t-caption comm-muted")}>인증된 동네가 없어요. 동네를 찾아 위치 인증을 해 주세요.</p>
        )}

        {full && <p className={c("t-caption comm-muted comm-note")}>다른 동네를 추가하려면 저장한 동네 하나를 먼저 삭제해 주세요.</p>}
        <Button variant="secondary" block icon="plus" disabled={busy || !saved || full} onClick={() => go("/neighborhoods")}>
          동네 추가
        </Button>
      </div>
    </div>
  );
}
