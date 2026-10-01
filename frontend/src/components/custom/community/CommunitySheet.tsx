import { useNavigate } from "react-router-dom";
import { useUi } from "@/components/overlay/UiContext";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import {
  MAX_COMMUNITIES,
  removeCommunity,
  setActiveCommunity,
  useActiveCommunity,
  useCommunities,
} from "@/stores/community";
import styles from "@/components/custom/community/CommunitySheet.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

type CommunitySheetProps = {
  onClose: () => void;
};

/** 내 동네 설정 바텀시트 — 최대 2개, 전환·삭제·추가 */
export function CommunitySheet({ onClose }: CommunitySheetProps) {
  const communities = useCommunities();
  const active = useActiveCommunity();
  const { confirm, toast } = useUi();
  const navigate = useNavigate();

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

        <div className={c("comm-list")} role="radiogroup" aria-label="내 동네">
          {communities.map((comm) => (
            <div key={comm.name} className={c(`comm-row${comm.name === active ? " comm-row--on" : ""}`)}>
              <button
                type="button"
                role="radio"
                aria-checked={comm.name === active ? "true" : "false"}
                className={c("comm-pick")}
                onClick={() => {
                  if (comm.name !== active) {
                    setActiveCommunity(comm.name);
                    toast(`${comm.name} 동네로 전환했어요`);
                  }
                  onClose();
                }}
              >
                <span className={c("comm-radio")} />
                <span className={c("comm-body")}>
                  {comm.name}
                  <span className={c("t-caption comm-muted")}>
                    {comm.region} · {comm.verified ? "동네 인증됨" : "인증 필요"}
                  </span>
                </span>
              </button>
              <button
                type="button"
                className={c("comm-del")}
                aria-label={`${comm.name} 삭제`}
                onClick={() => {
                  if (communities.length <= 1) {
                    toast("동네는 최소 1개 있어야 해요");
                    return;
                  }
                  confirm({
                    title: `${comm.name} 동네를 삭제할까요?`,
                    body: "삭제해도 다시 위치 인증을 하면 되돌릴 수 있어요.",
                    confirm: "삭제",
                    danger: true,
                    onConfirm: () => {
                      removeCommunity(comm.name);
                      toast("동네를 삭제했어요");
                    },
                  });
                }}
              >
                <Icon name="x" size={16} />
              </button>
            </div>
          ))}
        </div>

        {communities.length < MAX_COMMUNITIES ? (
          <Button
            variant="secondary"
            block
            icon="plus"
            onClick={() => {
              onClose();
              navigate("/neighborhoods");
            }}
          >
            동네 추가
          </Button>
        ) : null}
      </div>
    </div>
  );
}
