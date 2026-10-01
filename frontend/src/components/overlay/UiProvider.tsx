import { useCallback, useMemo, useRef, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { UiContext, type ConfirmOptions, type ReasonOptions, type ReportOptions } from "@/components/overlay/UiContext";
import styles from "@/components/overlay/overlay.module.css";
import { classes } from "@/utils/classes";

const c = classes(styles);

/** 토스트 유지 시간 — 프로토타입 기준 2.6초 */
const TOAST_MS = 2600;

/** 신고 사유 — API 명세 reasonCode 6종과 1:1 */
const REPORT_REASONS = [
  "돈이나 보증금을 요구했어요",
  "물건 상태가 설명과 크게 달라요",
  "약속을 반복해서 어겨요",
  "무례하거나 위협적인 언행을 했어요",
  "사기가 의심돼요",
  "기타",
];

function ConfirmModal({ opts, onClose }: { opts: ConfirmOptions; onClose: () => void }) {
  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div className={c("ui-modal")} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3 className={c("ui-modal-title")}>{opts.title}</h3>
        {opts.body ? <p className={c("ui-modal-body")}>{opts.body}</p> : null}
        <div className={c("ui-modal-actions")}>
          <Button variant="secondary" onClick={onClose}>{opts.cancel ?? "취소"}</Button>
          <Button
            variant={opts.danger ? "danger" : "primary"}
            onClick={() => {
              const fn = opts.onConfirm;
              onClose();
              fn?.();
            }}
          >
            {opts.confirm ?? "확인"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function ReasonModal({ opts, onClose }: { opts: ReasonOptions; onClose: () => void }) {
  const [value, setValue] = useState("");
  const valid = !opts.required || value.trim().length > 0;
  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div className={c("ui-modal")} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3 className={c("ui-modal-title")}>{opts.title}</h3>
        {opts.body ? <p className={c("ui-modal-body")}>{opts.body}</p> : null}
        <textarea
          className={c("ui-textarea")}
          rows={3}
          placeholder={opts.placeholder}
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
        <div className={c("ui-modal-actions")}>
          <Button variant="secondary" onClick={onClose}>취소</Button>
          <Button
            variant={opts.danger ? "danger" : "primary"}
            disabled={!valid}
            onClick={() => {
              const fn = opts.onConfirm;
              onClose();
              fn?.(value.trim());
            }}
          >
            {opts.confirm ?? "확인"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function ReportModal({ opts, onClose }: { opts: ReportOptions; onClose: () => void }) {
  const [reason, setReason] = useState<string | null>(null);
  const [other, setOther] = useState("");
  const valid = !!reason && (reason !== "기타" || other.trim().length > 0);
  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div className={c("ui-modal ui-modal--wide")} role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3 className={c("ui-modal-title")}>{opts.name} 님을 신고할까요?</h3>
        <p className={c("ui-modal-body")}>신고 이유를 선택해 주세요. 접수된 신고는 운영팀이 확인해요.</p>
        <div className={c("ui-opts")} role="radiogroup" aria-label="신고 이유">
          {REPORT_REASONS.map((r) => (
            <button
              key={r}
              type="button"
              role="radio"
              aria-checked={reason === r ? "true" : "false"}
              className={`ui-opt${reason === r ? " ui-opt--on" : ""}`}
              onClick={() => setReason(r)}
            >
              <span className={c("ui-opt-radio")} />
              <span>{r}</span>
            </button>
          ))}
        </div>
        {reason === "기타" ? (
          <textarea
            className={c("ui-textarea")}
            rows={3}
            placeholder="신고 이유를 자세히 적어 주세요."
            value={other}
            onChange={(e) => setOther(e.target.value)}
          />
        ) : null}
        <p className={c("t-caption ui-modal-hint")}>허위 신고는 이용이 제한될 수 있어요.</p>
        <div className={c("ui-modal-actions")}>
          <Button variant="secondary" onClick={onClose}>취소</Button>
          <Button
            variant="danger"
            disabled={!valid}
            onClick={() => {
              const fn = opts.onConfirm;
              const picked = reason === "기타" ? other.trim() : (reason ?? "");
              onClose();
              fn?.(picked);
            }}
          >
            신고
          </Button>
        </div>
      </div>
    </div>
  );
}

export function UiProvider({ children }: { children: ReactNode }) {
  const [modal, setModal] = useState<ConfirmOptions | null>(null);
  const [reasonModal, setReasonModal] = useState<ReasonOptions | null>(null);
  const [reportModal, setReportModal] = useState<ReportOptions | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const toastId = useRef(0);

  const confirm = useCallback((opts: ConfirmOptions) => setModal(opts), []);
  const reason = useCallback((opts: ReasonOptions) => setReasonModal(opts), []);
  const report = useCallback((opts: ReportOptions) => setReportModal(opts), []);
  const toast = useCallback((msg: string) => {
    const id = ++toastId.current;
    setToastMsg(msg);
    setTimeout(() => {
      if (toastId.current === id) setToastMsg(null);
    }, TOAST_MS);
  }, []);
  const api = useMemo(() => ({ confirm, reason, report, toast }), [confirm, reason, report, toast]);

  return (
    <UiContext.Provider value={api}>
      {children}

      {modal ? <ConfirmModal opts={modal} onClose={() => setModal(null)} /> : null}
      {reasonModal ? <ReasonModal opts={reasonModal} onClose={() => setReasonModal(null)} /> : null}
      {reportModal ? <ReportModal opts={reportModal} onClose={() => setReportModal(null)} /> : null}

      {toastMsg ? (
        <div className={c("ui-toast")} role="status">
          <Icon name="check" size={18} />
          {toastMsg}
        </div>
      ) : null}
    </UiContext.Provider>
  );
}
