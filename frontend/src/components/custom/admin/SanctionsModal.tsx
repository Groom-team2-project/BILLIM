import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { ApiSanction } from "@/api/reports";
import overlayStyles from "@/components/overlay/overlay.module.css";
import { classes } from "@/utils/classes";

const c = classes(overlayStyles);

/** 제재 종류 문구. 신고 처리 모달의 조치 문구와 같은 말을 쓴다 */
const KIND_LABEL: Record<ApiSanction["kind"], string> = {
  SUSPEND: "일시정지",
  BAN: "영구정지",
};

function dateLabel(iso: string): string {
  const at = new Date(iso);
  return `${at.getMonth() + 1}/${at.getDate()}`;
}

/** 제재 한 줄의 기간. 영구정지는 끝이 없다 */
function periodLabel(sanction: ApiSanction): string {
  if (!sanction.endsAt) {
    return `${dateLabel(sanction.startsAt)}부터 · 기간 없음`;
  }
  return `${dateLabel(sanction.startsAt)} ~ ${dateLabel(sanction.endsAt)}`;
}

/** 제재 한 줄의 상태. 해제는 기간 만료와 구분해서 보여준다 */
function statusLabel(sanction: ApiSanction): string {
  if (sanction.revokedAt) {
    return `${dateLabel(sanction.revokedAt)}에 해제됨`;
  }
  return sanction.isEffective ? "적용 중" : "기간 종료";
}

type SanctionsModalProps = {
  /** 대상 회원 이름. 제재 응답에는 이름이 없어 호출하는 화면이 넘겨준다 */
  name: string;
  /** null이면 불러오는 중 */
  rows: ApiSanction[] | null;
  error?: string;
  /** 해제 요청 중인 제재 id */
  revoking?: string;
  onRetry: () => void;
  onRevoke: (sanctionId: string, reason: string) => void;
  onClose: () => void;
};

/**
 * 제재 이력과 해제 (API 명세 E_061·E_062).
 *
 * <p>해제는 사유가 필수다(E_062). 전역 사유 모달을 띄우면 이 모달 위에 모달이 겹치므로,
 * 고른 제재 줄을 펼쳐 그 자리에서 사유를 받는다.
 *
 * <p>적용 중인 제재만 해제 버튼을 가진다. 기간이 끝난 제재와 이미 해제된 제재는 되돌릴 것이
 * 없고, 해제해도 상태가 달라지지 않는다.
 */
export function SanctionsModal({
  name, rows, error, revoking, onRetry, onRevoke, onClose,
}: SanctionsModalProps) {
  const [asking, setAsking] = useState<string | null>(null);
  const [reason, setReason] = useState("");

  const ask = (sanctionId: string) => {
    setAsking(sanctionId);
    setReason("");
  };

  return (
    <div className={c("ui-scrim")} onClick={onClose}>
      <div
        className={c("ui-modal ui-modal--wide ui-modal--report")}
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-sanctions-title"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 id="admin-sanctions-title" className={c("ui-modal-title")}>{name} 님 제재 이력</h3>

        <div className={c("ui-report-context")} aria-label="제재 이력">
          {rows === null && !error && (
            <p className={c("ui-modal-body")} role="status">제재 이력을 불러오는 중이에요.</p>
          )}

          {error && (
            <div className={c("ui-report-field")} role="alert">
              <p className={c("ui-report-value")}>{error}</p>
              <Button variant="secondary" size="sm" onClick={onRetry}>다시 시도</Button>
            </div>
          )}

          {rows !== null && !error && rows.length === 0 && (
            <p className={c("ui-modal-body")}>받은 제재가 없어요. 경고는 제재에 포함되지 않아요.</p>
          )}

          {rows?.map((sanction) => (
            <div key={sanction.id} className={c("ui-report-field")}>
              <span className={c("t-caption ui-report-label")}>
                {KIND_LABEL[sanction.kind]} · {periodLabel(sanction)}
              </span>
              <p className={c("ui-report-value")}>{statusLabel(sanction)}</p>

              {sanction.isEffective && asking !== sanction.id && (
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={Boolean(revoking)}
                  onClick={() => ask(sanction.id)}
                >
                  제재 해제
                </Button>
              )}

              {asking === sanction.id && (
                <>
                  <label className={c("t-caption ui-report-label")} htmlFor="admin-revoke-reason">
                    해제 사유 (필수)
                  </label>
                  <textarea
                    id="admin-revoke-reason"
                    className={c("ui-textarea")}
                    rows={2}
                    placeholder="왜 해제하는지 적어 주세요. 운영 기록에 남아요."
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                  <div className={c("ui-modal-actions")}>
                    <Button variant="secondary" size="sm" onClick={() => setAsking(null)}>취소</Button>
                    <Button
                      size="sm"
                      disabled={!reason.trim() || Boolean(revoking)}
                      onClick={() => onRevoke(sanction.id, reason.trim())}
                    >
                      {revoking === sanction.id ? "해제 중…" : "해제"}
                    </Button>
                  </div>
                </>
              )}
            </div>
          ))}
        </div>

        <p className={c("t-caption ui-modal-hint")}>
          해제해도 다른 유효한 제재가 남아 있으면 이용은 계속 제한돼요.
        </p>

        <div className={c("ui-modal-actions")}>
          <Button variant="secondary" onClick={onClose}>닫기</Button>
        </div>
      </div>
    </div>
  );
}
