import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { ApiAdminReport, ReportActionType } from "@/api/reports";
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
  reason: string;
  report: ApiAdminReport | null;
  loading: boolean;
  error?: string;
  onRetry: () => void;
  onConfirm: (action: ReportActionType, label: string, note: string) => void;
  onClose: () => void;
};

/** 신고 처리 모달 — 조치 선택 + 처리 메모 */
export function ActionModal({ name, reason, report, loading, error, onRetry, onConfirm, onClose }: ActionModalProps) {
  const [action, setAction] = useState<ReportActionType | null>(null);
  const [note, setNote] = useState("");
  const selected = ACTIONS.find((a) => a.value === action);
  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div className={c("ui-modal ui-modal--wide ui-modal--report")} role="dialog" aria-modal="true" aria-labelledby="admin-report-title" onClick={(e) => e.stopPropagation()}>
        <h3 id="admin-report-title" className={c("ui-modal-title")}>{name} 님 신고 처리</h3>
        <div className={c("ui-report-context")} aria-label="신고 내용">
          <div className={c("ui-report-field")}>
            <span className={c("t-caption ui-report-label")}>신고 사유</span>
            <p className={c("ui-report-value")}>{reason}</p>
          </div>
          {loading && <p className={c("ui-modal-body")} role="status">신고 내용을 불러오는 중이에요.</p>}
          {error && (
            <div className={c("ui-report-field")} role="alert">
              <p className={c("ui-report-value")}>{error}</p>
              <Button variant="secondary" size="sm" onClick={onRetry}>다시 시도</Button>
            </div>
          )}
          {report && (
            <>
              <div className={c("ui-report-field")}>
                <span className={c("t-caption ui-report-label")}>신고자 설명</span>
                <p className={c("ui-report-value")}>{report.report.detail?.trim() || "작성된 상세 설명이 없어요."}</p>
              </div>
              {report.report.targetType !== "MEMBER" && (
                <div className={c("ui-report-field")}>
                  <span className={c("t-caption ui-report-label")}>신고 대상 내용</span>
                  <p className={c("ui-report-value")}>{report.evidenceText?.trim() || "확인할 내용이 없어요."}</p>
                </div>
              )}
            </>
          )}
        </div>
        {report && (
          <>
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
            <label className={c("t-caption ui-report-label")} htmlFor="admin-report-note">대상에게 전달할 사유 (선택)</label>
            <textarea
              id="admin-report-note"
              className={c("ui-textarea")}
              rows={2}
              placeholder="처리 메모 (선택) — 대상에게 전달할 사유"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            <p className={c("t-caption ui-modal-hint")}>영구정지·탈퇴는 되돌리기 어려운 조치예요.</p>
          </>
        )}
        <div className={c("ui-modal-actions")}>
          <Button variant="secondary" onClick={onClose}>취소</Button>
          {report && <Button
            variant={action === "BAN" ? "danger" : "primary"}
            disabled={!action}
            onClick={() => {
              if (!action || !selected || !report) return;
              onClose();
              onConfirm(action, selected.label, note.trim());
            }}
          >
            처리 적용
          </Button>}
        </div>
      </div>
    </div>
  );
}
