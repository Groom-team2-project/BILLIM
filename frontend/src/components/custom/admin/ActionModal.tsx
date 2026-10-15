import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { ReportActionType } from "@/api/reports";
import overlayStyles from "@/components/overlay/overlay.module.css";
import { classes } from "@/utils/classes";

const c = classes(overlayStyles);

/** 제재 조치 5종 — 화면 문구와 서버 enum을 한곳에 둔다 (정책 및 상태 5, API 명세 ResolveReport) */
const ACTIONS: { value: ReportActionType; label: string }[] = [
  { value: "WARN", label: "경고" },
  { value: "SUSPEND_7D", label: "일시정지 7일" },
  { value: "SUSPEND_30D", label: "일시정지 30일" },
  { value: "BAN", label: "영구정지·탈퇴" },
  { value: "DISMISS", label: "반려(문제없음)" },
];

type ActionModalProps = {
  name: string;
  onConfirm: (action: ReportActionType, label: string, note: string) => void;
  onClose: () => void;
};

/** 신고 처리 모달 — 조치 선택 + 처리 메모 */
export function ActionModal({ name, onConfirm, onClose }: ActionModalProps) {
  const [action, setAction] = useState<ReportActionType | null>(null);
  const [note, setNote] = useState("");
  const selected = ACTIONS.find((a) => a.value === action);
  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div className={c("ui-modal ui-modal--wide")} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3 className={c("ui-modal-title")}>{name} 님 신고 처리</h3>
        <p className={c("ui-modal-body")}>조치를 선택하세요. 결과는 신고자와 대상에게 알림으로 전달돼요.</p>
        <div className={c("ui-opts")} role="radiogroup" aria-label="제재 조치">
          {ACTIONS.map((a) => (
            <button
              key={a.value}
              type="button"
              role="radio"
              aria-checked={action === a.value ? "true" : "false"}
              className={c(`ui-opt${action === a.value ? " ui-opt--on" : ""}`)}
              onClick={() => setAction(a.value)}
            >
              <span className={c("ui-opt-radio")} />
              <span>{a.label}</span>
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
            variant={action === "BAN" ? "danger" : "primary"}
            disabled={!action}
            onClick={() => {
              if (!action || !selected) return;
              onClose();
              onConfirm(action, selected.label, note.trim());
            }}
          >
            처리 적용
          </Button>
        </div>
      </div>
    </div>
  );
}
