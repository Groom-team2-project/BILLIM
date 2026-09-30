import { useState } from "react";
import { Button } from "@/components/ui/button";
import overlayStyles from "@/components/overlay/overlay.module.css";
import { classes } from "@/utils/classes";

const c = classes(overlayStyles);

/** 제재 조치 5종 — 정책 및 상태.md 기타 정책 */
const ACTIONS = ["경고", "일시정지 7일", "일시정지 30일", "영구정지·탈퇴", "반려(문제없음)"];

type ActionModalProps = {
  name: string;
  onConfirm: (action: string, note: string) => void;
  onClose: () => void;
};

/** 신고 처리 모달 — 조치 선택 + 처리 메모 */
export function ActionModal({ name, onConfirm, onClose }: ActionModalProps) {
  const [action, setAction] = useState<string | null>(null);
  const [note, setNote] = useState("");
  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div className={c("ui-modal ui-modal--wide")} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3 className={c("ui-modal-title")}>{name} 님 신고 처리</h3>
        <p className={c("ui-modal-body")}>조치를 선택하세요. 결과는 신고자와 대상에게 알림으로 전달돼요.</p>
        <div className={c("ui-opts")} role="radiogroup" aria-label="제재 조치">
          {ACTIONS.map((a) => (
            <button
              key={a}
              type="button"
              role="radio"
              aria-checked={action === a ? "true" : "false"}
              className={c(`ui-opt${action === a ? " ui-opt--on" : ""}`)}
              onClick={() => setAction(a)}
            >
              <span className={c("ui-opt-radio")} />
              <span>{a}</span>
            </button>
          ))}
        </div>
        <textarea
          className={c("ui-textarea")}
          rows={2}
          placeholder="처리 메모 (선택) — 대상에게 전달할 사유"
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
        <p className={c("t-caption ui-modal-hint")}>영구정지·탈퇴는 되돌리기 어려운 조치예요.</p>
        <div className={c("ui-modal-actions")}>
          <Button variant="secondary" onClick={onClose}>취소</Button>
          <Button
            variant={action === "영구정지·탈퇴" ? "danger" : "primary"}
            disabled={!action}
            onClick={() => {
              if (!action) return;
              onClose();
              onConfirm(action, note.trim());
            }}
          >
            처리 적용
          </Button>
        </div>
      </div>
    </div>
  );
}
